package drs.chat.data.store

import drs.chat.data.dao.IdentityDao
import drs.chat.data.entity.IdentityEntity
import kotlinx.coroutines.runBlocking
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
 *
 * The libsignal interface methods are non-suspend (Java); we bridge to the
 * suspend Room DAOs via `runBlocking`. This is acceptable because libsignal
 * calls these synchronously from its own internal locking — never on the
 * hot path of a coroutine dispatcher.
 */
class RoomIdentityKeyStore(
    private val dao: IdentityDao,
    private val localIdentity: IdentityKeyPair,
    private val localRegistrationId: Int = 1000,
) : IdentityKeyStore {
    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentity

    override fun getLocalRegistrationId(): Int = localRegistrationId

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        val existing = runBlocking { dao.find(address.name, address.deviceId) }
        runBlocking {
            dao.upsert(
                IdentityEntity(
                    addressName = address.name,
                    deviceId = address.deviceId,
                    identityKeyBytes = identityKey.serialize(),
                    trustedLevel = "trusted",
                ),
            )
        }
        return if (existing == null) IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED else IdentityKeyStore.IdentityChange.REPLACED_EXISTING
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val existing = runBlocking { dao.find(address.name, address.deviceId) } ?: return true
        return existing.identityKeyBytes.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        val rec = runBlocking { dao.find(address.name, address.deviceId) } ?: return null
        return IdentityKey(rec.identityKeyBytes, 0)
    }
}
