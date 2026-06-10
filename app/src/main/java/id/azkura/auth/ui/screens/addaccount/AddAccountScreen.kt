package id.azkura.auth.ui.screens.addaccount

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.BgInput
import id.azkura.auth.ui.theme.BorderMedium
import id.azkura.auth.ui.theme.BgElevated
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountScreen(
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: AddAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    Scaffold(
        containerColor = BgBase,
        topBar = {
            TopAppBar(
                title = { Text("Add Account", color = TextPrimary, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextSecondary)
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToScanner) {
                        Icon(Icons.Default.QrCodeScanner, "Scan QR", tint = Accent)
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            val fieldColors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Accent,
                unfocusedBorderColor = BorderMedium,
                focusedLabelColor = Accent,
                unfocusedLabelColor = TextMuted,
                cursorColor = Accent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedContainerColor = BgInput,
                unfocusedContainerColor = BgInput,
            )

            OutlinedTextField(
                value = state.issuer,
                onValueChange = viewModel::onIssuerChanged,
                label = { Text("Service name") },
                placeholder = { Text("e.g. Google, GitHub", color = TextMuted) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.account,
                onValueChange = viewModel::onAccountChanged,
                label = { Text("Account / email") },
                placeholder = { Text("e.g. user@example.com", color = TextMuted) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = state.secret,
                onValueChange = viewModel::onSecretChanged,
                label = { Text("Secret key (Base32)") },
                placeholder = { Text("JBSWY3DPEHPK3PXP", color = TextMuted) },
                singleLine = true,
                colors = fieldColors,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )

            Spacer(modifier = Modifier.height(12.dp))

            var folderExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = folderExpanded,
                onExpandedChange = { folderExpanded = it },
                modifier = Modifier.fillMaxWidth()
            ) {
                val selectedName = state.folders.find { it.id == state.selectedFolderId }?.name ?: "No folder"
                OutlinedTextField(
                    value = selectedName,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Folder (optional)") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = folderExpanded) },
                    colors = fieldColors,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable, enabled = true).fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                ExposedDropdownMenu(
                    expanded = folderExpanded,
                    onDismissRequest = { folderExpanded = false },
                    containerColor = BgElevated
                ) {
                    DropdownMenuItem(
                        text = { Text("No folder", color = TextPrimary) },
                        onClick = {
                            viewModel.onFolderSelected(null)
                            folderExpanded = false
                        }
                    )
                    state.folders.forEach { folder ->
                        DropdownMenuItem(
                            text = { Text(folder.name, color = TextPrimary) },
                            onClick = {
                                viewModel.onFolderSelected(folder.id)
                                folderExpanded = false
                            }
                        )
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Advanced toggle
            TextButton(onClick = viewModel::onToggleAdvanced) {
                Icon(
                    if (state.showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Advanced options", color = TextSecondary)
            }

            AnimatedVisibility(visible = state.showAdvanced) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))

                    // Algorithm dropdown
                    var algoExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = algoExpanded,
                        onExpandedChange = { algoExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = state.algorithm,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Algorithm") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = algoExpanded) },
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            shape = RoundedCornerShape(12.dp),
                        )
                        ExposedDropdownMenu(
                            expanded = algoExpanded,
                            onDismissRequest = { algoExpanded = false },
                        ) {
                            listOf("SHA1", "SHA256", "SHA512").forEach { algo ->
                                DropdownMenuItem(
                                    text = { Text(algo) },
                                    onClick = {
                                        viewModel.onAlgorithmChanged(algo)
                                        algoExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row {
                        OutlinedTextField(
                            value = state.digits.toString(),
                            onValueChange = { viewModel.onDigitsChanged(it.toIntOrNull() ?: 6) },
                            label = { Text("Digits") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedTextField(
                            value = state.period.toString(),
                            onValueChange = { viewModel.onPeriodChanged(it.toIntOrNull() ?: 30) },
                            label = { Text("Period (sec)") },
                            singleLine = true,
                            colors = fieldColors,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            // Error
            state.error?.let { errorText ->
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorText,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = viewModel::onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = Color.Black,
                ),
            ) {
                Text("Add Account", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
