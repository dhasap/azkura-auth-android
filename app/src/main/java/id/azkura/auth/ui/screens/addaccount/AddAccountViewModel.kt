package id.azkura.auth.ui.screens.addaccount

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.remote.CloudSyncProviderRegistry
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.data.repository.StatsRepository
import id.azkura.auth.util.TotpGenerator
import id.azkura.auth.util.UriParser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

private const val TAG = "AddAccountVM"

data class AddAccountUiState(
    val issuer: String = "",
    val account: String = "",
    val secret: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val folders: List<id.azkura.auth.data.model.Folder> = emptyList(),
    val selectedFolderId: String? = null,
    val showAdvanced: Boolean = false,
    val error: String? = null,
    val isSaved: Boolean = false,
)

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val statsRepository: StatsRepository,
    private val savedStateHandle: SavedStateHandle,
    private val providerRegistry: CloudSyncProviderRegistry,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    // Auto-backup debounce + rate limiting
    private var autoBackupJob: Job? = null
    private var lastAutoBackupTime: Long = 0L
    private val AUTO_BACKUP_DEBOUNCE_MS = 10L * 1000L // 10 seconds debounce
    private val AUTO_BACKUP_MIN_INTERVAL_MS = 5L * 60L * 1000L // 5 min between auto-backups

    init {
        viewModelScope.launch {
            accountRepository.observeAllFolders().collect { folders ->
                _uiState.value = _uiState.value.copy(folders = folders)
            }
        }

        // Restore last auto-backup timestamp for rate limiting across restarts
        viewModelScope.launch {
            val saved = preferencesManager.lastAutoBackupAt.first()?.toLongOrNull()
            if (saved != null) lastAutoBackupTime = saved
        }

        // Observe scanned URI results from ScannerScreen. The value is written
        // after this ViewModel is already created, so a one-shot get() in init
        // would miss normal scan flows.
        viewModelScope.launch {
            savedStateHandle.getStateFlow<String?>("scanned_uri", null)
                .collect { uri ->
                    if (uri != null) {
                        parseUri(uri)
                        savedStateHandle["scanned_uri"] = null
                    }
                }
        }
    }

    fun onIssuerChanged(value: String) {
        _uiState.value = _uiState.value.copy(issuer = value, error = null)
    }

    fun onAccountChanged(value: String) {
        _uiState.value = _uiState.value.copy(account = value, error = null)
    }

    fun onSecretChanged(value: String) {
        _uiState.value = _uiState.value.copy(secret = value.uppercase().filter { it.isLetterOrDigit() || it == '=' }, error = null)
    }

    fun onAlgorithmChanged(value: String) {
        _uiState.value = _uiState.value.copy(algorithm = value)
    }

    fun onDigitsChanged(value: Int) {
        _uiState.value = _uiState.value.copy(digits = value.coerceIn(1, 10))
    }

    fun onPeriodChanged(value: Int) {
        _uiState.value = _uiState.value.copy(period = value.coerceIn(1, 300))
    }

    fun onToggleAdvanced() {
        _uiState.value = _uiState.value.copy(showAdvanced = !_uiState.value.showAdvanced)
    }

    fun onFolderSelected(folderId: String?) {
        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
    }

    fun parseUri(uri: String) {
        try {
            val parsed = UriParser.parse(uri)
            // Defense in depth: a QR/deeplink payload can claim to be an
            // otpauth:// URI with a `secret` parameter that isn't valid
            // Base32 (malformed, truncated, or a non-TOTP QR that happens to
            // match the scheme). Reject it here so it never reaches TOTP
            // generation, where an unusable key would otherwise crash the
            // once-a-second code refresh loop on the Home screen.
            if (!TotpGenerator.isValidSecret(parsed.secret)) {
                _uiState.value = _uiState.value.copy(
                    error = "Invalid QR code: secret key is not valid Base32",
                )
                return
            }
            _uiState.value = _uiState.value.copy(
                issuer = parsed.issuer,
                account = parsed.account,
                secret = parsed.secret,
                algorithm = parsed.algorithm,
                digits = parsed.digits,
                period = parsed.period,
                error = null,
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Invalid QR code: ${e.message}")
        }
    }

    fun onSave() {
        val state = _uiState.value
        if (state.secret.isBlank()) {
            _uiState.value = state.copy(error = "Secret key is required")
            return
        }
        if (!TotpGenerator.isValidSecret(state.secret)) {
            _uiState.value = state.copy(error = "Secret key must be valid Base32 (A-Z, 2-7)")
            return
        }

        viewModelScope.launch {
            try {
                val account = Account(
                    id = UUID.randomUUID().toString(),
                    issuer = state.issuer.trim(),
                    account = state.account.trim(),
                    secret = state.secret.trim(),
                    algorithm = state.algorithm,
                    digits = state.digits,
                    period = state.period,
                    folderId = state.selectedFolderId,
                )
                accountRepository.addAccount(account)

                // Track first account
                if (accountRepository.getAccountCount() == 1) {
                    statsRepository.trackFirstAccount()
                }

                _uiState.value = state.copy(isSaved = true)

                // Schedule auto-backup to Google Drive (safety net).
                // Debounced: if user adds multiple accounts quickly, only
                // one backup fires after the last add.
                scheduleAutoBackup()

            } catch (e: Exception) {
                _uiState.value = state.copy(error = "Failed to save: ${e.message}")
            }
        }
    }

    /**
     * Schedule a debounced auto-backup to Google Drive.
     *
     * - Debounce: waits 10s after the LAST addAccount call before backing up.
     * - Rate limit: won't backup more than once every 5 minutes.
     * - Silent: no UI feedback, logs only. This is a safety net, not primary UX.
     */
    private fun scheduleAutoBackup() {
        // Cancel any pending auto-backup (debounce)
        autoBackupJob?.cancel()

        autoBackupJob = viewModelScope.launch {
            delay(AUTO_BACKUP_DEBOUNCE_MS)

            // Rate limit check
            val now = System.currentTimeMillis()
            if (now - lastAutoBackupTime < AUTO_BACKUP_MIN_INTERVAL_MS) {
                Log.d(TAG, "Auto-backup skipped: rate limited (${(now - lastAutoBackupTime) / 1000}s since last)")
                return@launch
            }

            performAutoBackup()
        }
    }

    private suspend fun performAutoBackup() {
        try {
            val provider = providerRegistry.getById("google-drive") ?: run {
                Log.d(TAG, "Auto-backup skipped: no Google Drive provider")
                return
            }

            if (!provider.isSignedIn()) {
                Log.d(TAG, "Auto-backup skipped: Google not connected")
                return
            }

            val token = provider.getAccessToken()
            if (token == null) {
                Log.d(TAG, "Auto-backup skipped: no valid token")
                return
            }

            // Check if backup encryption is enabled (respect user's setting)
            val encryptBackup = preferencesManager.encryptBackup.first()
            val backupPassword = preferencesManager.backupPassword.first()
            if (encryptBackup && backupPassword == null) {
                Log.d(TAG, "Auto-backup skipped: encryption enabled but no backup password set")
                return
            }

            // Prepare backup payload
            val accounts = accountRepository.getAllAccounts()
            val folders = accountRepository.getAllFolders()
            val payload = id.azkura.auth.data.remote.BackupPayload(
                accountsJson = kotlinx.serialization.json.Json.encodeToString(accounts),
                foldersJson = kotlinx.serialization.json.Json.encodeToString(folders),
                accountCount = accounts.size,
                folderCount = folders.size,
                versionName = id.azkura.auth.BuildConfig.VERSION_NAME,
            )

            val result = provider.backup(payload)
            lastAutoBackupTime = System.currentTimeMillis()
            preferencesManager.setLastAutoBackupAt(lastAutoBackupTime.toString())
            Log.i(TAG, "Auto-backup completed: ${result.fileName} (${result.accountCount} accounts)")
        } catch (e: Exception) {
            Log.w(TAG, "Auto-backup failed: ${e.message}")
            // Non-fatal — user can still backup manually
        }
    }
}
