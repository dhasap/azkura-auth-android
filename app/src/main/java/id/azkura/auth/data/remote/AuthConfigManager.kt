package id.azkura.auth.data.remote

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages backup and restore of **non-sensitive** cloud sync provider configuration.
 *
 * This stores which providers are configured (connected) and their public metadata
 * (display name, account email), but **never** tokens, secrets, keys, or passwords.
 *
 * The config file is designed to be:
 *  - Restorable on a fresh install to quickly reconnect providers.
 *  - Safe to commit to a repository (no secrets).
 *  - Automatically updated when new providers are added to the registry.
 *
 * Usage:
 *  1. After each provider connect/disconnect, call [saveCurrentConfig].
 *  2. On app launch, call [restoreConfig] to display previously connected accounts.
 *  3. The actual re-authentication is still manual (user must consent again).
 */
@Singleton
class AuthConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val providerRegistry: CloudSyncProviderRegistry,
    private val json: Json,
) {

    /** Serializable snapshot of a single provider's public config. */
    @Serializable
    data class ProviderConfig(
        val id: String,
        val displayName: String,
        val accountName: String? = null,
        val accountEmail: String? = null,
        val isConnected: Boolean = false,
    )

    /** Top-level config containing all provider snapshots. */
    @Serializable
    data class AuthConfig(
        val version: Int = CONFIG_VERSION,
        val providers: List<ProviderConfig> = emptyList(),
        val exportedAt: String = "",
    )

    /**
     * Save the current state of all providers to a local JSON file.
     * Only includes non-sensitive data (provider ID, display name, account info).
     */
    suspend fun saveCurrentConfig() {
        val providers = providerRegistry.allProviders.map { provider ->
            val accountInfo = if (provider.isSignedIn()) provider.getAccountInfo() else null
            ProviderConfig(
                id = provider.id,
                displayName = provider.displayName,
                accountName = accountInfo?.name,
                accountEmail = accountInfo?.email,
                isConnected = provider.isSignedIn(),
            )
        }

        val config = AuthConfig(
            version = CONFIG_VERSION,
            providers = providers,
            exportedAt = java.time.Instant.now().toString(),
        )

        getConfigFile().writeText(json.encodeToString(config))
    }

    /**
     * Read the saved config file. Returns null if no config exists.
     * Does NOT restore tokens — only returns the saved metadata.
     */
    fun loadSavedConfig(): AuthConfig? {
        val file = getConfigFile()
        if (!file.exists()) return null
        return try {
            json.decodeFromString<AuthConfig>(file.readText())
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get a list of previously connected providers from the saved config.
     * Useful for showing "Previously connected as X" in the UI.
     */
    fun getPreviouslyConnected(): List<ProviderConfig> {
        return loadSavedConfig()?.providers?.filter { it.isConnected } ?: emptyList()
    }

    /** Delete the config file. */
    fun clearConfig() {
        getConfigFile().delete()
    }

    /**
     * Auto-update config: scan all registered providers and save their state.
     * When a new provider is added to the registry, it will automatically
     * appear in the config without manual changes.
     */
    suspend fun autoSyncConfig() {
        saveCurrentConfig()
    }

    /**
     * Export the config as a JSON string for sharing/backup.
     * This file is safe to share — contains no secrets.
     */
    suspend fun exportConfig(): String {
        saveCurrentConfig()
        return getConfigFile().readText()
    }

    /**
     * Import a config from a JSON string (e.g., from a backup file).
     * Only imports non-sensitive metadata; does NOT restore tokens.
     */
    fun importConfig(configJson: String): AuthConfig? {
        return try {
            val config = json.decodeFromString<AuthConfig>(configJson)
            getConfigFile().writeText(json.encodeToString(config))
            config
        } catch (e: Exception) {
            null
        }
    }

    private fun getConfigFile(): File {
        val dir = File(context.filesDir, "auth_config")
        dir.mkdirs()
        return File(dir, AUTH_CONFIG_FILENAME)
    }

    companion object {
        private const val AUTH_CONFIG_FILENAME = "sync_config.json"
        private const val CONFIG_VERSION = 1
    }
}
