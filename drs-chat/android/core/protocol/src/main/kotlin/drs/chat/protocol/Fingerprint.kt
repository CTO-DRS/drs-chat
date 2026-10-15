package drs.chat.protocol

import drs.chat.domain.model.KeyId
import java.security.MessageDigest

object Fingerprint {
    private const val DOMAIN = "drs-id-v1"

    fun compute(
        ed25519Pub: ByteArray,
        x25519Pub: ByteArray,
    ): ByteArray {
        require(ed25519Pub.size == 32) { "Ed25519 pub must be 32 bytes" }
        require(x25519Pub.size == 32) { "X25519 pub must be 32 bytes" }
        val md = MessageDigest.getInstance("SHA-256")
        md.update(0x01.toByte())
        md.update(DOMAIN.toByteArray(Charsets.UTF_8))
        md.update(ed25519Pub)
        md.update(x25519Pub)
        return md.digest()
    }

    fun keyIdOf(fp: ByteArray): KeyId {
        require(fp.size >= EnvelopeConstants.KEY_ID_BYTES) { "fingerprint must be ≥ 16 bytes" }
        return KeyId(fp.copyOf(EnvelopeConstants.KEY_ID_BYTES))
    }

    fun keyIdOf(
        ed25519Pub: ByteArray,
        x25519Pub: ByteArray,
    ): KeyId = keyIdOf(compute(ed25519Pub, x25519Pub))

    fun toWords(fp: ByteArray): List<String> {
        require(fp.size >= EnvelopeConstants.WORD_LIST_BYTES) { "fingerprint must be ≥ 8 bytes" }
        return (0 until EnvelopeConstants.WORD_LIST_BYTES).map { i -> WordList.LIST[fp[i].toInt() and 0xFF] }
    }

    fun toHex(fp: ByteArray): String = fp.joinToString("") { "%02x".format(it) }
}
