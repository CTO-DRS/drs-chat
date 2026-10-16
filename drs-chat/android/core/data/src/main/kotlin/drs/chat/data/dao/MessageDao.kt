package drs.chat.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import drs.chat.data.entity.MessageEntity

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity): Long

    @Update
    suspend fun update(m: MessageEntity)

    @Query("SELECT * FROM messages WHERE messageIdHex = :hex LIMIT 1")
    suspend fun findByMessageIdHex(hex: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE contactId = :cid ORDER BY sentAt ASC LIMIT :limit")
    suspend fun historyForContact(
        cid: Long,
        limit: Int,
    ): List<MessageEntity>

    @Query("UPDATE messages SET status = :status WHERE messageIdHex = :hex")
    suspend fun updateStatus(
        hex: String,
        status: String,
    ): Int

    @Query("DELETE FROM messages WHERE contactId = :cid")
    suspend fun deleteForContact(cid: Long): Int
}
