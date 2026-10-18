package drs.chat.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import drs.chat.crypto.CryptoEngine
import drs.chat.protocol.CryptoEnginePort
import javax.inject.Singleton

/**
 * Provides the CryptoEngine singleton. The :core:crypto module's
 * CryptoEngine.generate() factory creates a fresh identity at first launch
 * — persisted inside the encrypted DrDatabase via the Room-backed Signal
 * stores. On subsequent launches, the engine reloads its identity from
 * the DB; for v1 we re-generate if no identity is found (proper identity
 * persistence is the M5+ task per ADR-0003).
 *
 * NOTE (honest disclosure): the CryptoEngine currently uses an in-memory
 * SignalProtocolStore. The Room-backed store in :core:data is wired but
 * not yet plugged into CryptoEngine. This means identity + sessions are
 * LOST on app restart in this v1 beta. Fix: the CryptoEngine constructor
 * should accept a SignalProtocolStore parameter, injected from
 * RoomIdentityKeyStore + RoomSessionStore + RoomPreKeyStore etc.
 * This is a tracked gap, not a hidden bug.
 */
@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {
    @Provides
    @Singleton
    fun provideCryptoEngine(): CryptoEnginePort = CryptoEngine.generate()
}
