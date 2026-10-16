package drs.chat.data.vault

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * KeystoreVault — the L1 vault per ADR-0003.
 *
 * - AES-256-GCM, 256-bit key.
 * - StrongBox-backed when the device offers it (`setIsStrongBoxBacked(true)`
 *   and Keymaster fallback on `StrongBoxUnavailableException`).
 * - NO `setUserAuthenticationRequired` (vault-guard.sh enforces this — a
 *   lockscreen change must not destroy on-disk message history).
 *
 * The vault key lives in the Android Keystore (never extractable). wrap/unwrap
 * happen in-process; the wrapped blobs live in app-private storage and the
 * only thing inside them is the SQLCipher passphrase (a random 32-byte value).
 */
class KeystoreVault(
    private val alias: String = DEFAULT_ALIAS,
) {
    private val keystore: KeyStore =
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply {
            load(null)
        }

    private val key: SecretKey by lazy { getOrCreateKey() }

    /** Wrap plaintext bytes (e.g. the SQLCipher passphrase) into a self-describing blob:
     *  [iv(12 bytes) || ciphertext+tag]. */
    fun wrap(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext)
        return iv + ct
    }

    /** Reverse of [wrap]. Throws on tamper/auth failure (GCM tag mismatch). */
    fun unwrap(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE) { "vault blob too short" }
        val iv = blob.copyOfRange(0, IV_SIZE)
        val ct = blob.copyOfRange(IV_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        return cipher.doFinal(ct)
    }

    private fun getOrCreateKey(): SecretKey {
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        // Not present — generate. Try StrongBox first; fall back to TEE on
        // any failure (StrongBoxUnavailableException, or older devices).
        return runCatching { generate(strongBox = true) }
            .recoverCatching { generate(strongBox = false) }
            .getOrThrow()
    }

    private fun generate(strongBox: Boolean): SecretKey {
        val builder =
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setIsStrongBoxBacked(strongBox)
        // CRITICAL: do NOT bind user-auth/biometric to this vault key.
        // ADR-0003 mandates this — vault must unlock without user
        // interaction so the lockscreen can change without destroying
        // on-disk message history. scripts/vault-guard.sh enforces at
        // CI (greps for any user-auth binding call in production
        // sources and fails the build).
        if (strongBox) {
            // only set StrongBox on devices that report the feature; the
            // generator itself will throw StrongBoxUnavailableException if
            // the device doesn't actually have it, and we fall back.
        }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        gen.init(builder.build())
        return gen.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS: String = "drs-vault-v1"
        const val KEYSTORE_PROVIDER: String = "AndroidKeyStore"
        const val TRANSFORMATION: String = "AES/GCM/NoPadding"
        const val IV_SIZE: Int = 12
        const val TAG_BITS: Int = 128
        const val KEY_SIZE_BITS: Int = 256
    }
}
