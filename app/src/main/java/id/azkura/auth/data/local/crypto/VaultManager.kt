package id.azkura.auth.data.local.crypto

import id.azkura.auth.data.model.Account
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

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
     * Snapshot current accounts into an encrypted blob.
     *
     * Room is the operational source of truth, so this does NOT write back to
     * the database. The returned encrypted string is useful for callers that
     * want to persist or upload a backup (e.g. Google Drive, local file export).
     *
     * @return The encrypted vault blob, or null if the vault is locked.
     */
    suspend fun saveVault(): String? {
        val password = currentPassword ?: return null
        val accounts = accountRepository.getAllAccounts()
        val plaintext = json.encodeToString(accounts)
        return cryptoManager.encrypt(plaintext, password)
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
     */
    suspend fun exportVault(password: String? = null): String {
        val pwd = password ?: currentPassword ?: cryptoManager.getDefaultKey()
        val accounts = accountRepository.getAllAccounts()
        val plaintext = json.encodeToString(accounts)
        return cryptoManager.encrypt(plaintext, pwd)
    }

    /**
     * Import vault from encrypted string.
     */
    suspend fun importVault(encryptedVault: String, password: String? = null): List<Account> {
        val pwd = password ?: currentPassword ?: cryptoManager.getDefaultKey()
        val plaintext = cryptoManager.decrypt(encryptedVault, pwd)
        val accounts: List<Account> = json.decodeFromString(plaintext)
        accountRepository.replaceAllAccounts(accounts)
        return accounts
    }
}
