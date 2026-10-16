package drs.chat.domain.pipeline

import drs.chat.domain.model.Direction
import drs.chat.domain.model.MessageRecord
import drs.chat.domain.model.MessageStatus
import drs.chat.domain.port.MessageRepository
import drs.chat.protocol.CryptoEnginePort
import drs.chat.protocol.EnvelopeKind
import drs.chat.protocol.InnerMessage
import drs.chat.protocol.InnerPayload
import drs.chat.protocol.InnerReceipt
import drs.chat.protocol.KeyId
import drs.chat.protocol.WireEnvelope

/**
 * Result of processing an incoming envelope. The pipeline never throws —
 * every failure is a typed [ReceiveResult.Rejected] so the caller (the
 * transport loop) can decide logging/telemetry without try/catch sprawl.
 */
sealed interface ReceiveResult {
    data class MessageStored(val record: MessageRecord) : ReceiveResult

    data class DeliveryAcked(val ackedMessageId: ByteArray) : ReceiveResult {
        override fun equals(other: Any?): Boolean =
            this === other || (other is DeliveryAcked && ackedMessageId.contentEquals(other.ackedMessageId))

        override fun hashCode(): Int = ackedMessageId.contentHashCode()
    }

    data class ReadAcked(val ackedMessageId: ByteArray) : ReceiveResult {
        override fun equals(other: Any?): Boolean =
            this === other || (other is ReadAcked && ackedMessageId.contentEquals(other.ackedMessageId))

        override fun hashCode(): Int = ackedMessageId.contentHashCode()
    }

    data class SessionReset(val reason: String) : ReceiveResult

    data class Rejected(val reason: String, val cause: Throwable? = null) : ReceiveResult
}

/**
 * The receive pipeline — one method per envelope. Steps:
 *
 * 1. Decode WireEnvelope bytes (signature is verified by the transport layer
 *    BEFORE this is called — the pipeline trusts what the transport passed).
 * 2. Dispatch by envelope kind:
 *    - MESSAGE → decrypt + decode InnerMessage → persist
 *    - DELIVERY_RECEIPT → mark the matching outgoing message DELIVERED
 *    - READ_RECEIPT → mark the matching outgoing message READ
 *    - SESSION_RESET → emit a SessionResetNotice (caller wires to session rebuild)
 * 3. Malformed payloads → [ReceiveResult.Rejected]. The pipeline never throws.
 */
class ReceivePipeline(
    private val crypto: CryptoEnginePort,
    private val messages: MessageRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** Process a decoded envelope. */
    suspend fun process(envelope: WireEnvelope): ReceiveResult {
        // Recipient check — recipient is "us" (verified by transport); we only
        // enforce that the senderKeyId is sized to KEY_ID_BYTES.
        if (envelope.senderKeyId.size != drs.chat.protocol.EnvelopeConstants.KEY_ID_BYTES) {
            return ReceiveResult.Rejected("senderKeyId has wrong size: ${envelope.senderKeyId.size}")
        }
        if (envelope.isExpired(clock())) {
            return ReceiveResult.Rejected("envelope expired")
        }
        // Defensive kind resolution — WireEnvelope init does NOT validate kind,
        // so an unknown kind code reaches here. Catch and return Rejected
        // rather than letting IllegalArgumentException escape (pipeline contract:
        // never throws).
        val kind =
            try {
                envelope.kind()
            } catch (t: Exception) {
                return ReceiveResult.Rejected("unknown envelope kind: ${envelope.kind}", t)
            }
        return when (kind) {
            EnvelopeKind.MESSAGE -> processMessage(envelope)
            EnvelopeKind.DELIVERY_RECEIPT -> processDeliveryReceipt(envelope)
            EnvelopeKind.READ_RECEIPT -> processReadReceipt(envelope)
            EnvelopeKind.SESSION_RESET -> processSessionReset(envelope)
        }
    }

    private suspend fun processMessage(env: WireEnvelope): ReceiveResult {
        val from = KeyId(env.senderKeyId)
        val plaintext =
            try {
                crypto.decryptFromContact(from, env.payloadCiphertext)
            } catch (t: Exception) {
                return ReceiveResult.Rejected("decrypt failed", t)
            }
        val inner: InnerMessage =
            try {
                InnerPayload.decodeMessage(plaintext)
            } catch (t: Exception) {
                return ReceiveResult.Rejected("inner payload decode failed", t)
            }
        val receivedAt = clock()
        messages.storeIncoming(from, inner, env.messageId, receivedAt)
        val record =
            MessageRecord(
                messageId = inner.id,
                fromKey = from,
                toKey = null,
                text = inner.text,
                sentAt = inner.sentAt,
                receivedAt = receivedAt,
                direction = Direction.INCOMING,
                status = MessageStatus.DELIVERED,
            )
        return ReceiveResult.MessageStored(record)
    }

    private suspend fun processDeliveryReceipt(env: WireEnvelope): ReceiveResult {
        val inner: InnerReceipt =
            try {
                val plaintext = crypto.decryptFromContact(KeyId(env.senderKeyId), env.payloadCiphertext)
                InnerPayload.decodeReceipt(plaintext)
            } catch (t: Exception) {
                return ReceiveResult.Rejected("delivery receipt decode failed", t)
            }
        messages.markOutgoing(inner.ackedMessageId, MessageStatus.DELIVERED, clock())
        return ReceiveResult.DeliveryAcked(inner.ackedMessageId)
    }

    private suspend fun processReadReceipt(env: WireEnvelope): ReceiveResult {
        val inner: InnerReceipt =
            try {
                val plaintext = crypto.decryptFromContact(KeyId(env.senderKeyId), env.payloadCiphertext)
                InnerPayload.decodeReceipt(plaintext)
            } catch (t: Exception) {
                return ReceiveResult.Rejected("read receipt decode failed", t)
            }
        messages.markOutgoing(inner.ackedMessageId, MessageStatus.READ, clock())
        return ReceiveResult.ReadAcked(inner.ackedMessageId)
    }

    private fun processSessionReset(env: WireEnvelope): ReceiveResult {
        // SESSION_RESET carries a plaintext notice (signed but not encrypted —
        // session reset bundles are prekey-tagged at the protocol layer; the
        // pipeline surfaces the reason string).
        val reason =
            try {
                drs.chat.protocol.InnerPayload.decodeReset(env.payloadCiphertext).reason
            } catch (t: Exception) {
                return ReceiveResult.Rejected("session reset decode failed", t)
            }
        return ReceiveResult.SessionReset(reason)
    }
}
