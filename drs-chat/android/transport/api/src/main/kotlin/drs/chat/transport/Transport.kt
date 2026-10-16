package drs.chat.transport

import drs.chat.domain.model.TransportId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class LinkState { DOWN, CONNECTING, UP }
sealed interface ConnectionState {
    data object Connected : ConnectionState
    data object Queued : ConnectionState
    data object Connecting : ConnectionState
}
sealed interface SendReceipt {
    data object Sent : SendReceipt
    data class Rejected(val reason: String) : SendReceipt
}
sealed interface SendResult {
    data object Accepted : SendResult
    data class Rejected(val reason: String) : SendResult
    data object QueuedLocally : SendResult
}
data class TransportMessage(val bytes: ByteArray, val receivedAt: Long, val transportId: TransportId) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is TransportMessage && bytes.contentEquals(other.bytes) && receivedAt == other.receivedAt && transportId == other.transportId)
    override fun hashCode(): Int = bytes.contentHashCode()
}
interface Transport {
    val id: TransportId
    val linkState: StateFlow<LinkState>
    val incoming: Flow<TransportMessage>
    suspend fun start()
    suspend fun stop()
    suspend fun send(recipientKeyId: ByteArray, bytes: ByteArray): SendReceipt
}
interface TransportManager {
    val connectionState: StateFlow<ConnectionState>
    val incomingEnvelopes: Flow<ByteArray>
    suspend fun send(recipientKeyId: ByteArray, envelopeBytes: ByteArray): SendResult
    suspend fun start()
    suspend fun stop()
}
