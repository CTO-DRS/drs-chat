package drs.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Persisted message (incoming or outgoing, decoded InnerMessage + status). */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["messageIdHex"], unique = true),
        Index(value = ["contactId"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageIdHex: String,
    val contactId: Long,
    val fromKeyIdHex: String?,
    val toKeyIdHex: String?,
    val text: String,
    val sentAt: Long,
    val receivedAt: Long,
    val direction: String,
    val status: String,
)
