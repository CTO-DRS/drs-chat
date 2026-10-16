package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

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

    // FROZEN golden vector — machine-captured from the real encoder (kotlinx-cbor 1.9.0,
    // useDefiniteLengthEncoding=true + alwaysUseByteString=true). Do NOT hand-edit.
    private val frozenBundleHex: String =
        "aa6265645820000102030405060708090a0b0c0d0e0f101112131415161718191a" +
            "1b1c1d1e1f62783258200102030405060708090a0b0c0d0e0f1011121314151617" +
            "18191a1b1c1d1e1f2064737069640163737070582002030405060708090a0b0c0d" +
            "0e0f101112131415161718191a1b1c1d1e1f202163737073584003040506070809" +
            "0a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a" +
            "2b2c2d2e2f303132333435363738393a3b3c3d3e3f404142646f74696402636f74" +
            "7058200405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f2021" +
            "2223646b70696403636b7070582005060708090a0b0c0d0e0f1011121314151617" +
            "18191a1b1c1d1e1f2021222324636b70735840060708090a0b0c0d0e0f10111213" +
            "1415161718191a1b1c1d1e1f202122232425262728292a2b2c2d2e2f3031323334" +
            "35363738393a3b3c3d3e3f404142434445"

    @Test fun `frozen golden vector pins the exact bundle wire bytes`() {
        assertEquals(
            frozenBundleHex,
            hex(ContactBundleCodec.encode(bundle())),
            "ContactBundle wire bytes drifted — bump protocolVersion",
        )
    }

    // Tamper at a KNOWN OFFSET inside the signedPreKeySig region.
    // From the frozen hex: the "sps" (signedPreKeySig) bstr lives at:
    //   offset 123: 0x58 (bstr length-marker for 64 bytes)
    //   offset 124: 0x40 (64 decimal)
    //   offsets 125..188: the 64-byte signature data (first byte = 0x03 = it+3 for it=0)
    @Test fun `tamper at known offset inside signedPreKeySig region`() {
        val original = bundle()
        val bytes = ContactBundleCodec.encode(original)
        // assert the sig region is where we expect
        assertEquals(0x58.toByte(), bytes[123], "expected bstr length-marker at offset 123")
        assertEquals(0x40.toByte(), bytes[124], "expected length=64 at offset 124")
        assertEquals(0x03.toByte(), bytes[125], "expected first sig byte (it+3=3) at offset 125")
        // flip byte at offset 125 (first byte of the sig data)
        val tampered = bytes.copyOf()
        tampered[125] = (tampered[125].toInt() xor 0x01).toByte()
        val decoded = ContactBundleCodec.decode(tampered)
        assertFalse(
            decoded.signedPreKeySig.contentEquals(original.signedPreKeySig),
            "decoded signedPreKeySig must differ after flipping offset 125",
        )
        assertEquals(
            0x02.toByte(),
            decoded.signedPreKeySig[0],
            "flipped byte should be 0x03 xor 0x01 = 0x02",
        )
    }
}
