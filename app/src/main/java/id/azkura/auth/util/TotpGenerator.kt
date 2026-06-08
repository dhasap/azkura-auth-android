package id.azkura.auth.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

/**
 * RFC 6238 TOTP generator.
 * Port of src/core/totp.js — supports SHA1, SHA256, SHA512.
 */
object TotpGenerator {

    enum class Algorithm(val hmacName: String) {
        SHA1("HmacSHA1"),
        SHA256("HmacSHA256"),
        SHA512("HmacSHA512");

        companion object {
            fun from(name: String): Algorithm = when (name.uppercase().replace("-", "")) {
                "SHA1" -> SHA1
                "SHA256" -> SHA256
                "SHA512" -> SHA512
                else -> SHA1
            }
        }
    }

    /**
     * Generate a TOTP code.
     *
     * @param secretBase32 Base32-encoded secret (spaces allowed, case-insensitive)
     * @param digits       Number of digits (default 6)
     * @param period       Time step in seconds (default 30)
     * @param algorithm    Hash algorithm (SHA1, SHA256, SHA512)
     * @param timeMillis   Current time in millis (defaults to System.currentTimeMillis())
     * @return Zero-padded OTP string
     */
    fun generate(
        secretBase32: String,
        digits: Int = 6,
        period: Int = 30,
        algorithm: Algorithm = Algorithm.SHA1,
        timeMillis: Long = System.currentTimeMillis(),
    ): String {
        val cleanSecret = secretBase32.replace("\\s".toRegex(), "").uppercase()
        require(isValidBase32(cleanSecret)) { "Invalid Base32 secret key" }

        val key = Base32.decode(cleanSecret)
        val counter = timeMillis / 1000 / period
        val counterBytes = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (value and 0xFF).toByte()
            value = value shr 8
        }

        val mac = Mac.getInstance(algorithm.hmacName)
        mac.init(SecretKeySpec(key, algorithm.hmacName))
        val hash = mac.doFinal(counterBytes)

        val offset = hash[hash.size - 1].toInt() and 0x0F
        val binary = ((hash[offset].toInt() and 0x7F) shl 24) or
                ((hash[offset + 1].toInt() and 0xFF) shl 16) or
                ((hash[offset + 2].toInt() and 0xFF) shl 8) or
                (hash[offset + 3].toInt() and 0xFF)

        val otp = binary % 10.0.pow(digits).toInt()
        return otp.toString().padStart(digits, '0')
    }

    /**
     * Verify a TOTP code (with time window tolerance).
     */
    fun verify(
        token: String,
        secretBase32: String,
        digits: Int = 6,
        period: Int = 30,
        algorithm: Algorithm = Algorithm.SHA1,
        window: Int = 1,
        timeMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        for (i in -window..window) {
            val adjustedTime = timeMillis + (i * period * 1000L)
            val generated = generate(secretBase32, digits, period, algorithm, adjustedTime)
            if (generated == token) return true
        }
        return false
    }

    /**
     * Seconds remaining until the current code expires.
     */
    fun getRemainingSeconds(period: Int = 30, timeMillis: Long = System.currentTimeMillis()): Int {
        val elapsed = (timeMillis / 1000) % period
        return (period - elapsed).toInt()
    }

    private fun isValidBase32(secret: String): Boolean {
        if (secret.isEmpty()) return false
        return secret.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567=" }
    }
}

/** Minimal Base32 (RFC 4648) decoder. */
object Base32 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun decode(input: String): ByteArray {
        val clean = input.trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)

        val output = ByteArray(clean.length * 5 / 8)
        var buffer = 0
        var bitsLeft = 0
        var index = 0

        for (c in clean) {
            val value = ALPHABET.indexOf(c)
            if (value < 0) throw IllegalArgumentException("Invalid Base32 character: $c")
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                output[index++] = (buffer shr bitsLeft and 0xFF).toByte()
            }
        }
        return output.copyOf(index)
    }
}
