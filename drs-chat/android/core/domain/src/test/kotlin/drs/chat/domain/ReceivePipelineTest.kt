package drs.chat.domain

import drs.chat.crypto.CryptoEngine
import drs.chat.domain.model.MessageStatus
import drs.chat.domain.pipeline.ReceivePipeline
import drs.chat.domain.pipeline.ReceiveResult
import drs.chat.domain.testing.FakeClock
import drs.chat.domain.testing.FakeMessageRepository
import drs.chat.protocol.EnvelopeConstants
import drs.chat.protocol.EnvelopeFactory
import drs.chat.protocol.EnvelopeKind
import drs.chat.protocol.InnerMessage
import drs.chat.protocol.InnerPayload
import drs.chat.protocol.InnerReceipt
import drs.chat.protocol.SessionResetNotice
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Suppress("StringLiteralDuplication", "LongMethod")
class ReceivePipelineTest {
    // 16. MESSAGE → decrypt + decode → store incoming
    @Test fun `MESSAGE envelope decrypts and is stored`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
            val now = 100L
            val mid = alice.newMessageId()
            val inner = InnerMessage(mid, "hello bob", now)
            val ct = alice.encryptForContact(bob.localKeyId(), InnerPayload.encodeMessage(inner))
            val env =
                EnvelopeFactory.build(
                    alice,
                    senderKeyId = alice.localKeyId().bytes,
                    recipientKeyId = bob.localKeyId().bytes,
                    createdAt = now,
                    ttlSeconds = 60,
                    kind = EnvelopeKind.MESSAGE,
                    payloadCiphertext = ct,
                    messageId = mid,
                )

            val messages = FakeMessageRepository()
            val clock = FakeClock(now + 1)
            val pipeline = ReceivePipeline(bob, messages) { clock.now() }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.MessageStored, "expected MessageStored, got $result")
            val stored = (result as ReceiveResult.MessageStored).record
            assertEquals("hello bob", stored.text)
            assertEquals(now, stored.sentAt)
            assertEquals(1, messages.incoming.size)
            assertArrayEquals(mid, messages.incoming[0].messageId)
        }

    // 17. DELIVERY_RECEIPT → marks outgoing DELIVERED
    @Test fun `DELIVERY_RECEIPT marks the matched outgoing message DELIVERED`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
            bob.establishSessionTo(alice.localKeyId(), alice.inviteBundle(1, 1, 1))

            val ackedId = alice.newMessageId()
            val now = 200L
            val inner = InnerReceipt(ackedId, now)
            // Bob encrypts a receipt for Alice using the live session
            val ct = bob.encryptForContact(alice.localKeyId(), InnerPayload.encodeReceipt(inner))
            val env =
                EnvelopeFactory.build(
                    bob,
                    senderKeyId = bob.localKeyId().bytes,
                    recipientKeyId = alice.localKeyId().bytes,
                    createdAt = now,
                    ttlSeconds = 60,
                    kind = EnvelopeKind.DELIVERY_RECEIPT,
                    payloadCiphertext = ct,
                    messageId = bob.newMessageId(),
                )

            val messages = FakeMessageRepository()
            // Pretend Alice has an outgoing message with id = ackedId
            messages.outgoing +=
                drs.chat.domain.model.MessageRecord(
                    messageId = ackedId,
                    fromKey = null,
                    toKey = null,
                    text = "earlier outgoing",
                    sentAt = now - 10,
                    receivedAt = now - 10,
                    direction = drs.chat.domain.model.Direction.OUTGOING,
                    status = MessageStatus.SENT,
                )
            val clock = FakeClock(now + 5)
            val pipeline = ReceivePipeline(alice, messages) { clock.now() }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.DeliveryAcked, "expected DeliveryAcked, got $result")
            assertArrayEquals(ackedId, (result as ReceiveResult.DeliveryAcked).ackedMessageId)
            assertEquals(MessageStatus.DELIVERED, messages.outgoing[0].status)
        }

    // 18. READ_RECEIPT → marks outgoing READ
    @Test fun `READ_RECEIPT marks the matched outgoing message READ`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
            bob.establishSessionTo(alice.localKeyId(), alice.inviteBundle(1, 1, 1))

            val ackedId = alice.newMessageId()
            val now = 300L
            val inner = InnerReceipt(ackedId, now)
            val ct = bob.encryptForContact(alice.localKeyId(), InnerPayload.encodeReceipt(inner))
            val env =
                EnvelopeFactory.build(
                    bob,
                    senderKeyId = bob.localKeyId().bytes,
                    recipientKeyId = alice.localKeyId().bytes,
                    createdAt = now,
                    ttlSeconds = 60,
                    kind = EnvelopeKind.READ_RECEIPT,
                    payloadCiphertext = ct,
                    messageId = bob.newMessageId(),
                )

            val messages = FakeMessageRepository()
            messages.outgoing +=
                drs.chat.domain.model.MessageRecord(
                    messageId = ackedId,
                    fromKey = null,
                    toKey = null,
                    text = "earlier",
                    sentAt = now - 10,
                    receivedAt = now - 10,
                    direction = drs.chat.domain.model.Direction.OUTGOING,
                    status = MessageStatus.DELIVERED,
                )
            val clock = FakeClock(now + 5)
            val pipeline = ReceivePipeline(alice, messages) { clock.now() }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.ReadAcked, "expected ReadAcked, got $result")
            assertEquals(MessageStatus.READ, messages.outgoing[0].status)
        }

    // 19. SESSION_RESET → emit reason (no decrypt — no live session)
    @Test fun `SESSION_RESET surfaces the reason string`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            // No session established — SESSION_RESET is plaintext
            val now = 400L
            val notice = SessionResetNotice("key-compromise", now)
            val envBytes = InnerPayload.encodeReset(notice)
            val env =
                EnvelopeFactory.build(
                    bob,
                    senderKeyId = bob.localKeyId().bytes,
                    recipientKeyId = alice.localKeyId().bytes,
                    createdAt = now,
                    ttlSeconds = 60,
                    kind = EnvelopeKind.SESSION_RESET,
                    payloadCiphertext = envBytes,
                    messageId = bob.newMessageId(),
                )

            val messages = FakeMessageRepository()
            val clock = FakeClock(now + 5)
            val pipeline = ReceivePipeline(alice, messages) { clock.now() }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.SessionReset, "expected SessionReset, got $result")
            assertEquals("key-compromise", (result as ReceiveResult.SessionReset).reason)
        }

    // 20. Expired envelope → Rejected
    @Test fun `expired envelope is rejected`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
            val sentAt = 100L
            // TTL 60s → envelope expires at 100 + 60 = 160s + 300s skew = 460s
            val inner = InnerMessage(alice.newMessageId(), "hello", sentAt)
            val ct = alice.encryptForContact(bob.localKeyId(), InnerPayload.encodeMessage(inner))
            val env =
                EnvelopeFactory.build(
                    alice,
                    senderKeyId = alice.localKeyId().bytes,
                    recipientKeyId = bob.localKeyId().bytes,
                    createdAt = sentAt,
                    ttlSeconds = 60,
                    kind = EnvelopeKind.MESSAGE,
                    payloadCiphertext = ct,
                    messageId = alice.newMessageId(),
                )

            val pipeline =
                ReceivePipeline(bob, FakeMessageRepository()) {
                    500_000L // far future
                }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.Rejected, "expected Rejected, got $result")
            assertEquals("envelope expired", (result as ReceiveResult.Rejected).reason)
        }

    // 21. Tampered ciphertext → decrypt fails → Rejected (pipeline doesn't throw)
    @Test fun `tampered ciphertext is Rejected not thrown`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
            val now = 100L
            val ct = alice.encryptForContact(bob.localKeyId(), InnerPayload.encodeMessage(InnerMessage(alice.newMessageId(), "hi", now)))
            val tampered = ct.copyOf().also { it[it.size / 2] = (it[it.size / 2].toInt() xor 0x01).toByte() }
            val env =
                EnvelopeFactory.build(
                    alice,
                    senderKeyId = alice.localKeyId().bytes,
                    recipientKeyId = bob.localKeyId().bytes,
                    createdAt = now,
                    ttlSeconds = 60,
                    kind = EnvelopeKind.MESSAGE,
                    payloadCiphertext = tampered,
                    messageId = alice.newMessageId(),
                )
            val pipeline = ReceivePipeline(bob, FakeMessageRepository()) { now + 1 }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.Rejected, "expected Rejected, got $result")
            val reason = (result as ReceiveResult.Rejected).reason
            assertTrue(reason.startsWith("decrypt"), "reason must mention decrypt: $reason")
        }

    // 22. Unknown envelope kind → Rejected (defensive: pipeline never throws)
    @Test fun `unknown envelope kind is rejected`() =
        runTest {
            val alice = CryptoEngine.generate(1001, 1)
            val bob = CryptoEngine.generate(2002, 1)
            alice.establishSessionTo(bob.localKeyId(), bob.inviteBundle(1, 1, 1))
            val now = 100L
            val ct = alice.encryptForContact(bob.localKeyId(), InnerPayload.encodeMessage(InnerMessage(alice.newMessageId(), "hi", now)))
            val header =
                drs.chat.protocol.EnvelopeHeader(
                    EnvelopeConstants.PROTOCOL_VERSION.toUByte(),
                    ByteArray(16),
                    alice.localKeyId().bytes,
                    bob.localKeyId().bytes,
                    now,
                    60,
                    // unknown kind code — pipeline must reject
                    99u,
                    ct,
                )
            val sig = alice.signEnvelopeHeader(drs.chat.protocol.EnvelopeCodec.headerBytesOf(header))
            val env =
                drs.chat.protocol.WireEnvelope(
                    header.protocolVersion,
                    header.messageId,
                    header.senderKeyId,
                    header.recipientKeyId,
                    header.createdAt,
                    header.ttlSeconds,
                    header.kind,
                    header.payloadCiphertext,
                    sig,
                )
            val pipeline = ReceivePipeline(bob, FakeMessageRepository()) { now + 1 }

            val result = pipeline.process(env)

            assertTrue(result is ReceiveResult.Rejected, "expected Rejected, got $result")
        }
}
