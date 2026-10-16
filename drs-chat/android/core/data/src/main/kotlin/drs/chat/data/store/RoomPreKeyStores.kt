package drs.chat.data.store

import drs.chat.data.dao.PreKeyDao
import drs.chat.data.dao.SignedPreKeyDao
import drs.chat.data.entity.PreKeyEntity
import drs.chat.data.entity.SignedPreKeyEntity
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

/**
 * Room-backed PreKeyStore + SignedPreKeyStore. Per ADR-0003, all keys live
 * inside the SQLCipher DB; this adapter is the libsignal-facing facade.
 *
 * libsignal's Java interfaces are non-suspend; we bridge to the suspend
 * Room DAOs via `runBlocking` (libsignal calls these synchronously from
 * internal locks — never on a coroutine dispatcher's hot path).
 */
class RoomPreKeyStore(private val dao: PreKeyDao) : org.signal.libsignal.protocol.state.PreKeyStore {
    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        val rec =
            runBlocking { dao.find(preKeyId) }
                ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("prekey $preKeyId not found")
        return PreKeyRecord(rec.keyBytes)
    }

    override fun storePreKey(
        preKeyId: Int,
        record: PreKeyRecord,
    ) {
        runBlocking { dao.upsert(PreKeyEntity(preKeyId = preKeyId, keyBytes = record.serialize())) }
    }

    override fun containsPreKey(preKeyId: Int): Boolean = runBlocking { dao.find(preKeyId) != null }

    override fun removePreKey(preKeyId: Int) {
        runBlocking { dao.delete(preKeyId) }
    }
}

class RoomSignedPreKeyStore(private val dao: SignedPreKeyDao) : SignedPreKeyStore {
    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        val rec =
            runBlocking { dao.find(signedPreKeyId) }
                ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("signed-prekey $signedPreKeyId not found")
        return SignedPreKeyRecord(rec.keyBytes)
    }

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> {
        // Phase 1: libsignal only iterates signed prekeys during
        // bundle generation, which :core:crypto's CryptoEngine handles via
        // loadSignedPreKey(id) directly. We expose an empty list here as a
        // placeholder; the M4 push extends this DAO with a `SELECT *` query
        // when a real caller exists.
        return emptyList()
    }

    override fun storeSignedPreKey(
        signedPreKeyId: Int,
        record: SignedPreKeyRecord,
    ) {
        runBlocking {
            dao.upsert(
                SignedPreKeyEntity(
                    signedPreKeyId = signedPreKeyId,
                    keyBytes = record.serialize(),
                    timestamp = record.timestamp,
                ),
            )
        }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = runBlocking { dao.find(signedPreKeyId) != null }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        runBlocking { dao.delete(signedPreKeyId) }
    }
}
