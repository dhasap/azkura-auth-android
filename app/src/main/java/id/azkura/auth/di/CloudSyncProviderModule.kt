package id.azkura.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import id.azkura.auth.data.remote.CloudSyncProvider
import id.azkura.auth.data.remote.GoogleDriveSyncProvider

/**
 * Hilt module that registers all [CloudSyncProvider] implementations.
 *
 * To add a new provider:
 *  1. Create a class implementing [CloudSyncProvider].
 *  2. Add a new @Binds @IntoSet method here.
 *  3. That's it — the registry, ViewModel, and UI pick it up automatically.
 *
 * Example for a future OneDrive provider:
 * ```kotlin
 * @Binds @IntoSet
 * fun bindOneDrive(impl: OneDriveSyncProvider): CloudSyncProvider
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSyncProviderModule {

    @Binds
    @IntoSet
    abstract fun bindGoogleDrive(
        impl: GoogleDriveSyncProvider,
    ): CloudSyncProvider

    // ── Future providers go here ─────────────────────────────────────────────
    // @Binds @IntoSet
    // abstract fun bindOneDrive(impl: OneDriveSyncProvider): CloudSyncProvider
    //
    // @Binds @IntoSet
    // abstract fun bindDropbox(impl: DropboxSyncProvider): CloudSyncProvider
}
