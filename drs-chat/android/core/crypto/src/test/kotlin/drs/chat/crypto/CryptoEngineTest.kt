package drs.chat.crypto

import drs.chat.domain.model.MessageId
import drs.chat.protocol.EnvelopeCodec
import drs.chat.protocol.EnvelopeFactory
import drs.chat.protocol.EnvelopeKind
import drs.chat.protocol.InnerMessage
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage

class CryptoEngineTest {
    private fun dispatchDecrypt(
        engine: CryptoEngine,
        contactKeyId: ByteArray,
        ct: ByteArray,
    ): ByteArray {
        val cipher =
            ct.let {
                try {
                    PreKeySignalMessage(it)
                } catch (_: Exception) {
                    SignalMessage(it)
                }
            }
        return when (cipher) {
            is PreKeySignalMessage -> {
                val sc =
                    org.signal.libsignal.protocol.SessionCipher(
                        engine.store,
                        org.signal.libsignal.protocol.SignalProtocolAddress(drs.chat.domain.model.KeyId(contactKeyId).toHex(), 1),
                    )
                sc.decrypt(cipher)
            }
            is SignalMessage -> {
                val sc =
                    org.signal.libsignal.protocol.SessionCipher(
                        engine.store,
                        org.signal.libsignal.protocol.SignalProtocolAddress(drs.chat.domain.model.KeyId(contactKeyId).toHex(), 1),
                    )
                sc.decrypt(cipher)
            }
            else -> error("unexpected")
        }
    }

    // 1. Alice→Bob PQXDH first message decrypts
    @Test fun `Alice to Bob first PQXDH message decrypts`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        val ct = alice.encryptForContact(bob.localKeyId(), "hello".toByteArray())
        val pt = dispatchDecrypt(bob, alice.localKeyId().bytes, ct)
        assertEquals("hello", String(pt))
    }

    // 2. Bob→Alice reverse PQXDH
    @Test fun `Bob to Alice reverse PQXDH`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        alice.signedPreKey(1)
        alice.oneTimePreKey(1)
        alice.kyberPreKey(1)
        bob.establishSessionTo(alice.localKeyId(), alice.inviteBundle())
        val ct = bob.encryptForContact(alice.localKeyId(), "reply".toByteArray())
        val pt = dispatchDecrypt(alice, bob.localKeyId().bytes, ct)
        assertEquals("reply", String(pt))
    }

    // 3. Subsequent messages (SignalMessage)
    @Test fun `subsequent messages decrypt on established session`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        val ct1 = alice.encryptForContact(bob.localKeyId(), "first".toByteArray())
        dispatchDecrypt(bob, alice.localKeyId().bytes, ct1) // Bob decrypts → session established
        val ct2 = alice.encryptForContact(bob.localKeyId(), "second".toByteArray())
        val pt2 = dispatchDecrypt(bob, alice.localKeyId().bytes, ct2)
        assertEquals("second", String(pt2))
    }

    // 4. Out-of-order (skipped keys)
    @Test fun `out of order messages decrypt correctly`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        val ct1 = alice.encryptForContact(bob.localKeyId(), "m1".toByteArray())
        val ct2 = alice.encryptForContact(bob.localKeyId(), "m2".toByteArray())
        val ct3 = alice.encryptForContact(bob.localKeyId(), "m3".toByteArray())
        // deliver 3, 1, 2 — Double Ratchet caches skipped keys
        assertEquals("m3", String(dispatchDecrypt(bob, alice.localKeyId().bytes, ct3)))
        assertEquals("m1", String(dispatchDecrypt(bob, alice.localKeyId().bytes, ct1)))
        assertEquals("m2", String(dispatchDecrypt(bob, alice.localKeyId().bytes, ct2)))
    }

    // 5. Tamper ciphertext → failure
    @Test fun `tampered ciphertext fails decryption`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        val ct = alice.encryptForContact(bob.localKeyId(), "hello".toByteArray()).copyOf()
        ct[ct.lastIndex] = (ct[ct.lastIndex].toInt() xor 0x01).toByte()
        assertThrows(Exception::class.java) { dispatchDecrypt(bob, alice.localKeyId().bytes, ct) }
    }

    // 6. Eve forgery → UntrustedIdentityException
    @Test fun `Eve forgery is rejected as untrusted identity`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        val eve = CryptoEngine.generate(3003)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        // Bob decrypts Alice's first message → trusts Alice's identity at address(aliceKeyId)
        val ctAlice = alice.encryptForContact(bob.localKeyId(), "from Alice".toByteArray())
        dispatchDecrypt(bob, alice.localKeyId().bytes, ctAlice)
        // Bob's OTK 1 was consumed; generate a fresh OTK for Eve's session
        bob.oneTimePreKey(2)
        // Eve establishes her own session to Bob and encrypts → PreKeySignalMessage with Eve's identity
        eve.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 2, 1))
        val ctEve = eve.encryptForContact(bob.localKeyId(), "forged".toByteArray())
        // Bob tries to decrypt Eve's message at ALICE's address → identity mismatch → UntrustedIdentityException
        assertThrows(Exception::class.java) {
            dispatchDecrypt(bob, alice.localKeyId().bytes, ctEve)
        }
    }

    // 7. Session reset → re-handshake
    @Test fun `session reset re-handshakes with fresh prekeys`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        val ct1 = alice.encryptForContact(bob.localKeyId(), "pre-reset".toByteArray())
        assertEquals("pre-reset", String(dispatchDecrypt(bob, alice.localKeyId().bytes, ct1)))
        // Alice drops session + Bob generates fresh OTK/Kyber
        alice.store.deleteSession(org.signal.libsignal.protocol.SignalProtocolAddress(bob.localKeyId().toHex(), 1))
        bob.oneTimePreKey(2)
        bob.kyberPreKey(2)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 2, 2))
        val ct2 = alice.encryptForContact(bob.localKeyId(), "post-reset".toByteArray())
        assertTrue(PreKeySignalMessage(ct2) is PreKeySignalMessage) // first after reset = PreKey
        assertEquals("post-reset", String(dispatchDecrypt(bob, alice.localKeyId().bytes, ct2)))
    }

    // 8. OTK consumption (one-time prekey used → removed)
    @Test fun `OTK is consumed on session establishment`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        assertTrue(bob.store.containsPreKey(1))
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
        val ct = alice.encryptForContact(bob.localKeyId(), "consume".toByteArray())
        // Bob decrypts → libsignal consumes the OTK from the store
        dispatchDecrypt(bob, alice.localKeyId().bytes, ct)
        assertFalse(bob.store.containsPreKey(1))
    }

    // 9. OTK replenish below threshold
    @Test fun `OTK replenish generates new keys when below threshold`() {
        val engine = CryptoEngine.generate(4004)
        val generated = engine.replenishOtk(currentPoolSize = 5)
        assertEquals(25, generated)
    }

    // 10. OTK replenish no-op when >= threshold (99 >= 25)
    @Test fun `OTK replenish is no-op when pool at or above threshold`() {
        val engine = CryptoEngine.generate(4004)
        assertEquals(0, engine.replenishOtk(currentPoolSize = 99))
    }

    // 11. Signed-prekey 72h rotation with injected clock
    @Test fun `signed prekey rotates after 72h with injected clock`() {
        val engine = CryptoEngine.generate(5005)
        engine.signedPreKey(1)
        var fakeNow = System.currentTimeMillis()
        engine.setClock { fakeNow }
        // not yet (within 72h)
        assertFalse(engine.rotateSignedPreKey(1))
        // advance past 72h
        fakeNow += 72 * 60 * 60 * 1000L + 1
        assertTrue(engine.rotateSignedPreKey(1))
        // new SPK at id=2
        assertTrue(engine.store.containsSignedPreKey(2))
        assertFalse(engine.store.containsSignedPreKey(1))
    }

    // 12. Relay challenge auth
    @Test fun `relay challenge auth sign and verify`() {
        val engine = CryptoEngine.generate(6006)
        val nonce = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val ts = 12345L
        val sig = engine.signAuthChallenge(nonce, ts)
        assertEquals(64, sig.size)
        // verify: reconstruct the auth data and check with Ed25519
        val tsBytes = ByteArray(8) { (ts ushr (it * 8)).toByte() }
        val data = nonce + tsBytes + "drs-auth-v1".toByteArray() + engine.localX25519Pub()
        val verifier = Ed25519Signer()
        verifier.init(false, Ed25519PublicKeyParameters(engine.localEd25519Pub(), 0))
        verifier.update(data, 0, data.size)
        assertTrue(verifier.verifySignature(sig))
    }

    // 13. Plaintext cap
    @Test fun `plaintext over cap is rejected`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        // InnerMessage rejects text > 4096 chars
        assertThrows(IllegalArgumentException::class.java) {
            drs.chat.protocol.InnerMessage(MessageId.random().bytes, "x".repeat(4097), 1L)
        }
    }

    // 14. Bundle-signature tamper → SessionBuilder rejects
    @Test fun `tampered bundle signature is rejected by SessionBuilder`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        val bundle = bob.inviteBundle()
        val tamperedSig = bundle.signedPreKeySignature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        // Reconstruct the bundle with the tampered sig — can't copy a PreKeyBundle; use the bundle constructor
        val tampered =
            org.signal.libsignal.protocol.state.PreKeyBundle(
                bundle.registrationId, bundle.deviceId, bundle.preKeyId, bundle.preKey,
                bundle.signedPreKeyId, bundle.signedPreKey, tamperedSig, bundle.identityKey,
                bundle.kyberPreKeyId, bundle.kyberPreKey, bundle.kyberPreKeySignature,
            )
        assertThrows(Exception::class.java) {
            alice.establishSessionTo(bob.localKeyId(), tampered)
        }
    }

    // 15. messageId-in-signed-header
    @Test fun `messageId is inside the signed envelope header`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        val env =
            EnvelopeFactory.build(
                alice,
                alice.localKeyId().bytes,
                bob.localKeyId().bytes,
                1L,
                60,
                EnvelopeKind.MESSAGE,
                byteArrayOf(1, 2, 3),
            )
        val tampered = env.copy(messageId = env.messageId.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() })
        assertFalse(alice.verifyEnvelopeHeader(alice.localEd25519Pub(), EnvelopeCodec.headerBytesOf(tampered), env.signature))
    }

    // 16. Ratchet across 20 messages
    @Test fun `ratchet keeps working across 20 messages`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        for (i in 1..20) {
            val sent = "m$i".toByteArray()
            assertEquals("m$i", String(dispatchDecrypt(bob, alice.localKeyId().bytes, alice.encryptForContact(bob.localKeyId(), sent))))
        }
    }

    // 17. Bidirectional conversation
    @Test fun `bidirectional conversation both directions`() {
        val alice = CryptoEngine.generate(1001)
        val bob = CryptoEngine.generate(2002)
        bob.signedPreKey(1)
        bob.oneTimePreKey(1)
        bob.kyberPreKey(1)
        alice.signedPreKey(1)
        alice.oneTimePreKey(1)
        alice.kyberPreKey(1)
        alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle())
        bob.establishSessionTo(alice.localKeyId(), alice.inviteBundle())
        // Alice → Bob
        assertEquals(
            "A1",
            String(dispatchDecrypt(bob, alice.localKeyId().bytes, alice.encryptForContact(bob.localKeyId(), "A1".toByteArray()))),
        )
        // Bob → Alice
        assertEquals(
            "B1",
            String(dispatchDecrypt(alice, bob.localKeyId().bytes, bob.encryptForContact(alice.localKeyId(), "B1".toByteArray()))),
        )
    }

    // 18. KeyId derivation consistency
    @Test fun `keyId is deterministic from Ed25519 pub`() {
        val engine = CryptoEngine.generate(7007)
        val k1 = engine.localKeyId()
        val k2 = engine.localKeyId()
        assertEquals(k1.toHex(), k2.toHex())
        assertEquals(16, k1.bytes.size)
    }

    // 19. Fingerprint word-list rendering
    @Test fun `fingerprint renders as 8 words from the 256-word list`() {
        val engine = CryptoEngine.generate(8008)
        val fp = drs.chat.protocol.Fingerprint.compute(engine.localEd25519Pub(), engine.localX25519Pub())
        val words = drs.chat.protocol.Fingerprint.toWords(fp)
        assertEquals(8, words.size)
        assertTrue(words.all { it in drs.chat.protocol.WordList.LIST })
    }

    // 20. ContactBundle round-trip with real prekeys
    @Test fun `ContactBundle round-trips with real libsignal prekeys`() {
        val engine = CryptoEngine.generate(9009)
        engine.signedPreKey(1)
        engine.oneTimePreKey(1)
        engine.kyberPreKey(1)
        val bundle = engine.buildContactBundle()
        val encoded = drs.chat.protocol.ContactBundleCodec.encode(bundle)
        val decoded = drs.chat.protocol.ContactBundleCodec.decode(encoded)
        assertEquals(bundle, decoded)
    }

    // 21. ContactBundle OTK-less fallback
    @Test fun `ContactBundle OTK-less fallback round-trips`() {
        val engine = CryptoEngine.generate(9009)
        engine.signedPreKey(1)
        engine.kyberPreKey(1)
        val bundle = engine.buildContactBundle(otkId = null)
        val encoded = drs.chat.protocol.ContactBundleCodec.encode(bundle)
        val decoded = drs.chat.protocol.ContactBundleCodec.decode(encoded)
        assertEquals(null, decoded.oneTimePreKeyId)
        assertEquals(null, decoded.oneTimePreKeyPub)
    }

    // 22. NewMessageId uniqueness
    @Test fun `newMessageId produces unique values`() {
        val engine = CryptoEngine.generate(1010)
        val a = engine.newMessageId()
        val b = engine.newMessageId()
        assertNotEquals(a.toList(), b.toList())
        assertEquals(16, a.size)
    }

    // 23. Ed25519 sign/verify round-trip
    @Test fun `Ed25519 sign and verify round-trip`() {
        val engine = CryptoEngine.generate(1111)
        val data = "test data".toByteArray()
        val sig = engine.signEnvelopeHeader(data)
        assertTrue(engine.verifyEnvelopeHeader(engine.localEd25519Pub(), data, sig))
        // tamper the data
        assertFalse(engine.verifyEnvelopeHeader(engine.localEd25519Pub(), "wrong".toByteArray(), sig))
    }

    // 24. Envelope signature verifies after CBOR round-trip
    @Test fun `envelope signature survives CBOR round-trip`() {
        val alice = CryptoEngine.generate(1212)
        val bob = CryptoEngine.generate(2222)
        val env =
            EnvelopeFactory.build(
                alice,
                alice.localKeyId().bytes,
                bob.localKeyId().bytes,
                1L,
                60,
                EnvelopeKind.MESSAGE,
                "hello".toByteArray(),
            )
        val wire = EnvelopeCodec.encode(env)
        val decoded = EnvelopeCodec.decode(wire)
        assertTrue(alice.verifyEnvelopeHeader(alice.localEd25519Pub(), EnvelopeCodec.headerBytesOf(decoded), decoded.signature))
    }

    private fun byteArrayOf(vararg b: Int): ByteArray = b.map { it.toByte() }.toByteArray()
}
