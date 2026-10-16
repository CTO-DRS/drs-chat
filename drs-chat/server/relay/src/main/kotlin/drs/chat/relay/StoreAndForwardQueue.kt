package drs.chat.relay

import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory store-and-forward queue per recipientKeyId.
 *
 * Phase 1: in-memory — adequate for the dumb-relay contract (a relay restart
 * loses un-deivered envelopes; that is documented as acceptable since the
 * SENDER keeps the outbox entry until DELIVERED, not just SENT). Per
 * ADR-0002 §4: end-to-end reliability comes from layers above the relay.
 *
 * Future: replace with SQLite-backed queue per PLAN §4.3 ("SQLite on the
 * relay for the store-and-forward queue (survives restart)"). The in-memory
 * interface is the same; the swap is internal.
 *
 * TTL enforcement: rows past [ttlSeconds] are evicted by [sweepExpired]
 * (called by the sweeper). Senders can re-send — relay dedupes by envelope
 * SHA-256 hash via the messageId field in the envelope header.
 */
class StoreAndForwardQueue(
    // 7 days default per ADR-0002
    private val ttlSeconds: Long = 7 * 24 * 60 * 60L,
    // backpressure: 1k per recipient
    private val maxQueuePerRecipient: Int = 1000,
) {
    private val queues: ConcurrentHashMap<String, MutableList<QueuedEnvelope>> = ConcurrentHashMap()

    data class QueuedEnvelope(
        val recipientKeyIdHex: String,
        val messageIdHex: String,
        val envelopeBytes: ByteArray,
        val queuedAtMs: Long,
        val ttlSeconds: Long,
    ) {
        override fun equals(other: Any?): Boolean =
            this === other ||
                (
                    other is QueuedEnvelope &&
                        recipientKeyIdHex == other.recipientKeyIdHex &&
                        messageIdHex == other.messageIdHex &&
                        envelopeBytes.contentEquals(other.envelopeBytes) &&
                        queuedAtMs == other.queuedAtMs &&
                        ttlSeconds == other.ttlSeconds
                )

        override fun hashCode(): Int {
            var r = recipientKeyIdHex.hashCode()
            r = 31 * r + messageIdHex.hashCode()
            r = 31 * r + envelopeBytes.contentHashCode()
            r = 31 * r + queuedAtMs.hashCode()
            r = 31 * r + ttlSeconds.hashCode()
            return r
        }
    }

    /** Returns true if stored, false if rejected (size cap or duplicate messageId). */
    fun enqueue(envelope: QueuedEnvelope): Boolean {
        val q = queues.computeIfAbsent(envelope.recipientKeyIdHex) { mutableListOf() }
        synchronized(q) {
            if (q.size >= maxQueuePerRecipient) return false
            if (q.any { it.messageIdHex == envelope.messageIdHex }) return false // dedup
            q.add(envelope)
            return true
        }
    }

    /** Drain all queued envelopes for [recipientKeyIdHex] — for flush-on-connect. */
    fun drain(recipientKeyIdHex: String): List<QueuedEnvelope> {
        val q = queues[recipientKeyIdHex] ?: return emptyList()
        synchronized(q) {
            val taken = q.toList()
            q.clear()
            return taken
        }
    }

    /** Count queued for a recipient — used by tests + observability. */
    fun queuedFor(recipientKeyIdHex: String): Int = queues[recipientKeyIdHex]?.size ?: 0

    /** Remove envelopes whose TTL has expired. Returns the count evicted. */
    fun sweepExpired(nowMs: Long): Int {
        var evicted = 0
        for ((_, q) in queues) {
            synchronized(q) {
                val it = q.iterator()
                while (it.hasNext()) {
                    val env = it.next()
                    if (nowMs - env.queuedAtMs > env.ttlSeconds * 1000L) {
                        it.remove()
                        evicted++
                    }
                }
            }
        }
        return evicted
    }

    /** Total queue size across all recipients — used by tests + metrics. */
    fun totalQueued(): Int = queues.values.sumOf { it.size }
}

/**
 * Hard cap on envelope size per ADR-0001. Envelopes larger than this are
 * rejected at the relay ingress — saves bandwidth + memory.
 */
object SizeCap {
    const val MAX_ENVELOPE_BYTES: Int = 64 * 1024 // 64 KB per ADR-0002

    fun isOversize(bytes: ByteArray): Boolean = bytes.size > MAX_ENVELOPE_BYTES
}

/**
 * Wire-format helper — the relay reads incoming WS frames as CBOR control
 * frames. Phase 1 supports two frame types: AUTH (handshake) and SEND
 * (envelope delivery). The relay does NOT parse the envelope's CBOR — it
 * reads the recipientKeyId from the AUTH frame's ed25519Pub.
 *
 * Future: extend with ACK frames for delivery confirmation.
 */
object WireFrame {
    /** Build an AUTH frame from the client's identity + nonce + signature. */
    fun buildAuth(
        ed25519Pub: ByteArray,
        x25519Pub: ByteArray,
        nonce: ByteArray,
        ts: Long,
        signature: ByteArray,
    ): ByteArray {
        // Phase 1 wire format: simple length-prefixed binary.
        // [type:1=AUTH][edLen:2][ed25519Pub][xLen:2][x25519Pub][nLen:2][nonce][ts:8][sigLen:2][signature]
        val buf = mutableListOf<Byte>()
        buf.add(0x01) // AUTH
        buf.addAll(lengthPrefix(ed25519Pub.size))
        buf.addAll(ed25519Pub.toList())
        buf.addAll(lengthPrefix(x25519Pub.size))
        buf.addAll(x25519Pub.toList())
        buf.addAll(lengthPrefix(nonce.size))
        buf.addAll(nonce.toList())
        for (i in 0 until 8) buf.add(((ts ushr (i * 8)) and 0xFF).toByte())
        buf.addAll(lengthPrefix(signature.size))
        buf.addAll(signature.toList())
        return buf.toByteArray()
    }

    /** Build a SEND frame: [type:1=SEND][recipientLen:2][recipientKeyId][envLen:4][envelopeBytes]. */
    fun buildSend(
        recipientKeyId: ByteArray,
        envelopeBytes: ByteArray,
    ): ByteArray {
        val buf = mutableListOf<Byte>()
        buf.add(0x02) // SEND
        buf.addAll(lengthPrefix(recipientKeyId.size))
        buf.addAll(recipientKeyId.toList())
        // 4-byte length for envelopes (up to 64 KB)
        val envLen = envelopeBytes.size
        buf.add(((envLen ushr 24) and 0xFF).toByte())
        buf.add(((envLen ushr 16) and 0xFF).toByte())
        buf.add(((envLen ushr 8) and 0xFF).toByte())
        buf.add((envLen and 0xFF).toByte())
        buf.addAll(envelopeBytes.toList())
        return buf.toByteArray()
    }

    private fun lengthPrefix(len: Int): List<Byte> =
        listOf(
            ((len ushr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte(),
        )
}
