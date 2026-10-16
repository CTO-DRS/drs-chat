package drs.chat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import drs.chat.data.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: ContactEntity): Long

    @Query("SELECT * FROM contacts WHERE keyIdHex = :hex LIMIT 1")
    suspend fun findByKeyIdHex(hex: String): ContactEntity?

    @Query("SELECT * FROM contacts ORDER BY addedAt ASC")
    fun all(): Flow<List<ContactEntity>>

    @Query("DELETE FROM contacts WHERE keyIdHex = :hex")
    suspend fun deleteByHex(hex: String): Int
}
