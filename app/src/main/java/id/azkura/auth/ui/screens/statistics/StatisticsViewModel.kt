package id.azkura.auth.ui.screens.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.azkura.auth.data.local.prefs.PreferencesManager
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.AppStats
import id.azkura.auth.data.model.DashboardStats
import id.azkura.auth.data.model.FolderDistribution
import id.azkura.auth.data.model.SecurityScoreOptions
import id.azkura.auth.data.model.SecurityStatus
import id.azkura.auth.data.model.ServiceDistribution
import id.azkura.auth.data.model.WeeklyActivity
import id.azkura.auth.data.model.AccountUsage
import id.azkura.auth.data.repository.AccountRepository
import id.azkura.auth.data.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val statsRepository: StatsRepository,
    private val preferencesManager: PreferencesManager,
) : ViewModel() {

    private val _dashboardStats = MutableStateFlow<DashboardStats?>(null)
    val dashboardStats: StateFlow<DashboardStats?> = _dashboardStats.asStateFlow()

    init {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccounts()
            val folders = accountRepository.getAllFolders()
            val stats = statsRepository.stats.value
            val pinEnabled = preferencesManager.pinEnabled.first()
            val lastBackup = preferencesManager.lastBackupAt.first()

            _dashboardStats.value = buildDashboard(accounts, stats, pinEnabled, lastBackup, folders.size)
        }
    }

    private fun buildDashboard(
        accounts: List<Account>,
        stats: AppStats,
        pinEnabled: Boolean,
        lastBackup: String?,
        folderCount: Int,
    ): DashboardStats {
        // Service distribution
        val serviceGroups = accounts.groupBy { it.issuer.ifEmpty { "Unknown" } }
        val serviceDistribution = serviceGroups.map { (name, list) ->
            ServiceDistribution(name, list.size)
        }.sortedByDescending { it.count }.take(10)

        // Weekly activity (last 7 days)
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val weeklyActivity = (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val dateStr = date.format(dateFormatter)
            WeeklyActivity(
                day = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                date = dateStr,
                count = stats.dailyActivity[dateStr] ?: 0,
            )
        }

        // Most used accounts
        val mostUsed = stats.copyCounts
            .mapNotNull { (accountId, count) ->
                val account = accounts.find { it.id == accountId }
                if (account != null) AccountUsage(account, count) else null
            }
            .sortedByDescending { it.copyCount }
            .take(5)

        // Security score
        val securityOpts = SecurityScoreOptions(
            hasPin = pinEnabled,
            pinEnabled = pinEnabled,
            hasGoogleBackup = lastBackup != null,
            accountCount = accounts.size,
            hasFolders = folderCount > 0,
        )
        val score = calculateSecurityScore(securityOpts)
        val securityStatus = getSecurityStatus(score)

        // Time ago helpers
        val backupAgo = lastBackup?.toLongOrNull()?.let { formatTimeAgo(it) } ?: "Never"
        val copyAgo = stats.lastCopyAt?.toLongOrNull()?.let { formatTimeAgo(it) } ?: "Never"
        val firstAccountAgo = stats.firstAccountCreatedAt?.toLongOrNull()?.let { formatTimeAgo(it) } ?: "N/A"

        return DashboardStats(
            totalAccounts = accounts.size,
            totalFolders = folderCount,
            securityScore = score,
            securityStatus = securityStatus,
            lastBackup = lastBackup,
            lastBackupAgo = backupAgo,
            totalCopies = stats.totalCopies,
            lastCopyAgo = copyAgo,
            firstAccountAgo = firstAccountAgo,
            serviceDistribution = serviceDistribution,
            weeklyActivity = weeklyActivity,
            mostUsed = mostUsed,
            folderDistribution = emptyList(),
            topService = serviceDistribution.firstOrNull(),
        )
    }

    private fun calculateSecurityScore(opts: SecurityScoreOptions): Int {
        var score = 20 // Base
        if (opts.pinEnabled) score += 30
        if (opts.hasGoogleBackup) score += 25
        if (opts.hasFolders) score += 10
        if (opts.accountCount > 0) score += 15
        return score.coerceAtMost(100)
    }

    private fun getSecurityStatus(score: Int): SecurityStatus = when {
        score >= 80 -> SecurityStatus("Excellent", "#00C853", "shield_check")
        score >= 60 -> SecurityStatus("Good", "#00E5FF", "shield")
        score >= 40 -> SecurityStatus("Fair", "#FF8800", "shield_alert")
        else -> SecurityStatus("Needs Improvement", "#FF3B3B", "shield_warning")
    }

    private fun formatTimeAgo(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }
}
