package id.azkura.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import id.azkura.auth.data.remote.CloudSyncProvider
import id.azkura.auth.data.remote.GoogleDriveSyncProvider

/**
 * Hilt module that registers all [CloudSyncProvider] implementations.
 *
 * To add a new provider:
 *  1. Create a class implementing [CloudSyncProvider].
 *  2. Add a new @Binds @ElementsIntoSet method here.
 *  3. That's it — the registry, ViewModel, and UI pick it up automatically.
 *
 * Example for a future OneDrive provider:
 * ```kotlin
 * @Binds @ElementsIntoSet
 * fun bindOneDrive(impl: OneDriveSyncProvider): CloudSyncProvider
 * ```
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CloudSyncProviderModule {

    @Binds
    @ElementsIntoSet
    abstract fun bindGoogleDrive(
        impl: GoogleDriveSyncProvider,
    ): CloudSyncProvider

    // ── Future providers go here ─────────────────────────────────────────────
    // @Binds @ElementsIntoSet
    // abstract fun bindOneDrive(impl: OneDriveSyncProvider): CloudSyncProvider
    //
    // @Binds @ElementsIntoSet
    // abstract fun bindDropbox(impl: DropboxSyncProvider): CloudSyncProvider
}
