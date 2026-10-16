package drs.chat.protocol

import java.util.Locale

/**
 * KeyId — 16-byte routing + lookup token (ADR-0001/ADR-0002). Lives in
 * :core:protocol because both :core:domain (port contracts, OutboxEntry.toKey)
 * and :core:crypto (CryptoEnginePort signatures) need it; protocol is the
 * lowest common dep and owns the wire format.
 *
 * ByteArray.equals is referential — do NOT use KeyId.bytes directly as a
 * HashMap key. Use [toHex] for content-based lookups.
 */
@JvmInline value class KeyId(val bytes: ByteArray) {
    init {
        require(bytes.size == KEY_ID_BYTES) { "KeyId must be $KEY_ID_BYTES bytes" }
    }

    fun toHex(): String = bytes.joinToString("") { "%02x".format(it) }

    fun toShortHex(): String = toHex().take(8)

    override fun toString(): String = "KeyId(${toShortHex()}…)"

    companion object {
        const val KEY_ID_BYTES: Int = 16
    }
}

typealias ContactId = KeyId

fun KeyId.toBadge(): String = toHex().take(8).uppercase(Locale.ROOT)
