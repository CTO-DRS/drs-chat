package drs.chat.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray

@Serializable
data class InnerMessage(
    @SerialName("id") val id: ByteArray,
    @SerialName("text") val text: String,
    @SerialName("ts") val sentAt: Long,
) {
    init {
        require(id.size == EnvelopeConstants.MESSAGE_ID_BYTES) { "inner id must be 16 bytes" }
        require(text.length <= EnvelopeConstants.INNER_TEXT_MAX_CHARS) { "text exceeds cap" }
        require(text.isNotBlank()) { "inner message text must not be blank" }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is InnerMessage && id.contentEquals(other.id) && text == other.text && sentAt == other.sentAt)

    override fun hashCode(): Int {
        var r = id.contentHashCode()
        r = 31 * r + text.hashCode()
        r = 31 * r + sentAt.hashCode()
        return r
    }
}

@Serializable
data class InnerReceipt(
    @SerialName("ack") val ackedMessageId: ByteArray,
    @SerialName("ts") val acknowledgedAt: Long,
) {
    init {
        require(ackedMessageId.size == EnvelopeConstants.MESSAGE_ID_BYTES)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is InnerReceipt && ackedMessageId.contentEquals(other.ackedMessageId) && acknowledgedAt == other.acknowledgedAt)

    override fun hashCode(): Int {
        var r = ackedMessageId.contentHashCode()
        r = 31 * r + acknowledgedAt.hashCode()
        return r
    }
}

@Serializable
data class SessionResetNotice(
    @SerialName("reason") val reason: String,
    @SerialName("ts") val at: Long,
)

object InnerPayload {
    @OptIn(ExperimentalSerializationApi::class)
    private val cbor =
        Cbor {
            encodeDefaults = true
            useDefiniteLengthEncoding = true
            alwaysUseByteString = true
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun encodeMessage(m: InnerMessage): ByteArray = cbor.encodeToByteArray(m)

    @OptIn(ExperimentalSerializationApi::class)
    fun encodeReceipt(r: InnerReceipt): ByteArray = cbor.encodeToByteArray(r)

    @OptIn(ExperimentalSerializationApi::class)
    fun encodeReset(r: SessionResetNotice): ByteArray = cbor.encodeToByteArray(r)

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeMessage(b: ByteArray): InnerMessage = cbor.decodeFromByteArray(InnerMessage.serializer(), b)

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeReceipt(b: ByteArray): InnerReceipt = cbor.decodeFromByteArray(InnerReceipt.serializer(), b)

    @OptIn(ExperimentalSerializationApi::class)
    fun decodeReset(b: ByteArray): SessionResetNotice = cbor.decodeFromByteArray(SessionResetNotice.serializer(), b)
}
