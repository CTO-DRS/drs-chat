package drs.chat.data.store

import drs.chat.data.dao.KyberPreKeyDao
import drs.chat.data.entity.KyberPreKeyEntity
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore

/** Room-backed libsignal KyberPreKeyStore (PQXDH post-quantum prekeys). */
class RoomKyberPreKeyStore(private val dao: KyberPreKeyDao) : KyberPreKeyStore {
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        val rec = dao.find(kyberPreKeyId) ?: error("kyber prekey $kyberPreKeyId not found")
        return KyberPreKeyRecord(rec.keyBytes)
    }

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = error("use loadKyberPreKey(id)")

    override fun storeKyberPreKey(
        kyberPreKeyId: Int,
        record: KEMKeyPair,
    ) {
        error("use storeKyberPreKey(id, KyberPreKeyRecord)")
    }

    // libsignal 0.86+ uses KyberPreKeyRecord overload
    fun storeKyberPreKeyRecord(
        kyberPreKeyId: Int,
        record: KyberPreKeyRecord,
    ) {
        dao.upsert(
            KyberPreKeyEntity(
                kyberPreKeyId = kyberPreKeyId,
                keyBytes = record.serialize(),
                timestamp = record.timestamp,
            ),
        )
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = dao.find(kyberPreKeyId) != null

    override fun removeKyberPreKey(kyberPreKeyId: Int) {
        dao.delete(kyberPreKeyId)
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int) {
        // One-shot prekeys — remove on use (matches RoomPreKeyStore behaviour).
        // libsignal uses this hook to deplete-and-replenish; we delete and let
        // the CryptoEngine's replenishOtkIfNeeded path backfill.
        dao.delete(kyberPreKeyId)
    }
}
