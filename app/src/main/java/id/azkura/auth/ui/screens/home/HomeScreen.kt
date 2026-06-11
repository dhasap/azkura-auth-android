package id.azkura.auth.ui.screens.home

import android.view.MotionEvent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import id.azkura.auth.data.local.prefs.SortOrder
import id.azkura.auth.data.model.Account
import id.azkura.auth.data.model.Folder
import id.azkura.auth.ui.components.ServiceLogoImage
import id.azkura.auth.ui.theme.Accent
import id.azkura.auth.ui.theme.BgBase
import id.azkura.auth.ui.theme.BgCard
import id.azkura.auth.ui.theme.BgElevated
import id.azkura.auth.ui.theme.BorderSubtle
import id.azkura.auth.ui.theme.Error
import id.azkura.auth.ui.theme.TextMuted
import id.azkura.auth.ui.theme.TextOnAccent
import id.azkura.auth.ui.theme.TextPrimary
import id.azkura.auth.ui.theme.TextSecondary
import id.azkura.auth.ui.theme.TotpDanger
import id.azkura.auth.ui.theme.TotpNormal
import id.azkura.auth.util.ClipboardHelper


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val searchFocusRequester = remember { FocusRequester() }
    val accountListState = rememberLazyListState()
    val accountDragDropState = rememberAccountDragDropState(
        lazyListState = accountListState,
        onMove = viewModel::onMoveAccount,
        onDrop = viewModel::onAccountOrderDrop,
        onCancel = viewModel::onAccountOrderDragCancel,
        onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
    )
    val canReorderAccounts = state.sortOrder == SortOrder.CUSTOM

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var folderBeingEdited by remember { mutableStateOf<Folder?>(null) }
    var editFolderName by remember { mutableStateOf("") }
    var folderBeingDeleted by remember { mutableStateOf<Folder?>(null) }
    var folderBeingManaged by remember { mutableStateOf<Folder?>(null) }
    var folderAccountsBeingManaged by remember { mutableStateOf<Folder?>(null) }
    var accountPendingDelete by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(state.isSearchActive) {
        if (state.isSearchActive) {
            searchFocusRequester.requestFocus()
            keyboard?.show()
        }
    }

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
                                unfocusedTextColor = TextPrimary,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(searchFocusRequester),
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToScanner,
                    icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    text = { Text("Scan QR", fontWeight = FontWeight.SemiBold) },
                    containerColor = Accent,
                    contentColor = TextOnAccent,
                )
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    containerColor = BgElevated,
                    contentColor = Accent,
                ) {
                    Icon(Icons.Default.Add, "Tambah Manual")
                }
            }
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
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
                items(state.folders, key = { it.id }) { folder ->
                    FolderChip(
                        label = folder.name,
                        color = folderColor(folder),
                        selected = state.selectedFolderId == folder.id,
                        onClick = { viewModel.onFolderSelected(folder.id) },
                        onLongPress = { folderBeingManaged = folder },
                    )
                }
                item {
                    FolderChip(
                        label = "+ New Folder",
                        color = TextMuted,
                        selected = false,
                        onClick = { showCreateFolderDialog = true },
                    )
                }
            }

            val dimAlpha = if (state.isSearchActive) 0.72f else 1f
            if (state.accounts.isEmpty() && !state.isLoading) {
                EmptyState(
                    isSearching = state.searchQuery.isNotBlank(),
                    modifier = Modifier.graphicsLayer { alpha = dimAlpha },
                )
            } else {
                LazyColumn(
                    state = accountListState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 124.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .graphicsLayer { alpha = dimAlpha }
                        .then(
                            if (canReorderAccounts) {
                                Modifier.pointerInput(accountDragDropState) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset -> accountDragDropState.onDragStart(offset.y) },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            accountDragDropState.onDrag(dragAmount.y)
                                        },
                                        onDragEnd = accountDragDropState::onDragEnd,
                                        onDragCancel = accountDragDropState::onDragCancel,
                                    )
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    itemsIndexed(state.accounts, key = { _, item -> item.account.id }) { index, item ->
                        val isDragging = accountDragDropState.draggingIndex == index
                        AccountCard(
                            item = item,
                            folders = state.folders,
                            isDragging = isDragging,
                            modifier = Modifier
                                .animateItem()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = accountDragDropState.draggingOffsetFor(index)
                                },
                            onCopy = {
                                ClipboardHelper.copy(context, "TOTP Code", item.code)
                                viewModel.onCopyCode(item.account.id)
                                Toast.makeText(context, "Kode disalin", Toast.LENGTH_SHORT).show()
                            },
                            onEdit = { onNavigateToEdit(item.account.id) },
                            onDelete = { accountPendingDelete = item.account },
                            onMoveToFolder = { folderId ->
                                viewModel.onMoveAccountToFolder(item.account.id, folderId)
                            },
                        )
                    }
                }
            }
        }

        if (showCreateFolderDialog) {
            FolderNameDialog(
                title = "Create Folder",
                value = newFolderName,
                onValueChange = { newFolderName = it },
                confirmText = "Create",
                onDismiss = { showCreateFolderDialog = false },
                onConfirm = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.onCreateFolder(newFolderName)
                        newFolderName = ""
                    }
                    showCreateFolderDialog = false
                },
            )
        }

        folderBeingManaged?.let { folder ->
            FolderActionMenuDialog(
                folder = folder,
                onDismiss = { folderBeingManaged = null },
                onEdit = {
                    folderBeingManaged = null
                    folderBeingEdited = folder
                    editFolderName = folder.name
                },
                onManageAccounts = {
                    folderBeingManaged = null
                    folderAccountsBeingManaged = folder
                },
                onDelete = {
                    folderBeingManaged = null
                    folderBeingDeleted = folder
                },
            )
        }

        folderBeingEdited?.let { folder ->
            FolderNameDialog(
                title = "Edit Nama Folder",
                value = editFolderName,
                onValueChange = { editFolderName = it },
                confirmText = "Save",
                onDismiss = { folderBeingEdited = null },
                onConfirm = {
                    if (editFolderName.isNotBlank()) {
                        viewModel.onRenameFolder(folder.id, editFolderName)
                    }
                    folderBeingEdited = null
                },
            )
        }

        folderAccountsBeingManaged?.let { folder ->
            ManageFolderAccountsDialog(
                folder = folder,
                accounts = state.allVaultAccounts,
                onDismiss = { folderAccountsBeingManaged = null },
                onAccountCheckedChange = { account, checked ->
                    viewModel.onMoveAccountToFolder(account.id, if (checked) folder.id else null)
                },
            )
        }

        folderBeingDeleted?.let { folder ->
            AlertDialog(
                onDismissRequest = { folderBeingDeleted = null },
                title = { Text("Hapus Folder?") },
                text = {
                    Text(
                        "Hanya folder \"${folder.name}\" yang dihapus, akun di dalamnya akan kembali ke tab 'All'.",
                        color = TextSecondary,
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.onDeleteFolder(folder.id)
                        folderBeingDeleted = null
                    }) {
                        Text("Hapus", color = Error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { folderBeingDeleted = null }) {
                        Text("Batal")
                    }
                },
            )
        }

        accountPendingDelete?.let { account ->
            ConfirmDeleteAccountDialog(
                account = account,
                onDismiss = { accountPendingDelete = null },
                onConfirm = {
                    viewModel.onDeleteAccount(account.id)
                    accountPendingDelete = null
                },
            )
        }
    }
}

@Composable
private fun rememberAccountDragDropState(
    lazyListState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    onDragStarted: () -> Unit,
): AccountDragDropState {
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDrop by rememberUpdatedState(onDrop)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val currentOnDragStarted by rememberUpdatedState(onDragStarted)

    return remember(lazyListState) {
        AccountDragDropState(
            lazyListState = lazyListState,
            onMove = { from, to -> currentOnMove(from, to) },
            onDrop = { currentOnDrop() },
            onCancel = { currentOnCancel() },
            onDragStarted = { currentOnDragStarted() },
        )
    }
}

private class AccountDragDropState(
    private val lazyListState: LazyListState,
    private val onMove: (Int, Int) -> Unit,
    private val onDrop: () -> Unit,
    private val onCancel: () -> Unit,
    private val onDragStarted: () -> Unit,
) {
    var draggingIndex by mutableStateOf<Int?>(null)
        private set

    private var draggedDistance by mutableStateOf(0f)
    private var draggingItemSize by mutableStateOf(0)
    private var hasMoved by mutableStateOf(false)

    fun onDragStart(pointerY: Float) {
        val touchedItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            pointerY.toInt() in item.offset..(item.offset + item.size)
        } ?: return

        draggingIndex = touchedItem.index
        draggingItemSize = touchedItem.size
        draggedDistance = 0f
        hasMoved = false
        onDragStarted()
    }

    fun onDrag(dragAmountY: Float) {
        val currentIndex = draggingIndex ?: return
        val currentItem = lazyListState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex } ?: return

        draggedDistance += dragAmountY
        val draggedStart = currentItem.offset + draggedDistance
        val draggedEnd = draggedStart + draggingItemSize

        val targetItem = lazyListState.layoutInfo.visibleItemsInfo
            .filterNot { it.index == currentIndex }
            .firstOrNull { item ->
                val itemCenter = item.offset + item.size / 2f
                itemCenter in draggedStart..draggedEnd
            }
            ?: return

        onMove(currentIndex, targetItem.index)
        draggingIndex = targetItem.index
        draggedDistance += currentItem.offset - targetItem.offset
        hasMoved = true
    }

    fun onDragEnd() {
        if (hasMoved) {
            onDrop()
        }
        reset()
    }

    fun onDragCancel() {
        if (hasMoved) {
            onCancel()
        }
        reset()
    }

    fun draggingOffsetFor(index: Int): Float = if (draggingIndex == index) draggedDistance else 0f

    private fun reset() {
        draggingIndex = null
        draggingItemSize = 0
        draggedDistance = 0f
        hasMoved = false
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun FolderChip(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
) {
    val bg by animateColorAsState(
        if (selected) color.copy(alpha = 0.22f) else BgCard,
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
            .then(
                if (onLongPress != null) {
                    Modifier.pointerInteropFilter { event ->
                        val isSecondaryClick = event.actionMasked == MotionEvent.ACTION_DOWN &&
                            (event.buttonState and MotionEvent.BUTTON_SECONDARY) != 0
                        if (isSecondaryClick) {
                            onLongPress()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(text = label, color = textColor, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FolderNameDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Folder Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = Accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FolderActionMenuDialog(
    folder: Folder,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onManageAccounts: () -> Unit,
    onDelete: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BgElevated,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(folderColor(folder)),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = folder.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            FolderActionRow(
                label = "Edit Nama Folder",
                icon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary) },
                onClick = onEdit,
            )
            FolderActionRow(
                label = "Kelola Akun (Masukkan Akun)",
                icon = { Icon(Icons.Default.Folder, contentDescription = null, tint = Accent) },
                onClick = onManageAccounts,
            )
            FolderActionRow(
                label = "Hapus Folder",
                labelColor = Error,
                icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Error) },
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun FolderActionRow(
    label: String,
    labelColor: Color = TextPrimary,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(modifier = Modifier.width(14.dp))
        Text(label, color = labelColor, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ManageFolderAccountsDialog(
    folder: Folder,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onAccountCheckedChange: (Account, Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Kelola Akun") },
        text = {
            Column {
                Text(
                    text = "Pilih akun yang ingin dimasukkan ke folder \"${folder.name}\".",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (accounts.isEmpty()) {
                    Text("Belum ada akun.", color = TextMuted)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(accounts, key = { it.id }) { account ->
                            val checked = account.folderId == folder.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onAccountCheckedChange(account, !checked) }
                                    .padding(horizontal = 4.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { onAccountCheckedChange(account, it) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Accent,
                                        uncheckedColor = TextMuted,
                                    ),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = account.issuer.ifBlank { "Unknown" },
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (account.account.isNotBlank()) {
                                        Text(
                                            text = account.account,
                                            color = TextMuted,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Selesai", color = Accent) }
        },
    )
}

@Composable
private fun ConfirmDeleteAccountDialog(
    account: Account,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val accountName = account.issuer.ifBlank { account.account.ifBlank { "akun ini" } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus Akun?") },
        text = {
            Text(
                text = "Apakah Anda yakin ingin menghapus $accountName dari Azkura Auth? Anda mungkin akan kehilangan akses ke akun tersebut jika belum menyiapkan metode login lain.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Hapus", color = Error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}

@Composable
private fun OtpCodeText(
    code: String,
    modifier: Modifier = Modifier,
) {
    val normalizedCode = remember(code) { code.filter(Char::isDigit).ifEmpty { code.trim().replace(Regex("\\s+"), " ") } }

    BoxWithConstraints(modifier = modifier) {
        val digitCount = normalizedCode.length
        val fontSize = when {
            digitCount >= 8 && maxWidth < 150.dp -> 21.sp
            digitCount >= 8 && maxWidth < 190.dp -> 24.sp
            maxWidth < 120.dp -> 22.sp
            maxWidth < 150.dp -> 24.sp
            maxWidth < 190.dp -> 26.sp
            else -> 28.sp
        }
        val letterSpacing = when {
            maxWidth < 130.dp -> 0.5.sp
            maxWidth < 170.dp -> 0.9.sp
            else -> 1.2.sp
        }
        val groupGap = when {
            maxWidth < 130.dp -> 4.dp
            maxWidth < 180.dp -> 6.dp
            else -> 8.dp
        }
        val splitIndex = when (digitCount) {
            6 -> 3
            8 -> 4
            else -> null
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (splitIndex != null && normalizedCode.all(Char::isDigit)) {
                OtpDigitGroup(
                    text = normalizedCode.take(splitIndex),
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                )
                Spacer(modifier = Modifier.width(groupGap))
                OtpDigitGroup(
                    text = normalizedCode.drop(splitIndex),
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                )
            } else {
                OtpDigitGroup(
                    text = normalizedCode,
                    fontSize = fontSize,
                    letterSpacing = letterSpacing,
                )
            }
        }
    }
}

@Composable
private fun OtpDigitGroup(
    text: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    letterSpacing: androidx.compose.ui.unit.TextUnit,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            lineHeight = fontSize,
            letterSpacing = letterSpacing,
            fontFeatureSettings = "tnum",
        ),
        color = TextPrimary,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountCard(
    item: AccountWithCode,
    folders: List<Folder>,
    modifier: Modifier = Modifier,
    isDragging: Boolean = false,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMoveToFolder: (String?) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }

    val cardElevation by animateDpAsState(
        targetValue = if (isDragging) 14.dp else 0.dp,
        animationSpec = tween(durationMillis = 160),
        label = "account_drag_elevation",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isDragging) 1.015f else 1f,
        animationSpec = tween(durationMillis = 160),
        label = "account_drag_scale",
    )
    val cardColor by animateColorAsState(
        targetValue = if (isDragging) BgElevated else BgCard,
        animationSpec = tween(durationMillis = 160),
        label = "account_drag_color",
    )
    val progress = item.remainingSeconds.toFloat() / item.period.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 950, easing = LinearEasing),
        label = "totp_progress",
    )
    val isCritical = item.remainingSeconds <= 5
    val blinkAlpha by animateFloatAsState(
        targetValue = if (isCritical) 0.38f else 1f,
        animationSpec = tween(durationMillis = 350, easing = LinearEasing),
        label = "blink",
    )
    val timerColor by animateColorAsState(
        if (isCritical) TotpDanger else TotpNormal,
        label = "timer_color",
    )

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                else -> false
            }
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.EndToStart -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TotpDanger)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = Color.White)
                    }
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Accent)
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Icon(Icons.Default.Edit, "Edit", tint = TextOnAccent)
                    }
                }
                else -> Unit
            }
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                    }
                    .shadow(cardElevation, RoundedCornerShape(14.dp), clip = false)
                    .clip(RoundedCornerShape(14.dp))
                    .background(cardColor)
                    .clickable(onClick = onCopy)
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ServiceLogoImage(
                        serviceName = item.account.issuer,
                        accountHint = item.account.account,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.account.issuer.ifEmpty { "Unknown" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
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
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OtpCodeText(
                        code = item.code,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCopy, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.ContentCopy, "Copy", tint = TextSecondary, modifier = Modifier.size(19.dp))
                        }
                        IconButton(onClick = onEdit, modifier = Modifier.size(38.dp)) {
                            Icon(Icons.Default.Edit, "Edit", tint = TextSecondary, modifier = Modifier.size(19.dp))
                        }
                        CountdownRing(
                            remainingSeconds = item.remainingSeconds,
                            progress = animatedProgress,
                            color = timerColor.copy(alpha = blinkAlpha),
                        )
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(38.dp)) {
                                Icon(Icons.Default.MoreVert, "More", tint = TextSecondary)
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                                containerColor = BgElevated,
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Pindahkan ke Folder...", color = TextPrimary) },
                                    leadingIcon = { Icon(Icons.Default.Folder, null, tint = Accent) },
                                    onClick = {
                                        menuExpanded = false
                                        showMoveDialog = true
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Hapus Akun", color = Error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Error) },
                                    onClick = {
                                        menuExpanded = false
                                        onDelete()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )

    if (showMoveDialog) {
        MoveToFolderDialog(
            folders = folders,
            currentFolderId = item.account.folderId,
            onDismiss = { showMoveDialog = false },
            onSelectFolder = { folderId ->
                onMoveToFolder(folderId)
                showMoveDialog = false
            },
        )
    }
}

@Composable
private fun CountdownRing(
    remainingSeconds: Int,
    progress: Float,
    color: Color,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
        CircularProgressIndicator(
            progress = { progress },
            modifier = Modifier.size(38.dp),
            color = color,
            trackColor = BorderSubtle,
            strokeWidth = 3.dp,
        )
        Text(
            text = remainingSeconds.toString(),
            color = color,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
        )
    }
}

@Composable
private fun MoveToFolderDialog(
    folders: List<Folder>,
    currentFolderId: String?,
    onDismiss: () -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pindahkan ke Folder") },
        text = {
            Column {
                FolderMoveRow(
                    label = "Tanpa folder",
                    selected = currentFolderId == null,
                    color = TextMuted,
                    onClick = { onSelectFolder(null) },
                )
                if (folders.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(modifier = Modifier.height(6.dp))
                }
                folders.forEach { folder ->
                    FolderMoveRow(
                        label = folder.name,
                        selected = currentFolderId == folder.id,
                        color = folderColor(folder),
                        onClick = { onSelectFolder(folder.id) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun FolderMoveRow(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .background(if (selected) color.copy(alpha = 0.16f) else Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = if (selected) color else TextPrimary)
    }
}

@Composable
private fun EmptyState(isSearching: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 32.dp)) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(42.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = if (isSearching) "No results" else "Belum ada kode otentikasi",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isSearching) "Coba kata kunci lain."
                else "Tekan tombol + di bawah untuk memulai, atau Scan QR untuk impor instan.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }
    }
}

private fun folderColor(folder: Folder): Color = try {
    Color(android.graphics.Color.parseColor(folder.color))
} catch (_: Exception) {
    Accent
}
