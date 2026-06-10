package id.azkura.auth.data.remote

import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.local.crypto.CryptoManager
import kotlinx.coroutines.flow.first

import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.data.repository.StatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Google Drive backup/restore service.
 *
 * The browser extension stores Drive backups as plain JSON files with this shape:
 * `{ app, version, exportedAt, accountCount, folderCount, accounts, folders }`.
 * Android writes and reads the same shape so backups are cross-platform. Local
 * vault encryption remains handled by the vault layer; Drive API access is scoped
 * with `drive.file`, so the app can see files it created/opened.
 */
@Singleton
class GoogleDriveService @Inject constructor(
    private val accountRepository: AccountRepository,
    private val preferencesManager: PreferencesManager,
    private val cryptoManager: CryptoManager,
    private val statsRepository: StatsRepository,
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /** Upload the current vault to Google Drive. */
    suspend fun backup(accessToken: String): Boolean {
        backupDetailed(accessToken)
        return true
    }

    /** Upload the current vault and return the created Drive file metadata. */
    suspend fun backupDetailed(accessToken: String): GoogleDriveBackupResult {
        require(accessToken.isNotBlank()) { "Google access token is missing" }

        val accounts = accountRepository.getAllAccounts()
        val folders = accountRepository.getAllFolders()
        val now = Instant.now()
        val fileName = "azkura-backup-${FILE_TIMESTAMP_FORMAT.format(now)}.json"
        val backupData = DriveBackupData(
            app = APP_NAME,
            version = APP_VERSION,
            exportedAt = now.toString(),
            accountCount = accounts.size,
            folderCount = folders.size,
            accounts = accounts,
            folders = folders,
        )
        val fileContent = BACKUP_JSON.encodeToString(backupData)
        
        val encryptBackup = preferencesManager.encryptBackup.first()
        val backupPassword = preferencesManager.backupPassword.first()
        val finalContent = if (encryptBackup && backupPassword != null) {
            cryptoManager.encrypt(fileContent, backupPassword)
        } else {
            fileContent
        }


        val metadata = DriveFileMetadata(
            name = fileName,
            mimeType = MIME_TYPE,
            description = "Azkura Auth Backup - TOTP Authenticator Data",
        )
        val request = Request.Builder()
            .url(DRIVE_UPLOAD_URL)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .post(createMultipartRelatedBody(json.encodeToString(metadata), finalContent))
            .build()

        val upload = executeJson<DriveUploadResponse>(request)
        val timestamp = System.currentTimeMillis().toString()
        statsRepository.trackBackup()
        preferencesManager.setLastBackupAt(timestamp)

        return GoogleDriveBackupResult(
            fileId = upload.id,
            fileName = upload.name.ifBlank { fileName },
            accountCount = accounts.size,
            folderCount = folders.size,
        )
    }

    /** Download the newest Google Drive backup and merge it into the local vault. */
    suspend fun restore(accessToken: String): Boolean {
        restoreLatest(accessToken)
        return true
    }

    /** Download the newest Google Drive backup and return import counts. */
    suspend fun restoreLatest(accessToken: String): GoogleDriveRestoreResult {
        val latest = listBackups(accessToken, maxResults = 1).firstOrNull()
            ?: throw IOException("No Azkura Auth backup found in Google Drive")
        return restoreFile(accessToken, latest)
    }

    /** List Drive backup files newest-first. */
    suspend fun listBackups(accessToken: String, maxResults: Int = 10): List<GoogleDriveBackupFile> {
        require(accessToken.isNotBlank()) { "Google access token is missing" }

        val url = DRIVE_FILES_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "name contains 'azkura-backup' and mimeType = 'application/json' and trashed = false")
            .addQueryParameter("pageSize", maxResults.coerceIn(1, 100).toString())
            .addQueryParameter("orderBy", "createdTime desc")
            .addQueryParameter("fields", "files(id,name,createdTime,size)")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .header("Accept", "application/json")
            .get()
            .build()

        return executeJson<DriveFilesResponse>(request).files
    }

    /** Download and restore a specific backup file. */
    suspend fun restoreFile(accessToken: String, file: GoogleDriveBackupFile): GoogleDriveRestoreResult {
        require(accessToken.isNotBlank()) { "Google access token is missing" }

        val url = "$DRIVE_FILES_URL/${file.id}".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media")
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()

        var content = executeLimitedText(request, MAX_BACKUP_BYTES)
        
        val backupPassword = preferencesManager.backupPassword.first()
        if (!content.trim().startsWith("{")) {
            // Probably encrypted
            if (backupPassword == null) {
                throw IOException("Backup is encrypted but no Master Password set. Please set Backup Password in Settings first.")
            }
            try {
                content = cryptoManager.decrypt(content, backupPassword)
            } catch (e: Exception) {
                throw IOException("Failed to decrypt backup. Incorrect password?")
            }
        }
        
        val backup = decodeBackup(content)
        validateBackup(backup)
        val folderMerge = mergeFolders(backup.folders)
        val importedAccounts = mergeAccounts(backup.accounts, folderMerge.idMap)
        val importedFolders = folderMerge.importedCount

        return GoogleDriveRestoreResult(
            fileId = file.id,
            fileName = file.name,
            importedAccounts = importedAccounts,
            totalAccounts = accountRepository.getAccountCount(),
            importedFolders = importedFolders,
        )
    }

    private fun decodeBackup(content: String): DriveBackupData {
        val element = try {
            json.parseToJsonElement(content)
        } catch (error: Exception) {
            throw IOException("Invalid backup file: not valid JSON", error)
        }
        val obj = element as? JsonObject
            ?: throw IOException("Invalid backup file format")

        if (obj["app"]?.jsonPrimitive?.contentOrNull != APP_NAME) {
            throw IOException("Invalid backup file: not an Azkura Auth backup")
        }
        if (obj["accounts"] !is JsonArray) {
            throw IOException("Invalid backup: no accounts data found")
        }

        val backup = try {
            json.decodeFromJsonElement(DriveBackupData.serializer(), obj)
        } catch (error: Exception) {
            throw IOException("Invalid backup file format", error)
        }

        if (backup.accounts.isEmpty() && backup.accountCount > 0) {
            throw IOException("Invalid backup: account data is missing")
        }
        return backup
    }

    private fun validateBackup(backup: DriveBackupData) {
        if (backup.accountCount != backup.accounts.size) {
            throw IOException("Invalid backup: account count does not match")
        }
        if (backup.folderCount != backup.folders.size) {
            throw IOException("Invalid backup: folder count does not match")
        }
        if (backup.accounts.size > MAX_ACCOUNTS) {
            throw IOException("Invalid backup: too many accounts")
        }
        if (backup.folders.size > MAX_FOLDERS) {
            throw IOException("Invalid backup: too many folders")
        }

        val folderIds = mutableSetOf<String>()
        backup.folders.forEach { folder ->
            requireBackup(folder.id.isNotBlank(), "folder id is missing")
            requireBackup(folder.name.isNotBlank(), "folder name is missing")
            requireBackup(folder.id.length <= MAX_ID_LENGTH, "folder id is too long")
            requireBackup(folder.name.length <= MAX_NAME_LENGTH, "folder name is too long")
            requireBackup(folderIds.add(folder.id), "duplicate folder id")
        }

        val accountIds = mutableSetOf<String>()
        backup.accounts.forEach { account ->
            requireBackup(account.id.isNotBlank(), "account id is missing")
            requireBackup(account.issuer.isNotBlank(), "issuer is missing")
            requireBackup(account.account.isNotBlank(), "account name is missing")
            requireBackup(account.secret.isNotBlank(), "secret is missing")
            requireBackup(account.id.length <= MAX_ID_LENGTH, "account id is too long")
            requireBackup(account.issuer.length <= MAX_NAME_LENGTH, "issuer is too long")
            requireBackup(account.account.length <= MAX_NAME_LENGTH, "account name is too long")
            requireBackup(account.secret.length <= MAX_SECRET_LENGTH, "secret is too long")
            requireBackup(account.algorithm.uppercase() in SUPPORTED_ALGORITHMS, "unsupported algorithm")
            requireBackup(account.digits in 6..8, "unsupported digit count")
            requireBackup(account.period in 15..120, "unsupported period")
            requireBackup(account.secret.isBase32Secret(), "secret is not valid Base32")
            requireBackup(accountIds.add(account.id), "duplicate account id")
        }
    }

    private suspend fun mergeAccounts(importedAccounts: List<Account>, folderIdMap: Map<String, String?>): Int {
        if (importedAccounts.isEmpty()) return 0

        val existing = accountRepository.getAllAccounts()
        val existingFolders = accountRepository.getAllFolders().map { it.id }.toSet()
        val existingKeys = existing.map { duplicateKey(it) }.toMutableSet()
        val newAccounts = importedAccounts
            .filter { imported -> existingKeys.add(duplicateKey(imported)) }
            .map { imported ->
                val mappedFolderId = imported.folderId?.let { originalFolderId ->
                    folderIdMap[originalFolderId] ?: originalFolderId.takeIf { it in existingFolders }
                }
                imported.copy(id = generateId(), folderId = mappedFolderId)
            }

        accountRepository.insertAccounts(newAccounts)
        return newAccounts.size
    }

    private suspend fun mergeFolders(importedFolders: List<Folder>): FolderMergeResult {
        if (importedFolders.isEmpty()) return FolderMergeResult(emptyMap(), 0)

        val existing = accountRepository.getAllFolders()
        val existingById = existing.associateBy { it.id }
        val usedIds = existing.map { it.id }.toMutableSet()
        val idMap = mutableMapOf<String, String?>()
        val newFolders = importedFolders.mapNotNull { folder ->
            val existingFolder = existingById[folder.id]
            when {
                existingFolder == null -> {
                    usedIds.add(folder.id)
                    idMap[folder.id] = folder.id
                    folder
                }
                existingFolder.name == folder.name && existingFolder.color.equals(folder.color, ignoreCase = true) -> {
                    idMap[folder.id] = existingFolder.id
                    null
                }
                else -> {
                    val newId = generateFolderId(usedIds)
                    usedIds.add(newId)
                    idMap[folder.id] = newId
                    folder.copy(id = newId)
                }
            }
        }

        accountRepository.insertFolders(newFolders)
        return FolderMergeResult(idMap, newFolders.size)
    }

    private fun requireBackup(condition: Boolean, message: String) {
        if (!condition) throw IOException("Invalid backup: $message")
    }

    private fun String.isBase32Secret(): Boolean {
        val normalized = trim().uppercase()
        if (normalized.isBlank()) return false
        val firstPadding = normalized.indexOf('=')
        val core = if (firstPadding >= 0) normalized.substring(0, firstPadding) else normalized
        val padding = if (firstPadding >= 0) normalized.substring(firstPadding) else ""
        return core.all { it in 'A'..'Z' || it in '2'..'7' } &&
            padding.all { it == '=' } &&
            (padding.isEmpty() || (normalized.length % 8 == 0 && padding.length in setOf(1, 3, 4, 6)))
    }

    private fun duplicateKey(account: Account): String =
        "${account.secret.trim().uppercase()}\u0000${account.account.trim().lowercase()}"

    private fun generateId(): String =
        "${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}"

    private fun generateFolderId(usedIds: Set<String>): String {
        repeat(10) {
            val id = "folder_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
            if (id !in usedIds) return id
        }
        throw IOException("Unable to generate folder id")
    }

    private fun createMultipartRelatedBody(metadataJson: String, fileContent: String) = buildString {
        append("--").append(MULTIPART_BOUNDARY).append("\r\n")
        append("Content-Type: application/json; charset=UTF-8\r\n\r\n")
        append(metadataJson)
        append("\r\n--").append(MULTIPART_BOUNDARY).append("\r\n")
        append("Content-Type: $MIME_TYPE\r\n\r\n")
        append(fileContent)
        append("\r\n--").append(MULTIPART_BOUNDARY).append("--")
    }.toRequestBody("multipart/related; boundary=$MULTIPART_BOUNDARY".toMediaType())

    private suspend inline fun <reified T> executeJson(request: Request): T {
        val body = executeText(request)
        return try {
            json.decodeFromString<T>(body)
        } catch (error: Exception) {
            throw IOException("Google Drive returned an invalid response", error)
        }
    }

    private suspend fun executeText(request: Request): String = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                if (response.code == 401 || response.code == 403) {
                    throw GoogleDriveAuthException("Google session expired. Please sign in again.")
                }
                throw IOException("Google Drive API error HTTP ${response.code}: ${body.ifBlank { response.message }}")
            }
            body
        }
    }

    private suspend fun executeLimitedText(request: Request, maxBytes: Int): String = withContext(Dispatchers.IO) {
        okHttpClient.newCall(request).execute().use { response ->
            val inputStream = response.body?.byteStream()
            if (!response.isSuccessful) {
                val errorBody = inputStream?.readUtf8WithLimit(maxBytes).orEmpty()
                if (response.code == 401 || response.code == 403) {
                    throw GoogleDriveAuthException("Google session expired. Please sign in again.")
                }
                throw IOException("Google Drive API error HTTP ${response.code}: ${errorBody.ifBlank { response.message }}")
            }
            inputStream?.readUtf8WithLimit(maxBytes)
                ?: throw IOException("Google Drive returned an empty backup file")
        }
    }

    private fun InputStream.readUtf8WithLimit(maxBytes: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read == -1) break
            total += read
            if (total > maxBytes) throw IOException("Backup file is too large")
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    companion object {
        private const val APP_NAME = "azkura-auth"
        private const val APP_VERSION = "2.1.5"
        private const val MIME_TYPE = "application/json"
        private const val MAX_BACKUP_BYTES = 1024 * 1024
        private const val MAX_ACCOUNTS = 1000
        private const val MAX_FOLDERS = 100
        private const val MAX_ID_LENGTH = 128
        private const val MAX_NAME_LENGTH = 256
        private const val MAX_SECRET_LENGTH = 256
        private val SUPPORTED_ALGORITHMS = setOf("SHA1", "SHA256", "SHA512")

        private val BACKUP_JSON = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            encodeDefaults = true
        }
        private const val DRIVE_UPLOAD_URL = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
        private const val DRIVE_FILES_URL = "https://www.googleapis.com/drive/v3/files"
        private const val MULTIPART_BOUNDARY = "-------azkura-auth-android-boundary"

        val FILE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd-HH-mm-ss")
            .withZone(ZoneOffset.UTC)
    }
}

class GoogleDriveAuthException(message: String) : IOException(message)

data class GoogleDriveBackupResult(
    val fileId: String,
    val fileName: String,
    val accountCount: Int,
    val folderCount: Int,
)

data class GoogleDriveRestoreResult(
    val fileId: String,
    val fileName: String,
    val importedAccounts: Int,
    val totalAccounts: Int,
    val importedFolders: Int,
)

@Serializable
data class GoogleDriveBackupFile(
    val id: String,
    val name: String,
    val createdTime: String? = null,
    val size: String? = null,
)

private data class FolderMergeResult(
    val idMap: Map<String, String?>,
    val importedCount: Int,
)

@Serializable
private data class DriveFilesResponse(
    val files: List<GoogleDriveBackupFile> = emptyList(),
)

@Serializable
private data class DriveUploadResponse(
    val id: String,
    val name: String = "",
)

@Serializable
private data class DriveFileMetadata(
    val name: String,
    val mimeType: String,
    val description: String,
)

@Serializable
private data class DriveBackupData(
    val app: String = "azkura-auth",
    val version: String = "2.1.5",
    val exportedAt: String = "",
    val accountCount: Int = 0,
    val folderCount: Int = 0,
    val accounts: List<Account> = emptyList(),
    val folders: List<Folder> = emptyList(),
)
