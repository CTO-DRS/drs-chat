package drs.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** libsignal SessionStore backing — opaque session blobs keyed by (address, device). */
@Entity(
    tableName = "sessions",
    indices = [Index(value = ["addressName", "deviceId"], unique = true)],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val addressName: String,
    val deviceId: Int,
    val sessionBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SessionEntity &&
                    id == other.id &&
                    addressName == other.addressName &&
                    deviceId == other.deviceId &&
                    sessionBytes.contentEquals(other.sessionBytes)
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + addressName.hashCode()
        r = 31 * r + deviceId.hashCode()
        r = 31 * r + sessionBytes.contentHashCode()
        return r
    }
}
