package id.azkura.auth.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Lock
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BorderMedium
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import id.azkura.auth.util.ClipboardHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // SAF file picker for importing local backup
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onImportLocalBackup(uri)
        }
    }

    // SAF file creator for exporting local backup
    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            viewModel.onExportLocalBackupTo(uri)
        } else {
            viewModel.onExportLocalBackupCancelled()
        }
    }

    val googleAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onGoogleAuthorizationResult(result.data)
        } else {
            viewModel.onGoogleAuthorizationCancelled()
        }
    }

    LaunchedEffect(state.pendingGoogleAuthorization) {
        val pendingIntent = state.pendingGoogleAuthorization ?: return@LaunchedEffect
        try {
            googleAuthorizationLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build(),
            )
            viewModel.onGoogleAuthorizationLaunched()
        } catch (error: Exception) {
            viewModel.onGoogleAuthorizationLaunchFailed(
                error.message ?: "Unable to launch Google sign-in",
            )
        }
    }

    LaunchedEffect(state.pendingExportUri) {
        if (state.pendingExportUri) {
            val timestamp = java.time.Instant.now()
                .atZone(java.time.ZoneOffset.UTC)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
            exportFileLauncher.launch("azkura-backup-$timestamp.json")
        }
    }


    if (state.showBackupPasswordDialog) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::onDismissBackupPasswordDialog,
            title = { Text("Backup Password") },
            text = {
                Column {
                    Text("Jika Anda lupa password ini, data backup tidak dapat dipulihkan.", color = id.azkura.auth.ui.theme.Error, style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("Master Password") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onSetBackupPassword(pwd) }) { Text("Set Password", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissBackupPasswordDialog) { Text("Cancel") }
            }
        )
    }

    if (state.showExportDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideExportDialog,
            title = { Text("Export Data") },
            text = { Text("Pilih format penyimpanan.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideExportDialog()
                    viewModel.onExportVault()
                }) { Text("Vault (Encrypted)", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.hideExportDialog()
                    viewModel.onExportLocalBackup()
                }) { Text("JSON (Unencrypted)") }
            }
        )
    }

    // PIN setup dialog
    if (state.showSetPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::onDismissPinDialog,
            title = { Text("Set PIN") },
            text = {
                Column {
                    Text("Enter a 6 digit PIN to protect your vault.", color = TextSecondary)
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
                    state.pinSetupError?.let { pinError ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            pinError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
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

    state.exportResult?.let { exportText ->
        AlertDialog(
            onDismissRequest = viewModel::clearExportResult,
            title = { Text("Export Vault") },
            text = {
                Text(
                    text = exportText,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ClipboardHelper.copy(context, "Azkura Auth vault export", exportText)
                        viewModel.clearExportResult()
                    },
                ) {
                    Text("Copy", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::clearExportResult) {
                    Text("Close")
                }
            },
        )
    }

    state.googleMessage?.let { googleMsg ->
        AlertDialog(
            onDismissRequest = viewModel::clearGoogleMessage,
            title = { Text("Google Drive") },
            text = {
                Text(
                    text = googleMsg,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearGoogleMessage) {
                    Text("OK", color = Accent)
                }
            },
        )
    }

    state.localBackupMessage?.let { backupMsg ->
        AlertDialog(
            onDismissRequest = viewModel::clearLocalBackupMessage,
            title = { Text("Local Backup") },
            text = {
                Text(
                    text = backupMsg,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::clearLocalBackupMessage) {
                    Text("OK", color = Accent)
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

            if (state.biometricAvailable) {
                SettingsItem(
                    icon = Icons.Default.Fingerprint,
                    title = "Biometric Unlock",
                    subtitle = when {
                        !state.pinEnabled -> "Enable PIN first"
                        state.biometricEnabled -> "Enabled"
                        else -> "Disabled"
                    },
                    trailing = {
                        Switch(
                            checked = state.biometricEnabled,
                            onCheckedChange = viewModel::onToggleBiometric,
                            enabled = state.pinEnabled && state.biometricAvailable,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Accent,
                                checkedTrackColor = Accent.copy(alpha = 0.3f),
                            ),
                        )
                    },
                )
            }

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
                trailing = {
                    TextButton(
                        enabled = !state.isGoogleBusy && activity != null,
                        onClick = {
                            val resolvedActivity = activity ?: return@TextButton
                            if (state.googleUserEmail == null) {
                                viewModel.onConnectGoogle(resolvedActivity)
                            } else {
                                viewModel.onDisconnectGoogle()
                            }
                        },
                    ) {
                        Text(
                            text = if (state.googleUserEmail == null) "Connect" else "Disconnect",
                            color = Accent,
                        )
                    }
                },
            )

            SettingsItem(
                icon = Icons.Default.CloudUpload,
                title = "Google Drive Backup",
                subtitle = when {
                    state.isGoogleBusy -> "Working..."
                    state.lastBackup != null -> "Last: ${state.lastBackup}"
                    else -> "Never backed up"
                },
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = !state.isGoogleBusy && activity != null,
                            onClick = { activity?.let(viewModel::onBackupToGoogleDrive) },
                        ) {
                            Text("Backup", color = Accent)
                        }
                        TextButton(
                            enabled = !state.isGoogleBusy && activity != null,
                            onClick = { activity?.let(viewModel::onRestoreFromGoogleDrive) },
                        ) {
                            Text("Restore", color = Accent)
                        }
                    }
                },
            )

            SettingsItem(
                icon = Icons.Default.Lock,
                title = "Encrypt Backup Data (Advanced)",
                subtitle = "Password required for restore",
                trailing = {
                    Switch(
                        checked = state.encryptBackup,
                        onCheckedChange = viewModel::onToggleEncryptBackup,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f),
                        ),
                    )
                }
            )

            SettingsItem(
                icon = Icons.Default.Share,
                title = "Export Data",
                subtitle = "Export JSON or Vault",
                onClick = viewModel::showExportDialog,
            )

            SettingsItem(
                icon = Icons.Default.FileDownload,
                title = "Import from File",
                subtitle = "Restore from JSON backup file",
                onClick = {
                    importFileLauncher.launch(arrayOf("application/json", "*/*"))
                },
            )

            // ── Display section ──
            SectionHeader("Display")

            @Suppress("DEPRECATION")
            val sortIcon = Icons.Filled.Sort
            SettingsItem(
                icon = sortIcon,
                title = "Sort Order",
                subtitle = state.sortOrder.replaceFirstChar { it.uppercase() },
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Version info
            Text(
                text = "Azkura Auth v2.1.5",
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
