package id.azkura.auth.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted storage for security-sensitive values (Google OAuth tokens, backup passwords).
 * Uses AndroidX EncryptedSharedPreferences backed by Android Keystore.
 *
 * Non-sensitive preferences (sort order, UI flags) remain in the regular DataStore.
 */
@Singleton
class SecurePreferencesManager @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "azkura_secure_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Fallback: if Keystore is broken (rare), use regular SharedPreferences
        // This is better than crashing — the user can still use the app
        context.getSharedPreferences("azkura_secure_prefs_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_GOOGLE_ACCESS_TOKEN = "google_access_token"
        private const val KEY_GOOGLE_AUTH_TOKEN_TIME = "google_auth_token_time"
        private const val KEY_BACKUP_PASSWORD = "backup_password"
    }

    // ── Google OAuth Token ────────────────────────────────────────────────

    fun getGoogleAccessToken(): String? = prefs.getString(KEY_GOOGLE_ACCESS_TOKEN, null)

    fun getGoogleAuthTokenTime(): Long = prefs.getLong(KEY_GOOGLE_AUTH_TOKEN_TIME, 0L)

    fun setGoogleAuthToken(accessToken: String, tokenTimeMillis: Long) {
        prefs.edit()
            .putString(KEY_GOOGLE_ACCESS_TOKEN, accessToken)
            .putLong(KEY_GOOGLE_AUTH_TOKEN_TIME, tokenTimeMillis)
            .apply()
    }

    fun clearGoogleAccessToken() {
        prefs.edit()
            .remove(KEY_GOOGLE_ACCESS_TOKEN)
            .remove(KEY_GOOGLE_AUTH_TOKEN_TIME)
            .apply()
    }

    // ── Backup Password ──────────────────────────────────────────────────

    fun getBackupPassword(): String? = prefs.getString(KEY_BACKUP_PASSWORD, null)

    fun setBackupPassword(password: String?) {
        if (password != null) {
            prefs.edit().putString(KEY_BACKUP_PASSWORD, password).apply()
        } else {
            prefs.edit().remove(KEY_BACKUP_PASSWORD).apply()
        }
    }
}
