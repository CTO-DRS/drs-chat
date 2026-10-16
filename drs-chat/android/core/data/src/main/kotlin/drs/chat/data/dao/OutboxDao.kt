package drs.chat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import drs.chat.data.entity.OutboxEntity

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(o: OutboxEntity): Long

    @Update
    suspend fun update(o: OutboxEntity)

    @Query("SELECT * FROM outbox WHERE messageIdHex = :hex LIMIT 1")
    suspend fun findByMessageIdHex(hex: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE state = 'QUEUED' AND nextAttemptAt <= :now ORDER BY nextAttemptAt ASC")
    suspend fun allDue(now: Long): List<OutboxEntity>

    @Query("UPDATE outbox SET state = :state, attempts = :attempts, nextAttemptAt = :nextAt, lastError = :err WHERE messageIdHex = :hex")
    suspend fun updateState(
        hex: String,
        state: String,
        attempts: Int,
        nextAt: Long,
        err: String?,
    ): Int
}
