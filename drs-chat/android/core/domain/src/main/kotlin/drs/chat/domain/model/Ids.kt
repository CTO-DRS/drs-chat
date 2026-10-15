package drs.chat.domain.model

import java.util.Locale

@JvmInline value class KeyId(val bytes: ByteArray) {
    init {
        require(bytes.size == KEY_ID_BYTES) { "KeyId must be $KEY_ID_BYTES bytes" }
    }

    fun toHex(): String = bytes.joinToString("") { "%02x".format(it) }

    fun toShortHex(): String = toHex().take(8)

    override fun toString(): String = "KeyId(${toShortHex()}…)"

    companion object {
        const val KEY_ID_BYTES = 16
    }
}

@JvmInline value class MessageId(val bytes: ByteArray) {
    init {
        require(bytes.size == MESSAGE_ID_BYTES) { "MessageId must be $MESSAGE_ID_BYTES bytes" }
    }

    companion object {
        const val MESSAGE_ID_BYTES = 16

        fun random(): MessageId = MessageId(ByteArray(MESSAGE_ID_BYTES).also { java.security.SecureRandom().nextBytes(it) })
    }
}
typealias ContactId = KeyId

@JvmInline value class TransportId(val value: String)

fun KeyId.toBadge(): String = toHex().take(8).uppercase(Locale.ROOT)
