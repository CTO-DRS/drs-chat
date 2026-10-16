package drs.chat.domain.testing

import drs.chat.domain.model.Contact
import drs.chat.domain.model.Direction
import drs.chat.domain.model.MessageRecord
import drs.chat.domain.model.MessageStatus
import drs.chat.domain.model.OutboxEntry
import drs.chat.domain.model.OutboxState
import drs.chat.domain.port.ContactRepository
import drs.chat.domain.port.EnqueueRequest
import drs.chat.domain.port.IdGenerator
import drs.chat.domain.port.MessageRepository
import drs.chat.domain.port.OutboxRepository
import drs.chat.domain.port.SendResult
import drs.chat.domain.port.TransportPort
import drs.chat.protocol.KeyId
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-memory fakes for all domain ports. Test-only — production adapters live
 * in :core:data (Room-backed). These fakes are intentionally simple: they
 * expose mutable state so tests can assert against them.
 */

class FakeClock(var fixedNow: Long = 0L) : drs.chat.domain.port.Clock {
    override fun now(): Long = fixedNow

    /** Advance the clock by [deltaMs]. Useful for backoff tests. */
    fun advance(deltaMs: Long) {
        fixedNow += deltaMs
    }
}

class FakeIdGenerator : IdGenerator {
    private val counter = AtomicInteger(0)

    /** Returns a deterministic 16-byte id with the counter in the last 4 bytes. */
    override fun newMessageId(): ByteArray {
        val n = counter.incrementAndGet()
        return ByteArray(16).also {
            it[12] = ((n ushr 24) and 0xFF).toByte()
            it[13] = ((n ushr 16) and 0xFF).toByte()
            it[14] = ((n ushr 8) and 0xFF).toByte()
            it[15] = (n and 0xFF).toByte()
        }
    }
}

class FakeOutboxRepository(
    private val clock: () -> Long = { 0L },
) : OutboxRepository {
    val entries = mutableListOf<OutboxEntry>()
    val transitions = mutableListOf<Pair<ByteArray, OutboxState>>()

    override suspend fun enqueue(entry: EnqueueRequest): OutboxEntry {
        val out =
            OutboxEntry(
                drs.chat.domain.model.MessageId(entry.messageId),
                entry.envelopeBytes,
                entry.toKey,
                OutboxState.QUEUED,
                attempts = 0,
                nextAttemptAt = clock(),
            )
        entries += out
        transitions += entry.messageId to OutboxState.QUEUED
        return out
    }

    override suspend fun allDue(now: Long): List<OutboxEntry> = entries.filter { it.nextAttemptAt <= now && it.state == OutboxState.QUEUED }

    override suspend fun update(entry: OutboxEntry) {
        val idx = entries.indexOfFirst { it.messageId.bytes.contentEquals(entry.messageId.bytes) }
        if (idx >= 0) entries[idx] = entry
        transitions += entry.messageId.bytes to entry.state
    }

    override suspend fun find(messageId: ByteArray): OutboxEntry? = entries.firstOrNull { it.messageId.bytes.contentEquals(messageId) }

    override suspend fun markInFlight(
        messageId: ByteArray,
        now: Long,
    ) {
        val idx = entries.indexOfFirst { it.messageId.bytes.contentEquals(messageId) }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(state = OutboxState.IN_FLIGHT)
            transitions += messageId to OutboxState.IN_FLIGHT
        }
    }

    override suspend fun markSent(
        messageId: ByteArray,
        now: Long,
    ) = transitionTo(messageId, OutboxState.SENT)

    override suspend fun markDelivered(
        messageId: ByteArray,
        now: Long,
    ) = transitionTo(messageId, OutboxState.DELIVERED)

    override suspend fun markRead(
        messageId: ByteArray,
        now: Long,
    ) = transitionTo(messageId, OutboxState.READ)

    override suspend fun markFailed(
        messageId: ByteArray,
        now: Long,
        error: String,
        finalAttemptCount: Int,
    ) = transitionTo(messageId, OutboxState.FAILED, finalAttemptCount)

    private fun transitionTo(
        messageId: ByteArray,
        state: OutboxState,
        attempts: Int? = null,
    ) {
        val idx = entries.indexOfFirst { it.messageId.bytes.contentEquals(messageId) }
        if (idx >= 0) {
            entries[idx] = entries[idx].copy(state = state, attempts = attempts ?: entries[idx].attempts)
        }
        transitions += messageId to state
    }
}

class FakeMessageRepository : MessageRepository {
    val incoming = mutableListOf<MessageRecord>()
    val outgoing = mutableListOf<MessageRecord>()

    override suspend fun storeIncoming(
        from: KeyId,
        message: drs.chat.protocol.InnerMessage,
        envelopeMessageId: ByteArray,
        receivedAt: Long,
    ) {
        incoming +=
            MessageRecord(
                messageId = message.id,
                fromKey = from,
                toKey = null,
                text = message.text,
                sentAt = message.sentAt,
                receivedAt = receivedAt,
                direction = Direction.INCOMING,
                status = MessageStatus.DELIVERED,
            )
    }

    override suspend fun markOutgoing(
        messageId: ByteArray,
        state: MessageStatus,
        at: Long,
    ) {
        val idx = outgoing.indexOfFirst { it.messageId.contentEquals(messageId) }
        if (idx >= 0) {
            outgoing[idx] = outgoing[idx].copy(status = state)
        }
    }

    override suspend fun history(
        contact: KeyId,
        limit: Int,
    ): List<MessageRecord> = (incoming + outgoing).take(limit)
}

class FakeContactRepository : ContactRepository {
    val contacts = mutableListOf<Contact>()

    override suspend fun upsert(contact: Contact) {
        val idx = contacts.indexOfFirst { it.keyId == contact.keyId }
        if (idx >= 0) contacts[idx] = contact else contacts += contact
    }

    override suspend fun find(keyId: KeyId): Contact? = contacts.firstOrNull { it.keyId == keyId }

    override suspend fun all(): List<Contact> = contacts.toList()
}

class FakeTransport(val sendBehavior: (ByteArray) -> SendResult = { SendResult.Sent }) : TransportPort {
    val sent = ArrayDeque<ByteArray>()

    override suspend fun send(envelopeBytes: ByteArray): SendResult {
        sent.add(envelopeBytes.copyOf())
        return sendBehavior(envelopeBytes)
    }
}
