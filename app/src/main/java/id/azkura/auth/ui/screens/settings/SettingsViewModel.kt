package id.azkura.auth.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.crypto.CryptoManager
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
    val autoLockMinutes: Int = 5,
    val sortOrder: String = "custom",
    val googleUserName: String? = null,
    val googleUserEmail: String? = null,
    val lastBackup: String? = null,
    val totalAccounts: Int = 0,
    val showSetPinDialog: Boolean = false,
    val showChangePinDialog: Boolean = false,
    val pinSetupError: String? = null,
    val isLoading: Boolean = true,
    val exportResult: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val cryptoManager: CryptoManager,
    private val vaultManager: VaultManager,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val pinEnabled = preferencesManager.pinEnabled.first()
            val biometricEnabled = preferencesManager.biometricEnabled.first()
            val autoLock = preferencesManager.autoLockMinutes.first()
            val sortOrder = preferencesManager.sortOrder.first()
            val googleName = preferencesManager.googleUserName.first()
            val googleEmail = preferencesManager.googleUserEmail.first()
            val lastBackup = preferencesManager.lastBackupAt.first()
            val count = accountRepository.getAccountCount()

            _uiState.value = SettingsUiState(
                pinEnabled = pinEnabled,
                biometricEnabled = biometricEnabled,
                autoLockMinutes = autoLock,
                sortOrder = sortOrder,
                googleUserName = googleName,
                googleUserEmail = googleEmail,
                lastBackup = lastBackup,
                totalAccounts = count,
                isLoading = false,
            )
        }
    }

    fun onSetupPin(pin: String) {
        if (pin.length < 4) {
            _uiState.value = _uiState.value.copy(pinSetupError = "PIN must be at least 4 digits")
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

    fun onToggleBiometric(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setBiometricEnabled(enabled)
            _uiState.value = _uiState.value.copy(biometricEnabled = enabled)
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
}
