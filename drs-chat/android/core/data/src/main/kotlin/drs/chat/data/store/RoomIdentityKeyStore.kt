package drs.chat.data.store

import drs.chat.data.dao.IdentityDao
import drs.chat.data.entity.IdentityEntity
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.IdentityKeyStore

/**
 * Room-backed IdentityKeyStore. Trust on first use: a record not present
 * is saved and trusted; a record present is verified against the incoming
 * key; mismatches return false (caller — the CryptoEngine — decides what
 * to do: accept once, mark untrusted, etc.).
 *
 * Per ADR-0003: identity records live INSIDE the SQLCipher DB; the Keystore
 * vault only wraps the passphrase. So a stolen DB file is useless without
 * the non-extractable Keystore key.
 */
class RoomIdentityKeyStore(
    private val dao: IdentityDao,
    private val localIdentity: IdentityKeyPair,
) : IdentityKeyStore {
    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentity

    override fun getLocalRegistrationId(): Int = localIdentity.publicKey.publicKeyBytes.size * 1000

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): Boolean {
        val existing = dao.find(address.name, address.deviceId)
        dao.upsert(
            IdentityEntity(
                addressName = address.name,
                deviceId = address.deviceId,
                identityKeyBytes = identityKey.serialize(),
                trustedLevel = "trusted",
            ),
        )
        // true if identity was NEW (no prior record); the caller (libsignal)
        // uses this to decide "session established for the first time".
        return existing == null
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val existing = dao.find(address.name, address.deviceId) ?: return true
        return existing.identityKeyBytes.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val rec = dao.find(address.name, address.deviceId) ?: return null
        return IdentityKey(rec.identityKeyBytes, 0)
    }
}
