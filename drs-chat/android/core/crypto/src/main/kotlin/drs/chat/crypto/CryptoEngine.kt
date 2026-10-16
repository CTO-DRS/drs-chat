package drs.chat.crypto

import drs.chat.domain.model.ContactId
import drs.chat.domain.model.KeyId
import drs.chat.domain.port.CryptoEnginePort
import drs.chat.protocol.ContactBundle
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import java.security.SecureRandom

/** Auth data construction (duplicated from RelayWire to avoid crypto→transport dependency). */
private const val AUTH_CONTEXT = "drs-auth-v1"

private fun authData(
    nonce: ByteArray,
    ts: Long,
    x25519Pub: ByteArray,
): ByteArray {
    val tsBytes = ByteArray(8) { (ts ushr (it * 8)).toByte() }
    return nonce + tsBytes + AUTH_CONTEXT.toByteArray(Charsets.UTF_8) + x25519Pub
}

/**
 * The single crypto facade (ARCH §2). Ed25519 (BC, uniform API 26–35) for
 * envelope signing + relay auth. libsignal (Rust) for PQXDH + Double Ratchet.
 * compileOnly libsignal-client → host-JVM tests run real Rust engine; :app
 * provides libsignal-android at runtime (same org.signal.libsignal.protocol.* API).
 */
class CryptoEngine(
    private val ed25519Priv: Ed25519PrivateKeyParameters,
    private val ed25519Pub: Ed25519PublicKeyParameters,
    val identity: IdentityKeyPair,
    val store: InMemorySignalProtocolStore,
    private val regId: Int,
    private val deviceId: Int = 1,
    private var nextPreKeyId: Int = 100,
    private var clock: () -> Long = { System.currentTimeMillis() },
) : CryptoEnginePort {
    private val otkThreshold = 25
    private val otkReplenishBatch = 25

    // ---- Prekey management ----
    fun signedPreKey(id: Int): SignedPreKeyRecord {
        val kp = ECKeyPair.generate()
        val sig = identity.privateKey.calculateSignature(kp.publicKey.serialize())
        val rec = SignedPreKeyRecord(id, clock(), kp, sig)
        store.storeSignedPreKey(id, rec)
        return rec
    }

    fun oneTimePreKey(id: Int): PreKeyRecord {
        val rec = PreKeyRecord(id, ECKeyPair.generate())
        store.storePreKey(id, rec)
        return rec
    }

    fun kyberPreKey(id: Int): KyberPreKeyRecord {
        val kp = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val sig = identity.privateKey.calculateSignature(kp.publicKey.serialize())
        val rec = KyberPreKeyRecord(id, clock(), kp, sig)
        store.storeKyberPreKey(id, rec)
        return rec
    }

    /** Replenish OTKs when pool < threshold. Returns count generated (0 = no-op). */
    fun replenishOtk(currentPoolSize: Int): Int {
        if (currentPoolSize >= otkThreshold) return 0
        for (i in 1..otkReplenishBatch) {
            oneTimePreKey(nextPreKeyId++)
        }
        return otkReplenishBatch
    }

    /** Rotate signed prekey if older than [intervalMs] (default 72h). */
    fun rotateSignedPreKey(
        currentSpkId: Int,
        intervalMs: Long = 72 * 60 * 60 * 1000L,
    ): Boolean {
        val spk = store.loadSignedPreKey(currentSpkId)
        if (clock() - spk.timestamp < intervalMs) return false
        store.removeSignedPreKey(currentSpkId)
        signedPreKey(currentSpkId + 1)
        return true
    }

    fun setClock(c: () -> Long) {
        clock = c
    }

    // ---- Session management ----
    fun inviteBundle(
        spkId: Int = 1,
        otkId: Int? = 1,
        kyberId: Int = 1,
    ): PreKeyBundle {
        val spk = store.loadSignedPreKey(spkId)
        val kyber = store.loadKyberPreKey(kyberId)
        val (preKeyId, preKey) =
            if (otkId != null) {
                val r = store.loadPreKey(otkId)
                r.id to r.keyPair.publicKey
            } else {
                0 to null
            }
        return PreKeyBundle(
            store.localRegistrationId, deviceId, preKeyId, preKey,
            spk.id, spk.keyPair.publicKey, spk.signature, identity.publicKey,
            kyber.id, kyber.keyPair.publicKey, kyber.signature,
        )
    }

    fun buildContactBundle(
        spkId: Int = 1,
        otkId: Int? = 1,
        kyberId: Int = 1,
    ): ContactBundle {
        val spk = store.loadSignedPreKey(spkId)
        val kyber = store.loadKyberPreKey(kyberId)
        val otid: Int? = otkId
        val otpub: ByteArray? = if (otkId != null) store.loadPreKey(otkId).keyPair.publicKey.publicKeyBytes else null
        return ContactBundle(
            ed25519Pub.encoded, identity.publicKey.publicKey.publicKeyBytes,
            spk.id, spk.keyPair.publicKey.publicKeyBytes, spk.signature,
            otid, otpub,
            kyber.id, kyber.keyPair.publicKey.serialize(), kyber.signature,
        )
    }

    fun establishSessionTo(
        contact: ContactId,
        bundle: PreKeyBundle,
    ) {
        SessionBuilder(store, addressOf(contact)).process(bundle)
    }

    private fun addressOf(contact: ContactId): SignalProtocolAddress = SignalProtocolAddress(contact.toHex(), deviceId)

    // ---- CryptoEnginePort ----
    override fun signEnvelopeHeader(headerBytes: ByteArray): ByteArray {
        val s = Ed25519Signer()
        s.init(true, ed25519Priv)
        s.update(headerBytes, 0, headerBytes.size)
        return s.generateSignature()
    }

    override fun verifyEnvelopeHeader(
        senderEd25519Pub: ByteArray,
        headerBytes: ByteArray,
        signature: ByteArray,
    ): Boolean {
        val v = Ed25519Signer()
        v.init(false, Ed25519PublicKeyParameters(senderEd25519Pub, 0))
        v.update(headerBytes, 0, headerBytes.size)
        return v.verifySignature(signature)
    }

    override fun encryptForContact(
        contact: ContactId,
        plaintext: ByteArray,
    ): ByteArray = SessionCipher(store, addressOf(contact)).encrypt(plaintext).serialize()

    override fun decryptFromContact(
        contact: ContactId,
        ciphertext: ByteArray,
    ): ByteArray {
        val cipher = SessionCipher(store, addressOf(contact))
        val ct: CiphertextMessage =
            try {
                PreKeySignalMessage(ciphertext)
            } catch (_: Exception) {
                SignalMessage(ciphertext)
            }
        return when (ct) {
            is PreKeySignalMessage -> cipher.decrypt(ct)
            is SignalMessage -> cipher.decrypt(ct)
            else -> error("unexpected")
        }
    }

    override fun newMessageId(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    override fun localEd25519Pub(): ByteArray = ed25519Pub.encoded

    override fun localX25519Pub(): ByteArray = identity.publicKey.publicKey.publicKeyBytes

    override fun localKeyId(): KeyId = KeyId(java.security.MessageDigest.getInstance("SHA-256").digest(ed25519Pub.encoded).copyOf(16))

    // ---- Relay challenge auth ----
    fun signAuthChallenge(
        nonce: ByteArray,
        ts: Long,
    ): ByteArray = signEnvelopeHeader(authData(nonce, ts, localX25519Pub()))

    companion object {
        fun generate(
            regId: Int = 1001,
            deviceId: Int = 1,
        ): CryptoEngine {
            val identity = IdentityKeyPair.generate()
            val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
            val pair = gen.generateKeyPair()
            val store = InMemorySignalProtocolStore(identity, regId)
            val ce =
                CryptoEngine(
                    pair.private as Ed25519PrivateKeyParameters,
                    pair.public as Ed25519PublicKeyParameters,
                    identity,
                    store,
                    regId,
                    deviceId,
                )
            ce.signedPreKey(1)
            ce.oneTimePreKey(1)
            ce.kyberPreKey(1)
            return ce
        }
    }
}
