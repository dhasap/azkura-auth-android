package id.azkura.auth.data.repository

import id.azkura.auth.data.local.crypto.SecretEncryptor
import id.azkura.auth.data.local.db.VaultDao
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.AccountEntity
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.model.FolderEntity
import id.azkura.auth.data.model.toAccount
import id.azkura.auth.data.model.toEntity
import id.azkura.auth.data.model.toFolder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository layer for accounts and folders.
 *
 * All TOTP secrets are encrypted at rest using Android Keystore (AES-256-GCM).
 * Secrets are encrypted before writing to Room and decrypted after reading.
 * The domain model [Account] always holds plaintext secrets for in-memory use.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val dao: VaultDao,
    private val secretEncryptor: SecretEncryptor,
) {
    // ── Accounts ─────────────────────────────────────────────────────────────

    fun observeAllAccounts(): Flow<List<Account>> =
        dao.observeAllAccounts().map { list -> list.map { it.toDecryptedAccount() } }

    suspend fun getAllAccounts(): List<Account> =
        dao.getAllAccounts().map { it.toDecryptedAccount() }

    suspend fun getAccountById(id: String): Account? =
        dao.getAccountById(id)?.toDecryptedAccount()

    fun observeAccountsByFolder(folderId: String): Flow<List<Account>> =
        dao.observeAccountsByFolder(folderId).map { list -> list.map { it.toDecryptedAccount() } }

    fun searchAccounts(query: String): Flow<List<Account>> =
        dao.searchAccounts(query).map { list -> list.map { it.toDecryptedAccount() } }

    suspend fun addAccount(account: Account) {
        val maxOrder = dao.getMaxOrder() ?: 0
        dao.insertAccount(account.copy(order = maxOrder + 1).toEncryptedEntity())
    }

    suspend fun updateAccount(account: Account) {
        dao.updateAccount(account.toEncryptedEntity())
    }

    suspend fun updateAccounts(accounts: List<Account>) {
        if (accounts.isNotEmpty()) {
            dao.updateAccounts(accounts.map { it.toEncryptedEntity() })
        }
    }

    suspend fun deleteAccount(id: String) {
        dao.deleteAccountById(id)
    }

    suspend fun insertAccounts(accounts: List<Account>) {
        if (accounts.isNotEmpty()) {
            dao.insertAccounts(accounts.map { it.toEncryptedEntity() })
        }
    }

    suspend fun replaceAllAccounts(accounts: List<Account>) {
        dao.deleteAllAccounts()
        dao.insertAccounts(accounts.map { it.toEncryptedEntity() })
    }

    suspend fun getAccountCount(): Int = dao.getAccountCount()

    /**
     * Migrate any plaintext secrets in the database to encrypted form.
     * Call this once on app startup. Idempotent — already-encrypted secrets are skipped.
     */
    suspend fun migrateSecretsToEncrypted() {
        val allEntities = dao.getAllAccounts()
        val needsMigration = allEntities.filter { !secretEncryptor.isEncrypted(it.secret) }
        if (needsMigration.isNotEmpty()) {
            val encrypted = needsMigration.map { entity ->
                entity.copy(secret = secretEncryptor.encrypt(entity.secret))
            }
            dao.updateAccounts(encrypted)
        }
    }

    // ── Folders ──────────────────────────────────────────────────────────────

    fun observeAllFolders(): Flow<List<Folder>> =
        dao.observeAllFolders().map { list -> list.map { it.toFolder() } }

    suspend fun getAllFolders(): List<Folder> =
        dao.getAllFolders().map { it.toFolder() }

    suspend fun addFolder(folder: Folder) {
        dao.insertFolder(folder.toEntity())
    }

    suspend fun insertFolders(folders: List<Folder>) {
        if (folders.isNotEmpty()) {
            dao.insertFolders(folders.map { it.toEntity() })
        }
    }

    suspend fun updateFolder(folder: Folder) {
        dao.updateFolder(folder.toEntity())
    }

    suspend fun deleteFolder(id: String) {
        dao.deleteFolderById(id)
    }

    suspend fun replaceAllFolders(folders: List<Folder>) {
        dao.deleteAllFolders()
        dao.insertFolders(folders.map { it.toEntity() })
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    /** Decrypt the secret field when reading from Room. */
    private fun AccountEntity.toDecryptedAccount(): Account {
        val decryptedSecret = secretEncryptor.decrypt(secret)
        return toAccount().copy(secret = decryptedSecret)
    }

    /** Encrypt the secret field when writing to Room. */
    private fun Account.toEncryptedEntity(): AccountEntity {
        val encryptedSecret = secretEncryptor.encrypt(secret)
        return copy(secret = encryptedSecret).toEntity()
    }
}
