package drs.chat.relay

import drs.chat.protocol.AuthWire
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.security.SecureRandom

@Suppress("StringLiteralDuplication", "LongMethod")
class AuthChallengeTest {
    // 1. Verify returns the correct keyId for a valid signature
    @Test
    fun `valid signature returns the expected keyId`() {
        val (priv, pub) = generateEd25519()
        val pubBytes: ByteArray = pub.encoded
        val nonce = randomBytes(32)
        val ts = 100L
        val x25519Pub = randomBytes(32)
        val data = AuthWire.authData(nonce, ts, x25519Pub)
        val sig = sign(priv, data)
        val keyId = AuthChallenge.verify(nonce, ts, x25519Pub, sig, pubBytes, relayNowMs = ts * 1000L)
        assertNotNull(keyId)
        assertArrayEquals(AuthWire.keyIdOf(pubBytes), keyId)
    }

    // 2. Bad signature → null (Eve forges)
    @Test
    fun `bad signature returns null`() {
        val (priv, _) = generateEd25519()
        val (_, otherPub) = generateEd25519()
        val otherPubBytes: ByteArray = otherPub.encoded
        val nonce = randomBytes(32)
        val ts = 100L
        val x25519Pub = randomBytes(32)
        val data = AuthWire.authData(nonce, ts, x25519Pub)
        val sig = sign(priv, data)
        // otherPubBytes does NOT correspond to priv → verify fails
        assertNull(AuthChallenge.verify(nonce, ts, x25519Pub, sig, otherPubBytes, relayNowMs = ts * 1000L))
    }

    // 3. Expired timestamp (skew > 60s) → null
    @Test
    fun `expired timestamp returns null`() {
        val (priv, pub) = generateEd25519()
        val pubBytes: ByteArray = pub.encoded
        val nonce = randomBytes(32)
        val ts = 100L
        val x25519Pub = randomBytes(32)
        val data = AuthWire.authData(nonce, ts, x25519Pub)
        val sig = sign(priv, data)
        assertNull(AuthChallenge.verify(nonce, ts, x25519Pub, sig, pubBytes, relayNowMs = (ts + 200) * 1000L))
    }

    // 4. Future timestamp within skew tolerance → ok
    @Test
    fun `future timestamp within skew tolerance returns keyId`() {
        val (priv, pub) = generateEd25519()
        val pubBytes: ByteArray = pub.encoded
        val nonce = randomBytes(32)
        val ts = 200L
        val x25519Pub = randomBytes(32)
        val data = AuthWire.authData(nonce, ts, x25519Pub)
        val sig = sign(priv, data)
        assertNotNull(AuthChallenge.verify(nonce, ts, x25519Pub, sig, pubBytes, relayNowMs = (ts - 30) * 1000L))
    }
}

internal fun generateEd25519(): Pair<Ed25519PrivateKeyParameters, Ed25519PublicKeyParameters> {
    val gen = Ed25519KeyPairGenerator().apply { init(Ed25519KeyGenerationParameters(SecureRandom())) }
    val pair = gen.generateKeyPair()
    return pair.private as Ed25519PrivateKeyParameters to pair.public as Ed25519PublicKeyParameters
}

internal fun sign(
    priv: Ed25519PrivateKeyParameters,
    data: ByteArray,
): ByteArray {
    val signer = Ed25519Signer()
    signer.init(true, priv)
    signer.update(data, 0, data.size)
    return signer.generateSignature()
}

internal fun randomBytes(n: Int): ByteArray = ByteArray(n).also { SecureRandom().nextBytes(it) }
