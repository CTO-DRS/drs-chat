package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SafetyNumberTest {
    private fun pub(b: Byte) = ByteArray(32) { (b + it).toByte() }

    @Test fun `safety number is symmetric`() {
        assertEquals(SafetyNumber.compute(pub(1), pub(2)), SafetyNumber.compute(pub(2), pub(1)))
    }

    @Test fun `safety number is 60 digits in 12 groups of 5`() {
        val s = SafetyNumber.compute(pub(1), pub(2))
        val g = s.split(' ')
        assertEquals(12, g.size)
        assertTrue(g.all { it.length == 5 })
        assertEquals(60, s.replace(" ", "").length)
        assertTrue(
            g.all {
                it.all {
                        c ->
                    c.isDigit()
                }
            },
        )
    }

    @Test fun `different contact pairs yield different safety numbers`() {
        assertNotEquals(SafetyNumber.compute(pub(1), pub(2)), SafetyNumber.compute(pub(1), pub(3)))
    }

    @Test fun `groups returns 12 strings`() {
        val g = SafetyNumber.groups(pub(1), pub(2))
        assertEquals(12, g.size)
        assertTrue(g.all { it.length == 5 })
    }
}
