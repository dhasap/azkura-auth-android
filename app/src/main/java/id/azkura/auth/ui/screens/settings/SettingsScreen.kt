package id.azkura.auth.ui.screens.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.BuildConfig
import id.azkura.auth.data.local.prefs.SortOrder
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.AccentDim2
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgElevated
import id.azkura.auth.ui.theme.BorderMedium
import id.azkura.auth.ui.theme.Error
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import id.azkura.auth.util.ClipboardHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    var showSortOrderSheet by remember { mutableStateOf(false) }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.onImportLocalBackup(uri)
        }
    }

    val exportVaultFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            viewModel.onExportVaultFileTo(uri)
        } else {
            viewModel.onExportVaultFileCancelled()
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
            exportVaultFileLauncher.launch("azkura-vault-$timestamp.vault")
        }
    }

    if (state.showBackupPasswordDialog) {
        var pwd by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = viewModel::onDismissBackupPasswordDialog,
            title = { Text("Backup Password") },
            text = {
                Column {
                    Text(
                        "Jika Anda lupa password ini, data backup tidak dapat dipulihkan.",
                        color = Error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pwd,
                        onValueChange = { pwd = it },
                        label = { Text("Master Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onSetBackupPassword(pwd) }) {
                    Text("Set Password", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onDismissBackupPasswordDialog) { Text("Cancel") }
            },
        )
    }

    if (showSortOrderSheet) {
        SortOrderBottomSheet(
            selectedOrder = state.sortOrder,
            sheetState = bottomSheetState,
            onDismiss = { showSortOrderSheet = false },
            onOrderSelected = { order ->
                viewModel.onSortOrderChanged(order)
                coroutineScope.launch {
                    bottomSheetState.hide()
                    showSortOrderSheet = false
                }
            },
        )
    }

    if (state.showExportDialog) {
        AlertDialog(
            onDismissRequest = viewModel::hideExportDialog,
            title = { Text("Export Data") },
            text = {
                Column {
                    Text(
                        "Pilih mode ekspor vault terenkripsi.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "• Simpan sebagai file .vault untuk backup lewat file manager.\n" +
                            "• Bagikan kode teks jika ingin mengirim payload terenkripsi secara manual.",
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.hideExportDialog()
                    viewModel.onExportVaultFile()
                }) {
                    Text("Simpan File (.vault)", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.hideExportDialog()
                    viewModel.onShareVaultText()
                }) {
                    Text("Bagikan Kode Teks")
                }
            },
        )
    }

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
                        visualTransformation = PasswordVisualTransformation(),
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
                TextButton(onClick = viewModel::onDismissPinDialog) { Text("Cancel") }
            },
        )
    }

    state.exportResult?.let { exportText ->
        AlertDialog(
            onDismissRequest = viewModel::clearExportResult,
            title = { Text("Bagikan Kode Vault") },
            text = {
                Text(
                    text = "Kode vault terenkripsi sudah siap. Gunakan Share untuk mengirim sebagai teks, atau Copy untuk menyalin ke clipboard.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "Azkura Auth encrypted vault")
                            putExtra(Intent.EXTRA_TEXT, exportText)
                        }
                        val chooser = Intent.createChooser(shareIntent, "Bagikan Kode Vault")
                        if (context !is Activity) {
                            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(chooser)
                        viewModel.clearExportResult()
                    },
                ) {
                    Text("Share", color = Accent)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        ClipboardHelper.copy(context, "Azkura Auth vault export", exportText)
                        viewModel.clearExportResult()
                    },
                ) {
                    Text("Copy")
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
            title = { Text("Backup / Export") },
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
                subtitle = if (state.encryptBackup) "Enabled — password required for restore" else "Off — Drive backup is seamless JSON",
                trailing = {
                    Switch(
                        checked = state.encryptBackup,
                        onCheckedChange = viewModel::onToggleEncryptBackup,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Accent,
                            checkedTrackColor = Accent.copy(alpha = 0.3f),
                        ),
                    )
                },
            )

            SettingsItem(
                icon = Icons.Default.FileUpload,
                title = "Export Data",
                subtitle = "Simpan file .vault atau bagikan kode teks",
                onClick = viewModel::showExportDialog,
            )

            SettingsItem(
                icon = Icons.Default.FileDownload,
                title = "Import from File",
                subtitle = "Restore from JSON backup or encrypted .vault",
                onClick = {
                    importFileLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                },
            )

            SectionHeader("Display")

            SettingsItem(
                icon = Icons.AutoMirrored.Filled.Sort,
                title = "Sort Order",
                subtitle = state.sortOrder.label,
                onClick = { showSortOrderSheet = true },
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Azkura Auth v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp),
            )

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortOrderBottomSheet(
    selectedOrder: SortOrder,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onOrderSelected: (SortOrder) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) {
            Text(
                text = "Sort Order",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            HorizontalDivider(color = BorderMedium)
            Spacer(modifier = Modifier.height(8.dp))

            SortOrder.entries.forEach { option ->
                SortOrderOptionRow(
                    option = option,
                    selected = option == selectedOrder,
                    onClick = { onOrderSelected(option) },
                )
            }
        }
    }
}

@Composable
private fun SortOrderOptionRow(
    option: SortOrder,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val textColor = if (selected) Accent else TextSecondary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AccentDim2 else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Accent,
                modifier = Modifier.size(22.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(22.dp))
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
