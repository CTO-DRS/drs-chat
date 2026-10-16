package drs.chat.domain

import drs.chat.domain.model.BackoffPolicy
import drs.chat.domain.model.IllegalOutboxTransition
import drs.chat.domain.model.OutboxEntry
import drs.chat.domain.model.OutboxState
import drs.chat.domain.model.OutboxStateMachine
import drs.chat.domain.model.isDue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("StringLiteralDuplication")
class OutboxStateMachineTest {
    // 1. QUEUED → IN_FLIGHT (legal)
    @Test fun `QUEUED to IN_FLIGHT is legal`() {
        assertEquals(OutboxState.IN_FLIGHT, OutboxStateMachine.transition(OutboxState.QUEUED, OutboxState.IN_FLIGHT))
    }

    // 2. IN_FLIGHT → SENT (legal)
    @Test fun `IN_FLIGHT to SENT is legal`() {
        assertEquals(OutboxState.SENT, OutboxStateMachine.transition(OutboxState.IN_FLIGHT, OutboxState.SENT))
    }

    // 3. IN_FLIGHT → QUEUED (re-queue after transient error)
    @Test fun `IN_FLIGHT to QUEUED is legal requeue`() {
        assertEquals(OutboxState.QUEUED, OutboxStateMachine.transition(OutboxState.IN_FLIGHT, OutboxState.QUEUED))
    }

    // 4. SENT → DELIVERED, DELIVERED → READ (receipt lifecycle)
    @Test fun `SENT to DELIVERED to READ is legal`() {
        assertEquals(OutboxState.DELIVERED, OutboxStateMachine.transition(OutboxState.SENT, OutboxState.DELIVERED))
        assertEquals(OutboxState.READ, OutboxStateMachine.transition(OutboxState.DELIVERED, OutboxState.READ))
    }

    // 5. IN_FLIGHT → FAILED (terminal)
    @Test fun `IN_FLIGHT to FAILED is legal terminal`() {
        assertEquals(OutboxState.FAILED, OutboxStateMachine.transition(OutboxState.IN_FLIGHT, OutboxState.FAILED))
    }

    // 6. Illegal transitions rejected (QUEUED → SENT, SENT → READ, etc.)
    @Test fun `illegal transitions are rejected`() {
        assertThrows(IllegalOutboxTransition::class.java) {
            OutboxStateMachine.transition(OutboxState.QUEUED, OutboxState.SENT)
        }
        assertThrows(IllegalOutboxTransition::class.java) {
            OutboxStateMachine.transition(OutboxState.QUEUED, OutboxState.DELIVERED)
        }
        assertThrows(IllegalOutboxTransition::class.java) {
            OutboxStateMachine.transition(OutboxState.SENT, OutboxState.READ)
        }
        assertThrows(IllegalOutboxTransition::class.java) {
            OutboxStateMachine.transition(OutboxState.DELIVERED, OutboxState.FAILED)
        }
    }

    // 7. Terminal states are sticky (FAILED cannot leave)
    @Test fun `FAILED is terminal`() {
        assertThrows(IllegalOutboxTransition::class.java) {
            OutboxStateMachine.transition(OutboxState.FAILED, OutboxState.QUEUED)
        }
        assertThrows(IllegalOutboxTransition::class.java) {
            OutboxStateMachine.transition(OutboxState.FAILED, OutboxState.IN_FLIGHT)
        }
    }

    // 8. Idempotent self-transition is legal (re-applying SENT on SENT)
    @Test fun `idempotent self transition is legal`() {
        assertEquals(OutboxState.SENT, OutboxStateMachine.transition(OutboxState.SENT, OutboxState.SENT))
        assertEquals(OutboxState.QUEUED, OutboxStateMachine.transition(OutboxState.QUEUED, OutboxState.QUEUED))
    }

    // 9. Backoff: first attempt = 1s, exponential, capped at 60s
    @Test fun `backoff is exponential with 60s cap`() {
        assertEquals(1_000L, BackoffPolicy.delayFor(1))
        assertEquals(2_000L, BackoffPolicy.delayFor(2))
        assertEquals(4_000L, BackoffPolicy.delayFor(3))
        assertEquals(8_000L, BackoffPolicy.delayFor(4))
        assertEquals(16_000L, BackoffPolicy.delayFor(5))
        assertEquals(32_000L, BackoffPolicy.delayFor(6))
        // attempt 7: raw=64_000 → capped to 60_000
        assertEquals(60_000L, BackoffPolicy.delayFor(7))
        assertEquals(60_000L, BackoffPolicy.delayFor(8))
    }

    // 10. Backoff: exhaustion at attempt 8 → FAILED
    @Test fun `backoff exhausts at attempt 8`() {
        assertFalse(BackoffPolicy.isExhausted(7), "7 < 8 → not exhausted")
        assertTrue(BackoffPolicy.isExhausted(8), "8 = MAX → exhausted")
        assertTrue(BackoffPolicy.isExhausted(99), "> MAX → exhausted")
    }

    // 11. isDue: entry due when now >= nextAttemptAt
    @Test fun `isDue true when now is at or past nextAttemptAt`() {
        val e = entry(state = OutboxState.QUEUED, nextAttemptAt = 100L)
        assertTrue(e.isDue(100L))
        assertTrue(e.isDue(101L))
    }

    // 12. isDue: false when now < nextAttemptAt, or terminal
    @Test fun `isDue false when now is before nextAttemptAt or state is terminal`() {
        val e = entry(state = OutboxState.QUEUED, nextAttemptAt = 100L)
        assertFalse(e.isDue(99L))
        val inFlight = entry(state = OutboxState.IN_FLIGHT, nextAttemptAt = 0L)
        assertFalse(inFlight.isDue(500L), "IN_FLIGHT is not due (already being sent)")
        val failed = entry(state = OutboxState.FAILED, nextAttemptAt = 0L)
        assertFalse(failed.isDue(500L), "FAILED is terminal — not due")
        val read = entry(state = OutboxState.READ, nextAttemptAt = 0L)
        assertFalse(read.isDue(500L), "READ is terminal — not due")
    }

    private fun entry(
        state: OutboxState,
        nextAttemptAt: Long,
    ) = OutboxEntry(
        messageId = drs.chat.domain.model.MessageId(ByteArray(16)),
        envelopeBytes = ByteArray(0),
        toKey = drs.chat.protocol.KeyId(ByteArray(16)),
        state = state,
        attempts = 0,
        nextAttemptAt = nextAttemptAt,
    )
}
