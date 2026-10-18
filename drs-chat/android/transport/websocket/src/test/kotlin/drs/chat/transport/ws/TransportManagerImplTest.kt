package drs.chat.transport.ws

import app.cash.turbine.test
import drs.chat.domain.model.TransportId
import drs.chat.transport.LinkState
import drs.chat.transport.SendReceipt
import drs.chat.transport.Transport
import drs.chat.transport.TransportMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Test-only Transport implementation. The linkState is a MutableStateFlow
 * so tests can flip it to UP/CONNECTING/DOWN synchronously; incoming is a
 * SharedFlow so tests can `tryEmit` synthetic frames.
 */
class FakeTransport(
    private val idValue: String = "fake",
    private val sendBehavior: (ByteArray, ByteArray) -> SendReceipt = { _, _ -> SendReceipt.Sent },
) : Transport {
    override val id: TransportId = TransportId(idValue)

    private val _linkState = MutableStateFlow(LinkState.DOWN)
    override val linkState: StateFlow<LinkState> = _linkState.asStateFlow()

    private val _incoming = MutableSharedFlow<TransportMessage>(extraBufferCapacity = 64)
    override val incoming: Flow<TransportMessage> = _incoming.asSharedFlow()

    var started = false
        private set

    var stopped = false
        private set

    val sendCount: Int get() = _sendCount
    private var _sendCount: Int = 0

    fun setLinkState(state: LinkState) {
        _linkState.value = state
    }

    fun emitIncoming(bytes: ByteArray) {
        _incoming.tryEmit(TransportMessage(bytes, 0L, id))
    }

    override suspend fun start() {
        started = true
        _linkState.value = LinkState.CONNECTING
    }

    override suspend fun stop() {
        stopped = true
        _linkState.value = LinkState.DOWN
    }

    override suspend fun send(
        recipientKeyId: ByteArray,
        bytes: ByteArray,
    ): SendReceipt {
        _sendCount++
        return sendBehavior(recipientKeyId, bytes)
    }
}

@Suppress("StringLiteralDuplication")
class TransportManagerImplTest {
    private fun newManager(transport: FakeTransport): Pair<TransportManagerImpl, TestScope> {
        val scope = TestScope(UnconfinedTestDispatcher())
        val mgr = TransportManagerImpl(transport, scope)
        return mgr to scope
    }

    // 1. Initial state maps from transport's initial DOWN → Queued
    @Test
    fun `initial state maps transport DOWN to manager Queued`() {
        val transport = FakeTransport()
        val (mgr, _) = newManager(transport)
        assertEquals(drs.chat.transport.ConnectionState.Queued, mgr.connectionState.value)
    }

    // 2. When transport goes CONNECTING → manager reflects Connecting
    @Test
    fun `transport CONNECTING maps to manager Connecting`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            transport.setLinkState(LinkState.CONNECTING)
            assertEquals(drs.chat.transport.ConnectionState.Connecting, mgr.connectionState.value)
        }

    // 3. When transport goes UP → manager reflects Connected
    @Test
    fun `transport UP maps to manager Connected`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            transport.setLinkState(LinkState.UP)
            assertEquals(drs.chat.transport.ConnectionState.Connected, mgr.connectionState.value)
        }

    // 4. When transport goes back DOWN → manager reflects Queued
    @Test
    fun `transport DOWN after UP maps to manager Queued`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            transport.setLinkState(LinkState.UP)
            transport.setLinkState(LinkState.DOWN)
            assertEquals(drs.chat.transport.ConnectionState.Queued, mgr.connectionState.value)
        }

    // 5. send delegates to transport → SendResult.Accepted on Sent
    @Test
    fun `send delegates to transport and returns Accepted on Sent`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            val result = mgr.send(ByteArray(16), ByteArray(8))
            assertEquals(drs.chat.transport.SendResult.Accepted, result)
            assertEquals(1, transport.sendCount)
        }

    // 6. send returns Rejected when transport returns Rejected
    @Test
    fun `send returns Rejected when transport rejects`() =
        runTest {
            val transport = FakeTransport(sendBehavior = { _, _ -> SendReceipt.Rejected("offline") })
            val (mgr, _) = newManager(transport)
            val result = mgr.send(ByteArray(16), ByteArray(8))
            assertTrue(result is drs.chat.transport.SendResult.Rejected)
            assertEquals("offline", (result as drs.chat.transport.SendResult.Rejected).reason)
        }

    // 7. start() and stop() delegate to transport
    @Test
    fun `start and stop delegate to transport`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            mgr.start()
            assertTrue(transport.started)
            mgr.stop()
            assertTrue(transport.stopped)
        }

    // 8. incomingEnvelopes flow strips TransportMessage to ByteArray
    @Test
    fun `incomingEnvelopes strips wrapper to bytes`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            mgr.incomingEnvelopes.test {
                // First emission: a 4-byte 0x01 frame
                transport.emitIncoming(ByteArray(4) { 0x01 })
                val first = awaitItem()
                assertEquals(4, first.size)
                assertEquals(0x01.toByte(), first[0])
                // Second emission: a 4-byte 0x02 frame
                transport.emitIncoming(ByteArray(4) { 0x02 })
                val second = awaitItem()
                assertEquals(0x02.toByte(), second[0])
            }
        }

    // 9. mapLinkState is exposed for tests — UP → Connected
    @Test
    fun `mapLinkState UP returns Connected`() {
        val transport = FakeTransport()
        val (mgr, _) = newManager(transport)
        assertEquals(drs.chat.transport.ConnectionState.Connected, mgr.mapLinkState(LinkState.UP))
    }

    // 10. sendCount increments on every call
    @Test
    fun `multiple sends increment sendCount`() =
        runTest {
            val transport = FakeTransport()
            val (mgr, _) = newManager(transport)
            repeat(3) { mgr.send(ByteArray(16), ByteArray(8)) }
            assertEquals(3, transport.sendCount)
        }

    // 11. ReconnectPolicy integration — long uptime resets the policy
    @Test
    fun `reconnect policy resets after stable uptime`() {
        val policy = ReconnectPolicy(random = { 0.5 })
        repeat(5) { policy.nextDelay() }
        policy.observeUptime(uptimeMs = 60_000L)
        assertEquals(0, policy.currentAttempt())
    }

    // 12. ReconnectPolicy cap at 60s with jitter stays within bounds
    @Test
    fun `reconnect policy 60s cap with jitter stays within bounds`() {
        val policy = ReconnectPolicy()
        // Walk to attempt 7+ (capped)
        repeat(7) { policy.nextDelay() }
        val (lo, hi) = policy.jitterBoundsFor(60_000L)
        repeat(50) {
            val d = policy.nextDelay()
            assertTrue(d in lo..hi, "delay $d outside [$lo, $hi]")
        }
    }
}
