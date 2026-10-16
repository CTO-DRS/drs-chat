package drs.chat.transport

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray
import java.security.MessageDigest

object RelayWire {
    const val PREFIX_CONTROL: Byte = 0x00
    const val PREFIX_ENVELOPE: Byte = 0x01
    const val AUTH_CONTEXT: String = "drs-auth-v1"
    const val KEY_ID_BYTES: Int = 16
    const val NONCE_TTL_SECONDS: Long = 60
    const val TTL_DEFAULT_SECONDS: Int = 7 * 24 * 60 * 60

    fun authData(
        nonce: ByteArray,
        ts: Long,
        x25519Pub: ByteArray,
    ): ByteArray {
        val tsBytes = ByteArray(8)
        for (i in 0 until 8) tsBytes[i] = (ts ushr (i * 8)).toByte()
        return nonce + tsBytes + AUTH_CONTEXT.toByteArray(Charsets.UTF_8) + x25519Pub
    }

    fun keyIdOf(ed25519Pub: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(ed25519Pub).copyOf(KEY_ID_BYTES)

    fun hashOf(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes).copyOf(16)

    fun prefixed(
        prefix: Byte,
        payload: ByteArray,
    ): ByteArray =
        ByteArray(payload.size + 1).also {
            it[0] = prefix
            System.arraycopy(payload, 0, it, 1, payload.size)
        }
}

@Serializable
sealed interface ControlFrame {
    @Serializable
    @SerialName("auth_challenge")
    data class AuthChallenge(val nonce: ByteArray, val ts: Long) : ControlFrame

    @Serializable
    @SerialName("auth_response")
    data class AuthResponse(val keyId: ByteArray, val ed25519Pub: ByteArray, val x25519Pub: ByteArray, val sig: ByteArray) : ControlFrame

    @Serializable
    @SerialName("auth_ok")
    data object AuthOk : ControlFrame

    @Serializable
    @SerialName("auth_fail")
    data class AuthFail(val reason: String) : ControlFrame

    @Serializable
    @SerialName("send")
    data class Send(val recipientKeyId: ByteArray, val ttlSeconds: Int) : ControlFrame

    @Serializable
    @SerialName("sent")
    data class Sent(val envelopeHash: ByteArray) : ControlFrame

    @Serializable
    @SerialName("delivered_ack")
    data class DeliveredAck(val envelopeHash: ByteArray) : ControlFrame

    @Serializable
    @SerialName("ping")
    data object Ping : ControlFrame

    @Serializable
    @SerialName("pong")
    data object Pong : ControlFrame
}

object ControlCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val cbor =
        Cbor {
            encodeDefaults = true
            useDefiniteLengthEncoding = true
            alwaysUseByteString = true
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(f: ControlFrame): ByteArray = cbor.encodeToByteArray(ControlFrame.serializer(), f)

    @OptIn(ExperimentalSerializationApi::class)
    fun decode(b: ByteArray): ControlFrame = cbor.decodeFromByteArray(ControlFrame.serializer(), b)
}

fun keyIdShort(bytes: ByteArray): String = bytes.take(4).joinToString("") { "%02x".format(it) }
