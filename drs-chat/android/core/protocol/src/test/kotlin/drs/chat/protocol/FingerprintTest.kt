package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FingerprintTest {
    private fun keys(seed: Byte): Pair<ByteArray, ByteArray> =
        ByteArray(32) { (seed + it).toByte() } to ByteArray(32) { (seed + it + 1).toByte() }

    @Test fun `fingerprint is deterministic`() {
        val (ed, x) = keys(1)
        assertEquals(Fingerprint.compute(ed, x).toList(), Fingerprint.compute(ed, x).toList())
    }

    @Test fun `fingerprint changes with the Ed25519 pub`() {
        val (ed1, x) = keys(1)
        val (ed2, _) = keys(5)
        assertNotEquals(Fingerprint.compute(ed1, x).toList(), Fingerprint.compute(ed2, x).toList())
    }

    @Test fun `fingerprint changes with the X25519 pub`() {
        val (ed, x1) = keys(1)
        val (_, x2) = keys(9)
        assertNotEquals(Fingerprint.compute(ed, x1).toList(), Fingerprint.compute(ed, x2).toList())
    }

    @Test fun `fingerprint is 32 bytes`() {
        val (ed, x) = keys(1)
        assertEquals(32, Fingerprint.compute(ed, x).size)
    }

    @Test fun `keyId is the first 16 bytes`() {
        val (ed, x) = keys(1)
        val fp = Fingerprint.compute(ed, x)
        val k = Fingerprint.keyIdOf(ed, x)
        assertEquals(16, k.bytes.size)
        assertEquals(fp.take(16).toList(), k.bytes.toList())
    }

    @Test fun `toWords renders exactly 8 words from the 256-word list`() {
        val (ed, x) = keys(1)
        val w = Fingerprint.toWords(Fingerprint.compute(ed, x))
        assertEquals(8, w.size)
        assertTrue(w.all { it in WordList.LIST })
    }
}
