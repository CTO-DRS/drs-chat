package drs.chat.domain.model

import drs.chat.protocol.KeyId

enum class OutboxState { QUEUED, IN_FLIGHT, SENT, DELIVERED, READ, FAILED }

data class OutboxEntry(
    val messageId: MessageId,
    val envelopeBytes: ByteArray,
    val toKey: KeyId,
    val state: OutboxState,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String? = null,
)
