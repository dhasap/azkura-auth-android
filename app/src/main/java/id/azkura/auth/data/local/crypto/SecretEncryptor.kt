package id.azkura.auth.data.local.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts/decrypts TOTP secret fields using an AES-256-GCM key stored in
 * Android Keystore. The key never leaves the hardware-backed keystore.
 *
 * Encrypted format: Base64(iv[12] + ciphertext + authTag[16])
 * Prefix: "ENC:" to distinguish encrypted values from plaintext during migration.
 */
@Singleton
class SecretEncryptor @Inject constructor() {

    companion object {
        private const val KEYSTORE_ALIAS = "azkura_secret_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_SIZE = 12
        private const val ENCRYPTED_PREFIX = "ENC:"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun getOrCreateKey(): SecretKey {
        keyStore.getEntry(KEYSTORE_ALIAS, null)?.let { entry ->
            return (entry as KeyStore.SecretKeyEntry).secretKey
        }

        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return keyGen.generateKey()
    }

    /** Returns true if the value is already encrypted (has the ENC: prefix). */
    fun isEncrypted(value: String): Boolean = value.startsWith(ENCRYPTED_PREFIX)

    /**
     * Encrypt a plaintext TOTP secret. Returns "ENC:" + Base64(iv + ciphertext + tag).
     * If the value is already encrypted, returns it unchanged.
     */
    fun encrypt(plaintext: String): String {
        if (isEncrypted(plaintext)) return plaintext
        if (plaintext.isBlank()) return plaintext

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv // GCM generates a random IV
        val ciphertextAndTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(iv.size + ciphertextAndTag.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertextAndTag, 0, combined, iv.size, ciphertextAndTag.size)

        return ENCRYPTED_PREFIX + Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt an encrypted TOTP secret. If the value is not encrypted (no ENC: prefix),
     * returns it as-is (backward compatibility during migration).
     */
    fun decrypt(encrypted: String): String {
        if (!isEncrypted(encrypted)) return encrypted
        if (encrypted == ENCRYPTED_PREFIX) return ""

        val data = Base64.decode(encrypted.removePrefix(ENCRYPTED_PREFIX), Base64.NO_WRAP)
        if (data.size < IV_SIZE + 1) return ""

        val iv = data.copyOfRange(0, IV_SIZE)
        val ciphertextAndTag = data.copyOfRange(IV_SIZE, data.size)

        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val plaintext = cipher.doFinal(ciphertextAndTag)
        return plaintext.toString(Charsets.UTF_8)
    }
}
