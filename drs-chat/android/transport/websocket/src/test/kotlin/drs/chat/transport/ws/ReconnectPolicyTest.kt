package drs.chat.transport.ws

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * ReconnectPolicy tests — pure logic, no I/O. Verifies the exponential
 * backoff sequence, the 60s cap, the ±25% jitter bounds, and the reset-on-
 * stable-uptime behaviour.
 */
@Suppress("StringLiteralDuplication")
class ReconnectPolicyTest {
    // 1. First call returns 1s base ±25%
    @Test
    fun `first delay is 1s base with jitter bounds`() {
        val policy = ReconnectPolicy(random = { 0.5 }) // 0 jitter
        val d = policy.nextDelay()
        val (lo, hi) = policy.jitterBoundsFor(1_000L)
        assertEquals(1_000L, d, "with zero jitter, delay = base exactly")
        assertTrue(d in lo..hi)
    }

    // 2. Exponential sequence: 1s, 2s, 4s, 8s, 16s, 32s, 60s (capped)
    @Test
    fun `exponential sequence caps at 60s`() {
        val policy = ReconnectPolicy(random = { 0.5 }) // zero jitter for predictable assert
        val delays = (1..10).map { policy.nextDelay() }
        // base sequence (jitter=0 → delay = base)
        assertEquals(1_000L, delays[0])
        assertEquals(2_000L, delays[1])
        assertEquals(4_000L, delays[2])
        assertEquals(8_000L, delays[3])
        assertEquals(16_000L, delays[4])
        assertEquals(32_000L, delays[5])
        // 7+ → capped at 60s
        assertEquals(60_000L, delays[6])
        assertEquals(60_000L, delays[7])
        assertEquals(60_000L, delays[8])
        assertEquals(60_000L, delays[9])
    }

    // 3. Jitter bounds: delays stay within ±25% of base
    @Test
    fun `jitter stays within plus-or-minus 25 percent of base`() {
        // For each attempt level 1..6, reset the policy and sample fresh.
        // The policy.nextDelay() INCREMENTS attempt on each call, so we must
        // reset between samples to stay at the same attempt level.
        for (attempt in 1..6) {
            val policy = ReconnectPolicy()
            val base = minOf(1_000L shl (attempt - 1), 60_000L)
            val (lo, hi) = policy.jitterBoundsFor(base)
            for (sample in 1..200) {
                val policy2 = ReconnectPolicy()
                // Walk to the target attempt level
                repeat(attempt - 1) { policy2.nextDelay() }
                val d = policy2.nextDelay()
                assertTrue(d in lo..hi, "attempt $attempt sample $sample: delay $d outside [$lo, $hi]")
            }
        }
    }

    // 4. Reset returns to attempt=1 (base delay)
    @Test
    fun `reset returns to first attempt`() {
        val policy = ReconnectPolicy(random = { 0.5 })
        repeat(5) { policy.nextDelay() } // walk up to 16s
        policy.reset()
        assertEquals(0, policy.currentAttempt())
        assertEquals(1_000L, policy.nextDelay(), "after reset, next delay = base")
    }

    // 5. observeUptime >= stableUptimeMs resets the attempt counter
    @Test
    fun `stable uptime resets the backoff`() {
        val policy = ReconnectPolicy(random = { 0.5 })
        repeat(5) { policy.nextDelay() }
        // Up for 60s+ → policy resets
        policy.observeUptime(uptimeMs = 60_000L)
        assertEquals(0, policy.currentAttempt())
        assertEquals(1_000L, policy.nextDelay())
    }

    // 6. observeUptime < stableUptimeMs does NOT reset
    @Test
    fun `short uptime does not reset backoff`() {
        val policy = ReconnectPolicy(random = { 0.5 })
        repeat(3) { policy.nextDelay() } // walk to attempt=3 (base 4s)
        policy.observeUptime(uptimeMs = 10_000L) // < 60s stable threshold
        // After observeUptime, currentAttempt stays at 3; the next nextDelay()
        // advances to attempt 4 (base 8s, since 1_000L << 3 = 8_000)
        assertEquals(8_000L, policy.nextDelay())
    }
}

private fun minOf(
    a: Long,
    b: Long,
): Long = if (a < b) a else b
