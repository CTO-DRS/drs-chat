package drs.chat.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import drs.chat.data.dao.ContactDao
import drs.chat.data.dao.IdentityDao
import drs.chat.data.dao.KyberPreKeyDao
import drs.chat.data.dao.MessageDao
import drs.chat.data.dao.OutboxDao
import drs.chat.data.dao.PreKeyDao
import drs.chat.data.dao.SessionDao
import drs.chat.data.dao.SignedPreKeyDao
import drs.chat.data.entity.ContactEntity
import drs.chat.data.entity.IdentityEntity
import drs.chat.data.entity.KyberPreKeyEntity
import drs.chat.data.entity.MessageEntity
import drs.chat.data.entity.OutboxEntity
import drs.chat.data.entity.PreKeyEntity
import drs.chat.data.entity.SessionEntity
import drs.chat.data.entity.SignedPreKeyEntity
import drs.chat.data.vault.KeystoreVault
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * DrDatabase — the single SQLCipher-encrypted Room store. Schema version 1
 * (M3 baseline). All tables live behind one DB so the SQLCipher passphrase
 * protects every signal record + outbox row + contact + message in one go.
 *
 * Construction (see [DatabaseFactory]):
 *   1. KeystoreVault.unwrap(savedPassphrase) → plaintext passphrase
 *   2. SupportOpenHelperFactory(passphraseBytes)
 *   3. Room.databaseBuilder(...).openHelperFactory(supportFactory)
 *
 * No `fallbackToDestructiveMigration` — losing the DB loses the identity.
 */
@Database(
    entities = [
        ContactEntity::class,
        MessageEntity::class,
        OutboxEntity::class,
        IdentityEntity::class,
        SessionEntity::class,
        PreKeyEntity::class,
        SignedPreKeyEntity::class,
        KyberPreKeyEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DrDatabase : RoomDatabase() {
    abstract fun contacts(): ContactDao

    abstract fun messages(): MessageDao

    abstract fun outbox(): OutboxDao

    abstract fun identities(): IdentityDao

    abstract fun sessions(): SessionDao

    abstract fun preKeys(): PreKeyDao

    abstract fun signedPreKeys(): SignedPreKeyDao

    abstract fun kyberPreKeys(): KyberPreKeyDao
}

/**
 * Factory — wires the Keystore vault → SQLCipher passphrase → Room.
 *
 * Process-death persistence: the wrapped passphrase is on disk (app-private
 * storage). KeystoreVault re-unwraps it after process restart using the
 * non-extractable AES-256-GCM key in the Android Keystore → DB reopens.
 */
object DatabaseFactory {
    /**
     * Build the DB. Caller supplies the vault + a stable passphrase blob
     * (already wrapped). We unwrap + feed to SupportOpenHelperFactory.
     */
    fun open(
        context: android.content.Context,
        vault: KeystoreVault,
        wrappedPassphrase: ByteArray,
        name: String = "drs.db",
    ): DrDatabase {
        val passphrase = vault.unwrap(wrappedPassphrase)
        // zero-out the wrapped form after unwrap — best-effort scrub
        wrappedPassphrase.fill(0)
        val supportFactory = SupportOpenHelperFactory(passphrase)
        return Room.databaseBuilder(context, DrDatabase::class.java, name)
            .openHelperFactory(supportFactory)
            .build()
    }
}
