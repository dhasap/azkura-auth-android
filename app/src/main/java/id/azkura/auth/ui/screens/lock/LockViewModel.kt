package id.azkura.auth.ui.screens.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.crypto.CryptoManager
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.local.prefs.PreferencesManager
import kotlinx.coroutines.delay
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
    val isLockedOut: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val failedAttempts: Int = 0,
)

@HiltViewModel
class LockViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val cryptoManager: CryptoManager,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(LockUiState())
    val uiState: StateFlow<LockUiState> = _uiState.asStateFlow()

    private var failedAttempts = 0
    private var lockoutEndTimeMs = 0L

    companion object {
        private const val MAX_ATTEMPTS_BEFORE_LOCKOUT = 5
        private val LOCKOUT_DURATIONS_MS = longArrayOf(
            30_000L,      // 30 seconds after 5 failures
            60_000L,      // 1 minute after 10 failures
            300_000L,     // 5 minutes after 15 failures
            1_800_000L,   // 30 minutes after 20+ failures
        )
    }

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
        if (current.isLockedOut) return
        if (current.pin.length >= current.maxPinLength) return
        val newPin = current.pin + digit
        _uiState.value = current.copy(pin = newPin, error = null)

        if (newPin.length == current.maxPinLength) {
            verifyPin(newPin)
        }
    }

    fun onBackspace() {
        val current = _uiState.value
        if (current.isLockedOut) return
        if (current.pin.isNotEmpty()) {
            _uiState.value = current.copy(pin = current.pin.dropLast(1), error = null)
        }
    }

    fun onBiometricSuccess() {
        viewModelScope.launch {
            failedAttempts = 0
            vaultManager.unlockVault(null)
            _uiState.value = _uiState.value.copy(isUnlocked = true, failedAttempts = 0)
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
                failedAttempts = 0
                vaultManager.unlockVault(pin)
                _uiState.value = _uiState.value.copy(isUnlocked = true, failedAttempts = 0)
            } else {
                failedAttempts++
                if (failedAttempts >= MAX_ATTEMPTS_BEFORE_LOCKOUT) {
                    startLockout()
                } else {
                    _uiState.value = _uiState.value.copy(
                        pin = "",
                        error = "Incorrect PIN (${MAX_ATTEMPTS_BEFORE_LOCKOUT - failedAttempts} attempts remaining)",
                        failedAttempts = failedAttempts,
                    )
                }
            }
        }
    }

    private fun startLockout() {
        val lockoutIndex = ((failedAttempts - MAX_ATTEMPTS_BEFORE_LOCKOUT) / MAX_ATTEMPTS_BEFORE_LOCKOUT)
            .coerceIn(0, LOCKOUT_DURATIONS_MS.size - 1)
        val lockoutDurationMs = LOCKOUT_DURATIONS_MS[lockoutIndex]
        lockoutEndTimeMs = System.currentTimeMillis() + lockoutDurationMs

        _uiState.value = _uiState.value.copy(
            pin = "",
            error = null,
            isLockedOut = true,
            lockoutRemainingSeconds = (lockoutDurationMs / 1000).toInt(),
            failedAttempts = failedAttempts,
        )

        // Countdown timer
        viewModelScope.launch {
            while (true) {
                val remaining = ((lockoutEndTimeMs - System.currentTimeMillis()) / 1000).toInt()
                if (remaining <= 0) {
                    _uiState.value = _uiState.value.copy(
                        isLockedOut = false,
                        lockoutRemainingSeconds = 0,
                        error = "Try again",
                    )
                    break
                }
                _uiState.value = _uiState.value.copy(lockoutRemainingSeconds = remaining)
                delay(1000)
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
