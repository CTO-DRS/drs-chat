package drs.chat.data.store

import drs.chat.data.dao.SessionDao
import drs.chat.data.entity.SessionEntity
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore

/** Room-backed libsignal SessionStore. Session blobs are opaque. */
class RoomSessionStore(private val dao: SessionDao) : SessionStore {
    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        val rec = dao.find(address.name, address.deviceId) ?: return SessionRecord()
        return SessionRecord(rec.sessionBytes)
    }

    override fun getSubDeviceSessions(name: String): List<Int> {
        // Simple flat model: one device per name in Phase 1. Full multi-device
        // support would add a `getSubDeviceSessions(name)` DAO query.
        return listOf(1)
    }

    override fun storeSession(
        address: SignalProtocolAddress,
        record: SessionRecord,
    ) {
        dao.upsert(
            SessionEntity(
                addressName = address.name,
                deviceId = address.deviceId,
                sessionBytes = record.serialize(),
            ),
        )
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = dao.contains(address.name, address.deviceId)

    override fun deleteSession(address: SignalProtocolAddress) {
        dao.delete(address.name, address.deviceId)
    }

    override fun deleteAllSessions(name: String) {
        // Single-device in Phase 1; a multi-device implementation would
        // run a DELETE ... WHERE addressName = ? here.
    }
}
