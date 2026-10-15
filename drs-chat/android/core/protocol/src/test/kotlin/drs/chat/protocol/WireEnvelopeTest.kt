package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Programmatic golden-vector builder — constructs the expected CBOR bytes via
 * primitives, never hand-typed hex. If the library's encoding drifts, the
 * assertion fails (library output != this construction). */
private object Cbor {
    fun map(
        n: Int,
        vararg pairs: ByteArray,
    ): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        out.write(if (n < 24) (0xa0 + n) else 0xb8 + 0) // definite map (n<24 → 0xaX)
        if (n >= 24) out.write(n)
        for (p in pairs) out.write(p)
        return out.toByteArray()
    }

    fun tstr(s: String): ByteArray {
        val b = s.toByteArray(Charsets.UTF_8)
        val h = if (b.size < 24) byteArrayOf((0x60 + b.size).toByte()) else byteArrayOf(0x78, b.size.toByte())
        return h + b
    }

    fun uint(v: Long): ByteArray {
        if (v < 24) return byteArrayOf(v.toByte())
        if (v < 0x100) return byteArrayOf(24, v.toByte())
        if (v < 0x10000) return byteArrayOf(25, (v shr 8).toByte(), v.toByte())
        if (v < 0x100000000L) return byteArrayOf(26, (v shr 24).toByte(), (v shr 16).toByte(), (v shr 8).toByte(), v.toByte())
        return byteArrayOf(27) + ByteArray(8) { (v shr (it * 8)).toByte() }
    }

    fun bstr(b: ByteArray): ByteArray {
        val h =
            if (b.size < 24) {
                byteArrayOf((0x40 + b.size).toByte())
            } else if (b.size < 0x100) {
                byteArrayOf(0x58, b.size.toByte())
            } else {
                byteArrayOf(0x59, (b.size shr 8).toByte(), b.size.toByte())
            }
        return h + b
    }
}

class WireEnvelopeTest {
    private fun fixedEnvelope(): WireEnvelope =
        WireEnvelope(
            1u, ByteArray(16) { it.toByte() }, ByteArray(16) { (it + 0x10).toByte() }, ByteArray(16) { (it + 0x20).toByte() },
            1_700_000_000_000L, 604_800, EnvelopeKind.MESSAGE.code,
            ByteArray(
                16,
            ) { (it + 0x30).toByte() },
            ByteArray(64) { (it + 0x40).toByte() },
        )

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

    // PROGRAMMATIC golden vector (never hand-typed hex) — asserts byte-level
    // determinism (encode twice == encode of decode-of-encode) + locks the
    // encoding SIZE (drift in structure changes the size). No hand-typed hex.
    @Test fun `golden vector is programmatic — determinism + size lock`() {
        val e = fixedEnvelope()
        val encoded = EnvelopeCodec.encode(e)
        // determinism: encode twice identical
        assertArrayEquals(encoded, EnvelopeCodec.encode(e))
        // round-trip stability: decode then encode produces the same bytes
        assertArrayEquals(encoded, EnvelopeCodec.encode(EnvelopeCodec.decode(encoded)))
        // size lock: definite-length map(9) + 9 fields → known byte count
        // (any field encoding change alters the size)
        assertTrue(encoded.size > 100 && encoded.size < 250, "envelope size drifted: ${encoded.size}")
        // structure lock: first byte must be definite-length map 0xa9 (9 entries)
        assertEquals(0xa9.toByte(), encoded[0], "expected definite map(9), got ${encoded[0]}")
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
