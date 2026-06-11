package id.azkura.auth.ui.screens.statistics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.ui.components.ServiceLogoImage
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.BgElevated
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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

        val overviewVisible = rememberDelayedVisibility(delayMillis = 80)
        val securityVisible = rememberDelayedVisibility(delayMillis = 240)
        val chartVisible = rememberDelayedVisibility(delayMillis = 400)
        val topServicesVisible = rememberDelayedVisibility(delayMillis = 560)
        val infoVisible = rememberDelayedVisibility(delayMillis = 720)

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredFadeIn(overviewVisible),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StatCard(
                        title = "Accounts",
                        targetValue = dashboard.totalAccounts,
                        animationStarted = overviewVisible,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "Folders",
                        targetValue = dashboard.totalFolders,
                        animationStarted = overviewVisible,
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        title = "Copies",
                        targetValue = dashboard.totalCopies,
                        animationStarted = overviewVisible,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Security score
            item {
                val statusColor = try {
                    Color(dashboard.securityStatus.color.toColorInt())
                } catch (_: Exception) { Accent }
                val animatedSecurityScore by animateIntAsState(
                    targetValue = if (securityVisible) dashboard.securityScore else 0,
                    animationSpec = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
                    label = "security_score_counter",
                )
                val animatedSecurityProgress by animateFloatAsState(
                    targetValue = if (securityVisible) dashboard.securityScore / 100f else 0f,
                    animationSpec = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
                    label = "security_score_progress",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredFadeIn(securityVisible)
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
                            "$animatedSecurityScore%",
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
                        progress = { animatedSecurityProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = statusColor,
                        trackColor = BgElevated,
                    )
                }
            }

            // Weekly activity
            item {
                val chartProgress by animateFloatAsState(
                    targetValue = if (chartVisible) 1f else 0f,
                    animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                    label = "weekly_activity_fill",
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .staggeredFadeIn(chartVisible)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgCard)
                        .padding(16.dp),
                ) {
                    Text("Weekly Activity", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        val maxCount = (dashboard.weeklyActivity.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
                        dashboard.weeklyActivity.forEachIndexed { index, day ->
                            val perBarProgress = (chartProgress - (index * 0.07f)).coerceIn(0f, 1f)
                            val targetBarHeight = if (maxCount > 0) {
                                (day.count.toFloat() / maxCount * 60f).coerceAtLeast(4f)
                            } else {
                                4f
                            }
                            val barHeight = if (day.count > 0) {
                                (4f + ((targetBarHeight - 4f) * perBarProgress)).dp
                            } else {
                                4.dp
                            }
                            val displayedCount = (day.count * perBarProgress).roundToInt().coerceAtMost(day.count)

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier.height(64.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(20.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (day.count > 0) Accent else BgElevated),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(day.day, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                Text("$displayedCount", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
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
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .staggeredFadeIn(topServicesVisible),
                    )
                }
                itemsIndexed(
                    items = dashboard.serviceDistribution,
                    key = { _, service -> service.name },
                ) { index, service ->
                    val serviceRowVisible = rememberDelayedVisibility(delayMillis = 620 + (index * 55))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggeredFadeIn(serviceRowVisible)
                            .clip(RoundedCornerShape(12.dp))
                            .background(BgCard)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ServiceLogoImage(
                            serviceName = service.name,
                            size = 32.dp,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(service.name, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, modifier = Modifier.weight(1f))
                        AnimatedNumberText(
                            targetValue = service.count,
                            animationStarted = serviceRowVisible,
                            style = MaterialTheme.typography.titleSmall,
                            color = Accent,
                        )
                    }
                }
            }

            // Info row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .staggeredFadeIn(infoVisible),
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
private fun StatCard(
    title: String,
    targetValue: Int,
    animationStarted: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedNumberText(
            targetValue = targetValue,
            animationStarted = animationStarted,
            style = MaterialTheme.typography.headlineMedium,
            color = Accent,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun AnimatedNumberText(
    targetValue: Int,
    animationStarted: Boolean,
    modifier: Modifier = Modifier,
    suffix: String = "",
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = TextPrimary,
    fontWeight: FontWeight? = null,
) {
    val animatedValue by animateIntAsState(
        targetValue = if (animationStarted) targetValue else 0,
        animationSpec = tween(durationMillis = 1_000, easing = FastOutSlowInEasing),
        label = "stats_number_counter",
    )

    Text(
        text = "$animatedValue$suffix",
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
    )
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@Composable
private fun rememberDelayedVisibility(delayMillis: Int): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(delayMillis) {
        delay(delayMillis.toLong())
        visible = true
    }
    return visible
}

@Composable
private fun Modifier.staggeredFadeIn(visible: Boolean): Modifier {
    val density = LocalDensity.current
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "stats_stagger_alpha",
    )
    val offsetY by animateDpAsState(
        targetValue = if (visible) 0.dp else 18.dp,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "stats_stagger_offset",
    )

    return graphicsLayer {
        this.alpha = alpha
        translationY = with(density) { offsetY.toPx() }
    }
}
