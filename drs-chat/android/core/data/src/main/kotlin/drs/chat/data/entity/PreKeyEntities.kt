package drs.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** libsignal PreKeyStore (one-time prekeys, X25519) backing. */
@Entity(
    tableName = "prekeys",
    indices = [Index(value = ["preKeyId"], unique = true)],
)
data class PreKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val preKeyId: Int,
    val keyBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is PreKeyEntity &&
                    id == other.id &&
                    preKeyId == other.preKeyId &&
                    keyBytes.contentEquals(other.keyBytes)
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + preKeyId.hashCode()
        r = 31 * r + keyBytes.contentHashCode()
        return r
    }
}

/** libsignal SignedPreKeyStore backing. */
@Entity(
    tableName = "signed_prekeys",
    indices = [Index(value = ["signedPreKeyId"], unique = true)],
)
data class SignedPreKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val signedPreKeyId: Int,
    val keyBytes: ByteArray,
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is SignedPreKeyEntity &&
                    id == other.id &&
                    signedPreKeyId == other.signedPreKeyId &&
                    keyBytes.contentEquals(other.keyBytes) &&
                    timestamp == other.timestamp
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + signedPreKeyId.hashCode()
        r = 31 * r + keyBytes.contentHashCode()
        r = 31 * r + timestamp.hashCode()
        return r
    }
}

/** libsignal KyberPreKeyStore backing (PQ prekeys, libsignal 0.86+). */
@Entity(
    tableName = "kyber_prekeys",
    indices = [Index(value = ["kyberPreKeyId"], unique = true)],
)
data class KyberPreKeyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kyberPreKeyId: Int,
    val keyBytes: ByteArray,
    val timestamp: Long,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is KyberPreKeyEntity &&
                    id == other.id &&
                    kyberPreKeyId == other.kyberPreKeyId &&
                    keyBytes.contentEquals(other.keyBytes) &&
                    timestamp == other.timestamp
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + kyberPreKeyId.hashCode()
        r = 31 * r + keyBytes.contentHashCode()
        r = 31 * r + timestamp.hashCode()
        return r
    }
}
