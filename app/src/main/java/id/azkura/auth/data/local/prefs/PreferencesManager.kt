package id.azkura.auth.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "azkura_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securePrefs: SecurePreferencesManager,
) {
    private val dataStore = context.dataStore

    // ── Keys ─────────────────────────────────────────────────────────────────

    private object Keys {
        val PIN_ENABLED = booleanPreferencesKey("pin_enabled")
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_SALT = stringPreferencesKey("pin_salt")
        val BIOMETRIC_ENABLED = booleanPreferencesKey("biometric_enabled")
        val AUTO_LOCK_MINUTES = intPreferencesKey("auto_lock_minutes")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val LAST_BACKUP_AT = stringPreferencesKey("last_backup_at")
        val GOOGLE_USER_NAME = stringPreferencesKey("google_user_name")
        val GOOGLE_USER_EMAIL = stringPreferencesKey("google_user_email")
        val GOOGLE_USER_PICTURE = stringPreferencesKey("google_user_picture")
        val ENCRYPT_BACKUP = booleanPreferencesKey("encrypt_backup")
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    // ── Reads (Flow-based) ───────────────────────────────────────────────────

    val pinEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.PIN_ENABLED] ?: false }
    val pinHash: Flow<String?> = dataStore.data.map { it[Keys.PIN_HASH] }
    val pinSalt: Flow<String?> = dataStore.data.map { it[Keys.PIN_SALT] }
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }
    val autoLockMinutes: Flow<Int> = dataStore.data.map { it[Keys.AUTO_LOCK_MINUTES] ?: 5 }
    val sortOrder: Flow<SortOrder> = dataStore.data.map { preferences ->
        SortOrder.fromStoredValue(preferences[Keys.SORT_ORDER])
    }
    val lastBackupAt: Flow<String?> = dataStore.data.map { it[Keys.LAST_BACKUP_AT] }
    val googleUserName: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_USER_NAME] }
    val googleUserEmail: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_USER_EMAIL] }
    val googleUserPicture: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_USER_PICTURE] }
    val isFirstLaunch: Flow<Boolean> = dataStore.data.map { it[Keys.FIRST_LAUNCH] ?: true }

    // Default to true — backups should be encrypted by default (Issue #2)
    val encryptBackup: Flow<Boolean> = dataStore.data.map { it[Keys.ENCRYPT_BACKUP] ?: true }

    // Sensitive values now read from EncryptedSharedPreferences
    val googleAccessToken: Flow<String?> = dataStore.data.map { securePrefs.getGoogleAccessToken() }
    val googleAuthTokenTime: Flow<Long?> = dataStore.data.map {
        val t = securePrefs.getGoogleAuthTokenTime()
        if (t == 0L) null else t
    }
    val backupPassword: Flow<String?> = dataStore.data.map { securePrefs.getBackupPassword() }

    suspend fun setEncryptBackup(enabled: Boolean) {
        dataStore.edit { it[Keys.ENCRYPT_BACKUP] = enabled }
    }

    suspend fun setBackupPassword(password: String?) {
        securePrefs.setBackupPassword(password)
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    suspend fun setPinEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PIN_ENABLED] = enabled }
    }

    suspend fun setPinCredentials(hash: String, salt: String) {
        dataStore.edit {
            it[Keys.PIN_HASH] = hash
            it[Keys.PIN_SALT] = salt
            it[Keys.PIN_ENABLED] = true
        }
    }

    suspend fun clearPin() {
        dataStore.edit {
            it.remove(Keys.PIN_HASH)
            it.remove(Keys.PIN_SALT)
            it[Keys.PIN_ENABLED] = false
        }
    }

    suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.BIOMETRIC_ENABLED] = enabled }
    }

    suspend fun setAutoLockMinutes(minutes: Int) {
        dataStore.edit { it[Keys.AUTO_LOCK_MINUTES] = minutes }
    }

    suspend fun setSortOrder(order: SortOrder) {
        dataStore.edit { it[Keys.SORT_ORDER] = order.storedValue }
    }

    suspend fun setSortOrder(order: String) {
        setSortOrder(SortOrder.fromStoredValue(order))
    }

    suspend fun setLastBackupAt(timestamp: String) {
        dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    }

    suspend fun setGoogleUser(name: String, email: String, picture: String?) {
        dataStore.edit {
            it[Keys.GOOGLE_USER_NAME] = name
            it[Keys.GOOGLE_USER_EMAIL] = email
            if (picture.isNullOrBlank()) {
                it.remove(Keys.GOOGLE_USER_PICTURE)
            } else {
                it[Keys.GOOGLE_USER_PICTURE] = picture
            }
        }
    }

    suspend fun setGoogleAuthSession(
        name: String,
        email: String,
        picture: String?,
        accessToken: String,
        tokenTimeMillis: Long,
    ) {
        dataStore.edit {
            it[Keys.GOOGLE_USER_NAME] = name
            it[Keys.GOOGLE_USER_EMAIL] = email
            if (picture.isNullOrBlank()) {
                it.remove(Keys.GOOGLE_USER_PICTURE)
            } else {
                it[Keys.GOOGLE_USER_PICTURE] = picture
            }
        }
        // Store sensitive token in encrypted storage
        securePrefs.setGoogleAuthToken(accessToken, tokenTimeMillis)
    }

    suspend fun clearGoogleAccessToken() {
        securePrefs.clearGoogleAccessToken()
    }

    suspend fun clearGoogleUser() {
        dataStore.edit {
            it.remove(Keys.GOOGLE_USER_NAME)
            it.remove(Keys.GOOGLE_USER_EMAIL)
            it.remove(Keys.GOOGLE_USER_PICTURE)
        }
        securePrefs.clearGoogleAccessToken()
    }

    suspend fun setFirstLaunchDone() {
        dataStore.edit { it[Keys.FIRST_LAUNCH] = false }
    }
}
