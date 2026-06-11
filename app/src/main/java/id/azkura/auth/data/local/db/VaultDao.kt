package id.azkura.auth.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import id.azkura.auth.data.model.AccountEntity
import id.azkura.auth.data.model.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    // ── Accounts ─────────────────────────────────────────────────────────────

    @Query("SELECT * FROM accounts ORDER BY `order` ASC, createdAt DESC")
    fun observeAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY `order` ASC, createdAt DESC")
    suspend fun getAllAccounts(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getAccountById(id: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE folderId = :folderId ORDER BY `order` ASC")
    fun observeAccountsByFolder(folderId: String): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE issuer LIKE '%' || :query || '%' OR account LIKE '%' || :query || '%'")
    fun searchAccounts(query: String): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccount(account: AccountEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    suspend fun updateAccount(account: AccountEntity)

    @Transaction
    @Update
    suspend fun updateAccounts(accounts: List<AccountEntity>)

    @Delete
    suspend fun deleteAccount(account: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteAccountById(id: String)

    @Query("DELETE FROM accounts")
    suspend fun deleteAllAccounts()

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun getAccountCount(): Int

    @Query("SELECT MAX(`order`) FROM accounts")
    suspend fun getMaxOrder(): Int?

    // ── Folders ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM folders ORDER BY `order` ASC")
    fun observeAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders ORDER BY `order` ASC")
    suspend fun getAllFolders(): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getFolderById(id: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Update
    suspend fun updateFolder(folder: FolderEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderEntity)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolderById(id: String)

    @Query("DELETE FROM folders")
    suspend fun deleteAllFolders()
}
