package id.azkura.auth.ui.screens.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.AccentDim
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.BgElevated
import id.azkura.auth.ui.theme.BgInput
import id.azkura.auth.ui.theme.BorderSubtle
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import id.azkura.auth.ui.theme.TotpDanger
import id.azkura.auth.ui.theme.TotpNormal
import id.azkura.auth.ui.theme.TotpWarning
import id.azkura.auth.util.ClipboardHelper
import id.azkura.auth.util.ServiceIconMap

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToScanner: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToStats: () -> Unit,
    onLock: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = {
                    if (state.isSearchActive) {
                        TextField(
                            value = state.searchQuery,
                            onValueChange = viewModel::onSearchQueryChanged,
                            placeholder = { Text("Search accounts...", color = TextMuted) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = Accent,
                                focusedTextColor = TextPrimary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text("Azkura Auth", color = Accent, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase),
                actions = {
                    IconButton(onClick = viewModel::onToggleSearch) {
                        Icon(
                            if (state.isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = TextSecondary,
                        )
                    }
                    IconButton(onClick = onNavigateToStats) {
                        Icon(Icons.Default.BarChart, "Statistics", tint = TextSecondary)
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = TextSecondary)
                    }
                    IconButton(onClick = {
                        viewModel.onLock()
                        onLock()
                    }) {
                        Icon(Icons.Default.Lock, "Lock", tint = TextSecondary)
                    }
                },
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                FloatingActionButton(
                    onClick = onNavigateToScanner,
                    containerColor = BgElevated,
                    contentColor = Accent,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                ) {
                    Icon(Icons.Default.QrCodeScanner, "Scan QR", modifier = Modifier.size(22.dp))
                }
                Spacer(modifier = Modifier.height(12.dp))
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    containerColor = Accent,
                    contentColor = Color.Black,
                ) {
                    Icon(Icons.Default.Add, "Add Account")
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Folder filter chips
            if (state.folders.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    item {
                        FolderChip(
                            label = "All",
                            color = Accent,
                            selected = state.selectedFolderId == null,
                            onClick = { viewModel.onFolderSelected(null) },
                        )
                    }
                    items(state.folders) { folder ->
                        FolderChip(
                            label = folder.name,
                            color = try { Color(folder.color.toColorInt()) } catch (_: Exception) { Accent },
                            selected = state.selectedFolderId == folder.id,
                            onClick = { viewModel.onFolderSelected(folder.id) },
                        )
                    }
                }
            }

            if (state.accounts.isEmpty() && !state.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "No results" else "No accounts yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextMuted,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.searchQuery.isNotBlank()) "Try a different search"
                            else "Tap + to add your first account",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.accounts, key = { it.account.id }) { item ->
                        AccountCard(
                            item = item,
                            onCopy = {
                                ClipboardHelper.copy(context, "TOTP Code", item.code)
                                viewModel.onCopyCode(item.account.id)
                            },
                            onEdit = { onNavigateToEdit(item.account.id) },
                            onDelete = { viewModel.onDeleteAccount(item.account.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(
        if (selected) color.copy(alpha = 0.2f) else BgCard,
        label = "chip_bg",
    )
    val textColor by animateColorAsState(
        if (selected) color else TextSecondary,
        label = "chip_text",
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = textColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AccountCard(
    item: AccountWithCode,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val meta = ServiceIconMap.getServiceMeta(item.account.issuer)
    val bgColor = try { Color(meta.bg.toColorInt()) } catch (_: Exception) { Accent }
    val progress = item.remainingSeconds.toFloat() / item.period.toFloat()
    val animatedProgress by animateFloatAsState(progress, label = "totp_progress")
    val timerColor by animateColorAsState(
        when {
            item.remainingSeconds <= 5 -> TotpDanger
            item.remainingSeconds <= 10 -> TotpWarning
            else -> TotpNormal
        },
        label = "timer_color",
    )

    // Format code with space in middle: "123 456"
    val formattedCode = if (item.code.length == 6) {
        "${item.code.substring(0, 3)} ${item.code.substring(3)}"
    } else if (item.code.length == 8) {
        "${item.code.substring(0, 4)} ${item.code.substring(4)}"
    } else {
        item.code
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BgCard)
            .clickable(onClick = onCopy)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Service icon circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = meta.letter,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Account info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.account.issuer.ifEmpty { "Unknown" },
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.account.account.isNotEmpty()) {
                Text(
                    text = item.account.account,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formattedCode,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp,
                ),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        }

        // Timer & actions
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(36.dp),
                    color = timerColor,
                    trackColor = BorderSubtle,
                    strokeWidth = 3.dp,
                )
                Text(
                    text = "${item.remainingSeconds}",
                    style = MaterialTheme.typography.labelSmall,
                    color = timerColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Icon(
                    Icons.Default.ContentCopy,
                    "Copy",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onCopy),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.Edit,
                    "Edit",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable(onClick = onEdit),
                )
            }
        }
    }
}
