package drs.chat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import drs.chat.data.entity.IdentityEntity
import drs.chat.data.entity.KyberPreKeyEntity
import drs.chat.data.entity.PreKeyEntity
import drs.chat.data.entity.SessionEntity
import drs.chat.data.entity.SignedPreKeyEntity

@Dao
interface IdentityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: IdentityEntity): Long

    @Query("SELECT * FROM identity_records WHERE addressName = :name AND deviceId = :deviceId LIMIT 1")
    suspend fun find(
        name: String,
        deviceId: Int,
    ): IdentityEntity?

    @Query("DELETE FROM identity_records WHERE addressName = :name AND deviceId = :deviceId")
    suspend fun delete(
        name: String,
        deviceId: Int,
    ): Int
}

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SessionEntity): Long

    @Query("SELECT * FROM sessions WHERE addressName = :name AND deviceId = :deviceId LIMIT 1")
    suspend fun find(
        name: String,
        deviceId: Int,
    ): SessionEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM sessions WHERE addressName = :name AND deviceId = :deviceId)")
    suspend fun contains(
        name: String,
        deviceId: Int,
    ): Boolean

    @Query("DELETE FROM sessions WHERE addressName = :name AND deviceId = :deviceId")
    suspend fun delete(
        name: String,
        deviceId: Int,
    ): Int

    @Query("SELECT * FROM sessions")
    suspend fun all(): List<SessionEntity>
}

@Dao
interface PreKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(p: PreKeyEntity): Long

    @Query("SELECT * FROM prekeys WHERE preKeyId = :id LIMIT 1")
    suspend fun find(id: Int): PreKeyEntity?

    @Query("DELETE FROM prekeys WHERE preKeyId = :id")
    suspend fun delete(id: Int): Int

    @Query("SELECT COUNT(*) FROM prekeys")
    suspend fun count(): Int
}

@Dao
interface SignedPreKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: SignedPreKeyEntity): Long

    @Query("SELECT * FROM signed_prekeys WHERE signedPreKeyId = :id LIMIT 1")
    suspend fun find(id: Int): SignedPreKeyEntity?

    @Query("DELETE FROM signed_prekeys WHERE signedPreKeyId = :id")
    suspend fun delete(id: Int): Int
}

@Dao
interface KyberPreKeyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(k: KyberPreKeyEntity): Long

    @Query("SELECT * FROM kyber_prekeys WHERE kyberPreKeyId = :id LIMIT 1")
    suspend fun find(id: Int): KyberPreKeyEntity?

    @Query("DELETE FROM kyber_prekeys WHERE kyberPreKeyId = :id")
    suspend fun delete(id: Int): Int

    @Query("SELECT COUNT(*) FROM kyber_prekeys")
    suspend fun count(): Int
}
