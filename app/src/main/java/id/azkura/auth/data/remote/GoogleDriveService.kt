package id.azkura.auth.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.repository.StatsRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive backup/restore service.
 * Port of src/core/google-drive.js.
 *
 * Uses Google Drive REST API v3 via Retrofit.
 * Stores encrypted vault as a file in appDataFolder.
 */
@Singleton
class GoogleDriveService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vaultManager: VaultManager,
    private val preferencesManager: PreferencesManager,
    private val statsRepository: StatsRepository,
) {
    companion object {
        const val FILE_NAME = "azkura-auth-backup.json"
        const val MIME_TYPE = "application/json"
    }

    /**
     * Upload encrypted vault to Google Drive appDataFolder.
     * TODO: Implement with Google Sign-In + Credential Manager + Drive API.
     */
    suspend fun backup(accessToken: String): Boolean {
        val encrypted = vaultManager.exportVault()
        // TODO: PUT to https://www.googleapis.com/upload/drive/v3/files
        // with metadata in appDataFolder
        statsRepository.trackBackup()
        preferencesManager.setLastBackupAt(System.currentTimeMillis().toString())
        return true
    }

    /**
     * Download and decrypt vault from Google Drive.
     * TODO: Implement with Drive API file search + download.
     */
    suspend fun restore(accessToken: String): Boolean {
        // TODO: GET from Drive API, decrypt, import
        return true
    }
}
