package id.azkura.auth.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import id.azkura.auth.data.model.AccountEntity
import id.azkura.auth.data.model.FolderEntity

@Database(
    entities = [AccountEntity::class, FolderEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}
