package id.azkura.auth.data.local.crypto

import id.azkura.auth.BuildConfig
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Vault manager — handles lock/unlock state and encrypted vault operations.
 * Port of accounts.js vault operations (unlockVault, saveVault, lockVault).
 */
@Singleton
class VaultManager @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val accountRepository: AccountRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false; encodeDefaults = true }

    private val _isLocked = MutableStateFlow(true)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var currentPassword: String? = null

    /**
     * Unlock the vault with the given password/PIN.
     * Decrypts the vault and loads accounts into Room.
     * If no vault exists yet (first time), creates an empty vault.
     */
    suspend fun unlockVault(password: String?): List<Account> {
        val effectivePassword = password?.takeIf { it.isNotEmpty() } ?: cryptoManager.getDefaultKey()
        currentPassword = effectivePassword
        _isLocked.value = false
        return accountRepository.getAllAccounts()
    }

    /**
     * Snapshot current accounts + folders into an encrypted blob.
     *
     * Room is the operational source of truth, so this does NOT write back to
     * the database. The returned encrypted string is useful for callers that
     * want to persist or upload a backup (e.g. local .vault export).
     *
     * @return The encrypted vault blob, or null if the vault is locked.
     */
    suspend fun saveVault(): String? {
        val password = currentPassword ?: return null
        return exportVault(password)
    }

    /**
     * Lock the vault — clear in-memory password.
     */
    fun lockVault() {
        currentPassword = null
        _isLocked.value = true
    }

    /**
     * Check if vault has a password set (PIN enabled).
     */
    fun hasPassword(): Boolean = currentPassword != null

    /**
     * Get current password for backup operations.
     */
    fun getCurrentPassword(): String? = currentPassword

    /**
     * Export vault as encrypted string (for backup/transfer).
     * The payload includes accounts and folders so folder assignment survives restore.
     */
    suspend fun exportVault(password: String? = null): String {
        val pwd = password ?: currentPassword ?: cryptoManager.getDefaultKey()
        val accounts = accountRepository.getAllAccounts()
        val folders = accountRepository.getAllFolders()
        val payload = EncryptedVaultData(
            version = BuildConfig.VERSION_NAME,
            exportedAt = Instant.now().toString(),
            accountCount = accounts.size,
            folderCount = folders.size,
            accounts = accounts,
            folders = folders,
        )
        val plaintext = json.encodeToString(payload)
        return cryptoManager.encrypt(plaintext, pwd)
    }

    /**
     * Import vault from encrypted string and return the imported account list.
     * Supports the new accounts+folders payload and legacy account-list payloads.
     */
    suspend fun importVault(encryptedVault: String, password: String? = null): List<Account> =
        importVaultDetailed(encryptedVault, password).accounts

    suspend fun importVaultDetailed(encryptedVault: String, password: String? = null): VaultImportResult {
        val pwd = password ?: currentPassword ?: cryptoManager.getDefaultKey()
        val plaintext = cryptoManager.decrypt(encryptedVault, pwd)
        val element = json.parseToJsonElement(plaintext)

        val result = when (element) {
            is JsonArray -> {
                val accounts: List<Account> = json.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(Account.serializer()),
                    element,
                )
                VaultImportResult(accounts = accounts, folderCount = 0)
            }
            is JsonObject -> {
                val payload = json.decodeFromJsonElement(EncryptedVaultData.serializer(), element.jsonObject)
                accountRepository.replaceAllFolders(payload.folders)
                VaultImportResult(accounts = payload.accounts, folderCount = payload.folders.size)
            }
            else -> throw IllegalArgumentException("Unsupported vault payload")
        }

        accountRepository.replaceAllAccounts(result.accounts)
        return result
    }
}

data class VaultImportResult(
    val accounts: List<Account>,
    val folderCount: Int,
) {
    val accountCount: Int get() = accounts.size
}

@Serializable
private data class EncryptedVaultData(
    val app: String = "azkura-auth",
    val version: String = BuildConfig.VERSION_NAME,
    val exportedAt: String = "",
    val accountCount: Int = 0,
    val folderCount: Int = 0,
    val accounts: List<Account> = emptyList(),
    val folders: List<Folder> = emptyList(),
)
