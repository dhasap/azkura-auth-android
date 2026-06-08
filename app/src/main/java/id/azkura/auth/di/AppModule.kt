package id.azkura.auth.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // General app-wide bindings can go here.
    // CryptoManager, VaultManager, PreferencesManager, StatsRepository
    // are already @Singleton @Inject — Hilt auto-provides them.
}
