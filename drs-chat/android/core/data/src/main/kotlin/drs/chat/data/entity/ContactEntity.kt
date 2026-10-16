package drs.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted Contact (per ADR-0003 storage layer).
 *
 * `keyIdHex` is the content-based hex form of the 16-byte KeyId — never the
 * raw ByteArray (referential-equals bug family, byte-array-key-guard.sh).
 */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["keyIdHex"], unique = true)],
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyIdHex: String,
    val displayName: String,
    val ed25519Pub: ByteArray,
    val x25519Pub: ByteArray,
    val verified: Boolean,
    val addedAt: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is ContactEntity &&
                    id == other.id &&
                    keyIdHex == other.keyIdHex &&
                    displayName == other.displayName &&
                    ed25519Pub.contentEquals(other.ed25519Pub) &&
                    x25519Pub.contentEquals(other.x25519Pub) &&
                    verified == other.verified &&
                    addedAt == other.addedAt
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + keyIdHex.hashCode()
        r = 31 * r + displayName.hashCode()
        r = 31 * r + ed25519Pub.contentHashCode()
        r = 31 * r + x25519Pub.contentHashCode()
        r = 31 * r + verified.hashCode()
        r = 31 * r + addedAt.hashCode()
        return r
    }
}
