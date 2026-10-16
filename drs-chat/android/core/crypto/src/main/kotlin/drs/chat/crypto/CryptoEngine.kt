package drs.chat.crypto

import drs.chat.domain.model.ContactId
import drs.chat.domain.model.KeyId
import drs.chat.domain.port.CryptoEnginePort
import drs.chat.protocol.AuthWire
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

/**
 * The single crypto facade (ARCH §2 — no crypto code outside this module). Real
 * BouncyCastle Ed25519 (envelope signing + relay auth) + real libsignal PQXDH
 * (X3DH + Double Ratchet + Kyber). Pure JVM — runs on host JVM via libsignal-client
 * (same API as libsignal-android, which :app provides at runtime).
 *
 * M3.5 status: FULL — identity gen, prekeys, PQXDH sessions, OTK replenish,
 * signed-prekey rotation, relay challenge, plaintext cap, identity trust.
 */
@Suppress("TooManyFunctions") // inherent to a crypto facade
class CryptoEngine private constructor(
    private val ed25519Priv: Ed25519PrivateKeyParameters,
    private val ed25519Pub: Ed25519PublicKeyParameters,
    private val identity: IdentityKeyPair,
    internal val store: InMemorySignalProtocolStore,
    internal val regId: Int,
    internal val deviceId: Int,
) : CryptoEnginePort {
    // String keys (toHex) — NOT ByteArray (ByteArray.equals is referential → HashMap<ByteArray> bug)
    private val trustedIdentities = mutableMapOf<String, Boolean>()

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
    ): ByteArray {
        require(plaintext.size <= drs.chat.protocol.EnvelopeConstants.INNER_TEXT_MAX_CHARS) { "plaintext exceeds cap" }
        return SessionCipher(store, addressOf(contact)).encrypt(plaintext).serialize()
    }

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
            else -> error("unexpected ciphertext type")
        }
    }

    override fun newMessageId(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }

    override fun localEd25519Pub(): ByteArray = ed25519Pub.encoded

    override fun localX25519Pub(): ByteArray = identity.publicKey.publicKey.publicKeyBytes

    override fun localKeyId(): KeyId = KeyId(AuthWire.keyIdOf(ed25519Pub.encoded))

    fun addressOf(contact: ContactId): SignalProtocolAddress = SignalProtocolAddress(contact.toHex(), deviceId)

    fun inviteBundle(
        spkId: Int,
        otpId: Int?,
        kyberId: Int,
    ): PreKeyBundle {
        val spk = store.loadSignedPreKey(spkId)
        val kyber = store.loadKyberPreKey(kyberId)
        val (preKeyId, preKey) =
            if (otpId != null) {
                val r = store.loadPreKey(otpId)
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

    fun establishSessionTo(
        contact: ContactId,
        bundle: PreKeyBundle,
    ) {
        SessionBuilder(store, addressOf(contact)).process(bundle)
    }

    fun isTrustedIdentity(keyId: ByteArray): Boolean = trustedIdentities[keyId.joinToString("") { "%02x".format(it) }] == true

    fun trustIdentity(keyId: ByteArray) {
        trustedIdentities[keyId.joinToString("") { "%02x".format(it) }] = true
    }

    fun relayChallengeSig(
        nonce: ByteArray,
        ts: Long,
    ): ByteArray = signEnvelopeHeader(AuthWire.authData(nonce, ts, localX25519Pub()))

    companion object {
        const val OTK_POOL_SIZE = 25
        const val OTK_REPLENISH_THRESHOLD = 10
        const val SIGNED_PREKEY_ROTATION_MS = 72 * 60 * 60 * 1000L

        fun generate(
            regId: Int = 1000,
            deviceId: Int = 1,
        ): CryptoEngine {
            val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
            val pair = gen.generateKeyPair()
            val ed25519Priv = pair.private as Ed25519PrivateKeyParameters
            val ed25519Pub = pair.public as Ed25519PublicKeyParameters
            val identity = IdentityKeyPair.generate()
            val store = InMemorySignalProtocolStore(identity, regId)
            val engine = CryptoEngine(ed25519Priv, ed25519Pub, identity, store, regId, deviceId)
            engine.generateSignedPreKey(1, System.currentTimeMillis())
            for (i in 1..OTK_POOL_SIZE) engine.generateOneTimePreKey(i)
            engine.generateKyberPreKey(1)
            return engine
        }
    }

    internal fun generateSignedPreKey(
        id: Int,
        timestamp: Long,
    ) {
        val kp = ECKeyPair.generate()
        val sig = identity.privateKey.calculateSignature(kp.publicKey.serialize())
        store.storeSignedPreKey(id, SignedPreKeyRecord(id, timestamp, kp, sig))
    }

    internal fun generateOneTimePreKey(id: Int) {
        store.storePreKey(id, PreKeyRecord(id, ECKeyPair.generate()))
    }

    internal fun generateKyberPreKey(id: Int) {
        val kp: KEMKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val sig = identity.privateKey.calculateSignature(kp.publicKey.serialize())
        store.storeKyberPreKey(id, KyberPreKeyRecord(id, System.currentTimeMillis(), kp, sig))
    }

    internal fun otkPoolSize(): Int {
        var count = 0
        for (i in 1..255) {
            try {
                store.loadPreKey(i)
                count++
            } catch (_: Exception) {
            }
        }
        return count
    }

    internal fun replenishOtkIfNeeded(nextId: Int): Int {
        val current = otkPoolSize()
        if (current >= OTK_REPLENISH_THRESHOLD) return 0 // no-op (99>=25 → 0 added)
        val toAdd = OTK_POOL_SIZE - current
        for (i in nextId until nextId + toAdd) generateOneTimePreKey(i)
        return toAdd
    }

    internal fun rotateSignedPreKeyIfNeeded(
        id: Int,
        now: Long,
    ): Boolean {
        val spk = store.loadSignedPreKey(id)
        if (now - spk.timestamp < SIGNED_PREKEY_ROTATION_MS) return false
        store.removeSignedPreKey(id)
        generateSignedPreKey(id, now)
        return true
    }
}
