package id.azkura.auth

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AzkuraAuthApp : Application() {

    @Inject lateinit var accountRepository: AccountRepository

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
    }
}
