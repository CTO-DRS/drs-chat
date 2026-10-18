package drs.chat.transport.ws

import drs.chat.transport.ConnectionState
import drs.chat.transport.LinkState
import drs.chat.transport.SendResult
import drs.chat.transport.Transport
import drs.chat.transport.TransportManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * TransportManagerImpl — Phase 1: aggregates ONE transport (the WebSocketTransport).
 *
 * Per ADR-0002 §6: when transport #2 lands (BLE/LoRa), this class moves to a
 * new `:transport:core` module in a refactor that touches zero domain files
 * (they reference only :transport:api). Phase 1 keeps it in :transport:websocket
 * because there's exactly one transport and a manager-only module would be
 * empty ceremony.
 *
 * Aggregation rules (Phase 1, single transport):
 *   - Transport.linkState UP → ConnectionState.Connected
 *   - Transport.linkState CONNECTING → ConnectionState.Connecting
 *   - Transport.linkState DOWN → ConnectionState.Queued (outbox holds)
 *
 * The incomingEnvelopes flow strips the TransportMessage wrapper and emits
 * only the raw envelope bytes — the domain layer consumes ByteArray, not
 * transport-typed values.
 */
class TransportManagerImpl(
    private val transport: Transport,
    private val scope: CoroutineScope,
) : TransportManager {
    private val _connectionState = MutableStateFlow<ConnectionState>(mapLinkState(transport.linkState.value))
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingEnvelopes = MutableSharedFlow<ByteArray>(extraBufferCapacity = 64)
    override val incomingEnvelopes: Flow<ByteArray> = _incomingEnvelopes.asSharedFlow()

    init {
        // Propagate transport.linkState → manager.connectionState
        scope.launch {
            transport.linkState.collect { state ->
                _connectionState.value = mapLinkState(state)
            }
        }
        // Bridge incoming TransportMessages → incomingEnvelopes (ByteArray-only)
        scope.launch {
            transport.incoming.collect { msg ->
                _incomingEnvelopes.tryEmit(msg.bytes)
            }
        }
    }

    private var started = false

    override suspend fun start() {
        if (started) return
        started = true
        transport.start()
    }

    override suspend fun stop() {
        started = false
        transport.stop()
    }

    override suspend fun send(
        recipientKeyId: ByteArray,
        envelopeBytes: ByteArray,
    ): SendResult =
        when (val receipt = transport.send(recipientKeyId, envelopeBytes)) {
            is drs.chat.transport.SendReceipt.Sent -> SendResult.Accepted
            is drs.chat.transport.SendReceipt.Rejected -> SendResult.Rejected(receipt.reason)
        }

    /** Map a [LinkState] to the corresponding [ConnectionState]. Exposed for tests. */
    fun mapLinkState(state: LinkState): ConnectionState =
        when (state) {
            LinkState.UP -> ConnectionState.Connected
            LinkState.CONNECTING -> ConnectionState.Connecting
            LinkState.DOWN -> ConnectionState.Queued
        }
}
