package drs.chat.domain

import drs.chat.domain.model.MessageId
import drs.chat.domain.model.OutboxEntry
import drs.chat.domain.model.OutboxState
import drs.chat.domain.pipeline.DrainReport
import drs.chat.domain.pipeline.OutboxDrainer
import drs.chat.domain.port.SendResult
import drs.chat.domain.testing.FakeClock
import drs.chat.domain.testing.FakeOutboxRepository
import drs.chat.domain.testing.FakeTransport
import drs.chat.protocol.KeyId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("StringLiteralDuplication")
class OutboxDrainerTest {
    private val now = 1_000L
    private val clock = FakeClock(now)

    // 13. drainOnce sends due entries and marks SENT
    @Test fun `drainOnce sends due entries and marks SENT`() =
        runTest {
            val outbox = FakeOutboxRepository { clock.now() }
            val transport = FakeTransport { SendResult.Sent }
            outbox.entries += entry(state = OutboxState.QUEUED, nextAttemptAt = now)
            val drainer = OutboxDrainer(outbox, transport) { clock.now() }

            val report: DrainReport = drainer.drainOnce()

            assertEquals(1, report.processed)
            assertEquals(1, report.sent)
            assertEquals(0, report.requeued)
            assertEquals(0, report.failed)
            assertEquals(OutboxState.SENT, outbox.entries[0].state)
            assertEquals(1, transport.sent.size, "transport.send called once")
        }

    // 14. drainOnce skips not-due entries (nextAttemptAt > now)
    @Test fun `drainOnce skips entries not yet due`() =
        runTest {
            val outbox = FakeOutboxRepository { clock.now() }
            outbox.entries += entry(state = OutboxState.QUEUED, nextAttemptAt = now + 5_000L)
            val drainer = OutboxDrainer(outbox, FakeTransport()) { clock.now() }

            val report = drainer.drainOnce()

            assertEquals(0, report.processed, "nothing due → nothing processed")
            assertEquals(OutboxState.QUEUED, outbox.entries[0].state)
        }

    // 15. drainOnce: transient Rejected → requeue + backoff; on attempt 8 → FAILED
    @Test fun `drainOnce requeues transient failures and fails after backoff exhausted`() =
        runTest {
            val outbox = FakeOutboxRepository { clock.now() }
            val transport = FakeTransport { SendResult.Rejected("network") }
            outbox.entries += entry(state = OutboxState.QUEUED, nextAttemptAt = now)
            val drainer = OutboxDrainer(outbox, transport) { clock.now() }

            // 8 attempts — each drains + requeues + advances time past the next
            // attempt's nextAttemptAt. On the 8th, the entry is marked FAILED.
            var report = drainer.drainOnce()
            assertEquals(OutboxState.QUEUED, outbox.entries[0].state)
            assertEquals(1, outbox.entries[0].attempts)
            assertTrue(outbox.entries[0].nextAttemptAt > now, "nextAttemptAt advanced by backoff")

            for (i in 2..7) {
                // advance clock past the backoff for this attempt
                clock.fixedNow = outbox.entries[0].nextAttemptAt + 1
                report = drainer.drainOnce()
                assertEquals(OutboxState.QUEUED, outbox.entries[0].state, "attempt $i should requeue")
                assertEquals(i, outbox.entries[0].attempts)
            }

            // 8th attempt — backoff exhausted → FAILED
            clock.fixedNow = outbox.entries[0].nextAttemptAt + 1
            report = drainer.drainOnce()
            assertEquals(OutboxState.FAILED, outbox.entries[0].state, "8th attempt must FAIL")
            assertEquals(8, outbox.entries[0].attempts)
        }

    private fun entry(
        state: OutboxState,
        nextAttemptAt: Long,
    ) = OutboxEntry(
        messageId = MessageId(ByteArray(16).also { it[0] = 1 }),
        envelopeBytes = ByteArray(8),
        toKey = KeyId(ByteArray(16)),
        state = state,
        attempts = 0,
        nextAttemptAt = nextAttemptAt,
    )
}
