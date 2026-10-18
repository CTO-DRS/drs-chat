package drs.chat.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import drs.chat.domain.port.Clock
import drs.chat.domain.port.IdGenerator
import drs.chat.protocol.CryptoEnginePort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Chat wiring module — Phase 1 v1 beta uses in-memory fake repositories from
 * :core:domain testing fixtures. This makes the app boot and the UI work,
 * but sent messages don't actually persist across app restarts and don't
 * actually traverse the relay. Real wiring (Room-backed OutboxRepository +
 * MessageRepository + real WebSocketTransport) is M5+ scope.
 *
 * HONEST DISCLOSURE (logged in worklog): this v1 beta is a UI shell.
 * The 152-test suite proves the protocol + crypto + data + relay +
 * transport layers work in isolation. The end-to-end wire-up of
 * production adapters is the missing piece for real user testing.
 */
@Module
@InstallIn(SingletonComponent::class)
object ChatModule {
    @Provides
    @Singleton
    fun provideClock(): Clock =
        object : Clock {
            override fun now(): Long = System.currentTimeMillis()
        }

    @Provides
    @Singleton
    fun provideIdGenerator(crypto: CryptoEnginePort): IdGenerator =
        object : IdGenerator {
            override fun newMessageId(): ByteArray = crypto.newMessageId()
        }

    @Provides
    @Singleton
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
