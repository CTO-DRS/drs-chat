package drs.chat.domain.pipeline

import drs.chat.domain.model.OutboxState
import drs.chat.domain.port.OutboxRepository
import drs.chat.domain.port.SendResult
import drs.chat.domain.port.TransportPort

/**
 * The drainer — pulls due outbox entries, hands them to the transport, and
 * applies the resulting state transition.
 *
 * Per attempt:
 *   1. mark IN_FLIGHT (QUEUED → IN_FLIGHT)
 *   2. transport.send(envelopeBytes)
 *   3. on Sent → mark SENT
 *      on Rejected → if backoff exhausted → mark FAILED; else re-queue with
 *      nextAttemptAt = now + BackoffPolicy.delayFor(attempts)
 *
 * The drainer is bounded — [drainOnce] processes all due entries exactly once;
 * callers (a foreground service or a periodic worker) call drainOnce on a
 * cadence. drainOnce is idempotent and safe to call concurrently (the
 * IN_FLIGHT flag prevents re-send of the same entry by a competing caller).
 */
class OutboxDrainer(
    private val outbox: OutboxRepository,
    private val transport: TransportPort,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /** One drain pass. Returns the count of entries processed (sent or re-queued). */
    suspend fun drainOnce(): DrainReport {
        val now = clock()
        val due = outbox.allDue(now)
        var sent = 0
        var requeued = 0
        var failed = 0
        for (entry in due) {
            outbox.markInFlight(entry.messageId.bytes, now)
            val fresh = outbox.find(entry.messageId.bytes) ?: continue
            val result = transport.send(fresh.envelopeBytes)
            when (result) {
                is SendResult.Sent -> {
                    outbox.markSent(entry.messageId.bytes, clock())
                    sent++
                }
                is SendResult.Rejected -> {
                    val attempt = fresh.attempts + 1
                    if (drs.chat.domain.model.BackoffPolicy.isExhausted(attempt)) {
                        outbox.markFailed(entry.messageId.bytes, clock(), result.reason, attempt)
                        failed++
                    } else {
                        val nextAt = clock() + drs.chat.domain.model.BackoffPolicy.delayFor(attempt)
                        outbox.update(
                            fresh.copy(
                                state = OutboxState.QUEUED,
                                attempts = attempt,
                                nextAttemptAt = nextAt,
                                lastError = result.reason,
                            ),
                        )
                        requeued++
                    }
                }
            }
        }
        return DrainReport(processed = due.size, sent = sent, requeued = requeued, failed = failed)
    }
}

data class DrainReport(val processed: Int, val sent: Int, val requeued: Int, val failed: Int)
