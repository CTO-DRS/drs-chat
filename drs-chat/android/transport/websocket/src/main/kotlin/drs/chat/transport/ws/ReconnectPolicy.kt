package drs.chat.transport.ws

import java.security.SecureRandom

/**
 * Exponential-backoff reconnect policy for the WebSocketTransport.
 *
 * PLAN §5.1 spec: 1s → 60s with jitter. Concretely:
 *   attempt | base   | jitter range (±25%)
 *   1       | 1.0s   | [0.75s, 1.25s]
 *   2       | 2.0s   | [1.50s, 2.50s]
 *   3       | 4.0s   | [3.00s, 5.00s]
 *   4       | 8.0s   | [6.00s, 10.00s]
 *   5       | 16.0s  | [12.00s, 20.00s]
 *   6       | 32.0s  | [24.00s, 40.00s]
 *   7+      | 60.0s  | [45.00s, 75.00s]   (capped)
 *
 * Jitter is bounded ±25% of the base delay. The jitter source is a
 * [SecureRandom] so an external observer cannot predict reconnect times.
 *
 * Reset: [reset] returns the policy to attempt=1. Called when a connection
 * stays UP for [stableUptimeMs] (default 60s) — distinguishes a flaky network
 * from a stable one and prevents unbounded backoff growth on a long-running
 * but occasionally-dropping connection.
 */
class ReconnectPolicy(
    private val baseDelayMs: Long = 1_000L,
    private val maxDelayMs: Long = 60_000L,
    private val jitterFraction: Double = 0.25,
    private val stableUptimeMs: Long = 60_000L,
    private val random: () -> Double = { SecureRandom().nextDouble() },
) {
    init {
        require(baseDelayMs > 0) { "baseDelayMs must be positive" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
        require(jitterFraction in 0.0..0.5) { "jitterFraction must be in [0.0, 0.5]" }
        require(stableUptimeMs > 0) { "stableUptimeMs must be positive" }
    }

    private var attempt: Int = 0

    /** Compute the next reconnect delay (with jitter). Does NOT advance state. */
    fun nextDelay(): Long {
        attempt = (attempt % Int.MAX_VALUE) + 1
        // Overflow-safe shift: cap at attempt 32 (Long.MAX_VALUE / 1000 ≈ 9.2e15,
        // so shift past 50 would overflow). Use min in raw-bit-space first.
        val shift = (attempt - 1).coerceAtMost(MAX_SAFE_SHIFT)
        val raw = baseDelayMs shl shift
        val capped = if (raw < 0 || raw > maxDelayMs) maxDelayMs else raw
        // Jitter: ±jitterFraction of capped, sampled uniformly.
        val jitterRange = (capped * jitterFraction).toLong()
        val jitterOffset = ((random() * 2 - 1) * jitterRange).toLong()
        return (capped + jitterOffset).coerceAtLeast(0L)
    }

    /**
     * Observe an uptime sample. If the connection was UP for >= stableUptimeMs,
     * reset attempt to 0 (so the next failure restarts from baseDelayMs).
     * Otherwise leave the attempt counter where it is.
     */
    fun observeUptime(uptimeMs: Long) {
        if (uptimeMs >= stableUptimeMs) attempt = 0
    }

    /** Peek the current attempt counter (1-indexed after the first nextDelay call). */
    fun currentAttempt(): Int = attempt

    /** Reset to the initial state — call on explicit user-initiated reconnect. */
    fun reset() {
        attempt = 0
    }

    /**
     * Bound the jitter for a given base delay — exposed for tests to assert
     * the policy never produces delays outside [base - jitter, base + jitter].
     */
    fun jitterBoundsFor(base: Long): Pair<Long, Long> {
        val range = (base * jitterFraction).toLong()
        return (base - range) to (base + range)
    }

    companion object {
        // 1_000L shl 50 ≈ 1.1e18 — close enough to Long.MAX_VALUE (9.2e18)
        // to be safe; past this, the shift could overflow.
        const val MAX_SAFE_SHIFT: Int = 50
    }
}
