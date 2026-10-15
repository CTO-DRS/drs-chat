package drs.chat.domain.port

import drs.chat.domain.model.ContactId
import drs.chat.domain.model.KeyId

/** Domain port for all cryptography. Adapter in :core:crypto (libsignal-android + BC). */
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
