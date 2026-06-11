package id.azkura.auth.util

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.azkura.auth.BuildConfig
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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
 * Local file backup/restore manager.
 *
 * Reads and writes the same JSON format used by Google Drive backups and the
 * browser extension, so backup files are fully cross-platform:
 * `{ app, version, exportedAt, accountCount, folderCount, accounts, folders }`.
 */
@Singleton
class LocalBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
) {
    /** Export vault backup JSON to a user-chosen URI via SAF (Storage Access Framework). */
    suspend fun exportToUri(uri: Uri): LocalBackupExportResult = withContext(Dispatchers.IO) {
        val accounts = accountRepository.getAllAccounts()
        val folders = accountRepository.getAllFolders()
        val now = Instant.now()
        val fileName = getFileNameFromUri(uri).ifBlank {
            "azkura-backup-${FILE_TIMESTAMP_FORMAT.format(now)}.json"
        }
        val content = buildBackupJson(accounts, folders, now)

        writeTextToUri(uri, content)

        LocalBackupExportResult(
            fileName = fileName,
            accountCount = accounts.size,
            folderCount = folders.size,
        )
    }

    /** Export encrypted .vault content to a user-chosen URI. */
    suspend fun exportVaultToUri(uri: Uri, encryptedVault: String): LocalBackupExportResult = withContext(Dispatchers.IO) {
        val accounts = accountRepository.getAllAccounts()
        val folders = accountRepository.getAllFolders()
        val now = Instant.now()
        val fileName = getFileNameFromUri(uri).ifBlank {
            "azkura-vault-${FILE_TIMESTAMP_FORMAT.format(now)}.vault"
        }

        writeTextToUri(uri, encryptedVault)

        LocalBackupExportResult(
            fileName = fileName,
            accountCount = accounts.size,
            folderCount = folders.size,
        )
    }

    private fun buildBackupJson(accounts: List<Account>, folders: List<Folder>, exportedAt: Instant): String {
        val backup = LocalBackupData(
            app = APP_NAME,
            version = APP_VERSION,
            exportedAt = exportedAt.toString(),
            accountCount = accounts.size,
            folderCount = folders.size,
            accounts = accounts,
            folders = folders,
        )
        return BACKUP_JSON.encodeToString(backup)
    }

    private fun writeTextToUri(uri: Uri, content: String) {
        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Unable to write to the selected file")
    }

    /** Read a user-chosen text file with the same safety limit used by backups. */
    suspend fun readTextFromUri(uri: Uri): LocalBackupTextResult = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readUtf8WithLimit(MAX_BACKUP_BYTES)
        } ?: throw IOException("Unable to read the selected file")
        LocalBackupTextResult(fileName = getFileNameFromUri(uri), content = content)
    }

    /** Import vault from a user-chosen local JSON backup file. */
    suspend fun importFromUri(uri: Uri): LocalBackupImportResult = withContext(Dispatchers.IO) {
        val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.readUtf8WithLimit(MAX_BACKUP_BYTES)
        } ?: throw IOException("Unable to read the selected file")

        val fileName = getFileNameFromUri(uri)
        val backup = decodeBackup(content)
        validateBackup(backup)
        val folderMerge = mergeFolders(backup.folders)
        val importedAccounts = mergeAccounts(backup.accounts, folderMerge.idMap)
        val importedFolders = folderMerge.importedCount

        LocalBackupImportResult(
            fileName = fileName,
            importedAccounts = importedAccounts,
            totalAccounts = accountRepository.getAccountCount(),
            importedFolders = importedFolders,
        )
    }

    private fun decodeBackup(content: String): LocalBackupData {
        val element = try {
            BACKUP_JSON.parseToJsonElement(content)
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
            BACKUP_JSON.decodeFromJsonElement(LocalBackupData.serializer(), obj)
        } catch (error: Exception) {
            throw IOException("Invalid backup file format", error)
        }

        if (backup.accounts.isEmpty() && backup.accountCount > 0) {
            throw IOException("Invalid backup: account data is missing")
        }
        return backup
    }

    private fun validateBackup(backup: LocalBackupData) {
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

    private fun getFileNameFromUri(uri: Uri): String {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex) ?: "backup.json"
            }
        }
        return uri.lastPathSegment ?: "backup.json"
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
        private val APP_VERSION: String get() = BuildConfig.VERSION_NAME
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

        val FILE_TIMESTAMP_FORMAT: DateTimeFormatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd-HH-mm-ss")
            .withZone(ZoneOffset.UTC)
    }
}

data class LocalBackupExportResult(
    val fileName: String,
    val accountCount: Int,
    val folderCount: Int,
)

data class LocalBackupTextResult(
    val fileName: String,
    val content: String,
)

data class LocalBackupImportResult(
    val fileName: String,
    val importedAccounts: Int,
    val totalAccounts: Int,
    val importedFolders: Int,
)

private data class FolderMergeResult(
    val idMap: Map<String, String?>,
    val importedCount: Int,
)

@Serializable
private data class LocalBackupData(
    val app: String = "azkura-auth",
    val version: String = BuildConfig.VERSION_NAME,
    val exportedAt: String = "",
    val accountCount: Int = 0,
    val folderCount: Int = 0,
    val accounts: List<Account> = emptyList(),
    val folders: List<Folder> = emptyList(),
)
