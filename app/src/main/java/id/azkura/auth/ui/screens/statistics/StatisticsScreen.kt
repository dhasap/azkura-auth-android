package id.azkura.auth.ui.screens.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.AccentDim
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.BgElevated
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import id.azkura.auth.util.ServiceIconMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val stats by viewModel.dashboardStats.collectAsState()

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = { Text("Statistics", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase),
            )
        },
    ) { paddingValues ->
        val dashboard = stats
        if (dashboard == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = Accent)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Overview cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        title = "Accounts",
                        value = "${dashboard.totalAccounts}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "Folders",
                        value = "${dashboard.totalFolders}",
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "Copies",
                        value = "${dashboard.totalCopies}",
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Security score
            item {
                val statusColor = try {
                    Color(dashboard.securityStatus.color.toColorInt())
                } catch (_: Exception) { Accent }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, null, tint = statusColor, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Security Score", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${dashboard.securityScore}%",
                            style = MaterialTheme.typography.headlineLarge,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            dashboard.securityStatus.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = statusColor,
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { dashboard.securityScore / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = BgElevated,
                    )
                }
            }

            // Weekly activity
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(16.dp),
                ) {
                    Text("Weekly Activity", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        val maxCount = dashboard.weeklyActivity.maxOfOrNull { it.count } ?: 1
                        dashboard.weeklyActivity.forEach { day ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                val barHeight = if (maxCount > 0) (day.count.toFloat() / maxCount * 60).dp else 4.dp
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(barHeight.coerceAtLeast(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (day.count > 0) Accent else BgElevated),
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(day.day, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("${day.count}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                        }
                    }
                }
            }

            // Top services
            if (dashboard.serviceDistribution.isNotEmpty()) {
                item {
                    Text(
                        "Top Services",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                items(dashboard.serviceDistribution) { service ->
                    val meta = ServiceIconMap.getServiceMeta(service.name)
                    val bgColor = try { Color(meta.bg.toColorInt()) } catch (_: Exception) { Accent }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(bgColor),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(meta.letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(service.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        Text("${service.count}", style = MaterialTheme.typography.titleSmall, color = Accent)
                    }
                }
            }

            // Info row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    InfoChip("Last backup", dashboard.lastBackupAgo)
                    InfoChip("Last copy", dashboard.lastCopyAgo)
                    InfoChip("Member since", dashboard.firstAccountAgo)
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = Accent, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}
