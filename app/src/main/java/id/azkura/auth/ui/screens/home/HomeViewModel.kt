package id.azkura.auth.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.crypto.VaultManager
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.local.prefs.SortOrder
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.data.repository.StatsRepository
import id.azkura.auth.util.TotpGenerator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val allVaultAccounts: List<Account> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val selectedFolderId: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val sortOrder: SortOrder = SortOrder.DEFAULT,
    val isLoading: Boolean = true,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val statsRepository: StatsRepository,
    private val preferencesManager: PreferencesManager,
    private val vaultManager: VaultManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var allAccounts: List<Account> = emptyList()
    private var allFolders: List<Folder> = emptyList()
    private var sortOrder: SortOrder = SortOrder.DEFAULT
    private var copyCounts: Map<String, Int> = emptyMap()
    private var pendingCustomOrderIds: List<String>? = null

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

        // Observe sort order preference so the account list reacts instantly
        viewModelScope.launch {
            preferencesManager.sortOrder.collectLatest { order ->
                sortOrder = order
                if (order != SortOrder.CUSTOM) {
                    pendingCustomOrderIds = null
                }
                refreshState()
            }
        }

        // Observe usage stats for the "Most Used" sort order.
        viewModelScope.launch {
            statsRepository.stats.collectLatest { stats ->
                copyCounts = stats.copyCounts
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

        val visibleAccounts = allAccounts
            .filter { acc ->
                if (folderId != null) acc.folderId == folderId else true
            }
            .filter { acc ->
                if (query.isBlank()) true
                else acc.issuer.contains(query, ignoreCase = true) ||
                    acc.account.contains(query, ignoreCase = true)
            }
            .sortedFor(sortOrder, copyCounts)
            .withPendingCustomOrder()

        val filtered = visibleAccounts.map { acc ->
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
            allVaultAccounts = allAccounts,
            folders = allFolders,
            sortOrder = sortOrder,
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



    fun onMoveAccount(fromIndex: Int, toIndex: Int) {
        if (sortOrder != SortOrder.CUSTOM) return

        val currentAccounts = _uiState.value.accounts.toMutableList()
        if (fromIndex !in currentAccounts.indices || toIndex !in currentAccounts.indices || fromIndex == toIndex) return

        val item = currentAccounts.removeAt(fromIndex)
        currentAccounts.add(toIndex, item)
        pendingCustomOrderIds = currentAccounts.map { it.account.id }
        _uiState.value = _uiState.value.copy(accounts = currentAccounts)
    }

    fun onAccountOrderDrop() {
        if (sortOrder != SortOrder.CUSTOM) return
        val visibleOrderIds = pendingCustomOrderIds ?: return
        if (visibleOrderIds.size < 2) {
            pendingCustomOrderIds = null
            return
        }

        viewModelScope.launch {
            val reorderedAccounts = buildGlobalCustomOrder(visibleOrderIds)
                .mapIndexed { index, account -> account.copy(order = index) }
            accountRepository.updateAccounts(reorderedAccounts)
            pendingCustomOrderIds = null
            refreshState()
        }
    }

    fun onAccountOrderDragCancel() {
        if (pendingCustomOrderIds == null) return
        pendingCustomOrderIds = null
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

    fun onRenameFolder(folderId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val folder = allFolders.firstOrNull { it.id == folderId } ?: return@launch
            accountRepository.updateFolder(folder.copy(name = trimmed))
        }
    }

    fun onDeleteFolder(folderId: String) {
        viewModelScope.launch {
            accountRepository.getAllAccounts()
                .filter { it.folderId == folderId }
                .forEach { account ->
                    accountRepository.updateAccount(account.copy(folderId = null))
                }
            accountRepository.deleteFolder(folderId)
            if (_uiState.value.selectedFolderId == folderId) {
                _uiState.value = _uiState.value.copy(selectedFolderId = null)
            }
            refreshState()
        }
    }

    fun onMoveAccountToFolder(accountId: String, folderId: String?) {
        viewModelScope.launch {
            if (folderId != null && allFolders.none { it.id == folderId }) return@launch
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            accountRepository.updateAccount(account.copy(folderId = folderId))
        }
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

    private fun List<Account>.withPendingCustomOrder(): List<Account> {
        if (sortOrder != SortOrder.CUSTOM) return this
        val pendingIds = pendingCustomOrderIds ?: return this
        if (pendingIds.isEmpty()) return this

        val accountsById = associateBy { it.id }
        val pendingAccounts = pendingIds.mapNotNull { id -> accountsById[id] }
        if (pendingAccounts.isEmpty()) return this

        val reorderedIds = pendingAccounts.map { it.id }.toSet()
        return pendingAccounts + filterNot { it.id in reorderedIds }
    }

    private fun buildGlobalCustomOrder(visibleOrderIds: List<String>): List<Account> {
        val currentGlobalOrder = allAccounts.sortedFor(SortOrder.CUSTOM, copyCounts)
        val visibleIdSet = visibleOrderIds.toSet()
        if (visibleIdSet.isEmpty()) return currentGlobalOrder

        val visibleAccountsById = currentGlobalOrder
            .filter { it.id in visibleIdSet }
            .associateBy { it.id }
        val reorderedVisibleAccounts = visibleOrderIds.mapNotNull { id -> visibleAccountsById[id] }
        if (reorderedVisibleAccounts.isEmpty()) return currentGlobalOrder

        var visibleIndex = 0
        return currentGlobalOrder.map { account ->
            if (account.id in visibleIdSet && visibleIndex < reorderedVisibleAccounts.size) {
                reorderedVisibleAccounts[visibleIndex++]
            } else {
                account
            }
        }
    }

    private fun List<Account>.sortedFor(
        order: SortOrder,
        usageCounts: Map<String, Int>,
    ): List<Account> = when (order) {
        SortOrder.CUSTOM -> sortedWith(
            compareBy<Account> { it.order }
                .thenByDescending { it.createdAt },
        )

        SortOrder.ALPHABETICAL -> sortedWith(
            compareBy<Account> { it.issuer.ifBlank { it.account }.lowercase() }
                .thenBy { it.account.lowercase() }
                .thenBy { it.order },
        )

        SortOrder.MOST_USED -> sortedWith(
            compareByDescending<Account> { usageCounts[it.id] ?: 0 }
                .thenBy { it.order }
                .thenByDescending { it.createdAt },
        )

        SortOrder.RECENTLY_ADDED -> sortedWith(
            compareByDescending<Account> { it.createdAt }
                .thenBy { it.order },
        )
    }
}
