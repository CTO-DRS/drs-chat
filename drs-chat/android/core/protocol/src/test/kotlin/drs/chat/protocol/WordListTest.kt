package drs.chat.protocol

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WordListTest {
    @Test fun `has exactly 256 entries`() {
        assertEquals(256, WordList.LIST.size)
    }

    @Test fun `all entries are distinct`() {
        assertEquals(256, WordList.LIST.toSet().size)
    }

    @Test fun `all entries are short, non-blank, lowercase`() {
        WordList.LIST.forEach { w ->
            assertTrue(w.length in 2..12)
            assertTrue(w.isNotBlank())
            assertTrue(w == w.lowercase())
        }
    }

    @Test fun `wordFor byte is a bijection with LIST`() {
        (0..255).forEach { b -> assertEquals(WordList.LIST[b], WordList.wordFor(b.toByte())) }
    }
}
