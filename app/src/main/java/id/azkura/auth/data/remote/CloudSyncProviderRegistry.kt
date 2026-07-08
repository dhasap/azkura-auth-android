package id.azkura.auth.data.remote

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registry of all available cloud sync providers.
 *
 * Providers are registered at DI time via [CloudSyncProviderModule].
 * The ViewModel and UI query this registry to discover and interact
 * with providers without hardcoding any specific implementation.
 *
 * Adding a new provider:
 *  1. Implement [CloudSyncProvider] (see [GoogleDriveSyncProvider] as reference).
 *  2. Add a binding in `di/CloudSyncProviderModule.kt`.
 *  3. Done — the registry and UI pick it up automatically.
 */
@Singleton
class CloudSyncProviderRegistry @Inject constructor(
    providers: Set<@JvmSuppressWildcards CloudSyncProvider>,
) {
    private val providerMap: Map<String, CloudSyncProvider> =
        providers.associateBy { it.id }

    /** All registered providers. */
    val allProviders: List<CloudSyncProvider>
        get() = providerMap.values.toList()

    /** Only providers that are available on this device/configuration. */
    val availableProviders: List<CloudSyncProvider>
        get() = providerMap.values.filter { it.isAvailable }

    /** Get a provider by its [CloudSyncProvider.id]. */
    fun getById(id: String): CloudSyncProvider? = providerMap[id]

    /** Get a provider or throw if not found. */
    fun requireById(id: String): CloudSyncProvider =
        providerMap[id] ?: throw IllegalArgumentException(
            "Cloud sync provider '$id' is not registered. " +
                "Available: ${providerMap.keys}",
        )
}
