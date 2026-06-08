package id.azkura.auth.data.local.crypto

import android.content.Context
import android.os.Build
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Cryptographic utilities for Azkura Auth.
 *
 * This is the Android/Kotlin equivalent of src/core/crypto.js:
 * - AES-256-GCM encryption/decryption
 * - PBKDF2-HMAC-SHA256 key derivation with 310,000 iterations
 * - 16-byte random salt
 * - 12-byte random IV/nonce
 * - 128-bit GCM authentication tag
 *
 * Encrypted string format used by the Android app:
 *   Base64(salt[16] + iv[12] + ciphertext + authTag)
 *
 * The AES-GCM payload (ciphertext + authTag) is byte-compatible with WebCrypto's
 * AES-GCM output for the same plaintext/password/salt/IV. PBKDF2 is implemented
 * manually over UTF-8 password bytes so it matches TextEncoder + WebCrypto even
 * for non-ASCII passwords/PINs.
 */
@Singleton
class CryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedDefaultKey: String? = null

    /** Generate cryptographically secure random bytes. */
    fun randomBytes(length: Int): ByteArray {
        require(length >= 0) { "length must be >= 0" }
        return ByteArray(length).also(secureRandom::nextBytes)
    }

    /** Base64 encode with Android's NO_WRAP flag, matching browser btoa output. */
    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    /** Base64 decode. */
    fun fromBase64(base64: String): ByteArray = Base64.decode(base64, Base64.NO_WRAP)

    /**
     * Get or generate the deterministic default key for PIN-less mode.
     *
     * Like the extension implementation, this is intended for convenience and
     * obfuscation only; users should enable a PIN/biometric protection for strong
     * protection of TOTP secrets.
     */
    fun getDefaultKey(): String {
        cachedDefaultKey?.let { return it }

        return synchronized(this) {
            cachedDefaultKey ?: run {
                val keyMaterial = buildDefaultKeyMaterial()
                val digest = sha256(keyMaterial.toByteArray(Charsets.UTF_8))
                toBase64(digest).also { cachedDefaultKey = it }
            }
        }
    }

    /** Clear cached default key material. */
    fun clearDefaultKeyCache() {
        cachedDefaultKey = null
    }

    /**
     * Encrypt plaintext with a password/PIN.
     *
     * If [password] is null or empty, the deterministic default key is used, just
     * as crypto.js does for PIN-less mode.
     */
    fun encrypt(plaintext: String, password: String? = null): String {
        val effectivePassword = password?.takeIf { it.isNotEmpty() } ?: getDefaultKey()
        val salt = randomBytes(SALT_SIZE_BYTES)
        val iv = randomBytes(IV_SIZE_BYTES)
        val key = deriveAesKey(effectivePassword, salt)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
        val ciphertextAndTag = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteArray(salt.size + iv.size + ciphertextAndTag.size)
        System.arraycopy(salt, 0, combined, 0, salt.size)
        System.arraycopy(iv, 0, combined, salt.size, iv.size)
        System.arraycopy(ciphertextAndTag, 0, combined, salt.size + iv.size, ciphertextAndTag.size)

        return toBase64(combined)
    }

    /**
     * Decrypt a Base64(salt + iv + ciphertext + authTag) string.
     *
     * Throws [IllegalArgumentException] when the password is wrong or the payload
     * is malformed/corrupted.
     */
    fun decrypt(ciphertext: String, password: String? = null): String {
        val effectivePassword = password?.takeIf { it.isNotEmpty() } ?: getDefaultKey()

        try {
            val combined = fromBase64(ciphertext)
            val minimumSize = SALT_SIZE_BYTES + IV_SIZE_BYTES + GCM_TAG_SIZE_BYTES
            require(combined.size >= minimumSize) { "Encrypted data is too short" }

            val salt = combined.copyOfRange(0, SALT_SIZE_BYTES)
            val iv = combined.copyOfRange(SALT_SIZE_BYTES, SALT_SIZE_BYTES + IV_SIZE_BYTES)
            val ciphertextAndTag = combined.copyOfRange(SALT_SIZE_BYTES + IV_SIZE_BYTES, combined.size)
            val key = deriveAesKey(effectivePassword, salt)

            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_SIZE_BITS, iv))
            val plaintext = cipher.doFinal(ciphertextAndTag)
            return plaintext.toString(Charsets.UTF_8)
        } catch (error: Exception) {
            throw IllegalArgumentException("Incorrect PIN/password or corrupted data", error)
        }
    }

    /** Hash a PIN/password for verification, matching crypto.js hashPin(). */
    fun hashPin(pin: String, salt: ByteArray): String {
        val hash = pbkdf2HmacSha256(
            password = pin.toByteArray(Charsets.UTF_8),
            salt = salt,
            iterations = PIN_HASH_ITERATIONS,
            derivedKeyLengthBytes = AES_KEY_SIZE_BYTES,
        )
        return toBase64(hash)
    }

    /** Verify a PIN against a Base64 hash and Base64 salt. */
    fun verifyPin(pin: String, storedHash: String, storedSalt: String): Boolean {
        return try {
            val salt = fromBase64(storedSalt)
            val actualHash = fromBase64(hashPin(pin, salt))
            val expectedHash = fromBase64(storedHash)
            MessageDigest.isEqual(actualHash, expectedHash)
        } catch (_: Exception) {
            false
        }
    }

    /** Setup PIN: generate a random salt and PBKDF2 verification hash. */
    fun setupPin(pin: String): PinData {
        val salt = randomBytes(SALT_SIZE_BYTES)
        return PinData(
            hash = hashPin(pin, salt),
            salt = toBase64(salt),
        )
    }

    private fun deriveAesKey(password: String, salt: ByteArray): SecretKeySpec {
        val keyBytes = pbkdf2HmacSha256(
            password = password.toByteArray(Charsets.UTF_8),
            salt = salt,
            iterations = VAULT_KEY_ITERATIONS,
            derivedKeyLengthBytes = AES_KEY_SIZE_BYTES,
        )
        return SecretKeySpec(keyBytes, AES_ALGORITHM)
    }

    /**
     * PBKDF2-HMAC-SHA256 implemented directly over bytes for WebCrypto parity.
     */
    private fun pbkdf2HmacSha256(
        password: ByteArray,
        salt: ByteArray,
        iterations: Int,
        derivedKeyLengthBytes: Int,
    ): ByteArray {
        require(iterations > 0) { "iterations must be > 0" }
        require(derivedKeyLengthBytes > 0) { "derived key length must be > 0" }

        val hLen = SHA256_SIZE_BYTES
        val blockCount = (derivedKeyLengthBytes + hLen - 1) / hLen
        val derived = ByteArray(blockCount * hLen)

        for (blockIndex in 1..blockCount) {
            val mac = Mac.getInstance(HMAC_SHA256)
            mac.init(SecretKeySpec(password, HMAC_SHA256))

            val saltWithBlockIndex = ByteArray(salt.size + 4)
            System.arraycopy(salt, 0, saltWithBlockIndex, 0, salt.size)
            saltWithBlockIndex[salt.size] = (blockIndex ushr 24).toByte()
            saltWithBlockIndex[salt.size + 1] = (blockIndex ushr 16).toByte()
            saltWithBlockIndex[salt.size + 2] = (blockIndex ushr 8).toByte()
            saltWithBlockIndex[salt.size + 3] = blockIndex.toByte()

            var u = mac.doFinal(saltWithBlockIndex)
            val t = u.copyOf()

            repeat(iterations - 1) {
                u = mac.doFinal(u)
                for (i in t.indices) {
                    t[i] = (t[i].toInt() xor u[i].toInt()).toByte()
                }
            }

            System.arraycopy(t, 0, derived, (blockIndex - 1) * hLen, hLen)
        }

        return derived.copyOf(derivedKeyLengthBytes)
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun buildDefaultKeyMaterial(): String {
        val extensionIdEquivalent = context.packageName.takeIf { it.isNotBlank() } ?: "azkura-auth"
        val androidInfo = buildString {
            append("Android/")
            append(Build.VERSION.RELEASE ?: "unknown")
            append(";")
            append(Build.MANUFACTURER ?: "unknown")
            append(";")
            append(Build.MODEL ?: "unknown")
        }

        val metrics = context.resources.displayMetrics
        val width = max(metrics.widthPixels, metrics.heightPixels)
        val height = min(metrics.widthPixels, metrics.heightPixels)
        val screenInfo = "${width}x${height}"

        return "$extensionIdEquivalent:$androidInfo:$screenInfo:azkura-default-key-v1"
    }

    companion object {
        const val SALT_SIZE_BYTES = 16
        const val IV_SIZE_BYTES = 12
        const val GCM_TAG_SIZE_BYTES = 16
        const val GCM_TAG_SIZE_BITS = GCM_TAG_SIZE_BYTES * 8
        const val AES_KEY_SIZE_BYTES = 32
        const val VAULT_KEY_ITERATIONS = 310_000
        const val PIN_HASH_ITERATIONS = 100_000

        private const val AES_ALGORITHM = "AES"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val HMAC_SHA256 = "HmacSHA256"
        private const val SHA256_SIZE_BYTES = 32
    }
}

data class PinData(
    val hash: String,
    val salt: String,
)
