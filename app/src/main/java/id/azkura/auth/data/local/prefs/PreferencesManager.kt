package id.azkura.auth.data.local.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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
        val FIRST_LAUNCH = booleanPreferencesKey("first_launch")
    }

    // ── Reads (Flow-based) ───────────────────────────────────────────────────

    val pinEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.PIN_ENABLED] ?: false }
    val pinHash: Flow<String?> = dataStore.data.map { it[Keys.PIN_HASH] }
    val pinSalt: Flow<String?> = dataStore.data.map { it[Keys.PIN_SALT] }
    val biometricEnabled: Flow<Boolean> = dataStore.data.map { it[Keys.BIOMETRIC_ENABLED] ?: false }
    val autoLockMinutes: Flow<Int> = dataStore.data.map { it[Keys.AUTO_LOCK_MINUTES] ?: 5 }
    val sortOrder: Flow<String> = dataStore.data.map { it[Keys.SORT_ORDER] ?: "custom" }
    val lastBackupAt: Flow<String?> = dataStore.data.map { it[Keys.LAST_BACKUP_AT] }
    val googleUserName: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_USER_NAME] }
    val googleUserEmail: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_USER_EMAIL] }
    val googleUserPicture: Flow<String?> = dataStore.data.map { it[Keys.GOOGLE_USER_PICTURE] }
    val isFirstLaunch: Flow<Boolean> = dataStore.data.map { it[Keys.FIRST_LAUNCH] ?: true }

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

    suspend fun setSortOrder(order: String) {
        dataStore.edit { it[Keys.SORT_ORDER] = order }
    }

    suspend fun setLastBackupAt(timestamp: String) {
        dataStore.edit { it[Keys.LAST_BACKUP_AT] = timestamp }
    }

    suspend fun setGoogleUser(name: String, email: String, picture: String) {
        dataStore.edit {
            it[Keys.GOOGLE_USER_NAME] = name
            it[Keys.GOOGLE_USER_EMAIL] = email
            it[Keys.GOOGLE_USER_PICTURE] = picture
        }
    }

    suspend fun clearGoogleUser() {
        dataStore.edit {
            it.remove(Keys.GOOGLE_USER_NAME)
            it.remove(Keys.GOOGLE_USER_EMAIL)
            it.remove(Keys.GOOGLE_USER_PICTURE)
        }
    }

    suspend fun setFirstLaunchDone() {
        dataStore.edit { it[Keys.FIRST_LAUNCH] = false }
    }
}
