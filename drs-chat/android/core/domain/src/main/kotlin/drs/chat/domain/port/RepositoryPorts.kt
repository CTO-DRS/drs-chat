package drs.chat.domain.port

import drs.chat.protocol.KeyId

/**
 * Outbox repository port — persistence + query surface for the local outbox.
 *
 * Adapter lives in :core:data (Room-backed). Domain depends on this port;
 * the drainer/facade orchestrates against it. Fakes live in test fixtures.
 */
interface OutboxRepository {
    suspend fun enqueue(entry: EnqueueRequest): drs.chat.domain.model.OutboxEntry

    suspend fun allDue(now: Long): List<drs.chat.domain.model.OutboxEntry>

    suspend fun update(entry: drs.chat.domain.model.OutboxEntry)

    suspend fun find(messageId: ByteArray): drs.chat.domain.model.OutboxEntry?

    suspend fun markInFlight(
        messageId: ByteArray,
        now: Long,
    )

    suspend fun markSent(
        messageId: ByteArray,
        now: Long,
    )

    suspend fun markDelivered(
        messageId: ByteArray,
        now: Long,
    )

    suspend fun markRead(
        messageId: ByteArray,
        now: Long,
    )

    suspend fun markFailed(
        messageId: ByteArray,
        now: Long,
        error: String,
        finalAttemptCount: Int,
    )
}

/**
 * Request to enqueue a new message into the outbox. The repository assigns
 * attempts=0, nextAttemptAt=now, state=QUEUED. The envelope bytes are already
 * signed+encrypted (the facade called :core:crypto before this).
 */
data class EnqueueRequest(
    val messageId: ByteArray,
    val envelopeBytes: ByteArray,
    val toKey: KeyId,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is EnqueueRequest &&
                    messageId.contentEquals(other.messageId) &&
                    envelopeBytes.contentEquals(other.envelopeBytes) &&
                    toKey == other.toKey &&
                    createdAt == other.createdAt
            )

    override fun hashCode(): Int {
        var r = messageId.contentHashCode()
        r = 31 * r + envelopeBytes.contentHashCode()
        r = 31 * r + toKey.hashCode()
        r = 31 * r + createdAt.hashCode()
        return r
    }
}

/**
 * Message store (incoming + outgoing messages after their envelope is
 * delivered/decoded). Adapter in :core:data (Room).
 */
interface MessageRepository {
    suspend fun storeIncoming(
        from: KeyId,
        message: drs.chat.protocol.InnerMessage,
        envelopeMessageId: ByteArray,
        receivedAt: Long,
    )

    suspend fun markOutgoing(
        messageId: ByteArray,
        state: drs.chat.domain.model.MessageStatus,
        at: Long,
    )

    suspend fun history(
        contact: KeyId,
        limit: Int,
    ): List<drs.chat.domain.model.MessageRecord>
}

/**
 * Contact store. Adapter in :core:data (Room).
 */
interface ContactRepository {
    suspend fun upsert(contact: drs.chat.domain.model.Contact)

    suspend fun find(keyId: KeyId): drs.chat.domain.model.Contact?

    suspend fun all(): List<drs.chat.domain.model.Contact>
}

/**
 * Transport port — domain's view of the byte pipe (ADR-0002). The :transport:api
 * module implements this; :core:domain consumes it. WireEnvelope import is
 * deliberately omitted: the port layer stays wire-agnostic; the receive
 * pipeline takes a WireEnvelope directly.
 */
interface TransportPort {
    suspend fun send(envelopeBytes: ByteArray): SendResult
}

sealed interface SendResult {
    data object Sent : SendResult

    data class Rejected(val reason: String) : SendResult
}

/** Clock port — injected everywhere; lets tests deterministically advance time. */
interface Clock {
    fun now(): Long
}

/** Id generator — injectable so tests can pin messageIds. */
interface IdGenerator {
    fun newMessageId(): ByteArray
}

/** Vault port — :core:data adapter wraps Keystore. Domain uses plaintext bytes only. */
interface VaultPort {
    fun wrap(plaintext: ByteArray): ByteArray

    fun unwrap(ciphertext: ByteArray): ByteArray
}
