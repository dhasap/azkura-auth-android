package id.azkura.auth.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import id.azkura.auth.data.model.AppStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Statistics tracking repository.
 * Port of src/core/stats.js — tracks copy counts, daily activity, etc.
 * Persists to a JSON file (same approach as the extension's chrome.storage.local).
 */
@Singleton
class StatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val statsFile = File(context.filesDir, "app_stats.json")
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val _stats = MutableStateFlow(loadStats())
    val stats: StateFlow<AppStats> = _stats.asStateFlow()

    /** Track when user copies a TOTP code. */
    suspend fun trackAccountCopy(accountId: String) {
        val current = _stats.value
        val today = LocalDate.now().format(dateFormatter)

        val newCopyCounts = current.copyCounts.toMutableMap()
        newCopyCounts[accountId] = (newCopyCounts[accountId] ?: 0) + 1

        val newDailyActivity = current.dailyActivity.toMutableMap()
        newDailyActivity[today] = (newDailyActivity[today] ?: 0) + 1

        // Trim daily activity to last 30 days
        val cutoff = LocalDate.now().minusDays(30).format(dateFormatter)
        val trimmedDaily = newDailyActivity.filterKeys { it >= cutoff }

        val updated = current.copy(
            copyCounts = newCopyCounts,
            dailyActivity = trimmedDaily,
            totalCopies = current.totalCopies + 1,
            lastCopyAt = System.currentTimeMillis().toString(),
        )
        saveAndEmit(updated)
    }

    /** Track backup timestamp. */
    suspend fun trackBackup() {
        val updated = _stats.value.copy(
            lastBackupAt = System.currentTimeMillis().toString(),
        )
        saveAndEmit(updated)
    }

    /** Track first account creation. */
    suspend fun trackFirstAccount() {
        if (_stats.value.firstAccountCreatedAt != null) return
        val updated = _stats.value.copy(
            firstAccountCreatedAt = System.currentTimeMillis().toString(),
        )
        saveAndEmit(updated)
    }

    /** Remove stats for a deleted account. */
    suspend fun removeAccountStats(accountId: String) {
        val newCopyCounts = _stats.value.copyCounts.toMutableMap()
        newCopyCounts.remove(accountId)
        val updated = _stats.value.copy(copyCounts = newCopyCounts)
        saveAndEmit(updated)
    }

    /** Reset all stats. */
    suspend fun resetStats() {
        saveAndEmit(AppStats())
    }

    private fun loadStats(): AppStats {
        return try {
            if (statsFile.exists()) {
                json.decodeFromString(statsFile.readText())
            } else {
                AppStats()
            }
        } catch (_: Exception) {
            AppStats()
        }
    }

    private fun saveAndEmit(stats: AppStats) {
        _stats.value = stats
        try {
            statsFile.writeText(json.encodeToString(stats))
        } catch (_: Exception) {
            // Silently fail — not critical
        }
    }
}
