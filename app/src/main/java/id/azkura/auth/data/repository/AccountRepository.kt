package id.azkura.auth.data.repository

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

@Singleton
class AccountRepository @Inject constructor(
    private val dao: VaultDao,
) {
    // ── Accounts ─────────────────────────────────────────────────────────────

    fun observeAllAccounts(): Flow<List<Account>> =
        dao.observeAllAccounts().map { list -> list.map { it.toAccount() } }

    suspend fun getAllAccounts(): List<Account> =
        dao.getAllAccounts().map { it.toAccount() }

    suspend fun getAccountById(id: String): Account? =
        dao.getAccountById(id)?.toAccount()

    fun observeAccountsByFolder(folderId: String): Flow<List<Account>> =
        dao.observeAccountsByFolder(folderId).map { list -> list.map { it.toAccount() } }

    fun searchAccounts(query: String): Flow<List<Account>> =
        dao.searchAccounts(query).map { list -> list.map { it.toAccount() } }

    suspend fun addAccount(account: Account) {
        val maxOrder = dao.getMaxOrder() ?: 0
        dao.insertAccount(account.copy(order = maxOrder + 1).toEntity())
    }

    suspend fun updateAccount(account: Account) {
        dao.updateAccount(account.toEntity())
    }

    suspend fun deleteAccount(id: String) {
        dao.deleteAccountById(id)
    }

    suspend fun insertAccounts(accounts: List<Account>) {
        if (accounts.isNotEmpty()) {
            dao.insertAccounts(accounts.map { it.toEntity() })
        }
    }

    suspend fun replaceAllAccounts(accounts: List<Account>) {
        dao.deleteAllAccounts()
        dao.insertAccounts(accounts.map { it.toEntity() })
    }

    suspend fun getAccountCount(): Int = dao.getAccountCount()

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
}
