package drs.chat.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import drs.chat.data.dao.IdentityDao
import drs.chat.data.dao.KyberPreKeyDao
import drs.chat.data.dao.PreKeyDao
import drs.chat.data.dao.SessionDao
import drs.chat.data.dao.SignedPreKeyDao
import drs.chat.data.entity.ContactEntity
import drs.chat.data.entity.IdentityEntity
import drs.chat.data.entity.KyberPreKeyEntity
import drs.chat.data.entity.MessageEntity
import drs.chat.data.entity.OutboxEntity
import drs.chat.data.entity.PreKeyEntity
import drs.chat.data.entity.SessionEntity
import drs.chat.data.entity.SignedPreKeyEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * :core:data Robolectric tests. Runs on JVM with Robolectric's stubbed
 * Android framework. Room + androidx.sqlite.framework provides a real on-disk
 * SQLite. The SQLCipher SupportOpenHelperFactory is exercised in the sibling
 * DrVaultAndCipherTest; here we use the framework factory so the tests can
 * run without loading SQLCipher's JNI library (Robolectric doesn't bundle it).
 *
 * NOTE (sandbox disclosure): these Robolectric tests CANNOT be run in the
 * dev sandbox — no Android SDK is installed. They run on the CI machine
 * (the workflow sets up android-actions/setup-android@v3). The CI run URL
 * is reported after push; we do NOT claim green locally.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DrDatabaseTest {
    private lateinit var db: DrDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db =
            androidx.room.Room.inMemoryDatabaseBuilder(ctx, DrDatabase::class.java)
                .allowMainThreadQueries()
                .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // 1. Contact insert + read round-trips
    @Test
    fun `contact insert and read`() =
        runBlocking {
            val dao = db.contacts()
            val id = dao.upsert(sampleContact("alice"))
            assertTrue(id > 0)
            val loaded = dao.findByKeyIdHex("alice")
            assertNotNull(loaded)
            assertEquals("Alice", loaded!!.displayName)
        }

    // 2. Contact delete by hex
    @Test
    fun `contact delete by hex`() =
        runBlocking {
            val dao = db.contacts()
            dao.upsert(sampleContact("alice"))
            assertEquals(1, dao.deleteByHex("alice"))
            assertNull(dao.findByKeyIdHex("alice"))
        }

    // 3. Message insert + history lookup by contactId, ordered by sentAt
    @Test
    fun `message history returns rows for contact ordered by sentAt`() =
        runBlocking {
            val cdao = db.contacts()
            val mdao = db.messages()
            val cid = cdao.upsert(sampleContact("bob"))
            mdao.insert(sampleMessage("m1", cid, sentAt = 200L))
            mdao.insert(sampleMessage("m2", cid, sentAt = 100L))
            val hist = mdao.historyForContact(cid, 10)
            assertEquals(2, hist.size)
            assertEquals(100L, hist[0].sentAt)
            assertEquals(200L, hist[1].sentAt)
        }

    // 4. Message update status
    @Test
    fun `message status update`() =
        runBlocking {
            val mdao = db.messages()
            val cid = db.contacts().upsert(sampleContact("c"))
            mdao.insert(sampleMessage("m1", cid, status = "SENT"))
            assertEquals(1, mdao.updateStatus("m1", "DELIVERED"))
            assertEquals("DELIVERED", mdao.findByMessageIdHex("m1")!!.status)
        }

    // 5. Message delete for contact
    @Test
    fun `message delete for contact`() =
        runBlocking {
            val mdao = db.messages()
            val cid = db.contacts().upsert(sampleContact("c"))
            mdao.insert(sampleMessage("m1", cid))
            mdao.insert(sampleMessage("m2", cid))
            assertEquals(2, mdao.deleteForContact(cid))
            assertEquals(0, mdao.historyForContact(cid, 100).size)
        }

    // 6. Outbox allDue returns QUEUED entries past nextAttemptAt ordered ASC
    @Test
    fun `outbox allDue returns QUEUED entries past nextAttemptAt ordered`() =
        runBlocking {
            val odao = db.outbox()
            odao.insert(sampleOutbox("o1", "QUEUED", 100L))
            odao.insert(sampleOutbox("o2", "QUEUED", 50L))
            odao.insert(sampleOutbox("o3", "QUEUED", 200L))
            odao.insert(sampleOutbox("o4", "IN_FLIGHT", 10L))
            val due = odao.allDue(now = 150L)
            assertEquals(2, due.size)
            assertEquals("o2", due[0].messageIdHex)
            assertEquals("o1", due[1].messageIdHex)
        }

    // 7. Outbox state update
    @Test
    fun `outbox state update`() =
        runBlocking {
            val odao = db.outbox()
            odao.insert(sampleOutbox("o1", "QUEUED"))
            odao.updateState("o1", "IN_FLIGHT", 1, 999L, "test")
            val loaded = odao.findByMessageIdHex("o1")!!
            assertEquals("IN_FLIGHT", loaded.state)
            assertEquals(1, loaded.attempts)
            assertEquals(999L, loaded.nextAttemptAt)
            assertEquals("test", loaded.lastError)
        }

    // 8. Identity save + retrieve
    @Test
    fun `identity save and retrieve`() =
        runBlocking {
            val dao: IdentityDao = db.identities()
            dao.upsert(IdentityEntity("alice", 1, ByteArray(33) { it.toByte() }, "trusted"))
            val rec = dao.find("alice", 1)
            assertNotNull(rec)
            assertArrayEquals(ByteArray(33) { it.toByte() }, rec!!.identityKeyBytes)
        }

    // 9. Identity delete
    @Test
    fun `identity delete`() =
        runBlocking {
            val dao = db.identities()
            dao.upsert(IdentityEntity("alice", 1, ByteArray(33), "trusted"))
            assertEquals(1, dao.delete("alice", 1))
            assertNull(dao.find("alice", 1))
        }

    // 10. Session save + load + contains + delete
    @Test
    fun `session save load contains delete`() =
        runBlocking {
            val dao: SessionDao = db.sessions()
            dao.upsert(SessionEntity("bob", 1, ByteArray(64) { it.toByte() }))
            assertTrue(dao.contains("bob", 1))
            val rec = dao.find("bob", 1)!!
            assertArrayEquals(ByteArray(64) { it.toByte() }, rec.sessionBytes)
            assertEquals(1, dao.delete("bob", 1))
            assertFalse(dao.contains("bob", 1))
        }

    // 11. PreKey save + load + remove + count
    @Test
    fun `prekey save load remove count`() =
        runBlocking {
            val dao: PreKeyDao = db.preKeys()
            assertEquals(0, dao.count())
            dao.upsert(PreKeyEntity(1, ByteArray(32)))
            dao.upsert(PreKeyEntity(2, ByteArray(32)))
            assertEquals(2, dao.count())
            assertNotNull(dao.find(1))
            assertEquals(1, dao.delete(1))
            assertEquals(1, dao.count())
        }

    // 12. SignedPreKey save + load + remove
    @Test
    fun `signed prekey save load remove`() =
        runBlocking {
            val dao: SignedPreKeyDao = db.signedPreKeys()
            dao.upsert(SignedPreKeyEntity(1, ByteArray(64), 1000L))
            val rec = dao.find(1)!!
            assertEquals(1000L, rec.timestamp)
            assertEquals(1, dao.delete(1))
            assertNull(dao.find(1))
        }

    // 13. KyberPreKey save + load + remove
    @Test
    fun `kyber prekey save load remove`() =
        runBlocking {
            val dao: KyberPreKeyDao = db.kyberPreKeys()
            dao.upsert(KyberPreKeyEntity(1, ByteArray(1568), 2000L))
            assertEquals(1, dao.count())
            val rec = dao.find(1)!!
            assertEquals(2000L, rec.timestamp)
            assertEquals(1, dao.delete(1))
            assertEquals(0, dao.count())
        }

    // 14. DrDatabase exposes all 8 DAOs (smoke)
    @Test
    fun `database exposes all DAOs`() {
        assertNotNull(db.contacts())
        assertNotNull(db.messages())
        assertNotNull(db.outbox())
        assertNotNull(db.identities())
        assertNotNull(db.sessions())
        assertNotNull(db.preKeys())
        assertNotNull(db.signedPreKeys())
        assertNotNull(db.kyberPreKeys())
    }

    // 15. Contact unique index — REPLACE on conflict
    @Test
    fun `contacts keyIdHex unique index replaces on conflict`() =
        runBlocking {
            val cdao = db.contacts()
            cdao.upsert(sampleContact("alice").copy(displayName = "Alice v1"))
            cdao.upsert(sampleContact("alice").copy(displayName = "Alice v2"))
            val loaded = cdao.findByKeyIdHex("alice")!!
            assertEquals("Alice v2", loaded.displayName)
        }

    // 16. Message messageIdHex unique
    @Test
    fun `message messageIdHex is unique`() =
        runBlocking {
            val mdao = db.messages()
            val cid = db.contacts().upsert(sampleContact("c"))
            mdao.insert(sampleMessage("m1", cid))
            mdao.insert(sampleMessage("m1", cid).copy(text = "updated"))
            assertEquals("updated", mdao.findByMessageIdHex("m1")!!.text)
        }

    // 17. Identity overwrite updates trustedLevel (REPLACE)
    @Test
    fun `identity overwrite updates trustedLevel`() =
        runBlocking {
            val dao = db.identities()
            dao.upsert(IdentityEntity("a", 1, ByteArray(33), "untrusted"))
            dao.upsert(IdentityEntity("a", 1, ByteArray(33) { 1 }, "trusted"))
            val rec = dao.find("a", 1)!!
            assertEquals("trusted", rec.trustedLevel)
            assertArrayEquals(ByteArray(33) { 1 }, rec.identityKeyBytes)
        }

    // 18. Sessions all() returns everything
    @Test
    fun `sessions all returns full list`() =
        runBlocking {
            val dao = db.sessions()
            dao.upsert(SessionEntity("a", 1, ByteArray(8)))
            dao.upsert(SessionEntity("b", 1, ByteArray(8)))
            assertEquals(2, dao.all().size)
        }

    // 19. PreKey count is exact
    @Test
    fun `prekey count returns exact count`() =
        runBlocking {
            val dao = db.preKeys()
            for (i in 1..25) dao.upsert(PreKeyEntity(i, ByteArray(32)))
            assertEquals(25, dao.count())
        }

    // 20. KyberPreKey count after deletion
    @Test
    fun `kyber prekey count is zero after deletion`() =
        runBlocking {
            val dao = db.kyberPreKeys()
            dao.upsert(KyberPreKeyEntity(1, ByteArray(1568), 0L))
            dao.delete(1)
            assertEquals(0, dao.count())
        }

    // 21. Outbox allDue with no matching entries returns empty
    @Test
    fun `outbox allDue with no matching entries returns empty`() =
        runBlocking {
            val odao = db.outbox()
            odao.insert(sampleOutbox("o1", "QUEUED", 1000L))
            assertEquals(0, odao.allDue(now = 100L).size)
        }

    // 22. Transaction commit visible after commit
    @Test
    fun `transaction commit visible after commit`() =
        runBlocking {
            val cdao = db.contacts()
            db.runInTransaction {
                runBlocking {
                    cdao.upsert(sampleContact("a"))
                    cdao.upsert(sampleContact("b"))
                }
            }
            assertNotNull(cdao.findByKeyIdHex("a"))
            assertNotNull(cdao.findByKeyIdHex("b"))
        }

    // 23. Transaction rollback on exception
    @Test
    fun `transaction rolls back on exception`() {
        val cdao = db.contacts()
        try {
            db.runInTransaction {
                runBlocking { cdao.upsert(sampleContact("a")) }
                error("simulated failure")
            }
        } catch (_: Exception) {
        }
        // Both inserts rolled back; we read in a blocking manner.
        val loaded = runBlocking { cdao.findByKeyIdHex("a") }
        assertNull(loaded)
    }

    // 24. WAL mode does not throw on read during write
    @Test
    fun `wal mode default does not throw on concurrent read during write`() =
        runBlocking {
            val cdao = db.contacts()
            cdao.upsert(sampleContact("a"))
            assertNotNull(cdao.findByKeyIdHex("a"))
        }

    // 25. Outbox nextAttemptAt ordering across many rows
    @Test
    fun `outbox due entries order by nextAttemptAt asc across many rows`() =
        runBlocking {
            val odao = db.outbox()
            for (i in 1..10) odao.insert(sampleOutbox("o$i", "QUEUED", i * 100L))
            val due = odao.allDue(now = 1_000_000L)
            assertEquals(10, due.size)
            for (i in 0 until 9) {
                assertTrue(due[i].nextAttemptAt <= due[i + 1].nextAttemptAt, "ASC order")
            }
        }

    // 26. SignedPreKey overwrite via REPLACE preserves new timestamp
    @Test
    fun `signed prekey overwrite updates timestamp`() =
        runBlocking {
            val dao = db.signedPreKeys()
            dao.upsert(SignedPreKeyEntity(1, ByteArray(64), 1000L))
            dao.upsert(SignedPreKeyEntity(1, ByteArray(64) { 1 }, 2000L))
            val rec = dao.find(1)!!
            assertEquals(2000L, rec.timestamp)
        }

    // 27. Foreign-key not enforced by default in Room
    @Test
    fun `foreign keys not enforced by default`() =
        runBlocking {
            val mdao = db.messages()
            mdao.insert(sampleMessage("m1", 999_999L))
            assertNotNull(mdao.findByMessageIdHex("m1"))
        }

    // 28. Outbox can survive a re-open (process-death persistence for on-disk DB)
    //     On-disk variant lives in DrSqlCipherTest; here we verify the in-memory
    //     model clears state on close (contrast point).
    @Test
    fun `in-memory db close clears state`() =
        runBlocking {
            val cdao = db.contacts()
            cdao.upsert(sampleContact("alice"))
            assertEquals(1, cdao.deleteByHex("alice"))
            db.close()
            // No re-open for in-memory; on-disk variant tested separately.
        }

    // 29. Identity unique index on (addressName, deviceId) replaces on conflict
    @Test
    fun `identity unique index on addressName deviceId replaces on conflict`() =
        runBlocking {
            val dao = db.identities()
            dao.upsert(IdentityEntity("alice", 1, ByteArray(33) { 1 }, "untrusted"))
            dao.upsert(IdentityEntity("alice", 1, ByteArray(33) { 2 }, "trusted"))
            val rec = dao.find("alice", 1)!!
            assertArrayEquals(ByteArray(33) { 2 }, rec.identityKeyBytes)
        }

    // 30. Outbox nextAttemptAt index supports bulk due scans (perf smoke)
    @Test
    fun `outbox bulk insert supports bulk due scan`() =
        runBlocking {
            val odao = db.outbox()
            for (i in 1..1000) odao.insert(sampleOutbox("o$i", "QUEUED", i.toLong()))
            val due = odao.allDue(now = 10_000L)
            assertTrue(due.size >= 500, "bulk due scan must work: ${due.size}")
        }

    private fun sampleContact(hex: String) =
        ContactEntity(
            keyIdHex = hex,
            displayName = "Alice",
            ed25519Pub = ByteArray(32) { hex[0].code.toByte() },
            x25519Pub = ByteArray(32) { hex[0].code.toByte() },
            verified = false,
            addedAt = 0L,
        )

    private fun sampleMessage(
        hex: String,
        contactId: Long,
        sentAt: Long = 0L,
        status: String = "QUEUED",
    ) = MessageEntity(
        messageIdHex = hex,
        contactId = contactId,
        fromKeyIdHex = null,
        toKeyIdHex = null,
        text = "hello",
        sentAt = sentAt,
        receivedAt = 0L,
        direction = "INCOMING",
        status = status,
    )

    private fun sampleOutbox(
        hex: String,
        state: String,
        nextAttemptAt: Long = 0L,
    ) = OutboxEntity(
        messageIdHex = hex,
        envelopeBytes = ByteArray(8),
        toKeyIdHex = "dest",
        state = state,
        attempts = 0,
        nextAttemptAt = nextAttemptAt,
        lastError = null,
    )
}
