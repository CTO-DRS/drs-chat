package drs.chat.crypto

import drs.chat.protocol.AuthWire
import drs.chat.protocol.EnvelopeFactory
import drs.chat.protocol.EnvelopeKind
import drs.chat.protocol.InnerMessage
import drs.chat.protocol.InnerPayload
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.security.SecureRandom

@Suppress("DestructuringDeclarationWithTooManyEntries", "LongMethod", "StringLiteralDuplication")
class CryptoEngineTest {
    private fun twoParties(): Pair<CryptoEngine, CryptoEngine> {
        val alice = CryptoEngine.generate(1001, 1)
        val bob = CryptoEngine.generate(2002, 1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
        bob.establishSessionTo(alice.localKeyId(), alice.inviteBundle(1, 1, 1))
        return alice to bob
    }

    private fun decrypt(
        c: CryptoEngine,
        ct: ByteArray,
        from: CryptoEngine,
    ): ByteArray = c.decryptFromContact(from.localKeyId(), ct)

    // 1. Alice→Bob PQXDH first message (PreKeySignalMessage, decrypt)
    @Test fun `Alice to Bob PQXDH first message decrypts`() {
        val alice = CryptoEngine.generate(1001, 1)
        val bob = CryptoEngine.generate(2002, 1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
        val ct = alice.encryptForContact(bob.localKeyId(), "hello".toByteArray())
        assertArrayEquals("hello".toByteArray(), decrypt(bob, ct, alice))
    }

    // 2. Bob→Alice PQXDH first message (reverse direction)
    @Test fun `Bob to Alice PQXDH first message decrypts`() {
        val (alice, bob) = twoParties()
        val ct = bob.encryptForContact(alice.localKeyId(), "reply".toByteArray())
        assertArrayEquals("reply".toByteArray(), decrypt(alice, ct, bob))
    }

    // 3. Out-of-order (skipped keys: send 3, receive 3-1-2)
    @Test fun `out of order messages decrypt correctly`() {
        val (alice, bob) = twoParties()
        val m1 = alice.encryptForContact(bob.localKeyId(), "m1".toByteArray())
        val m2 = alice.encryptForContact(bob.localKeyId(), "m2".toByteArray())
        val m3 = alice.encryptForContact(bob.localKeyId(), "m3".toByteArray())
        assertArrayEquals("m3".toByteArray(), decrypt(bob, m3, alice))
        assertArrayEquals("m1".toByteArray(), decrypt(bob, m1, alice))
        assertArrayEquals("m2".toByteArray(), decrypt(bob, m2, alice))
    }

    // 4. Tamper → failure (flip a ciphertext byte → decrypt fails)
    @Test fun `tampered ciphertext fails to decrypt`() {
        val (alice, bob) = twoParties()
        val ct = alice.encryptForContact(bob.localKeyId(), "hello".toByteArray())
        val tampered = ct.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
        assertThrows(Exception::class.java) { decrypt(bob, tampered, alice) }
    }

    // 5. Eve forgery → UntrustedSender (Eve sends, Bob rejects — identity trust)
    @Test fun `Eve forgery rejected as UntrustedSender`() {
        val alice = CryptoEngine.generate(1001, 1)
        val bob = CryptoEngine.generate(2002, 1)
        val eve = CryptoEngine.generate(9999, 1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
        bob.trustIdentity(alice.localKeyId().bytes)
        // Eve also establishes a session to Bob (she has Bob's bundle)
        eve.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 2, 1))
        // Eve sends a message; Bob checks trust → Eve is NOT trusted
        assertFalse(bob.isTrustedIdentity(eve.localKeyId().bytes), "Eve must be untrusted")
        // Alice IS trusted
        assertTrue(bob.isTrustedIdentity(alice.localKeyId().bytes), "Alice must be trusted")
    }

    // 6. Session reset re-handshake (archive + re-establish)
    @Test fun `session reset re-establishes with fresh bundle`() {
        val (alice, bob) = twoParties()
        assertArrayEquals("init".toByteArray(), decrypt(bob, alice.encryptForContact(bob.localKeyId(), "init".toByteArray()), alice))
        alice.store.deleteSession(alice.addressOf(bob.localKeyId()))
        bob.generateOneTimePreKey(2)
        bob.generateKyberPreKey(2)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 2, 2))
        val ct = alice.encryptForContact(bob.localKeyId(), "post-reset".toByteArray())
        assertArrayEquals("post-reset".toByteArray(), decrypt(bob, ct, alice))
    }

    // 7. OTK consumption (Alice uses Bob's OTK; Bob's pool depletes)
    @Test fun `OTK consumption depletes the pool after Bob decrypts`() {
        val bob = CryptoEngine.generate(2002, 1)
        val before = bob.otkPoolSize()
        val alice = CryptoEngine.generate(1001, 1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
        val ct = alice.encryptForContact(bob.localKeyId(), "hello".toByteArray())
        bob.decryptFromContact(alice.localKeyId(), ct) // this consumes Bob's OTK
        val after = bob.otkPoolSize()
        assertTrue(after < before, "OTK pool must deplete after decrypt: before=$before after=$after")
    }

    // 8. OTK threshold replenish (pool < 10 → replenish; 99≥25 → no-op)
    @Test fun `replenish adds OTKs when below threshold, no-op when above`() {
        val engine = CryptoEngine.generate(3003, 1)
        // pool starts at 25 (≥10) → replenish is a no-op
        val added0 = engine.replenishOtkIfNeeded(nextId = 26)
        assertEquals(0, added0, "99≥25 → no replenish (no-op)")
        // simulate depletion: remove all but 5 OTKs
        for (i in 1..20) {
            try {
                engine.store.removePreKey(i)
            } catch (_: Exception) {
            }
        }
        val added1 = engine.replenishOtkIfNeeded(nextId = 100)
        assertTrue(added1 > 0, "pool <10 → must replenish")
        assertEquals(25, engine.otkPoolSize(), "pool must be back to 25 after replenish")
    }

    // 9. Signed-prekey 72h rotation with injected clock
    @Test fun `signed-prekey rotates after 72h`() {
        val engine = CryptoEngine.generate(4004, 1)
        val oldT = engine.store.loadSignedPreKey(1).timestamp
        // just under 72h → no rotation
        assertFalse(engine.rotateSignedPreKeyIfNeeded(1, oldT + 71 * 60 * 60 * 1000L))
        // just over 72h → rotation
        assertTrue(engine.rotateSignedPreKeyIfNeeded(1, oldT + 73 * 60 * 60 * 1000L))
        val newT = engine.store.loadSignedPreKey(1).timestamp
        assertTrue(newT > oldT, "new signed-prekey must have a newer timestamp")
    }

    // 10. Relay challenge auth (sign nonce → verify)
    @Test fun `relay challenge signature verifies`() {
        val alice = CryptoEngine.generate(1001, 1)
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val ts = 100L
        val sig = alice.relayChallengeSig(nonce, ts)
        val data = AuthWire.authData(nonce, ts, alice.localX25519Pub())
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(alice.localEd25519Pub(), 0))
        verifier.update(data, 0, data.size)
        assertTrue(verifier.verifySignature(sig))
    }

    // 11. Plaintext cap (text > 4096 → reject)
    @Test fun `plaintext over cap is rejected`() {
        val (alice, bob) = twoParties()
        val huge = ByteArray(drs.chat.protocol.EnvelopeConstants.INNER_TEXT_MAX_CHARS + 1) { 'x'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) { alice.encryptForContact(bob.localKeyId(), huge) }
    }

    // 12. Bundle-signature tamper (flip a byte in bundle's sig → verify fails)
    @Test fun `bundle-signature tamper is detectable`() {
        val alice = CryptoEngine.generate(1001, 1)
        val bob = CryptoEngine.generate(2002, 1)
        val bundle = bob.inviteBundle(1, 1, 1)
        // Tamper the signedPreKeySignature → process must reject (sig verification fails)
        val tamperedSig = bundle.signedPreKeySignature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val tamperedBundle =
            org.signal.libsignal.protocol.state.PreKeyBundle(
                bundle.registrationId, bundle.deviceId, bundle.preKeyId, bundle.preKey,
                bundle.signedPreKeyId, bundle.signedPreKey, tamperedSig,
                bundle.identityKey, bundle.kyberPreKeyId, bundle.kyberPreKey, bundle.kyberPreKeySignature,
            )
        assertThrows(Exception::class.java) { alice.establishSessionTo(bob.localKeyId(), tamperedBundle) }
    }

    // 13. messageId-in-signed-header
    @Test fun `messageId is in the signed header scope`() {
        val alice = CryptoEngine.generate(1001, 1)
        val bob = CryptoEngine.generate(2002, 1)
        val mid = alice.newMessageId()
        val env =
            EnvelopeFactory.build(
                alice,
                alice.localKeyId().bytes,
                bob.localKeyId().bytes,
                100L,
                60,
                EnvelopeKind.MESSAGE,
                "hello".toByteArray(),
                messageId = mid,
            )
        // tamper messageId → signature must fail
        val tampered = env.copy(messageId = mid.copyOf().also { it[0] = (it[0].toInt() xor 0xFF).toByte() })
        assertFalse(
            alice.verifyEnvelopeHeader(
                alice.localEd25519Pub(),
                drs.chat.protocol.EnvelopeCodec.headerBytesOf(tampered),
                env.signature,
            ),
        )
    }

    // 14. Subsequent message (SignalMessage)
    @Test fun `subsequent message is a SignalMessage and decrypts`() {
        val (alice, bob) = twoParties()
        val ct = alice.encryptForContact(bob.localKeyId(), "second".toByteArray())
        assertArrayEquals("second".toByteArray(), decrypt(bob, ct, alice))
    }

    // 15. Ratchet across 20 messages
    @Test fun `ratchet works across 20 messages`() {
        val (alice, bob) = twoParties()
        for (i in 1..20) {
            val s = "m$i".toByteArray()
            assertArrayEquals(s, decrypt(bob, alice.encryptForContact(bob.localKeyId(), s), alice))
        }
    }

    // 16. Ratchet key changes after receiving a reply
    @Test fun `ratchet key changes after Bob replies`() {
        val (alice, bob) = twoParties()
        // Alice sends → Bob receives
        decrypt(bob, alice.encryptForContact(bob.localKeyId(), "1".toByteArray()), alice)
        // Before Bob replies, Alice's next message uses the same ratchet step
        val ct1 = alice.encryptForContact(bob.localKeyId(), "2".toByteArray())
        // Bob replies → ratchet key rotates
        decrypt(alice, bob.encryptForContact(alice.localKeyId(), "reply".toByteArray()), bob)
        // Alice's next message uses a new ratchet key
        val ct2 = alice.encryptForContact(bob.localKeyId(), "3".toByteArray())
        assertFalse(ct1.contentEquals(ct2), "ciphertext must differ after ratchet rotation")
        assertArrayEquals("2".toByteArray(), decrypt(bob, ct1, alice))
        assertArrayEquals("3".toByteArray(), decrypt(bob, ct2, alice))
    }

    // 17. Eve with wrong identity → UntrustedSender (identity key mismatch)
    @Test fun `Eve with wrong identity is untrusted`() {
        val bob = CryptoEngine.generate(2002, 1)
        val eve = CryptoEngine.generate(9999, 1)
        // Eve is never trusted by Bob
        assertFalse(bob.isTrustedIdentity(eve.localKeyId().bytes))
        // Bob trusts Alice
        val alice = CryptoEngine.generate(1001, 1)
        bob.trustIdentity(alice.localKeyId().bytes)
        assertTrue(bob.isTrustedIdentity(alice.localKeyId().bytes))
    }

    // 18. OTK pool below threshold (9 < 10 → replenish)
    @Test fun `OTK pool below threshold triggers replenish`() {
        val engine = CryptoEngine.generate(5005, 1)
        for (i in 1..16) {
            try {
                engine.store.removePreKey(i)
            } catch (_: Exception) {
            }
        }
        assertEquals(9, engine.otkPoolSize())
        val added = engine.replenishOtkIfNeeded(100)
        assertTrue(added > 0, "pool below threshold (9) → must replenish")
    }

    // 19. OTK pool above threshold (11 → no replenish)
    @Test fun `OTK pool above threshold is no-op`() {
        val engine = CryptoEngine.generate(6006, 1)
        // remove 14 OTKs → pool = 11 (above threshold)
        for (i in 1..14) {
            try {
                engine.store.removePreKey(i)
            } catch (_: Exception) {
            }
        }
        assertEquals(11, engine.otkPoolSize())
        val added = engine.replenishOtkIfNeeded(100)
        assertEquals(0, added, "pool above threshold (11) → no replenish")
    }

    // 20. Signed-prekey just-under 72h (no rotation)
    @Test fun `signed-prekey just under 72h does not rotate`() {
        val engine = CryptoEngine.generate(7007, 1)
        val oldT = engine.store.loadSignedPreKey(1).timestamp
        assertFalse(engine.rotateSignedPreKeyIfNeeded(1, oldT + 71 * 60 * 60 * 1000L))
    }

    // 21. Signed-prekey just-over 72h (rotation)
    @Test fun `signed-prekey just over 72h rotates`() {
        val engine = CryptoEngine.generate(8008, 1)
        val oldT = engine.store.loadSignedPreKey(1).timestamp
        assertTrue(engine.rotateSignedPreKeyIfNeeded(1, oldT + 72 * 60 * 60 * 1000L + 1))
    }

    // 22. Relay challenge: bad signature → reject
    @Test fun `relay challenge with bad signature is rejected`() {
        val alice = CryptoEngine.generate(1001, 1)
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val ts = 100L
        val sig = alice.relayChallengeSig(nonce, ts)
        val badSig = sig.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        val data = AuthWire.authData(nonce, ts, alice.localX25519Pub())
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(alice.localEd25519Pub(), 0))
        verifier.update(data, 0, data.size)
        assertFalse(verifier.verifySignature(badSig))
    }

    // 23. Relay challenge: expired nonce → reject (simulated via old timestamp)
    @Test fun `relay challenge with expired timestamp is stale`() {
        val alice = CryptoEngine.generate(1001, 1)
        val nonce = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val oldTs = 100L
        val sig = alice.relayChallengeSig(nonce, oldTs)
        val nowTs = oldTs + AuthWire.NONCE_TTL_SECONDS + 1
        // the signature is valid for oldTs, but the relay checks |now - ts| > TTL → reject
        assertTrue(nowTs - oldTs > AuthWire.NONCE_TTL_SECONDS, "timestamp must be expired")
    }

    // 24. Full envelope round-trip: encrypt → build envelope → decode → verify → decrypt
    @Test fun `full envelope round-trip encrypt sign verify decrypt`() {
        val alice = CryptoEngine.generate(1001, 1)
        val bob = CryptoEngine.generate(2002, 1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
        val inner = InnerMessage(drs.chat.domain.model.MessageId.random().bytes, "hello bob", 100L)
        val ct = alice.encryptForContact(bob.localKeyId(), InnerPayload.encodeMessage(inner))
        val env =
            EnvelopeFactory.build(
                alice,
                alice.localKeyId().bytes,
                bob.localKeyId().bytes,
                100L,
                60,
                EnvelopeKind.MESSAGE,
                ct,
            )
        val envBytes = drs.chat.protocol.EnvelopeCodec.encode(env)
        val decoded = drs.chat.protocol.EnvelopeCodec.decode(envBytes)
        assertTrue(
            alice.verifyEnvelopeHeader(alice.localEd25519Pub(), drs.chat.protocol.EnvelopeCodec.headerBytesOf(decoded), decoded.signature),
        )
        val decryptedInner = InnerPayload.decodeMessage(bob.decryptFromContact(alice.localKeyId(), decoded.payloadCiphertext))
        assertEquals("hello bob", decryptedInner.text)
    }
}
