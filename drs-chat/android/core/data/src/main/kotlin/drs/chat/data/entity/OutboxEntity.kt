package drs.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox row — pre-send buffer for reliability (ADR-0002 §4).
 *
 * The messageIdHex is the content-based key; `state` is the OutboxState
 * name (QUEUED/IN_FLIGHT/SENT/DELIVERED/READ/FAILED). `attempts` and
 * `nextAttemptAt` drive the backoff drainer.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["messageIdHex"], unique = true),
        Index(value = ["state"]),
        Index(value = ["nextAttemptAt"]),
    ],
)
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageIdHex: String,
    val envelopeBytes: ByteArray,
    val toKeyIdHex: String,
    val state: String,
    val attempts: Int,
    val nextAttemptAt: Long,
    val lastError: String?,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is OutboxEntity &&
                    id == other.id &&
                    messageIdHex == other.messageIdHex &&
                    envelopeBytes.contentEquals(other.envelopeBytes) &&
                    toKeyIdHex == other.toKeyIdHex &&
                    state == other.state &&
                    attempts == other.attempts &&
                    nextAttemptAt == other.nextAttemptAt &&
                    lastError == other.lastError
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + messageIdHex.hashCode()
        r = 31 * r + envelopeBytes.contentHashCode()
        r = 31 * r + toKeyIdHex.hashCode()
        r = 31 * r + state.hashCode()
        r = 31 * r + attempts.hashCode()
        r = 31 * r + nextAttemptAt.hashCode()
        r = 31 * r + (lastError?.hashCode() ?: 0)
        return r
    }
}
