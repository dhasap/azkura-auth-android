package id.azkura.auth.util

import android.net.Uri

/**
 * Parse otpauth:// URIs.
 * Port of src/core/uri-parser.js.
 *
 * Format: otpauth://totp/ISSUER:ACCOUNT?secret=BASE32&issuer=ISSUER&algorithm=SHA1&digits=6&period=30
 */
object UriParser {

    data class ParsedAccount(
        val type: String = "totp",
        val issuer: String = "",
        val account: String = "",
        val secret: String = "",
        val algorithm: String = "SHA1",
        val digits: Int = 6,
        val period: Int = 30,
    )

    /**
     * Parse an otpauth:// URI into a [ParsedAccount].
     * @throws IllegalArgumentException if the URI is invalid.
     */
    fun parse(uri: String): ParsedAccount {
        val trimmed = uri.trim()
        require(trimmed.startsWith("otpauth://", ignoreCase = true)) { "Invalid URI: must start with otpauth://" }

        val parsed = Uri.parse(trimmed)

        // Type: host part (totp or hotp)
        var type = parsed.host?.lowercase() ?: ""
        if (type.isEmpty()) {
            val segments = parsed.pathSegments
            if (segments.isNotEmpty()) {
                val candidate = segments[0].lowercase()
                if (candidate == "totp" || candidate == "hotp") type = candidate
            }
            if (type.isEmpty()) type = "totp"
        }
        require(type == "totp" || type == "hotp") { "Unsupported OTP type: $type" }

        // Label: path after the type, decode "Issuer:Account"
        val rawPath = parsed.path?.removePrefix("/") ?: ""
        val labelRaw = Uri.decode(rawPath)

        var labelIssuer = ""
        var account = labelRaw
        if (labelRaw.contains(":")) {
            val colonIndex = labelRaw.indexOf(':')
            labelIssuer = labelRaw.substring(0, colonIndex).trim()
            account = labelRaw.substring(colonIndex + 1).trim()
        }

        // Query parameters
        val secret = parsed.getQueryParameter("secret")?.replace("\\s".toRegex(), "")?.uppercase()
            ?: throw IllegalArgumentException("Missing required parameter: secret")
        val queryIssuer = parsed.getQueryParameter("issuer")?.trim() ?: ""
        // Hierarchy of truth for otpauth identity:
        // 1. The explicit query parameter is the most reliable source.
        // 2. If it is missing, use the label prefix before ':' (Issuer:Account).
        // 3. Never promote an account email domain (for example gmail.com) into an issuer.
        val issuer = when {
            queryIssuer.isNotEmpty() -> queryIssuer
            labelIssuer.isNotEmpty() -> labelIssuer
            else -> ""
        }

        val algorithm = parsed.getQueryParameter("algorithm")?.uppercase() ?: "SHA1"
        val digits = parsed.getQueryParameter("digits")?.toIntOrNull() ?: 6
        val period = parsed.getQueryParameter("period")?.toIntOrNull() ?: 30

        require(digits in 1..10) { "Digits must be between 1 and 10" }
        require(period in 1..300) { "Period must be between 1 and 300" }

        return ParsedAccount(
            type = type,
            issuer = issuer,
            account = account,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period,
        )
    }

    /** Build an otpauth:// URI from account data. */
    fun buildUri(
        issuer: String,
        account: String,
        secret: String,
        algorithm: String = "SHA1",
        digits: Int = 6,
        period: Int = 30,
    ): String {
        val label = if (issuer.isNotEmpty()) "$issuer:$account" else account
        return Uri.Builder()
            .scheme("otpauth")
            .authority("totp")
            .appendPath(label)
            .appendQueryParameter("secret", secret)
            .appendQueryParameter("issuer", issuer)
            .appendQueryParameter("algorithm", algorithm)
            .appendQueryParameter("digits", digits.toString())
            .appendQueryParameter("period", period.toString())
            .build()
            .toString()
    }
}
