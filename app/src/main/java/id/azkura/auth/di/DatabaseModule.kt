package id.azkura.auth.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import id.azkura.auth.data.local.db.VaultDao
import id.azkura.auth.data.local.db.VaultDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideVaultDatabase(@ApplicationContext context: Context): VaultDatabase {
        return Room.databaseBuilder(
            context,
            VaultDatabase::class.java,
            "azkura_vault.db",
        ).build()
    }

    @Provides
    @Singleton
    fun provideVaultDao(database: VaultDatabase): VaultDao {
        return database.vaultDao()
    }
}
