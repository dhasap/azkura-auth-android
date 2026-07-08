package id.azkura.auth.data.remote

import android.app.Activity
import id.azkura.auth.data.local.prefs.PreferencesManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive implementation of [CloudSyncProvider].
 *
 * Delegates OAuth to [GoogleAuthService] and backup/restore operations to
 * [GoogleDriveService]. This class adds no new logic — it adapts the
 * existing services to the provider interface.
 *
 * To add a new cloud provider:
 *  1. Create a class implementing [CloudSyncProvider].
 *  2. Register it in [CloudSyncProviderModule].
 *  3. No changes needed in SettingsViewModel or SettingsScreen.
 */
@Singleton
class GoogleDriveSyncProvider @Inject constructor(
    private val googleAuthService: GoogleAuthService,
    private val googleDriveService: GoogleDriveService,
    private val preferencesManager: PreferencesManager,
) : CloudSyncProvider {

    override val id: String = PROVIDER_ID
    override val displayName: String = "Google Drive"
    override val isAvailable: Boolean = true

    override suspend fun isSignedIn(): Boolean = googleAuthService.isSignedIn()

    override suspend fun connect(activity: Activity): ConnectResult {
        val outcome = googleAuthService.signIn(activity)
        return when (outcome) {
            is GoogleAuthorizationOutcome.Authorized -> {
                ConnectResult.Connected(
                    accountInfo = SyncAccountInfo(
                        name = outcome.session.user.name,
                        email = outcome.session.user.email,
                        picture = outcome.session.user.picture,
                    ),
                )
            }
            is GoogleAuthorizationOutcome.NeedsResolution -> {
                ConnectResult.NeedsConsent(outcome.pendingIntent)
            }
        }
    }

    override suspend fun disconnect() {
        googleAuthService.signOut()
    }

    override suspend fun getAccountInfo(): SyncAccountInfo? {
        if (!isSignedIn()) return null
        return SyncAccountInfo(
            name = preferencesManager.googleUserName.first().orEmpty(),
            email = preferencesManager.googleUserEmail.first().orEmpty(),
            picture = preferencesManager.googleUserPicture.first(),
        )
    }

    override suspend fun backup(payload: BackupPayload): BackupResult {
        val token = getAccessToken()
            ?: throw GoogleDriveAuthException("Google session expired. Please sign in again.")
        val result = googleDriveService.backupDetailed(token)
        return BackupResult(
            fileId = result.fileId,
            fileName = result.fileName,
            accountCount = result.accountCount,
            folderCount = result.folderCount,
        )
    }

    override suspend fun restore(): RestoreResult {
        val token = getAccessToken()
            ?: throw GoogleDriveAuthException("Google session expired. Please sign in again.")
        val result = googleDriveService.restoreLatest(token)
        return RestoreResult(
            fileName = result.fileName,
            importedAccounts = result.importedAccounts,
            totalAccounts = result.totalAccounts,
            importedFolders = result.importedFolders,
        )
    }

    override suspend fun listBackups(maxResults: Int): List<BackupMetadata> {
        val token = getAccessToken()
            ?: throw GoogleDriveAuthException("Google session expired. Please sign in again.")
        return googleDriveService.listBackups(token, maxResults).map { file ->
            BackupMetadata(
                fileId = file.id,
                fileName = file.name,
                createdAt = file.createdTime,
                sizeBytes = file.size?.toLongOrNull(),
            )
        }
    }

    override suspend fun getAccessToken(): String? {
        return googleAuthService.getStoredAccessTokenIfFresh()
    }

    override suspend fun clearInvalidToken() {
        googleAuthService.clearInvalidToken()
    }

    companion object {
        const val PROVIDER_ID = "google-drive"
    }
}
