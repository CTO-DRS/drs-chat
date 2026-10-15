package drs.chat.protocol

import drs.chat.domain.model.KeyId
import drs.chat.domain.port.CryptoEnginePort
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.SecureRandom

/** Test-only Ed25519 signer (real BouncyCastle) implementing CryptoEnginePort. */
class FakeCryptoEngine(
    private val priv: Ed25519PrivateKeyParameters,
    private val pub: Ed25519PublicKeyParameters,
) : CryptoEnginePort {
    override fun signEnvelopeHeader(h: ByteArray): ByteArray {
        val s = Ed25519Signer()
        s.init(true, priv)
        s.update(h, 0, h.size)
        return s.generateSignature()
    }

    override fun verifyEnvelopeHeader(
        senderEd25519Pub: ByteArray,
        h: ByteArray,
        sig: ByteArray,
    ): Boolean {
        val v = Ed25519Signer()
        v.init(false, org.bouncycastle.crypto.params.Ed25519PublicKeyParameters(senderEd25519Pub, 0))
        v.update(h, 0, h.size)
        return v.verifySignature(sig)
    }

    override fun encryptForContact(
        c: KeyId,
        p: ByteArray,
    ): ByteArray = error("not used by protocol tests")

    override fun decryptFromContact(
        c: KeyId,
        ct: ByteArray,
    ): ByteArray = error("not used by protocol tests")

    override fun newMessageId(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    override fun localEd25519Pub(): ByteArray = pub.encoded

    override fun localX25519Pub(): ByteArray = ByteArray(32)

    override fun localKeyId(): KeyId = Fingerprint.keyIdOf(pub.encoded, localX25519Pub())

    companion object {
        fun generate(): FakeCryptoEngine {
            val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
            val p = gen.generateKeyPair()
            return FakeCryptoEngine(p.private as Ed25519PrivateKeyParameters, p.public as Ed25519PublicKeyParameters)
        }
    }
}
