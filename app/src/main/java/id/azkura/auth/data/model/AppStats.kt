package id.azkura.auth.data.model

import kotlinx.serialization.Serializable

/** Raw persisted statistics structure, matching src/core/stats.js. */
@Serializable
data class AppStats(
    val copyCounts: Map<String, Int> = emptyMap(),
    val dailyActivity: Map<String, Int> = emptyMap(),
    val lastBackupAt: String? = null,
    val firstAccountCreatedAt: String? = null,
    val totalCopies: Int = 0,
    val lastCopyAt: String? = null,
)

@Serializable
data class ServiceDistribution(
    val name: String,
    val count: Int,
)

@Serializable
data class WeeklyActivity(
    val day: String,
    val date: String,
    val count: Int,
)

@Serializable
data class SecurityStatus(
    val text: String,
    val color: String,
    val icon: String,
)

@Serializable
data class FolderDistribution(
    val name: String,
    val count: Int,
    val color: String,
)

@Serializable
data class AccountUsage(
    val account: Account,
    val copyCount: Int,
)

@Serializable
data class DashboardStats(
    val totalAccounts: Int,
    val totalFolders: Int,
    val securityScore: Int,
    val securityStatus: SecurityStatus,
    val lastBackup: String?,
    val lastBackupAgo: String,
    val totalCopies: Int,
    val lastCopyAgo: String,
    val firstAccountAgo: String,
    val serviceDistribution: List<ServiceDistribution>,
    val weeklyActivity: List<WeeklyActivity>,
    val mostUsed: List<AccountUsage>,
    val folderDistribution: List<FolderDistribution>,
    val topService: ServiceDistribution?,
)

data class SecurityScoreOptions(
    val hasPin: Boolean = false,
    val pinEnabled: Boolean = false,
    val hasGoogleBackup: Boolean = false,
    val hasLocalBackup: Boolean = false,
    val accountCount: Int = 0,
    val hasFolders: Boolean = false,
)
