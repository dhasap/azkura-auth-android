package id.azkura.auth.data.remote

import android.app.Activity
import android.app.PendingIntent

/**
 * Common interface for cloud sync providers (Google Drive, OneDrive, Dropbox, etc.).
 *
 * Every provider must implement this interface so that:
 *  - The Settings UI can list and interact with providers generically.
 *  - New providers are added by creating a class + registering in DI,
 *    without touching existing code (Open/Closed Principle).
 *  - Backup/restore logic is decoupled from the UI layer.
 *
 * Design notes:
 *  - [connect] returns a sealed [ConnectResult] because some OAuth flows
 *    require user consent via a PendingIntent that must be launched from UI.
 *  - Backup payloads use [BackupPayload] so providers don't depend on
 *    internal data model types.
 */
interface CloudSyncProvider {

    /** Machine-readable identifier (e.g. "google-drive", "onedrive"). */
    val id: String

    /** Human-readable display name shown in the UI. */
    val displayName: String

    /** Whether this provider is currently configured and available. */
    val isAvailable: Boolean

    // ── Authentication ──────────────────────────────────────────────────────

    /** Whether the user is currently signed in with a valid session. */
    suspend fun isSignedIn(): Boolean

    /**
     * Initiate sign-in / connect flow.
     *
     * Returns [ConnectResult.Connected] if sign-in succeeds immediately,
     * or [ConnectResult.NeedsConsent] if the user must complete a consent
     * screen (caller must launch the PendingIntent from UI).
     */
    suspend fun connect(activity: Activity): ConnectResult

    /** Sign out and clear stored session data. */
    suspend fun disconnect()

    /** Return the current user profile, or null if not signed in. */
    suspend fun getAccountInfo(): SyncAccountInfo?

    // ── Backup / Restore ────────────────────────────────────────────────────

    /** Upload data to the cloud. Returns metadata about the created backup. */
    suspend fun backup(payload: BackupPayload): BackupResult

    /** Download the latest backup and merge it into the local vault. */
    suspend fun restore(): RestoreResult

    /** List recent backups (newest first). */
    suspend fun listBackups(maxResults: Int = 10): List<BackupMetadata>

    // ── Token management ────────────────────────────────────────────────────

    /**
     * Get a valid access token, refreshing if needed.
     * Returns null if re-authentication is required.
     */
    suspend fun getAccessToken(): String?

    /** Clear an invalid/expired token without full sign-out. */
    suspend fun clearInvalidToken()
}

// ── Data classes ─────────────────────────────────────────────────────────────

/** Result of a [CloudSyncProvider.connect] call. */
sealed class ConnectResult {
    /** Sign-in succeeded immediately — no consent needed. */
    data class Connected(val accountInfo: SyncAccountInfo) : ConnectResult()

    /** User consent required — launch this PendingIntent from the UI. */
    data class NeedsConsent(val pendingIntent: PendingIntent) : ConnectResult()
}

/** Minimal user profile returned by cloud providers. */
data class SyncAccountInfo(
    val name: String,
    val email: String,
    val picture: String?,
    /** Provider-specific extra data (e.g. Google account type). */
    val extras: Map<String, String> = emptyMap(),
)

/** Data payload to back up. Provider-agnostic; the provider serializes it. */
data class BackupPayload(
    val accountsJson: String,
    val foldersJson: String,
    val accountCount: Int,
    val folderCount: Int,
    val versionName: String,
)

/** Metadata about a successfully created backup. */
data class BackupResult(
    val fileId: String,
    val fileName: String,
    val accountCount: Int,
    val folderCount: Int,
)

/** Result of a restore operation. */
data class RestoreResult(
    val fileName: String,
    val importedAccounts: Int,
    val totalAccounts: Int,
    val importedFolders: Int,
)

/** Metadata for listing existing backups. */
data class BackupMetadata(
    val fileId: String,
    val fileName: String,
    val createdAt: String?,
    val sizeBytes: Long?,
)
