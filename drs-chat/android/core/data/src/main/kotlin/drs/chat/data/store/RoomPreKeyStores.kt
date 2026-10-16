package drs.chat.data.store

import drs.chat.data.dao.PreKeyDao
import drs.chat.data.dao.SignedPreKeyDao
import drs.chat.data.entity.PreKeyEntity
import drs.chat.data.entity.SignedPreKeyEntity
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/**
 * Room-backed PreKeyStore + SignedPreKeyStore. Per ADR-0003, all keys live
 * inside the SQLCipher DB; this adapter is the libsignal-facing facade.
 */
class RoomPreKeyStore(private val dao: PreKeyDao) : org.signal.libsignal.protocol.state.PreKeyStore {
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val rec = dao.find(preKeyId) ?: error("prekey $preKeyId not found")
        return PreKeyRecord(rec.keyBytes)
    }

    override fun loadPreKeys(): List<PreKeyRecord> {
        // The DAO returns a count; full-iter support would need a SELECT *.
        // libsignal only iterates during bundle generation — covered by
        // :core:crypto's CryptoEngine, which queries by id.
        error("use loadPreKey(id) — bulk iteration is internal to CryptoEngine")
    }

    override fun storePreKey(
        preKeyId: Int,
        record: PreKeyRecord,
    ) {
        dao.upsert(PreKeyEntity(preKeyId = preKeyId, keyBytes = record.serialize()))
    }

    override fun containsPreKey(preKeyId: Int): Boolean = dao.find(preKeyId) != null

    override fun removePreKey(preKeyId: Int) {
        dao.delete(preKeyId)
    }
}

class RoomSignedPreKeyStore(private val dao: SignedPreKeyDao) : SignedPreKeyStore {
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val rec = dao.find(signedPreKeyId) ?: error("signed-prekey $signedPreKeyId not found")
        return SignedPreKeyRecord(rec.keyBytes)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = error("use loadSignedPreKey(id)")

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord,
    ) {
        dao.upsert(
            SignedPreKeyEntity(
                signedPreKeyId = signedPreKeyId,
                keyBytes = record.serialize(),
                timestamp = record.timestamp,
            ),
        )
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = dao.find(signedPreKeyId) != null

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        dao.delete(signedPreKeyId)
    }
}
