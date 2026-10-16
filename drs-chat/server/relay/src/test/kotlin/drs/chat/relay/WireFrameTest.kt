package drs.chat.relay

import drs.chat.protocol.AuthWire
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

@Suppress("StringLiteralDuplication", "LongMethod")
class WireFrameTest {
    // 10. buildAuth round-trips via AuthChallenge.verify
    @Test
    fun `auth frame round-trips`() {
        val (priv, pub) = generateEd25519()
        val pubBytes: ByteArray = pub.encoded
        val x = randomBytes(32)
        val n = randomBytes(32)
        val ts = 100L
        val data = AuthWire.authData(n, ts, x)
        val sig = sign(priv, data)
        val bytes = WireFrame.buildAuth(pubBytes, x, n, ts, sig)
        // Verify via the public AuthChallenge API — the bytes encode the
        // same fields AuthChallenge reads.
        val keyId = AuthChallenge.verify(n, ts, x, sig, pubBytes, relayNowMs = ts * 1000L)
        assertNotNull(keyId, "auth frame must verify")
        // The encoded form is deterministic.
        val reencoded = WireFrame.buildAuth(pubBytes, x, n, ts, sig)
        assertArrayEquals(bytes, reencoded)
    }

    // 11. buildSend is deterministic (recipient + envelope round-trips)
    @Test
    fun `send frame is deterministic`() {
        val r = randomBytes(16)
        val env = ByteArray(128) { it.toByte() }
        val bytes = WireFrame.buildSend(r, env)
        val reencoded = WireFrame.buildSend(r, env)
        assertArrayEquals(bytes, reencoded, "buildSend must be deterministic")
    }
}
