package drs.chat.domain.model

import drs.chat.domain.model.MessageId as DrsMessageId

enum class OutboxState { QUEUED, IN_FLIGHT, SENT, DELIVERED, READ, FAILED }

data class OutboxEntry(
    val messageId: DrsMessageId,
    val envelopeBytes: ByteArray,
    val toKey: KeyId,
    val state: OutboxState,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String? = null,
)
