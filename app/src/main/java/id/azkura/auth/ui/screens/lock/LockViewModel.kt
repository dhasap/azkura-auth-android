package id.azkura.auth.ui.screens.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.crypto.CryptoManager
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.local.prefs.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockUiState(
    val pinEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pin: String = "",
    val error: String? = null,
    val isLoading: Boolean = true,
    val isUnlocked: Boolean = false,
    val maxPinLength: Int = 6,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val cryptoManager: CryptoManager,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val pinEnabled = preferencesManager.pinEnabled.first()
            val biometricEnabled = preferencesManager.biometricEnabled.first()
            _uiState.value = _uiState.value.copy(
                pinEnabled = pinEnabled,
                biometricEnabled = biometricEnabled,
                isLoading = false,
            )

            // Auto-unlock if no PIN
            if (!pinEnabled) {
                unlockWithoutPin()
            }
        }
    }

    fun onPinDigit(digit: Char) {
        val current = _uiState.value
        if (current.pin.length >= current.maxPinLength) return
        val newPin = current.pin + digit
        _uiState.value = current.copy(pin = newPin, error = null)

        if (newPin.length == current.maxPinLength) {
            verifyPin(newPin)
        }
    }

    fun onBackspace() {
        val current = _uiState.value
        if (current.pin.isNotEmpty()) {
            _uiState.value = current.copy(pin = current.pin.dropLast(1), error = null)
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            vaultManager.unlockVault(null)
            _uiState.value = _uiState.value.copy(isUnlocked = true)
        }
    }

    fun onBiometricError(message: String) {
        _uiState.value = _uiState.value.copy(pin = "", error = message)
    }

    private fun verifyPin(pin: String) {
        viewModelScope.launch {
            val storedHash = preferencesManager.pinHash.first()
            val storedSalt = preferencesManager.pinSalt.first()

            if (storedHash == null || storedSalt == null) {
                _uiState.value = _uiState.value.copy(pin = "", error = "PIN not configured")
                return@launch
            }

            val valid = cryptoManager.verifyPin(pin, storedHash, storedSalt)
            if (valid) {
                vaultManager.unlockVault(pin)
                _uiState.value = _uiState.value.copy(isUnlocked = true)
            } else {
                _uiState.value = _uiState.value.copy(pin = "", error = "Incorrect PIN")
            }
        }
    }

    private fun unlockWithoutPin() {
        viewModelScope.launch {
            vaultManager.unlockVault(null)
            _uiState.value = _uiState.value.copy(isUnlocked = true)
        }
    }
}
