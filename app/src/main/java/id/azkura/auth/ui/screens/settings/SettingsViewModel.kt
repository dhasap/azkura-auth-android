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
import id.azkura.auth.data.remote.GoogleAuthService
import id.azkura.auth.data.remote.GoogleAuthorizationOutcome
import id.azkura.auth.data.remote.GoogleDriveAuthException
import id.azkura.auth.data.remote.GoogleDriveService
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.util.BiometricHelper
import id.azkura.auth.util.LocalBackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val sortOrder: String = "custom",
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
) : ViewModel() {

    private enum class PendingGoogleAction { SIGN_IN, BACKUP, RESTORE }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private var pendingGoogleAction: PendingGoogleAction? = null

    init {
        viewModelScope.launch { refreshState() }
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

    fun onRemovePin() {
        viewModelScope.launch {
            preferencesManager.clearPin()
            _uiState.value = _uiState.value.copy(pinEnabled = false)
        }
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

    fun onSortOrderChanged(order: String) {
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

    fun onExportLocalBackup() {
        _uiState.value = _uiState.value.copy(pendingExportUri = true)
    }

    fun onExportLocalBackupTo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(pendingExportUri = false)
            try {
                val result = localBackupManager.exportToUri(uri)
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Exported ${result.accountCount} account(s) and ${result.folderCount} folder(s) to ${result.fileName}",
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Export failed: ${e.message}",
                )
            }
        }
    }

    fun onExportLocalBackupCancelled() {
        _uiState.value = _uiState.value.copy(pendingExportUri = false)
    }

    fun onImportLocalBackup(uri: Uri) {
        viewModelScope.launch {
            try {
                val result = localBackupManager.importFromUri(uri)
                refreshState()
                _uiState.value = _uiState.value.copy(
                    localBackupMessage = "Imported ${result.importedAccounts} account(s) and ${result.importedFolders} folder(s) from ${result.fileName}",
                )
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

    fun onExportVault() {
        viewModelScope.launch {
            try {
                val encrypted = vaultManager.exportVault()
                _uiState.value = _uiState.value.copy(exportResult = encrypted)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(exportResult = "Error: ${e.message}")
            }
        }
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
}
