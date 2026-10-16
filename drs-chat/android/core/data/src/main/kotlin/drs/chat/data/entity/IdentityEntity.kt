package drs.chat.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Identity record (libsignal IdentityKeyStore backing). `addressName` is
 * SignalProtocolAddress.name (e.g. the contact's keyIdHex); `deviceId` is
 * the SignalProtocolAddress.deviceId. `identityKeyBytes` is the serialized
 * IdentityKey; `trustedLevel` is "trusted"/"untrusted".
 *
 * Single combined PK (addressName+deviceId) — modelled as a synthetic Long
 * autoincrement for Room + a unique index on the natural key.
 */
@Entity(
    tableName = "identity_records",
    indices = [Index(value = ["addressName", "deviceId"], unique = true)],
)
data class IdentityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val addressName: String,
    val deviceId: Int,
    val identityKeyBytes: ByteArray,
    val trustedLevel: String,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is IdentityEntity &&
                    id == other.id &&
                    addressName == other.addressName &&
                    deviceId == other.deviceId &&
                    identityKeyBytes.contentEquals(other.identityKeyBytes) &&
                    trustedLevel == other.trustedLevel
            )

    override fun hashCode(): Int {
        var r = id.hashCode()
        r = 31 * r + addressName.hashCode()
        r = 31 * r + deviceId.hashCode()
        r = 31 * r + identityKeyBytes.contentHashCode()
        r = 31 * r + trustedLevel.hashCode()
        return r
    }
}
