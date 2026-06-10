package id.azkura.auth.ui.screens.addaccount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.data.repository.StatsRepository
import id.azkura.auth.util.UriParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

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
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            accountRepository.observeAllFolders().collect { folders ->
                _uiState.value = _uiState.value.copy(folders = folders)
            }
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
            } catch (e: Exception) {
                _uiState.value = state.copy(error = "Failed to save: ${e.message}")
            }
        }
    }
}
