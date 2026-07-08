package id.azkura.auth

import android.app.Application
import android.util.Log
import dagger.hilt.android.HiltAndroidApp
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "AzkuraAuthApp"

@HiltAndroidApp
class AzkuraAuthApp : Application() {

    @Inject lateinit var accountRepository: AccountRepository
    @Inject lateinit var preferencesManager: PreferencesManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // Migrate any plaintext TOTP secrets to encrypted form (one-time, idempotent)
        appScope.launch {
            try {
                accountRepository.migrateSecretsToEncrypted()
            } catch (_: Exception) {
                // Non-fatal — secrets will be encrypted on next write
            }
        }

        // Purge expired Google OAuth tokens on cold start.
        // If the app was killed while a token was valid, it may have expired
        // while the app was in the background. A stale token left in storage
        // could cause silent failures on the next Drive backup/restore attempt.
        appScope.launch {
            try {
                val tokenTime = preferencesManager.googleAuthTokenTime.first()
                if (tokenTime != null) {
                    val elapsed = System.currentTimeMillis() - tokenTime
                    if (elapsed >= TOKEN_VALIDITY_MS) {
                        Log.i(TAG, "Purging expired Google token (age: ${elapsed / 1000}s)")
                        preferencesManager.clearGoogleAccessToken()
                    }
                }
            } catch (_: Exception) {
                // Non-fatal — token will be cleared on next failed API call
            }
        }
    }

    companion object {
        /** Token validity window matching GoogleAuthService (55 minutes). */
        private const val TOKEN_VALIDITY_MS = 55L * 60L * 1000L
    }
}
