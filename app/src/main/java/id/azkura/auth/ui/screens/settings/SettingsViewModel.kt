package id.azkura.auth.ui.screens.settings

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.crypto.CryptoManager
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.local.prefs.SortOrder
import id.azkura.auth.data.remote.GoogleAuthService
import id.azkura.auth.data.remote.GoogleAuthorizationOutcome
import id.azkura.auth.data.remote.GoogleDriveAuthException
import id.azkura.auth.data.remote.GoogleDriveService
import id.azkura.auth.data.remote.CloudSyncProviderRegistry
import id.azkura.auth.data.remote.ConnectResult
import id.azkura.auth.data.remote.CloudSyncProvider
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.util.BiometricHelper
import id.azkura.auth.util.LocalBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

data class SettingsUiState(
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val autoLockMinutes: Int = 5,
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val googleUserName: String? = null,
    val googleUserEmail: String? = null,
    val googleUserPicture: String? = null,
    val lastBackup: String? = null,
    val totalAccounts: Int = 0,
    val showSetPinDialog: Boolean = false,
    val showChangePinDialog: Boolean = false,
    val pinSetupError: String? = null,
    val isLoading: Boolean = true,
    val exportResult: String? = null,
    val isGoogleBusy: Boolean = false,
    val googleMessage: String? = null,
    val pendingGoogleAuthorization: PendingIntent? = null,
    val localBackupMessage: String? = null,
    val pendingExportUri: Boolean = false,
    val encryptBackup: Boolean = false,
    val showExportDialog: Boolean = false,
    val showBackupPasswordDialog: Boolean = false,
    val showRemovePinDialog: Boolean = false,
    // ── Provider-agnostic state ────────────────────────────────────────────
    val activeProviderId: String? = null,
    val activeProviderBusy: Boolean = false,
    val activeProviderMessage: String? = null,
    val pendingProviderConsent: android.app.PendingIntent? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val cryptoManager: CryptoManager,
    private val vaultManager: VaultManager,
    private val accountRepository: AccountRepository,
    private val googleAuthService: GoogleAuthService,
    private val googleDriveService: GoogleDriveService,
    private val localBackupManager: LocalBackupManager,
    private val providerRegistry: CloudSyncProviderRegistry,
) : ViewModel() {

    private enum class PendingGoogleAction { SIGN_IN, BACKUP, RESTORE }
    private enum class PendingProviderAction { CONNECT, BACKUP, RESTORE }
    private var pendingProviderAction: PendingProviderAction? = null
    private var pendingProviderId: String? = null

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pendingGoogleAction: PendingGoogleAction? = null

    init {
        viewModelScope.launch { refreshState() }
        viewModelScope.launch {
            preferencesManager.sortOrder.collectLatest { sortOrder ->
                _uiState.value = _uiState.value.copy(sortOrder = sortOrder)
            }
        }
    }

    fun onSetupPin(pin: String) {
        if (pin.length != 6) {
            _uiState.value = _uiState.value.copy(pinSetupError = "PIN must be exactly 6 digits")
            return
        }
        viewModelScope.launch {
            val pinData = cryptoManager.setupPin(pin)
            preferencesManager.setPinCredentials(pinData.hash, pinData.salt)
            _uiState.value = _uiState.value.copy(
                pinEnabled = true,
                showSetPinDialog = false,
                pinSetupError = null,
            )
        }
    }

    /**
     * Remove PIN only after verifying the current PIN.
     * This prevents privilege escalation via accessibility services or automated UI.
     */
    fun onRemovePin(currentPin: String) {
        viewModelScope.launch {
            val storedHash = preferencesManager.pinHash.first()
            val storedSalt = preferencesManager.pinSalt.first()
            if (storedHash == null || storedSalt == null) {
                // PIN data is already missing — just clear the flag
                preferencesManager.clearPin()
                _uiState.value = _uiState.value.copy(pinEnabled = false, showRemovePinDialog = false)
                return@launch
            }
            val valid = cryptoManager.verifyPin(currentPin, storedHash, storedSalt)
            if (valid) {
                preferencesManager.clearPin()
                preferencesManager.setBiometricEnabled(false)
                _uiState.value = _uiState.value.copy(
                    pinEnabled = false,
                    biometricEnabled = false,
                    showRemovePinDialog = false,
                    pinSetupError = null,
                )
            } else {
                _uiState.value = _uiState.value.copy(pinSetupError = "Incorrect PIN")
            }
        }
    }


    fun onShowRemovePinDialog() {
        _uiState.value = _uiState.value.copy(showRemovePinDialog = true, pinSetupError = null)
    }

    fun onDismissRemovePinDialog() {
        _uiState.value = _uiState.value.copy(showRemovePinDialog = false, pinSetupError = null)
    }

    fun onToggleEncryptBackup(enabled: Boolean) {
        if (!enabled) {
            viewModelScope.launch {
                preferencesManager.setEncryptBackup(false)
                preferencesManager.setBackupPassword(null)
                refreshState()
            }
        } else {
            _uiState.value = _uiState.value.copy(showBackupPasswordDialog = true)
        }
    }

    fun onSetBackupPassword(password: String) {
        viewModelScope.launch {
            if (password.isNotBlank()) {
                preferencesManager.setEncryptBackup(true)
                preferencesManager.setBackupPassword(password)
                _uiState.value = _uiState.value.copy(showBackupPasswordDialog = false)
                refreshState()
            }
        }
    }

    fun onDismissBackupPasswordDialog() {
        _uiState.value = _uiState.value.copy(showBackupPasswordDialog = false)
    }

    fun showExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = true)
    }

    fun hideExportDialog() {
        _uiState.value = _uiState.value.copy(showExportDialog = false)
    }

    fun onToggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            val canEnable = enabled && _uiState.value.pinEnabled && BiometricHelper.isAvailable(context)
            preferencesManager.setBiometricEnabled(canEnable)
            _uiState.value = _uiState.value.copy(
                biometricEnabled = canEnable,
                biometricAvailable = BiometricHelper.isAvailable(context),
            )
        }
    }

    fun onAutoLockChanged(minutes: Int) {
        viewModelScope.launch {
            preferencesManager.setAutoLockMinutes(minutes)
            _uiState.value = _uiState.value.copy(autoLockMinutes = minutes)
        }
    }

    fun onSortOrderChanged(order: SortOrder) {
        viewModelScope.launch {
            preferencesManager.setSortOrder(order)
            _uiState.value = _uiState.value.copy(sortOrder = order)
        }
    }

    fun onShowSetPinDialog() {
        _uiState.value = _uiState.value.copy(showSetPinDialog = true)
    }

    fun onDismissPinDialog() {
        _uiState.value = _uiState.value.copy(showSetPinDialog = false, pinSetupError = null)
    }

    fun onConnectGoogle(activity: Activity) {
        viewModelScope.launch {
            startGoogleOperation()
            try {
                when (val outcome = googleAuthService.signIn(activity)) {
                    is GoogleAuthorizationOutcome.Authorized -> {
                        refreshState("Connected as ${outcome.session.user.email}")
                    }
                    is GoogleAuthorizationOutcome.NeedsResolution -> {
                        pendingGoogleAction = PendingGoogleAction.SIGN_IN
                        requestGoogleResolution(outcome.pendingIntent, "Complete Google sign-in to continue")
                    }
                }
            } catch (error: Exception) {
                handleGoogleOperationFailure(error)
            }
        }
    }

    fun onDisconnectGoogle() {
        viewModelScope.launch {
            googleAuthService.signOut()
            refreshState("Google account disconnected")
        }
    }

    fun onBackupToGoogleDrive(activity: Activity) {
        viewModelScope.launch {
            startGoogleOperation()
            try {
                val token = getAccessTokenOrRequestConsent(activity, PendingGoogleAction.BACKUP) ?: return@launch
                performBackup(token)
            } catch (error: Exception) {
                handleGoogleOperationFailure(error)
            }
        }
    }

    fun onRestoreFromGoogleDrive(activity: Activity) {
        viewModelScope.launch {
            startGoogleOperation()
            try {
                val token = getAccessTokenOrRequestConsent(activity, PendingGoogleAction.RESTORE) ?: return@launch
                performRestore(token)
            } catch (error: Exception) {
                handleGoogleOperationFailure(error)
            }
        }
    }

    fun onGoogleAuthorizationLaunched() {
        _uiState.value = _uiState.value.copy(pendingGoogleAuthorization = null)
    }

    fun onGoogleAuthorizationLaunchFailed(message: String) {
        pendingGoogleAction = null
        _uiState.value = _uiState.value.copy(
            isGoogleBusy = false,
            pendingGoogleAuthorization = null,
            googleMessage = message,
        )
    }

    fun onGoogleAuthorizationCancelled() {
        pendingGoogleAction = null
        _uiState.value = _uiState.value.copy(
            isGoogleBusy = false,
            pendingGoogleAuthorization = null,
            googleMessage = "Google sign-in cancelled",
        )
    }

    fun onGoogleAuthorizationResult(intent: Intent?) {
        viewModelScope.launch {
            startGoogleOperation()
            val action = pendingGoogleAction ?: PendingGoogleAction.SIGN_IN
            pendingGoogleAction = null

            try {
                val session = googleAuthService.handleAuthorizationResult(intent)
                when (action) {
                    PendingGoogleAction.SIGN_IN -> refreshState("Connected as ${session.user.email}")
                    PendingGoogleAction.BACKUP -> performBackup(session.accessToken)
                    PendingGoogleAction.RESTORE -> performRestore(session.accessToken)
                }
            } catch (error: Exception) {
                handleGoogleOperationFailure(error)
            }
        }
    }

    fun onExportVaultFile() {
        _uiState.value = _uiState.value.copy(pendingExportUri = true)
    }

    fun onExportVaultFileTo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingExportUri = false)
            try {
                val encrypted = vaultManager.exportVault()
                val result = localBackupManager.exportVaultToUri(uri, encrypted)
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Saved encrypted .vault with ${result.accountCount} account(s) and ${result.folderCount} folder(s) to ${result.fileName}",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Export failed: ${e.message}",
                )
            }
        }
    }

    fun onExportVaultFileCancelled() {
        _uiState.value = _uiState.value.copy(pendingExportUri = false)
    }

    fun onShareVaultText() {
        viewModelScope.launch {
            try {
                val encrypted = vaultManager.exportVault()
                _uiState.value = _uiState.value.copy(exportResult = encrypted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Export failed: ${e.message}",
                )
            }
        }
    }

    // Backward-compatible aliases for older UI wiring/tests.
    fun onExportLocalBackup() = onExportVaultFile()
    fun onExportLocalBackupTo(uri: Uri) = onExportVaultFileTo(uri)
    fun onExportLocalBackupCancelled() = onExportVaultFileCancelled()
    fun onExportVault() = onShareVaultText()

    fun onImportLocalBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val textResult = localBackupManager.readTextFromUri(uri)
                if (textResult.content.trimStart().startsWith("{")) {
                    val result = localBackupManager.importFromUri(uri)
                    refreshState()
                    _uiState.value = _uiState.value.copy(
                        localBackupMessage = "Imported ${result.importedAccounts} account(s) and ${result.importedFolders} folder(s) from ${result.fileName}",
                    )
                } else {
                    val result = vaultManager.importVaultDetailed(textResult.content.trim())
                    refreshState()
                    _uiState.value = _uiState.value.copy(
                        localBackupMessage = "Imported encrypted .vault from ${textResult.fileName}: ${result.accountCount} account(s) and ${result.folderCount} folder(s)",
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Import failed: ${e.message}",
                )
            }
        }
    }

    fun clearLocalBackupMessage() {
        _uiState.value = _uiState.value.copy(localBackupMessage = null)
    }

    fun clearExportResult() {
        _uiState.value = _uiState.value.copy(exportResult = null)
    }

    fun clearGoogleMessage() {
        _uiState.value = _uiState.value.copy(googleMessage = null)
    }

    private suspend fun refreshState(message: String? = _uiState.value.googleMessage) {
        val pinEnabled = preferencesManager.pinEnabled.first()
        val biometricAvailable = BiometricHelper.isAvailable(context)
        val storedBiometricEnabled = preferencesManager.biometricEnabled.first()
        val biometricEnabled = pinEnabled && biometricAvailable && storedBiometricEnabled
        if (storedBiometricEnabled != biometricEnabled) {
            preferencesManager.setBiometricEnabled(biometricEnabled)
        }
        val autoLock = preferencesManager.autoLockMinutes.first()
        val sortOrder = preferencesManager.sortOrder.first()
        val googleName = preferencesManager.googleUserName.first()
        val googleEmail = preferencesManager.googleUserEmail.first()
        val googlePicture = preferencesManager.googleUserPicture.first()
        val lastBackup = preferencesManager.lastBackupAt.first()
        val count = accountRepository.getAccountCount()

        _uiState.value = _uiState.value.copy(
            pinEnabled = pinEnabled,
            biometricEnabled = biometricEnabled,
            biometricAvailable = biometricAvailable,
            autoLockMinutes = autoLock,
            sortOrder = sortOrder,
            googleUserName = googleName,
            googleUserEmail = googleEmail,
            googleUserPicture = googlePicture,
            lastBackup = formatTimestamp(lastBackup),
            totalAccounts = count,
            isLoading = false,
            isGoogleBusy = false,
            googleMessage = message,
            pendingGoogleAuthorization = null,
        )
    }

    private suspend fun getAccessTokenOrRequestConsent(
        activity: Activity,
        action: PendingGoogleAction,
    ): String? {
        googleAuthService.getStoredAccessTokenIfFresh()?.let { return it }

        return when (val outcome = googleAuthService.signIn(activity)) {
            is GoogleAuthorizationOutcome.Authorized -> outcome.session.accessToken
            is GoogleAuthorizationOutcome.NeedsResolution -> {
                pendingGoogleAction = action
                requestGoogleResolution(outcome.pendingIntent, "Grant Google Drive access to continue")
                null
            }
        }
    }

    private fun startGoogleOperation() {
        _uiState.value = _uiState.value.copy(
            isGoogleBusy = true,
            googleMessage = null,
            pendingGoogleAuthorization = null,
        )
    }

    private fun requestGoogleResolution(pendingIntent: PendingIntent, message: String) {
        _uiState.value = _uiState.value.copy(
            isGoogleBusy = false,
            googleMessage = message,
            pendingGoogleAuthorization = pendingIntent,
        )
    }

    private suspend fun performBackup(accessToken: String) {
        val result = googleDriveService.backupDetailed(accessToken)
        refreshState(
            "Backup uploaded: ${result.fileName} (${result.accountCount} account(s), ${result.folderCount} folder(s))",
        )
    }

    private suspend fun performRestore(accessToken: String) {
        val result = googleDriveService.restoreLatest(accessToken)
        refreshState(
            "Restored ${result.importedAccounts} account(s) and ${result.importedFolders} folder(s) from ${result.fileName}",
        )
    }

    private suspend fun handleGoogleOperationFailure(error: Exception) {
        if (error is GoogleDriveAuthException) {
            googleAuthService.clearInvalidToken()
        }
        val message = error.message?.takeIf { it.isNotBlank() } ?: "Google operation failed"
        refreshState(message)
    }

    private fun formatTimestamp(value: String?): String? {
        val millis = value?.toLongOrNull() ?: return value
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Provider-agnostic methods (new — prefer these for future UI components)
    // ═══════════════════════════════════════════════════════════════════════════

    /** All registered cloud sync providers. */
    val availableProviders: List<CloudSyncProvider>
        get() = providerRegistry.availableProviders

    /** Connect to any registered cloud sync provider by ID. */
    fun onConnectProvider(providerId: String, activity: Activity) {
        val provider = providerRegistry.getById(providerId) ?: return
        viewModelScope.launch {
            startProviderOp(providerId)
            try {
                when (val result = provider.connect(activity)) {
                    is ConnectResult.Connected -> {
                        pendingProviderAction = null
                        pendingProviderId = null
                        setProviderResult(providerId, "Connected as ${result.accountInfo.email}")
                    }
                    is ConnectResult.NeedsConsent -> {
                        pendingProviderAction = PendingProviderAction.CONNECT
                        pendingProviderId = providerId
                        _uiState.value = _uiState.value.copy(
                            activeProviderBusy = false,
                            activeProviderMessage = "Complete ${provider.displayName} sign-in",
                            pendingProviderConsent = result.pendingIntent,
                        )
                    }
                }
            } catch (error: Exception) {
                handleProviderError(providerId, error)
            }
        }
    }

    /** Disconnect from any registered cloud sync provider by ID. */
    fun onDisconnectProvider(providerId: String) {
        val provider = providerRegistry.getById(providerId) ?: return
        viewModelScope.launch {
            provider.disconnect()
            setProviderResult(providerId, "${provider.displayName} disconnected")
        }
    }

    /** Backup to any registered cloud sync provider. */
    fun onBackupToProvider(providerId: String, activity: Activity) {
        val provider = providerRegistry.getById(providerId) ?: return
        viewModelScope.launch {
            startProviderOp(providerId)
            try {
                val token = ensureProviderToken(provider, activity, PendingProviderAction.BACKUP)
                    ?: return@launch
                runProviderBackup(provider)
            } catch (error: Exception) {
                handleProviderError(providerId, error)
            }
        }
    }

    /** Restore from any registered cloud sync provider. */
    fun onRestoreFromProvider(providerId: String, activity: Activity) {
        val provider = providerRegistry.getById(providerId) ?: return
        viewModelScope.launch {
            startProviderOp(providerId)
            try {
                val token = ensureProviderToken(provider, activity, PendingProviderAction.RESTORE)
                    ?: return@launch
                runProviderRestore(provider)
            } catch (error: Exception) {
                handleProviderError(providerId, error)
            }
        }
    }

    /** Handle provider consent result from ActivityResultLauncher. */
    fun onProviderConsentResult(intent: Intent?) {
        val providerId = pendingProviderId ?: return
        val provider = providerRegistry.getById(providerId) ?: return
        val action = pendingProviderAction ?: PendingProviderAction.CONNECT
        pendingProviderAction = null
        pendingProviderId = null
        viewModelScope.launch {
            startProviderOp(providerId)
            try {
                when (action) {
                    PendingProviderAction.CONNECT -> {
                        when (val res = provider.connect(context as? Activity ?: return@launch)) {
                            is ConnectResult.Connected -> setProviderResult(providerId, "Connected to ${provider.displayName}")
                            is ConnectResult.NeedsConsent -> {
                                pendingProviderAction = PendingProviderAction.CONNECT
                                pendingProviderId = providerId
                                _uiState.value = _uiState.value.copy(
                                    activeProviderBusy = false,
                                    pendingProviderConsent = res.pendingIntent,
                                )
                            }
                        }
                    }
                    PendingProviderAction.BACKUP -> runProviderBackup(provider)
                    PendingProviderAction.RESTORE -> runProviderRestore(provider)
                }
            } catch (error: Exception) {
                handleProviderError(providerId, error)
            }
        }
    }

    fun onProviderConsentCancelled() {
        pendingProviderAction = null
        pendingProviderId = null
        _uiState.value = _uiState.value.copy(
            activeProviderBusy = false,
            activeProviderMessage = "Sign-in cancelled",
            pendingProviderConsent = null,
        )
    }

    fun clearProviderMessage() {
        _uiState.value = _uiState.value.copy(activeProviderMessage = null)
    }

    // ── Provider private helpers ────────────────────────────────────────────

    private fun startProviderOp(providerId: String) {
        _uiState.value = _uiState.value.copy(
            activeProviderId = providerId,
            activeProviderBusy = true,
            activeProviderMessage = null,
            pendingProviderConsent = null,
        )
    }

    private suspend fun ensureProviderToken(
        provider: CloudSyncProvider,
        activity: Activity,
        action: PendingProviderAction,
    ): String? {
        provider.getAccessToken()?.let { return it }
        when (val outcome = provider.connect(activity)) {
            is ConnectResult.Connected -> return outcome.accountInfo.extras["access_token"]
            is ConnectResult.NeedsConsent -> {
                pendingProviderAction = action
                pendingProviderId = provider.id
                _uiState.value = _uiState.value.copy(
                    activeProviderBusy = false,
                    activeProviderMessage = "Grant ${provider.displayName} access",
                    pendingProviderConsent = outcome.pendingIntent,
                )
                return null
            }
        }
    }

    private suspend fun runProviderBackup(provider: CloudSyncProvider) {
        val accounts = accountRepository.getAllAccounts()
        val folders = accountRepository.getAllFolders()
        val payload = id.azkura.auth.data.remote.BackupPayload(
            accountsJson = Json.encodeToString(accounts),
            foldersJson = Json.encodeToString(folders),
            accountCount = accounts.size,
            folderCount = folders.size,
            versionName = id.azkura.auth.BuildConfig.VERSION_NAME,
        )
        val result = provider.backup(payload)
        setProviderResult(
            provider.id,
            "Backup: ${result.fileName} (${result.accountCount} accounts, ${result.folderCount} folders)",
        )
    }

    private suspend fun runProviderRestore(provider: CloudSyncProvider) {
        val result = provider.restore()
        setProviderResult(
            provider.id,
            "Restored ${result.importedAccounts} accounts, ${result.importedFolders} folders from ${result.fileName}",
        )
    }

    private suspend fun handleProviderError(providerId: String, error: Exception) {
        if (error is GoogleDriveAuthException) {
            providerRegistry.getById(providerId)?.clearInvalidToken()
        }
        setProviderResult(providerId, error.message?.takeIf { it.isNotBlank() } ?: "Operation failed")
    }

    private fun setProviderResult(providerId: String, message: String) {
        _uiState.value = _uiState.value.copy(
            activeProviderId = providerId,
            activeProviderBusy = false,
            activeProviderMessage = message,
            pendingProviderConsent = null,
        )
    }
}
