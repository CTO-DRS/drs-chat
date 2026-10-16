package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WireEnvelopeTest {
    private fun fixedEnvelope(): WireEnvelope =
        WireEnvelope(
            1u, ByteArray(16) { it.toByte() }, ByteArray(16) { (it + 0x10).toByte() }, ByteArray(16) { (it + 0x20).toByte() },
            100L, 60, EnvelopeKind.MESSAGE.code, ByteArray(16) { (it + 0x30).toByte() }, ByteArray(64) { (it + 0x40).toByte() },
        )

    private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

    @Test fun `envelope round-trips through CBOR`() {
        val o = fixedEnvelope()
        assertEquals(o, EnvelopeCodec.decode(EnvelopeCodec.encode(o)))
    }

    @Test fun `encode is deterministic`() {
        val e = fixedEnvelope()
        assertArrayEquals(EnvelopeCodec.encode(e), EnvelopeCodec.encode(e))
    }

    @Test fun `encode decode encode is stable`() {
        val e = fixedEnvelope()
        val once = EnvelopeCodec.encode(e)
        assertArrayEquals(once, EnvelopeCodec.encode(EnvelopeCodec.decode(once)))
    }

    @Test fun `headerBytesOf identical from envelope or bare header`() {
        val e = fixedEnvelope()
        assertArrayEquals(EnvelopeCodec.headerBytesOf(e), EnvelopeCodec.headerBytesOf(e.header()))
    }

    // FROZEN golden vector — machine-captured from the real encoder (kotlinx-cbor 1.9.0,
    // useDefiniteLengthEncoding=true + alwaysUseByteString=true). Do NOT hand-edit.
    // If this fails after a dependency upgrade, bump EnvelopeConstants.PROTOCOL_VERSION.
    private val frozenGoldenHex: String =
        "a9617601636d696450000102030405060708090a0b0c0d0e0f63736964" +
            "50101112131415161718191a1b1c1d1e1f637269645020212223242526272829" +
            "2a2b2c2d2e2f62747318646374746c183c616b0062637450303132333435363738" +
            "393a3b3c3d3e3f637369675840404142434445464748494a4b4c4d4e4f50515253" +
            "5455565758595a5b5c5d5e5f606162636465666768696a6b6c6d6e6f70717273" +
            "7475767778797a7b7c7d7e7f"

    @Test fun `frozen golden vector pins the exact wire bytes`() {
        assertEquals(
            frozenGoldenHex,
            hex(EnvelopeCodec.encode(fixedEnvelope())),
            "envelope wire bytes drifted — bump protocolVersion",
        )
    }

    @Test fun `envelope built by EnvelopeFactory verifies`() {
        val crypto = FakeCryptoEngine.generate()
        val e =
            EnvelopeFactory.build(
                crypto,
                crypto.localKeyId().bytes,
                ByteArray(16) {
                    0xAA.toByte()
                },
                1_700_000_000_000L,
                604_800,
                EnvelopeKind.MESSAGE,
                "hello".toByteArray(),
            )
        assertTrue(crypto.verifyEnvelopeHeader(crypto.localEd25519Pub(), EnvelopeCodec.headerBytesOf(e), e.signature))
    }

    @Test fun `tampering the signature breaks verification`() {
        val crypto = FakeCryptoEngine.generate()
        val e =
            EnvelopeFactory.build(
                crypto,
                crypto.localKeyId().bytes,
                ByteArray(16) { 0x55 },
                1L,
                1,
                EnvelopeKind.MESSAGE,
                byteArrayOf(1, 2, 3),
            )
        val t = e.signature.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() }
        assertFalse(crypto.verifyEnvelopeHeader(crypto.localEd25519Pub(), EnvelopeCodec.headerBytesOf(e), t))
    }

    @Test fun `messageId is inside the signed scope (amendment proof)`() {
        val crypto = FakeCryptoEngine.generate()
        val e =
            EnvelopeFactory.build(
                crypto,
                crypto.localKeyId().bytes,
                ByteArray(16) {
                    0x55
                },
                1L,
                1,
                EnvelopeKind.MESSAGE,
                byteArrayOf(1, 2, 3),
                messageId = ByteArray(16) { 0x00 },
            )
        val tampered = e.copy(messageId = ByteArray(16) { 0xFF.toByte() })
        assertNotEquals(EnvelopeCodec.headerBytesOf(e).toList(), EnvelopeCodec.headerBytesOf(tampered).toList())
        assertFalse(crypto.verifyEnvelopeHeader(crypto.localEd25519Pub(), EnvelopeCodec.headerBytesOf(tampered), e.signature))
    }

    @Test fun `tampering senderKeyId breaks verification`() {
        val crypto = FakeCryptoEngine.generate()
        val e =
            EnvelopeFactory.build(
                crypto,
                crypto.localKeyId().bytes,
                ByteArray(16) { 0x55 },
                1L,
                1,
                EnvelopeKind.MESSAGE,
                byteArrayOf(1, 2, 3),
            )
        val t = e.copy(senderKeyId = e.senderKeyId.copyOf().also { it[0] = (it[0].toInt() xor 0x01).toByte() })
        assertFalse(crypto.verifyEnvelopeHeader(crypto.localEd25519Pub(), EnvelopeCodec.headerBytesOf(t), e.signature))
    }

    @Test fun `unsupported protocolVersion is rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            WireEnvelope(99u, ByteArray(16), ByteArray(16), ByteArray(16), 1L, 1, 0u, ByteArray(1), ByteArray(64))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EnvelopeHeader(99u, ByteArray(16), ByteArray(16), ByteArray(16), 1L, 1, 0u, ByteArray(1))
        }
    }

    @Test fun `oversized envelope throws`() {
        val e =
            WireEnvelope(
                1u,
                ByteArray(
                    16,
                ),
                ByteArray(16), ByteArray(16), 1L, 1, 0u, ByteArray(EnvelopeConstants.MAX_ENVELOPE_BYTES + 1024), ByteArray(64),
            )
        assertThrows(EnvelopeTooLargeException::class.java) { EnvelopeCodec.encode(e) }
    }

    @Test fun `isExpired respects ttl and clock skew`() {
        val now = 1_700_000_000_000L
        val fresh = fixedEnvelope().copy(createdAt = now, ttlSeconds = 60)
        assertFalse(fresh.isExpired(now))
        assertFalse(fresh.isExpired(now + 60_000 + 299_000))
        assertTrue(fresh.isExpired(now + 60_000 + 301_000))
    }

    // FROZEN kind table (ADR-0009) — kind 4 = READ_RECEIPT, kind 5 = DELIVERY_RECEIPT.
    @Test fun `kind 4 is READ_RECEIPT (frozen by ADR-0009)`() {
        assertEquals(4u.toUByte(), EnvelopeKind.READ_RECEIPT.code)
        assertEquals(EnvelopeKind.READ_RECEIPT, EnvelopeKind.fromCode(4u))
    }

    @Test fun `kind 5 is DELIVERY_RECEIPT (frozen by ADR-0009)`() {
        assertEquals(5u.toUByte(), EnvelopeKind.DELIVERY_RECEIPT.code)
        assertEquals(EnvelopeKind.DELIVERY_RECEIPT, EnvelopeKind.fromCode(5u))
    }

    @Test fun `unknown kind code 6 is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { EnvelopeKind.fromCode(6u) }
    }

    @Test fun `kind 0 is MESSAGE, kind 1 is SESSION_RESET`() {
        assertEquals(0u.toUByte(), EnvelopeKind.MESSAGE.code)
        assertEquals(1u.toUByte(), EnvelopeKind.SESSION_RESET.code)
    }
}
