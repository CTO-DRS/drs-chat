package drs.chat.relay

import drs.chat.protocol.AuthWire
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

/**
 * Relay-side auth challenge verification (ADR-0008 wire contract).
 *
 * The client signs `AuthWire.authData(nonce, ts, x25519Pub)` with its Ed25519
 * identity key. The relay verifies the signature, checks the timestamp is
 * within [AuthWire.NONCE_TTL_SECONDS] of the relay's clock, and accepts the
 * connection as authenticated.
 *
 * The relay does NOT decrypt or parse envelopes — this is the only crypto
 * operation it performs. Identity key bytes are stored against the connection
 * so future frames are routed by `recipientKeyId = SHA-256(ed25519Pub)[0..15]`.
 *
 * Returns the verified `keyId` on success, or null on any failure.
 */
object AuthChallenge {
    /** Verify a client's auth challenge response. Returns the verified keyId, or null on failure. */
    fun verify(
        nonce: ByteArray,
        ts: Long,
        x25519Pub: ByteArray,
        signature: ByteArray,
        ed25519Pub: ByteArray,
        relayNowMs: Long,
    ): ByteArray? {
        // Clock skew: |now - ts| must be within NONCE_TTL_SECONDS * 1000 ms.
        // The ts in the wire format is seconds; relayNowMs is millis.
        val tsMs = ts * 1000L
        val skew = Math.abs(relayNowMs - tsMs)
        if (skew > AuthWire.NONCE_TTL_SECONDS * 1000L) return null

        // Verify Ed25519 sig over AuthWire.authData(nonce, ts, x25519Pub)
        val data = AuthWire.authData(nonce, ts, x25519Pub)
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(ed25519Pub, 0))
        verifier.update(data, 0, data.size)
        if (!verifier.verifySignature(signature)) return null

        // Compute the recipientKeyId — same as the client's KeyId.
        return AuthWire.keyIdOf(ed25519Pub)
    }
}
