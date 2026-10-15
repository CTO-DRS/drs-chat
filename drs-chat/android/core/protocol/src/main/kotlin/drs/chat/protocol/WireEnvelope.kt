package drs.chat.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.Arrays

@Serializable
data class EnvelopeHeader(
    @SerialName("v") val protocolVersion: UByte,
    @SerialName("mid") val messageId: ByteArray,
    @SerialName("sid") val senderKeyId: ByteArray,
    @SerialName("rid") val recipientKeyId: ByteArray,
    @SerialName("ts") val createdAt: Long,
    @SerialName("ttl") val ttlSeconds: Int,
    @SerialName("k") val kind: UByte,
    @SerialName("ct") val payloadCiphertext: ByteArray,
) {
    init {
        require(protocolVersion.toInt() == EnvelopeConstants.PROTOCOL_VERSION) { "unsupported protocolVersion" }
        require(messageId.size == EnvelopeConstants.MESSAGE_ID_BYTES) { "messageId must be 16 bytes" }
        require(senderKeyId.size == EnvelopeConstants.KEY_ID_BYTES) { "senderKeyId must be 16 bytes" }
        require(recipientKeyId.size == EnvelopeConstants.KEY_ID_BYTES) { "recipientKeyId must be 16 bytes" }
    }
    constructor(
        e: WireEnvelope,
    ) : this(e.protocolVersion, e.messageId, e.senderKeyId, e.recipientKeyId, e.createdAt, e.ttlSeconds, e.kind, e.payloadCiphertext)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EnvelopeHeader) return false
        return protocolVersion == other.protocolVersion && Arrays.equals(messageId, other.messageId) &&
            Arrays.equals(senderKeyId, other.senderKeyId) && Arrays.equals(recipientKeyId, other.recipientKeyId) &&
            createdAt == other.createdAt && ttlSeconds == other.ttlSeconds && kind == other.kind &&
            Arrays.equals(payloadCiphertext, other.payloadCiphertext)
    }

    override fun hashCode(): Int {
        var r = protocolVersion.hashCode()
        r = 31 * r + Arrays.hashCode(messageId)
        r = 31 * r + Arrays.hashCode(senderKeyId)
        r = 31 * r + Arrays.hashCode(recipientKeyId)
        r = 31 * r + createdAt.hashCode()
        r = 31 * r + ttlSeconds
        r = 31 * r + kind.hashCode()
        r = 31 * r + Arrays.hashCode(payloadCiphertext)
        return r
    }
}

@Serializable
data class WireEnvelope(
    @SerialName("v") val protocolVersion: UByte,
    @SerialName("mid") val messageId: ByteArray,
    @SerialName("sid") val senderKeyId: ByteArray,
    @SerialName("rid") val recipientKeyId: ByteArray,
    @SerialName("ts") val createdAt: Long,
    @SerialName("ttl") val ttlSeconds: Int,
    @SerialName("k") val kind: UByte,
    @SerialName("ct") val payloadCiphertext: ByteArray,
    @SerialName("sig") val signature: ByteArray,
) {
    init {
        require(protocolVersion.toInt() == EnvelopeConstants.PROTOCOL_VERSION) { "unsupported protocolVersion" }
        require(messageId.size == EnvelopeConstants.MESSAGE_ID_BYTES) { "messageId must be 16 bytes" }
        require(senderKeyId.size == EnvelopeConstants.KEY_ID_BYTES) { "senderKeyId must be 16 bytes" }
        require(recipientKeyId.size == EnvelopeConstants.KEY_ID_BYTES) { "recipientKeyId must be 16 bytes" }
        require(signature.size == EnvelopeConstants.SIGNATURE_BYTES) { "signature must be 64 bytes" }
    }

    fun kind(): EnvelopeKind = EnvelopeKind.fromCode(kind)

    fun header(): EnvelopeHeader = EnvelopeHeader(this)

    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        now > createdAt + ttlSeconds * 1000L + EnvelopeConstants.CLOCK_SKEW_SECONDS * 1000L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WireEnvelope) return false
        return protocolVersion == other.protocolVersion && Arrays.equals(messageId, other.messageId) &&
            Arrays.equals(senderKeyId, other.senderKeyId) && Arrays.equals(recipientKeyId, other.recipientKeyId) &&
            createdAt == other.createdAt && ttlSeconds == other.ttlSeconds && kind == other.kind &&
            Arrays.equals(payloadCiphertext, other.payloadCiphertext) && Arrays.equals(signature, other.signature)
    }

    override fun hashCode(): Int {
        var r = protocolVersion.hashCode()
        r = 31 * r + Arrays.hashCode(messageId)
        r = 31 * r + Arrays.hashCode(senderKeyId)
        r = 31 * r + Arrays.hashCode(recipientKeyId)
        r = 31 * r + createdAt.hashCode()
        r = 31 * r + ttlSeconds
        r = 31 * r + kind.hashCode()
        r = 31 * r + Arrays.hashCode(payloadCiphertext)
        r = 31 * r + Arrays.hashCode(signature)
        return r
    }
}
