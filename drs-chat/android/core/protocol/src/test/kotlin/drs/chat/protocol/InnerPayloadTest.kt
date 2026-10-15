package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class InnerPayloadTest {
    private fun mid(): ByteArray = ByteArray(16) { it.toByte() }

    @Test fun `message round-trips through CBOR`() {
        val m = InnerMessage(mid(), "hello", 1234L)
        assertEquals(m, InnerPayload.decodeMessage(InnerPayload.encodeMessage(m)))
    }

    @Test fun `receipt round-trips through CBOR`() {
        val r = InnerReceipt(mid(), 5678L)
        assertEquals(r, InnerPayload.decodeReceipt(InnerPayload.encodeReceipt(r)))
    }

    @Test fun `text over cap is rejected`() {
        assertThrows(
            IllegalArgumentException::class.java,
        ) { InnerMessage(mid(), "x".repeat(EnvelopeConstants.INNER_TEXT_MAX_CHARS + 1), 1L) }
    }

    @Test fun `blank text is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { InnerMessage(mid(), "   ", 1L) }
    }

    @Test fun `session-reset round-trips`() {
        val n = SessionResetNotice("manual", 99L)
        assertEquals(n, InnerPayload.decodeReset(InnerPayload.encodeReset(n)))
    }

    @Test fun `message encode decode encode is stable`() {
        val m = InnerMessage(mid(), "hi", 1L)
        val once = InnerPayload.encodeMessage(m)
        assertArrayEquals(once, InnerPayload.encodeMessage(InnerPayload.decodeMessage(once)))
    }
}
