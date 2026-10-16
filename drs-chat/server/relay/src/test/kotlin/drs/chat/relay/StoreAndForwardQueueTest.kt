package drs.chat.relay

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("StringLiteralDuplication", "LongMethod")
class StoreAndForwardQueueTest {
    // 5. Enqueue + drain round-trips
    @Test
    fun `enqueue and drain round-trips`() {
        val q = StoreAndForwardQueue()
        val env = StoreAndForwardQueue.QueuedEnvelope("aabbcc", "0011", ByteArray(8) { it.toByte() }, 0L, 604_800L)
        assertTrue(q.enqueue(env))
        assertEquals(1, q.queuedFor("aabbcc"))
        val drained = q.drain("aabbcc")
        assertEquals(1, drained.size)
        assertArrayEquals(env.envelopeBytes, drained[0].envelopeBytes)
        assertEquals(0, q.queuedFor("aabbcc"))
    }

    // 6. Duplicate messageId is rejected (dedup)
    @Test
    fun `duplicate messageId is rejected`() {
        val q = StoreAndForwardQueue()
        val env = StoreAndForwardQueue.QueuedEnvelope("aabbcc", "0011", ByteArray(8), 0L, 604_800L)
        assertTrue(q.enqueue(env))
        assertFalse(q.enqueue(env.copy(envelopeBytes = ByteArray(8) { 0x42 })), "same messageId → reject")
        assertEquals(1, q.queuedFor("aabbcc"))
    }

    // 7. Sweep expired evicts only old envelopes
    @Test
    fun `sweep expired evicts only old envelopes`() {
        val q = StoreAndForwardQueue(ttlSeconds = 60)
        val young = StoreAndForwardQueue.QueuedEnvelope("a", "m1", ByteArray(4), queuedAtMs = 0, ttlSeconds = 60)
        val old = StoreAndForwardQueue.QueuedEnvelope("a", "m2", ByteArray(4), queuedAtMs = 0, ttlSeconds = 60)
        q.enqueue(young)
        q.enqueue(old)
        assertEquals(0, q.sweepExpired(nowMs = 30_000L))
        assertEquals(2, q.sweepExpired(nowMs = 120_000L))
    }

    // 8. Size cap on per-recipient queue (backpressure)
    @Test
    fun `size cap on per-recipient queue`() {
        val q = StoreAndForwardQueue(maxQueuePerRecipient = 2)
        assertTrue(q.enqueue(StoreAndForwardQueue.QueuedEnvelope("a", "m1", ByteArray(4), 0, 60L)))
        assertTrue(q.enqueue(StoreAndForwardQueue.QueuedEnvelope("a", "m2", ByteArray(4), 0, 60L)))
        assertFalse(q.enqueue(StoreAndForwardQueue.QueuedEnvelope("a", "m3", ByteArray(4), 0, 60L)), "exceeds cap → reject")
        assertTrue(q.enqueue(StoreAndForwardQueue.QueuedEnvelope("b", "m4", ByteArray(4), 0, 60L)))
    }

    // 9. SizeCap on envelope bytes (>64KB = oversize)
    @Test
    fun `size cap on envelope bytes`() {
        val ok = ByteArray(64 * 1024)
        assertFalse(SizeCap.isOversize(ok))
        val tooBig = ByteArray(64 * 1024 + 1)
        assertTrue(SizeCap.isOversize(tooBig))
    }
}
