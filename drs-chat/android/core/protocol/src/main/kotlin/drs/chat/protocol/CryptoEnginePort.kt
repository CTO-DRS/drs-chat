package drs.chat.protocol

/**
 * Domain port for all cryptography. Adapter in :core:crypto (libsignal-android + BC).
 *
 * Lives in :core:protocol because :core:protocol's EnvelopeFactory is the
 * primary consumer (signs headers, mints messageIds) and :core:crypto + :core:domain
 * both depend on :core:protocol — placing the port here breaks the prior
 * :core:domain ↔ :core:protocol cycle (Amendment to ADR-0002, M3 push).
 */
interface CryptoEnginePort {
    fun signEnvelopeHeader(headerBytes: ByteArray): ByteArray

    fun verifyEnvelopeHeader(
        senderEd25519Pub: ByteArray,
        headerBytes: ByteArray,
        signature: ByteArray,
    ): Boolean

    fun encryptForContact(
        contact: ContactId,
        plaintext: ByteArray,
    ): ByteArray

    fun decryptFromContact(
        contact: ContactId,
        ciphertext: ByteArray,
    ): ByteArray

    fun newMessageId(): ByteArray

    fun localEd25519Pub(): ByteArray

    fun localX25519Pub(): ByteArray

    fun localKeyId(): KeyId
}
