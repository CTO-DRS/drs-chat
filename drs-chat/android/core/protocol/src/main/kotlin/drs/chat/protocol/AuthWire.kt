package drs.chat.protocol

import java.security.MessageDigest

/**
 * Shared auth + routing wire constants (ADR-0008, ADR-0009). Lives in :core:protocol
 * so BOTH :core:crypto (for signing) and :transport:api (for the relay/client) can
 * use it without crypto depending on transport (ARCH §2 dependency rule).
 */
object AuthWire {
    const val AUTH_CONTEXT: String = "drs-auth-v1"
    const val KEY_ID_BYTES: Int = 16
    const val NONCE_TTL_SECONDS: Long = 60
    const val TTL_DEFAULT_SECONDS: Int = 7 * 24 * 60 * 60

    /** Bytes the client signs with its Ed25519 identity for the relay auth challenge. Binds x25519Pub (M3). */
    fun authData(
        nonce: ByteArray,
        ts: Long,
        x25519Pub: ByteArray,
    ): ByteArray {
        val tsBytes = ByteArray(8)
        for (i in 0 until 8) tsBytes[i] = (ts ushr (i * 8)).toByte()
        return nonce + tsBytes + AUTH_CONTEXT.toByteArray(Charsets.UTF_8) + x25519Pub
    }

    /** recipientKeyId = SHA-256(ed25519Pub)[0..15] (derivable from Ed25519 alone — ADR-0003 routingKeyId). */
    fun keyIdOf(ed25519Pub: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(ed25519Pub).copyOf(KEY_ID_BYTES)

    /** 16-byte drop-on-ack token = SHA-256(envelopeBytes)[0..15]; NOT the envelope's messageId. */
    fun hashOf(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes).copyOf(16)
}
