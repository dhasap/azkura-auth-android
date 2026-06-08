package id.azkura.auth.ui.screens.editaccount

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.repository.AccountRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditAccountUiState(
    val issuer: String = "",
    val account: String = "",
    val secret: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
    val folderId: String? = null,
    val notes: String? = null,
    val folders: List<Folder> = emptyList(),
    val error: String? = null,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class EditAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val accountId: String = savedStateHandle["accountId"] ?: ""
    private var originalAccount: Account? = null

    private val _uiState = MutableStateFlow(EditAccountUiState())
    val uiState: StateFlow<EditAccountUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId)
            val folders = accountRepository.observeAllFolders().first()
            if (account != null) {
                originalAccount = account
                _uiState.value = EditAccountUiState(
                    issuer = account.issuer,
                    account = account.account,
                    secret = account.secret,
                    algorithm = account.algorithm,
                    digits = account.digits,
                    period = account.period,
                    folderId = account.folderId,
                    notes = account.notes,
                    folders = folders,
                    isLoading = false,
                )
            } else {
                _uiState.value = _uiState.value.copy(error = "Account not found", isLoading = false)
            }
        }
    }

    fun onIssuerChanged(value: String) { _uiState.value = _uiState.value.copy(issuer = value) }
    fun onAccountChanged(value: String) { _uiState.value = _uiState.value.copy(account = value) }
    fun onNotesChanged(value: String) { _uiState.value = _uiState.value.copy(notes = value) }
    fun onFolderChanged(folderId: String?) { _uiState.value = _uiState.value.copy(folderId = folderId) }

    fun onSave() {
        val original = originalAccount ?: return
        viewModelScope.launch {
            val state = _uiState.value
            val updated = original.copy(
                issuer = state.issuer.trim(),
                account = state.account.trim(),
                folderId = state.folderId,
                notes = state.notes?.trim()?.takeIf { it.isNotEmpty() },
            )
            accountRepository.updateAccount(updated)
            _uiState.value = state.copy(isSaved = true)
        }
    }

    fun onDelete() {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
            _uiState.value = _uiState.value.copy(isDeleted = true)
        }
    }
}
