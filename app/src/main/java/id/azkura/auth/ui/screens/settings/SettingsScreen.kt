package id.azkura.auth.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.BgInput
import id.azkura.auth.ui.theme.BorderMedium
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // PIN setup dialog
    if (state.showSetPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::onDismissPinDialog,
            title = { Text("Set PIN") },
            text = {
                Column {
                    Text("Enter a 4-6 digit PIN to protect your vault.", color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("PIN") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = BorderMedium,
                            cursorColor = Accent,
                            focusedTextColor = TextPrimary,
                        ),
                    )
                    if (state.pinSetupError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(state.pinSetupError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onSetupPin(newPin) }) {
                    Text("Set PIN", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissPinDialog) {
                    Text("Cancel")
                }
            },
        )
    }

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgBase),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Security section ──
            SectionHeader("Security")

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "PIN Protection",
                subtitle = if (state.pinEnabled) "Enabled" else "Disabled",
                trailing = {
                    Switch(
                        checked = state.pinEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) viewModel.onShowSetPinDialog()
                            else viewModel.onRemovePin()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f),
                        ),
                    )
                },
            )

            SettingsItem(
                icon = Icons.Default.Fingerprint,
                title = "Biometric Unlock",
                subtitle = if (state.biometricEnabled) "Enabled" else "Disabled",
                trailing = {
                    Switch(
                        checked = state.biometricEnabled,
                        onCheckedChange = viewModel::onToggleBiometric,
                        enabled = state.pinEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f),
                        ),
                    )
                },
            )

            SettingsItem(
                icon = Icons.Default.Timer,
                title = "Auto-lock",
                subtitle = "${state.autoLockMinutes} minutes",
            )

            // ── Backup section ──
            SectionHeader("Backup")

            SettingsItem(
                icon = Icons.Default.Person,
                title = "Google Account",
                subtitle = state.googleUserEmail ?: "Not connected",
            )

            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = "Google Drive Backup",
                subtitle = state.lastBackup?.let { "Last: $it" } ?: "Never backed up",
            )

            SettingsItem(
                icon = Icons.Default.Share,
                title = "Export Vault",
                subtitle = "${state.totalAccounts} accounts",
                onClick = viewModel::onExportVault,
            )

            // ── Display section ──
            SectionHeader("Display")

            SettingsItem(
                icon = Icons.Filled.Sort,
                title = "Sort Order",
                subtitle = state.sortOrder.replaceFirstChar { it.uppercase() },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Version info
            Text(
                text = "Azkura Auth v1.0.0",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = Accent,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
        if (trailing != null) {
            trailing()
        }
    }
}
