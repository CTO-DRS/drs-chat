package drs.chat.domain.model

import drs.chat.protocol.KeyId

/**
 * Persisted message record (decoded inner message + envelope metadata).
 * Constructed by the receive pipeline from a [drs.chat.protocol.InnerMessage].
 */
data class MessageRecord(
    val messageId: ByteArray,
    val fromKey: KeyId?,
    val toKey: KeyId?,
    val text: String,
    val sentAt: Long,
    val receivedAt: Long,
    val direction: Direction,
    val status: MessageStatus,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is MessageRecord &&
                    messageId.contentEquals(other.messageId) &&
                    fromKey == other.fromKey &&
                    toKey == other.toKey &&
                    text == other.text &&
                    sentAt == other.sentAt &&
                    receivedAt == other.receivedAt &&
                    direction == other.direction &&
                    status == other.status
            )

    override fun hashCode(): Int {
        var r = messageId.contentHashCode()
        r = 31 * r + (fromKey?.hashCode() ?: 0)
        r = 31 * r + (toKey?.hashCode() ?: 0)
        r = 31 * r + text.hashCode()
        r = 31 * r + sentAt.hashCode()
        r = 31 * r + receivedAt.hashCode()
        r = 31 * r + direction.hashCode()
        r = 31 * r + status.hashCode()
        return r
    }
}
