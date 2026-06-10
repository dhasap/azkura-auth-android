package id.azkura.auth.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.data.repository.StatsRepository
import id.azkura.auth.util.TotpGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountWithCode(
    val account: Account,
    val code: String,
    val remainingSeconds: Int,
    val period: Int,
)

data class HomeUiState(
    val accounts: List<AccountWithCode> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val statsRepository: StatsRepository,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allAccounts: List<Account> = emptyList()
    private var allFolders: List<Folder> = emptyList()

    init {
        // Observe accounts from Room
        viewModelScope.launch {
            accountRepository.observeAllAccounts().collect { accounts ->
                allAccounts = accounts
                refreshState()
            }
        }

        // Observe folders from Room
        viewModelScope.launch {
            accountRepository.observeAllFolders().collect { folders ->
                allFolders = folders
                refreshState()
            }
        }

        // Tick every second to update TOTP codes + countdown
        viewModelScope.launch {
            while (isActive) {
                refreshState()
                delay(1000)
            }
        }
    }

    private fun refreshState() {
        val now = System.currentTimeMillis()
        val state = _uiState.value
        val query = state.searchQuery
        val folderId = state.selectedFolderId

        val filtered = allAccounts
            .filter { acc ->
                if (folderId != null) acc.folderId == folderId else true
            }
            .filter { acc ->
                if (query.isBlank()) true
                else acc.issuer.contains(query, ignoreCase = true) ||
                    acc.account.contains(query, ignoreCase = true)
            }
            .map { acc ->
                val algo = TotpGenerator.Algorithm.from(acc.algorithm)
                AccountWithCode(
                    account = acc,
                    code = TotpGenerator.generate(
                        secretBase32 = acc.secret,
                        digits = acc.digits,
                        period = acc.period,
                        algorithm = algo,
                        timeMillis = now,
                    ),
                    remainingSeconds = TotpGenerator.getRemainingSeconds(acc.period, now),
                    period = acc.period,
                )
            }

        _uiState.value = state.copy(
            accounts = filtered,
            folders = allFolders,
            isLoading = false,
        )
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        refreshState()
    }

    fun onToggleSearch() {
        val current = _uiState.value
        val newActive = !current.isSearchActive
        _uiState.value = current.copy(
            isSearchActive = newActive,
            searchQuery = if (!newActive) "" else current.searchQuery,
        )
        refreshState()
    }


    fun onCreateFolder(name: String) {
        viewModelScope.launch {
            val id = "folder_${System.currentTimeMillis()}"
            val colors = arrayOf("#00E5FF", "#FF3D00", "#D500F9", "#00E676", "#1DE9B6", "#FFEA00")
            val newFolder = Folder(
                id = id,
                name = name.trim(),
                color = colors.random(),
                order = _uiState.value.folders.size
            )
            accountRepository.addFolder(newFolder)
        }
    }

    fun onFolderSelected(folderId: String?) {

        _uiState.value = _uiState.value.copy(selectedFolderId = folderId)
        refreshState()
    }

    fun onCopyCode(accountId: String) {
        viewModelScope.launch {
            statsRepository.trackAccountCopy(accountId)
        }
    }

    fun onDeleteAccount(accountId: String) {
        viewModelScope.launch {
            accountRepository.deleteAccount(accountId)
            statsRepository.removeAccountStats(accountId)
        }
    }

    fun onLock() {
        vaultManager.lockVault()
    }
}
