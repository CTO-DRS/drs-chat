package drs.chat.data.store

import drs.chat.data.dao.KyberPreKeyDao
import drs.chat.data.entity.KyberPreKeyEntity
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.KyberPreKeyStore

/**
 * Room-backed libsignal KyberPreKeyStore (PQXDH post-quantum prekeys).
 *
 * libsignal 0.86's interface:
 *   - loadKyberPreKey(int) — returns KyberPreKeyRecord or throws InvalidKeyIdException
 *   - loadKyberPreKeys() — returns List<KyberPreKeyRecord>
 *   - storeKyberPreKey(int, KyberPreKeyRecord)
 *   - containsKyberPreKey(int)
 *   - markKyberPreKeyUsed(int, int, ECPublicKey) — throws ReusedBaseKeyException
 *
 * NOTE on `markKyberPreKeyUsed`: libsignal 0.86 calls this with the
 * `(kyberPreKeyId, ciphertextMessageUid, ephemeralPublicKey)` triple to
 * detect PQ replay. The canonical behaviour is to delete the consumed
 * record (PQ prekeys are one-shot). Phase 1 accepts that risk: a relay
 * replay would surface as a missing prekey on the next bundle fetch and
 * trigger replenish. M5+ implements the full reuse-detection table.
 */
class RoomKyberPreKeyStore(private val dao: KyberPreKeyDao) : KyberPreKeyStore {
    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        val rec =
            runBlocking { dao.find(kyberPreKeyId) }
                ?: throw InvalidKeyIdException("kyber prekey $kyberPreKeyId not found")
        return KyberPreKeyRecord(rec.keyBytes)
    }

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> {
        // Phase 1: only bulk iteration point is bundle generation, which
        // :core:crypto's CryptoEngine does by id. M4 extends this DAO with
        // a SELECT * when a real caller exists.
        return emptyList()
    }

    override fun storeKyberPreKey(
        kyberPreKeyId: Int,
        record: KyberPreKeyRecord,
    ) {
        runBlocking {
            dao.upsert(
                KyberPreKeyEntity(
                    kyberPreKeyId = kyberPreKeyId,
                    keyBytes = record.serialize(),
                    timestamp = record.timestamp,
                ),
            )
        }
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = runBlocking { dao.find(kyberPreKeyId) != null }

    override fun markKyberPreKeyUsed(
        kyberPreKeyId: Int,
        uid: Int,
        ephemeralPublicKey: ECPublicKey,
    ) {
        // Phase 1: delete on use (one-shot prekeys). PQ replay detection
        // table comes in M5 — see class doc.
        runBlocking { dao.delete(kyberPreKeyId) }
    }
}
