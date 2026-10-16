package drs.chat.data.store

import drs.chat.data.dao.SessionDao
import drs.chat.data.entity.SessionEntity
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

/**
 * Room-backed libsignal SessionStore. Session blobs are opaque.
 *
 * Phase 1 model: one device per SignalProtocolAddress.name (no real
 * multi-device support). `getSubDeviceSessions` returns `[1]` (the
 * canonical device). `loadExistingSessions` filters the requested
 * addresses against the persisted set.
 */
class RoomSessionStore(private val dao: SessionDao) : SessionStore {
    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val rec = runBlocking { dao.find(address.name, address.deviceId) } ?: return SessionRecord()
        return SessionRecord(rec.sessionBytes)
    }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> {
        return addresses.mapNotNull { addr ->
            val rec = runBlocking { dao.find(addr.name, addr.deviceId) } ?: return@mapNotNull null
            SessionRecord(rec.sessionBytes)
        }.ifEmpty { throw NoSessionException("no sessions for $addresses") }
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        // Phase 1: single-device. M5+ will add a real `SELECT deviceId
        // FROM sessions WHERE addressName = ?` DAO query.
        return if (runBlocking { dao.contains(name, 1) }) listOf(1) else emptyList()
    }

    override fun storeSession(
        address: SignalProtocolAddress,
        record: SessionRecord,
    ) {
        runBlocking {
            dao.upsert(
                SessionEntity(
                    addressName = address.name,
                    deviceId = address.deviceId,
                    sessionBytes = record.serialize(),
                ),
            )
        }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = runBlocking { dao.contains(address.name, address.deviceId) }

    override fun deleteSession(address: SignalProtocolAddress) {
        runBlocking { dao.delete(address.name, address.deviceId) }
    }

    override fun deleteAllSessions(name: String) {
        // Phase 1 single-device; M5+ will add a `DELETE ... WHERE addressName = ?` query.
        runBlocking { dao.delete(name, 1) }
    }
}
