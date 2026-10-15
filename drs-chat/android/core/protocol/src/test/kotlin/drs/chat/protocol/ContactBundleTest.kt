package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ContactBundleTest {
    private fun bundle(withOtk: Boolean = true): ContactBundle =
        ContactBundle(
            ed25519Pub = ByteArray(32) { it.toByte() },
            x25519Pub = ByteArray(32) { (it + 1).toByte() },
            signedPreKeyId = 1,
            signedPreKeyPub =
                ByteArray(
                    32,
                ) { (it + 2).toByte() },
            signedPreKeySig = ByteArray(64) { (it + 3).toByte() },
            oneTimePreKeyId = if (withOtk) 2 else null, oneTimePreKeyPub = if (withOtk) ByteArray(32) { (it + 4).toByte() } else null,
            kyberPreKeyId = 3, kyberPreKeyPub = ByteArray(32) { (it + 5).toByte() }, kyberPreKeySig = ByteArray(64) { (it + 6).toByte() },
        )

    @Test fun `round-trips through CBOR`() {
        val b = bundle()
        assertEquals(b, ContactBundleCodec.decode(ContactBundleCodec.encode(b)))
    }

    @Test fun `encode decode encode is stable`() {
        val b = bundle()
        val once = ContactBundleCodec.encode(b)
        assertArrayEquals(once, ContactBundleCodec.encode(ContactBundleCodec.decode(once)))
    }

    @Test fun `OTK-less bundle (signed-prekey-only X3DH fallback) round-trips`() {
        val b = bundle(withOtk = false)
        val d = ContactBundleCodec.decode(ContactBundleCodec.encode(b))
        assertEquals(null, d.oneTimePreKeyId)
        assertEquals(null, d.oneTimePreKeyPub)
    }

    @Test fun `OTK id+pub must both be null or both non-null`() {
        assertThrows(IllegalArgumentException::class.java) { bundle().copy(oneTimePreKeyId = null, oneTimePreKeyPub = ByteArray(32)) }
    }

    @Test fun `tampered signed-prekey sig is detectable by caller`() {
        // codec does not verify the inner XEdDSA sig (that's :core:crypto's job),
        // but a bundle with a different sig decodes to a different sig (observable)
        val original = bundle()
        val tampered = original.copy(signedPreKeySig = original.signedPreKeySig.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() })
        val d = ContactBundleCodec.decode(ContactBundleCodec.encode(tampered))
        assertEquals(true, !d.signedPreKeySig.contentEquals(original.signedPreKeySig))
    }

    @Test fun `field size validation rejects bad ed25519 pub`() {
        assertThrows(IllegalArgumentException::class.java) { bundle().copy(ed25519Pub = ByteArray(31)) }
    }
}
