package drs.chat.protocol

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.encodeToByteArray

/**
 * Out-of-band invite bundle (QR / share-link) — carries the identity keys +
 * PQXDH prekey bundle so a peer can start a Signal session WITHOUT the relay.
 * CBOR-encoded; the [signedPreKeySig] + [kyberPreKeySig] are X25519-identity
 * signatures (XEdDSA), attesting the prekeys. [oneTimePreKey] is nullable
 * (signed-prekey-only X3DH fallback when OTKs are exhausted).
 */
@Serializable
data class ContactBundle(
    @SerialName("ed") val ed25519Pub: ByteArray,
    @SerialName("x2") val x25519Pub: ByteArray,
    @SerialName("spid") val signedPreKeyId: Int,
    @SerialName("spp") val signedPreKeyPub: ByteArray,
    @SerialName("sps") val signedPreKeySig: ByteArray,
    @SerialName("otid") val oneTimePreKeyId: Int?,
    @SerialName("otp") val oneTimePreKeyPub: ByteArray?,
    @SerialName("kpid") val kyberPreKeyId: Int,
    @SerialName("kpp") val kyberPreKeyPub: ByteArray,
    @SerialName("kps") val kyberPreKeySig: ByteArray,
) {
    init {
        require(ed25519Pub.size == 32) { "ed25519Pub must be 32 bytes" }
        require(x25519Pub.size == 32) { "x25519Pub must be 32 bytes" }
        require(signedPreKeyPub.size == 32) { "signedPreKeyPub must be 32 bytes" }
        require(signedPreKeySig.size == 64) { "signedPreKeySig must be 64 bytes" }
        require(kyberPreKeySig.size == 64) { "kyberPreKeySig must be 64 bytes" }
        require((oneTimePreKeyId == null) == (oneTimePreKeyPub == null)) { "OTK id+pub must both be null or both non-null" }
        oneTimePreKeyPub?.let { require(it.size == 32) { "oneTimePreKeyPub must be 32 bytes" } }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactBundle) return false
        return ed25519Pub.contentEquals(other.ed25519Pub) && x25519Pub.contentEquals(other.x25519Pub) &&
            signedPreKeyId == other.signedPreKeyId && signedPreKeyPub.contentEquals(other.signedPreKeyPub) &&
            signedPreKeySig.contentEquals(other.signedPreKeySig) && oneTimePreKeyId == other.oneTimePreKeyId &&
            oneTimePreKeyPub.contentEquals(other.oneTimePreKeyPub) && kyberPreKeyId == other.kyberPreKeyId &&
            kyberPreKeyPub.contentEquals(other.kyberPreKeyPub) && kyberPreKeySig.contentEquals(other.kyberPreKeySig)
    }

    override fun hashCode(): Int {
        var r = ed25519Pub.contentHashCode()
        r = 31 * r + x25519Pub.contentHashCode()
        r = 31 * r + signedPreKeyId
        r = 31 * r + signedPreKeyPub.contentHashCode()
        r = 31 * r + signedPreKeySig.contentHashCode()
        r = 31 * r + (oneTimePreKeyId ?: 0)
        r = 31 * r + (oneTimePreKeyPub?.contentHashCode() ?: 0)
        r = 31 * r + kyberPreKeyId
        r = 31 * r + kyberPreKeyPub.contentHashCode()
        r = 31 * r + kyberPreKeySig.contentHashCode()
        return r
    }
}

object ContactBundleCodec {
    @OptIn(ExperimentalSerializationApi::class)
    private val cbor =
        Cbor {
            encodeDefaults = true
            useDefiniteLengthEncoding = true
            alwaysUseByteString = true
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun encode(b: ContactBundle): ByteArray = cbor.encodeToByteArray(b)

    @OptIn(ExperimentalSerializationApi::class)
    fun decode(b: ByteArray): ContactBundle = cbor.decodeFromByteArray(ContactBundle.serializer(), b)
}
