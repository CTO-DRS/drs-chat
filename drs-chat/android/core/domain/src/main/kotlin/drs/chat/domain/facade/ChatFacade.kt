package drs.chat.domain.facade

import drs.chat.domain.model.MessageId
import drs.chat.domain.pipeline.OutboxDrainer
import drs.chat.domain.pipeline.ReceivePipeline
import drs.chat.domain.pipeline.ReceiveResult
import drs.chat.domain.port.EnqueueRequest
import drs.chat.domain.port.IdGenerator
import drs.chat.domain.port.OutboxRepository
import drs.chat.protocol.CryptoEnginePort
import drs.chat.protocol.EnvelopeConstants
import drs.chat.protocol.EnvelopeFactory
import drs.chat.protocol.EnvelopeKind
import drs.chat.protocol.InnerMessage
import drs.chat.protocol.InnerPayload
import drs.chat.protocol.KeyId
import drs.chat.protocol.WireEnvelope

/**
 * ChatFacade — the single entry point the UI (and any test, or :app) calls
 * for outbound + inbound messaging.
 *
 * Send: encrypts + signs + persists outbox entry (QUEUED). The drainer does
 * the actual transport.send on the next drain cycle.
 *
 * Receive: hands the decoded WireEnvelope to the ReceivePipeline; the pipeline
 * returns a typed result.
 */
class ChatFacade(
    private val crypto: CryptoEnginePort,
    private val outbox: OutboxRepository,
    private val drainer: OutboxDrainer,
    private val receiver: ReceivePipeline,
    private val idGen: IdGenerator,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Encrypt + sign + enqueue a message. Returns the assigned messageId. */
    suspend fun sendMessage(
        toContact: KeyId,
        text: String,
    ): ByteArray {
        require(text.isNotBlank()) { "text must not be blank" }
        require(text.length <= EnvelopeConstants.INNER_TEXT_MAX_CHARS) { "text exceeds cap" }
        val now = clock()
        val messageId = MessageId(idGen.newMessageId())
        val inner = InnerMessage(messageId.bytes, text, now)
        val ct = crypto.encryptForContact(toContact, InnerPayload.encodeMessage(inner))
        val env =
            EnvelopeFactory.build(
                crypto,
                senderKeyId = crypto.localKeyId().bytes,
                recipientKeyId = toContact.bytes,
                createdAt = now,
                ttlSeconds = EnvelopeConstants.TTL_DEFAULT_SECONDS,
                kind = EnvelopeKind.MESSAGE,
                payloadCiphertext = ct,
                messageId = messageId.bytes,
            )
        val envBytes = drs.chat.protocol.EnvelopeCodec.encode(env)
        outbox.enqueue(EnqueueRequest(messageId.bytes, envBytes, toContact, now))
        return messageId.bytes
    }

    /** One drain pass — sent/requeued/failed counts come back via [DrainReport]. */
    suspend fun drainOnce() = drainer.drainOnce()

    /** Hand a decoded envelope to the receive pipeline. */
    suspend fun receive(envelope: WireEnvelope): ReceiveResult = receiver.process(envelope)
}
