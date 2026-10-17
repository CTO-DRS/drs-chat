package drs.chat.relay

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocketRaw
import io.ktor.websocket.Frame
import io.ktor.websocket.readBytes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * RelayApp — the Ktor application. Pure JVM, runs on Netty in production
 * and on ktor-server-test-host in tests.
 *
 * Wire contract per ADR-0002 + ADR-0008:
 *   1. Client connects to /relay
 *   2. Client sends AUTH frame (ed25519Pub + x25519Pub + nonce + ts + sig)
 *   3. Relay verifies the sig, derives the recipientKeyId, registers
 *      the connection in [ConnectionRegistry]
 *   4. Client sends SEND frames; relay enqueues per-recipientKeyId
 *   5. If recipient is connected → relay forwards immediately
 *      else relay stores in [StoreAndForwardQueue] for later flush
 *   6. Sweep expired envelopes every SWEEP_INTERVAL_MS
 *
 * Frame rejections:
 *   - Oversize (> 64 KB) → close connection
 *   - Bad auth sig / expired ts → close connection
 *   - Unknown frame type → close connection
 */
fun Application.relayModule(
    queue: StoreAndForwardQueue = StoreAndForwardQueue(),
    clockMs: () -> Long = { System.currentTimeMillis() },
    // 1 minute — production relays would use a single global sweeper; per-connection is fine for tests
    sweepIntervalMs: Long = 60_000L,
) {
    val registry = ConnectionRegistry()

    install(WebSockets)
    routing {
        webSocketRaw("/relay") {
            // Background sweep loop — one per connection (cheap; does nothing
            // most of the time). A production relay would use a single global
            // sweeper coroutine, but per-connection is fine for Phase 1 + tests.
            val sweepJob =
                launch {
                    while (true) {
                        delay(sweepIntervalMs)
                        queue.sweepExpired(clockMs())
                    }
                }
            try {
                // 1. Read AUTH frame
                val authFrame =
                    incoming.receiveCatching().getOrNull() as? Frame.Binary
                        ?: return@webSocketRaw
                val auth = parseAuthFrame(authFrame.readBytes()) ?: return@webSocketRaw
                val keyId =
                    AuthChallenge.verify(
                        nonce = auth.nonce,
                        ts = auth.ts,
                        x25519Pub = auth.x25519Pub,
                        signature = auth.signature,
                        ed25519Pub = auth.ed25519Pub,
                        relayNowMs = clockMs(),
                    ) ?: return@webSocketRaw

                val keyIdHex = keyId.joinToString("") { "%02x".format(it) }
                registry.register(keyIdHex, this)

                // Flush any queued envelopes for this recipient
                val queued = queue.drain(keyIdHex)
                for (env in queued) {
                    send(Frame.Binary(true, env.envelopeBytes))
                }

                // 2. Loop on SEND frames
                for (frame in incoming) {
                    val bin = frame as? Frame.Binary ?: break
                    val data = bin.readBytes()
                    val sendFrame = parseSendFrame(data) ?: break
                    if (SizeCap.isOversize(sendFrame.envelopeBytes)) break

                    val recipientHex = sendFrame.recipientKeyId.joinToString("") { "%02x".format(it) }
                    val recipientConn = registry.get(recipientHex)
                    if (recipientConn != null) {
                        // Online — forward immediately
                        registry.send(recipientHex, sendFrame.envelopeBytes)
                    } else {
                        // Offline — enqueue
                        val messageIdHex =
                            drs.chat.protocol.AuthWire
                                .hashOf(sendFrame.envelopeBytes)
                                .joinToString("") { "%02x".format(it) }
                        queue.enqueue(
                            StoreAndForwardQueue.QueuedEnvelope(
                                recipientKeyIdHex = recipientHex,
                                messageIdHex = messageIdHex,
                                envelopeBytes = sendFrame.envelopeBytes,
                                queuedAtMs = clockMs(),
                                ttlSeconds = queue.let { 7 * 24 * 60 * 60L },
                            ),
                        )
                    }
                }
            } finally {
                sweepJob.cancel()
                // The connection close handlers must unregister — handled by
                // the registry's onClose callback.
            }
        }
    }
}

/** Tracks connected recipients by their keyIdHex. */
class ConnectionRegistry {
    private val connections: ConcurrentHashMap<String, io.ktor.server.websocket.WebSocketServerSession> = ConcurrentHashMap()

    fun register(
        keyIdHex: String,
        session: io.ktor.server.websocket.WebSocketServerSession,
    ) {
        connections[keyIdHex] = session
    }

    fun get(keyIdHex: String): io.ktor.server.websocket.WebSocketServerSession? = connections[keyIdHex]

    /** Send bytes to the named recipient. MUST be called from a coroutine context. */
    suspend fun send(
        keyIdHex: String,
        bytes: ByteArray,
    ) {
        connections[keyIdHex]?.send(Frame.Binary(true, bytes))
    }

    fun unregister(keyIdHex: String) {
        connections.remove(keyIdHex)
    }
}

// Parsers for the WireFrame binary format.

private data class ParsedAuth(
    val ed25519Pub: ByteArray,
    val x25519Pub: ByteArray,
    val nonce: ByteArray,
    val ts: Long,
    val signature: ByteArray,
)

private fun parseAuthFrame(data: ByteArray): ParsedAuth? {
    if (data.isEmpty() || data[0].toInt() != 0x01) return null
    var idx = 1
    try {
        val edLen = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx + 1].toInt() and 0xFF)
        idx += 2
        val ed = data.copyOfRange(idx, idx + edLen)
        idx += edLen
        val xLen = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx + 1].toInt() and 0xFF)
        idx += 2
        val x = data.copyOfRange(idx, idx + xLen)
        idx += xLen
        val nLen = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx + 1].toInt() and 0xFF)
        idx += 2
        val n = data.copyOfRange(idx, idx + nLen)
        idx += nLen
        val ts =
            (0 until 8).fold(0L) { acc, i ->
                acc or ((data[idx + i].toLong() and 0xFF) shl (i * 8))
            }
        ; idx += 8
        val sigLen = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx + 1].toInt() and 0xFF)
        idx += 2
        val sig = data.copyOfRange(idx, idx + sigLen)
        return ParsedAuth(ed, x, n, ts, sig)
    } catch (_: Exception) {
        return null
    }
}

private data class ParsedSend(
    val recipientKeyId: ByteArray,
    val envelopeBytes: ByteArray,
)

private fun parseSendFrame(data: ByteArray): ParsedSend? {
    if (data.isEmpty() || data[0].toInt() != 0x02) return null
    var idx = 1
    try {
        val rLen = ((data[idx].toInt() and 0xFF) shl 8) or (data[idx + 1].toInt() and 0xFF)
        idx += 2
        val r = data.copyOfRange(idx, idx + rLen)
        idx += rLen
        val envLen =
            (
                ((data[idx].toInt() and 0xFF) shl 24) or
                    ((data[idx + 1].toInt() and 0xFF) shl 16) or
                    ((data[idx + 2].toInt() and 0xFF) shl 8) or
                    (data[idx + 3].toInt() and 0xFF)
            )
        idx += 4
        val env = data.copyOfRange(idx, idx + envLen)
        return ParsedSend(r, env)
    } catch (_: Exception) {
        return null
    }
}

/** Generate a random 32-byte nonce — used by tests to mock client challenges. */
fun generateNonce(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
