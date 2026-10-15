package drs.chat.protocol

import drs.chat.domain.port.CryptoEnginePort
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray

/**
 * The single place envelope bytes are produced or consumed (ARCH §2). Both
 * signer and verifier use [cbor] — same instance → byte-identical header
 * encoding (ADR-0001 determinism). Encoding (ADR-0001 byte-string decision,
 * M3 bounded eval ADOPTED): `useDefiniteLengthEncoding` (definite maps 0xaX/0xbb)
 * + `alwaysUseByteString` (ByteArray → CBOR byte string, major type 2) on
 * kotlinx-serialization-cbor 1.9.0. Golden vectors are regenerated
 * PROGRAMMATICALLY (see WireEnvelopeTest's CborBuilder helper — never hand-typed).
 */
object EnvelopeCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val cbor: Cbor =
        Cbor {
            encodeDefaults = true
            ignoreUnknownKeys = false
            useDefiniteLengthEncoding = true
            alwaysUseByteString = true
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(e: WireEnvelope): ByteArray {
        val b = cbor.encodeToByteArray(WireEnvelope.serializer(), e)
        if (b.size > EnvelopeConstants.MAX_ENVELOPE_BYTES) throw EnvelopeTooLargeException(b.size)
        return b
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun decode(b: ByteArray): WireEnvelope {
        val e = cbor.decodeFromByteArray(WireEnvelope.serializer(), b)
        if (b.size > EnvelopeConstants.MAX_ENVELOPE_BYTES) throw EnvelopeTooLargeException(b.size)
        if (e.protocolVersion.toInt() != EnvelopeConstants.PROTOCOL_VERSION) {
            throw UnsupportedEnvelopeVersionException(
                e.protocolVersion.toInt(),
            )
        }
        return e
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun headerBytesOf(e: WireEnvelope): ByteArray = cbor.encodeToByteArray(EnvelopeHeader.serializer(), e.header())

    @OptIn(ExperimentalSerializationApi::class)
    fun headerBytesOf(h: EnvelopeHeader): ByteArray = cbor.encodeToByteArray(EnvelopeHeader.serializer(), h)
}

object EnvelopeFactory {
    fun build(
        crypto: CryptoEnginePort,
        senderKeyId: ByteArray,
        recipientKeyId: ByteArray,
        createdAt: Long,
        ttlSeconds: Int,
        kind: EnvelopeKind,
        payloadCiphertext: ByteArray,
        messageId: ByteArray = crypto.newMessageId(),
    ): WireEnvelope {
        require(messageId.size == EnvelopeConstants.MESSAGE_ID_BYTES) { "messageId must be 16 bytes" }
        val h =
            EnvelopeHeader(
                EnvelopeConstants.PROTOCOL_VERSION.toUByte(),
                messageId,
                senderKeyId,
                recipientKeyId,
                createdAt,
                ttlSeconds,
                kind.code,
                payloadCiphertext,
            )
        val sig = crypto.signEnvelopeHeader(EnvelopeCodec.headerBytesOf(h))
        return WireEnvelope(h.protocolVersion, h.messageId, h.senderKeyId, h.recipientKeyId, h.createdAt, h.ttlSeconds, h.kind, h.payloadCiphertext, sig)
    }
}
