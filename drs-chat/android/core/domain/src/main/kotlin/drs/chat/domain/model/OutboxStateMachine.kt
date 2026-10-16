package drs.chat.domain.model

/**
 * Outbox state machine (ADR-0002 §4 reliability model). Legal transitions:
 *
 *   QUEUED → IN_FLIGHT
 *   IN_FLIGHT → SENT              (transport accepted)
 *   IN_FLIGHT → QUEUED           (transient error → re-queue + backoff)
 *   IN_FLIGHT → FAILED            (backoff exhausted OR terminal error)
 *   SENT → DELIVERED              (peer's DELIVERY_RECEIPT arrived)
 *   SENT → FAILED                 (TTL expired before delivery)
 *   DELIVERED → READ              (peer's READ_RECEIPT arrived)
 *
 * FAILED and READ are terminal — no transition out of either.
 *
 * Anything else (QUEUED → SENT, IN_FLIGHT → DELIVERED, etc.) is illegal and
 * rejected by [OutboxStateMachine.transition].
 */
object OutboxStateMachine {
    fun transition(
        from: OutboxState,
        to: OutboxState,
    ): OutboxState {
        if (from == to) return from // idempotent self-transition is legal
        if (from == OutboxState.FAILED || from == OutboxState.READ) {
            throw IllegalOutboxTransition("terminal state $from cannot transition to $to")
        }
        val legal = from to to
        val allowed =
            setOf(
                OutboxState.QUEUED to OutboxState.IN_FLIGHT,
                OutboxState.IN_FLIGHT to OutboxState.SENT,
                OutboxState.IN_FLIGHT to OutboxState.QUEUED,
                OutboxState.IN_FLIGHT to OutboxState.FAILED,
                OutboxState.SENT to OutboxState.DELIVERED,
                OutboxState.SENT to OutboxState.FAILED,
                OutboxState.DELIVERED to OutboxState.READ,
            )
        if (legal !in allowed) {
            throw IllegalOutboxTransition("$from → $to is not a legal transition")
        }
        return to
    }
}

class IllegalOutboxTransition(message: String) : IllegalStateException(message)

/**
 * Exponential backoff with hard cap. Used by the drainer when IN_FLIGHT → QUEUED.
 *
 *   attempt | delay
 *   1       | 1s
 *   2       | 2s
 *   3       | 4s
 *   4       | 8s
 *   5       | 16s
 *   6       | 32s
 *   7       | 60s (capped)
 *   8+      | FAILED (exhausted)
 */
object BackoffPolicy {
    const val INITIAL_DELAY_MS: Long = 1_000L
    const val MAX_DELAY_MS: Long = 60_000L
    const val MAX_ATTEMPTS: Int = 8

    /** Returns the delay for the next attempt (1-indexed). */
    fun delayFor(attempt: Int): Long {
        val raw = INITIAL_DELAY_MS shl (attempt - 1)
        return minOf(raw, MAX_DELAY_MS)
    }

    /** True if the entry has exhausted its attempts and must transition to FAILED. */
    fun isExhausted(attempt: Int): Boolean = attempt >= MAX_ATTEMPTS
}

/** True if the entry is due for a (re)send attempt at [now]. */
fun OutboxEntry.isDue(now: Long): Boolean =
    now >= nextAttemptAt && state != OutboxState.IN_FLIGHT && state != OutboxState.FAILED && state != OutboxState.READ
