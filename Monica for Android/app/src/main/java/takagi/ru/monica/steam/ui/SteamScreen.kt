package takagi.ru.monica.steam.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.net.HttpURLConnection
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.network.SteamPendingLogin
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.TotpCodeCard
import takagi.ru.monica.ui.password.PasswordTopActionsDropdownMenu

private const val STEAM_AVATAR_TIMEOUT_MS = 4_000
private const val STEAM_AVATAR_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L

private enum class SteamSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    CODE(R.string.steam_section_code, Icons.Default.Key),
    CONFIRMATIONS(R.string.steam_section_confirmations, Icons.Default.Check)
}

private enum class SteamAddAccountMethod {
    MAFILE,
    LOGIN
}

private data class ConfirmationActionRequest(
    val confirmations: List<SteamConfirmation>,
    val accept: Boolean
)

private data class LoginActionRequest(
    val login: SteamPendingLogin
)

private data class SteamDeleteAccountsRequest(
    val accounts: List<SteamAccount>
)

@Composable
fun SteamScreen(
    showStandaloneSettingsEntry: Boolean,
    onOpenStandaloneSettings: () -> Unit,
    modifier: Modifier = Modifier,
    pendingSteamQrResult: String? = null,
    onConsumePendingSteamQrResult: () -> Unit = {},
    onScanSteamQrCode: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccount = uiState.accounts.firstOrNull { it.id == uiState.selectedAccountId }
        ?: uiState.accounts.firstOrNull()
    var detailAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detailAccount = uiState.accounts.firstOrNull { it.id == detailAccountId }
    var selectedSection by rememberSaveable { mutableStateOf(SteamSection.CODE) }
    var showTopActionsMenu by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var addAccountMethod by remember { mutableStateOf<SteamAddAccountMethod?>(null) }
    var selectedTokenAccountIds by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var deleteRequest by remember { mutableStateOf<SteamDeleteAccountsRequest?>(null) }
    var scannedQrPayload by remember { mutableStateOf<String?>(null) }
    val pendingConfirmationCount = if (selectedAccount?.canUseConfirmations == true) {
        uiState.confirmations.size
    } else {
        0
    }

    LaunchedEffect(selectedAccount?.id) {
        if (selectedAccount == null) {
            selectedSection = SteamSection.CODE
        }
    }

    LaunchedEffect(detailAccountId, uiState.accounts) {
        if (detailAccountId != null && detailAccount == null) {
            detailAccountId = null
        }
    }

    LaunchedEffect(uiState.accounts) {
        val existingIds = uiState.accounts.map { it.id }.toSet()
        val prunedSelection = selectedTokenAccountIds.filter { it in existingIds }
        if (prunedSelection != selectedTokenAccountIds) {
            selectedTokenAccountIds = prunedSelection
        }
    }

    LaunchedEffect(selectedAccount?.id, selectedAccount?.canUseConfirmations) {
        if (selectedAccount?.canUseConfirmations == true) {
            viewModel.refreshConfirmations(silent = true)
        }
    }

    LaunchedEffect(detailAccount?.id, detailAccount?.canApproveLogins) {
        if (detailAccount?.canApproveLogins == true) {
            viewModel.refreshPendingLogins(silent = true)
        }
    }

    LaunchedEffect(uiState.accounts.size) {
        if (uiState.accounts.isNotEmpty()) {
            showAddAccountDialog = false
            addAccountMethod = null
        }
    }

    LaunchedEffect(pendingSteamQrResult) {
        val qr = pendingSteamQrResult?.trim().orEmpty()
        if (qr.isNotBlank()) {
            selectedAccount?.let { detailAccountId = it.id }
            scannedQrPayload = qr
            onConsumePendingSteamQrResult()
        }
    }

    if (detailAccount != null) {
        BackHandler {
            detailAccountId = null
            scannedQrPayload = null
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (showAddAccountDialog) {
        SteamAddMethodDialog(
            onDismissRequest = { showAddAccountDialog = false },
            onSelectMaFile = {
                showAddAccountDialog = false
                addAccountMethod = SteamAddAccountMethod.MAFILE
            },
            onSelectLogin = {
                showAddAccountDialog = false
                addAccountMethod = SteamAddAccountMethod.LOGIN
            }
        )
    }

    when (addAccountMethod) {
        SteamAddAccountMethod.MAFILE -> SteamMaFileImportDialog(
            onDismissRequest = { addAccountMethod = null },
            onImportMaFile = viewModel::importMaFile
        )
        SteamAddAccountMethod.LOGIN -> SteamLoginImportDialog(
            pendingChallenge = uiState.pendingLoginChallenge,
            onDismissRequest = {
                viewModel.cancelSteamLoginChallenge()
                addAccountMethod = null
            },
            onBeginLogin = viewModel::beginSteamLogin,
            onSubmitLoginCode = viewModel::submitSteamLoginCode
        )
        null -> Unit
    }

    deleteRequest?.let { request ->
        val accountsToDelete = request.accounts
        AlertDialog(
            onDismissRequest = { deleteRequest = null },
            title = {
                Text(
                    stringResource(
                        if (accountsToDelete.size == 1) {
                            R.string.steam_delete_account_title
                        } else {
                            R.string.steam_delete_accounts_title
                        }
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            if (accountsToDelete.size == 1) {
                                R.string.steam_delete_account_message
                            } else {
                                R.string.steam_delete_accounts_message
                            },
                            if (accountsToDelete.size == 1) {
                                accountsToDelete.first().displayName
                            } else {
                                accountsToDelete.size
                            }
                        )
                    )
                    accountsToDelete.take(8).forEach { account ->
                        Text(
                            text = account.displayName.ifBlank {
                                account.accountName.ifBlank { account.steamId }
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accountsToDelete.forEach { account ->
                            viewModel.deleteAccount(account.id)
                        }
                        selectedTokenAccountIds = emptyList()
                        if (accountsToDelete.any { it.id == detailAccountId }) {
                            detailAccountId = null
                        }
                        deleteRequest = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteRequest = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            ExpressiveTopBar(
                title = stringResource(R.string.nav_steam),
                searchQuery = "",
                onSearchQueryChange = {},
                isSearchExpanded = false,
                onSearchExpandedChange = {},
                searchHint = stringResource(R.string.nav_steam),
                navigationIcon = if (detailAccount != null) {
                    {
                        IconButton(
                            onClick = {
                                detailAccountId = null
                                scannedQrPayload = null
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    null
                },
                actions = {
                    if (detailAccount == null) {
                        IconButton(
                            onClick = {
                                showTopActionsMenu = false
                                showAddAccountDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.steam_add_account_button),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (detailAccount == null && (selectedAccount != null || showStandaloneSettingsEntry)) {
                        Box {
                            IconButton(
                                onClick = {
                                    showTopActionsMenu = true
                                }
                            ) {
                                BadgedBox(
                                    badge = {
                                        if (pendingConfirmationCount > 0) {
                                            Badge {
                                                Text(badgeCountText(pendingConfirmationCount))
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = if (pendingConfirmationCount > 0) {
                                            stringResource(
                                                R.string.steam_more_options_with_confirmations,
                                                pendingConfirmationCount
                                            )
                                        } else {
                                            stringResource(R.string.more_options)
                                        },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            SteamTopActionsMenu(
                                expanded = showTopActionsMenu,
                                onDismissRequest = { showTopActionsMenu = false },
                                selectedAccount = selectedAccount,
                                selectedSection = selectedSection,
                                pendingConfirmationCount = pendingConfirmationCount,
                                showStandaloneSettingsEntry = showStandaloneSettingsEntry,
                                onSelectSection = { section ->
                                    selectedSection = section
                                    when (section) {
                                        SteamSection.CONFIRMATIONS -> viewModel.refreshConfirmations()
                                        SteamSection.CODE -> Unit
                                    }
                                    showTopActionsMenu = false
                                },
                                onRefresh = {
                                    when (selectedSection) {
                                        SteamSection.CONFIRMATIONS -> viewModel.refreshConfirmations()
                                        SteamSection.CODE -> Unit
                                    }
                                    showTopActionsMenu = false
                                },
                                onOpenStandaloneSettings = {
                                    showTopActionsMenu = false
                                    onOpenStandaloneSettings()
                                }
                            )
                        }
                    } else if (detailAccount != null && showStandaloneSettingsEntry) {
                        Box {
                            IconButton(
                                onClick = { showTopActionsMenu = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.more_options),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            SteamTopActionsMenu(
                                expanded = showTopActionsMenu,
                                onDismissRequest = { showTopActionsMenu = false },
                                selectedAccount = null,
                                selectedSection = selectedSection,
                                pendingConfirmationCount = 0,
                                showStandaloneSettingsEntry = showStandaloneSettingsEntry,
                                onSelectSection = {},
                                onRefresh = {},
                                onOpenStandaloneSettings = {
                                    showTopActionsMenu = false
                                    onOpenStandaloneSettings()
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            val account = detailAccount
            val scanQr = onScanSteamQrCode
            if (account != null && account.canApproveLogins && scanQr != null) {
                FloatingActionButton(onClick = scanQr) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.scan_qr_code)
                    )
                }
            }
        }
    ) { contentPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                if (uiState.loading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                if (detailAccount != null) {
                    SteamAccountDetailContent(
                        account = detailAccount,
                        pendingLogins = uiState.pendingLogins,
                        pendingScannedQr = scannedQrPayload,
                        onScannedQrHandled = { scannedQrPayload = null },
                        onRefreshLogins = { viewModel.refreshPendingLogins() },
                        onRespondPending = viewModel::respondPendingLogin,
                        onRespondQr = viewModel::respondQr
                    )
                } else if (selectedAccount == null) {
                    SteamEmptyAccountContent(
                        onAddAccount = { showAddAccountDialog = true }
                    )
                } else {
                    when (selectedSection) {
                        SteamSection.CODE -> SteamCodeContent(
                            accounts = uiState.accounts,
                            selectedAccountIds = selectedTokenAccountIds,
                            onToggleSelection = { account ->
                                selectedTokenAccountIds = if (account.id in selectedTokenAccountIds) {
                                    selectedTokenAccountIds - account.id
                                } else {
                                    selectedTokenAccountIds + account.id
                                }
                            },
                            onClearSelection = {
                                selectedTokenAccountIds = emptyList()
                            },
                            onDeleteSelected = {
                                val targets = uiState.accounts.filter { it.id in selectedTokenAccountIds }
                                if (targets.isNotEmpty()) {
                                    deleteRequest = SteamDeleteAccountsRequest(targets)
                                }
                            },
                            onOpenDetail = { account ->
                                selectedTokenAccountIds = emptyList()
                                viewModel.selectAccount(account.id)
                                detailAccountId = account.id
                            }
                        )
                        SteamSection.CONFIRMATIONS -> SteamConfirmationsContent(
                            account = selectedAccount,
                            confirmations = uiState.confirmations,
                            selectedIds = uiState.selectedConfirmationIds,
                            onRefresh = { viewModel.refreshConfirmations() },
                            onToggle = viewModel::toggleConfirmation,
                            onSelectAll = viewModel::selectAllConfirmations,
                            onClearSelection = viewModel::clearSelectedConfirmations,
                            onRespond = viewModel::respondConfirmation,
                            onRespondSelected = viewModel::respondSelectedConfirmations
                        )
                    }
                }
            }

            if (uiState.loading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun SteamTopActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    selectedAccount: SteamAccount?,
    selectedSection: SteamSection,
    pendingConfirmationCount: Int,
    showStandaloneSettingsEntry: Boolean,
    onSelectSection: (SteamSection) -> Unit,
    onRefresh: () -> Unit,
    onOpenStandaloneSettings: () -> Unit
) {
    PasswordTopActionsDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (selectedAccount != null) {
            SteamSection.values().forEach { section ->
                DropdownMenuItem(
                    text = { Text(stringResource(section.labelRes)) },
                    leadingIcon = {
                        Icon(
                            imageVector = if (section == selectedSection) Icons.Default.Check else section.icon,
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (section == SteamSection.CONFIRMATIONS && pendingConfirmationCount > 0) {
                        {
                            Badge {
                                Text(badgeCountText(pendingConfirmationCount))
                            }
                        }
                    } else {
                        null
                    },
                    onClick = { onSelectSection(section) }
                )
            }
            if (selectedSection == SteamSection.CONFIRMATIONS) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.refresh)) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = onRefresh
                )
            }
        }
        if (showStandaloneSettingsEntry) {
            if (selectedAccount != null) {
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nav_settings)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick = onOpenStandaloneSettings
            )
        }
    }
}

@Composable
private fun SteamCodeContent(
    accounts: List<SteamAccount>,
    selectedAccountIds: List<Long>,
    onToggleSelection: (SteamAccount) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenDetail: (SteamAccount) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val selectedIds = selectedAccountIds.toSet()
    val selectionMode = selectedIds.isNotEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectionMode) {
            item {
                SteamTokenSelectionBar(
                    selectedCount = selectedIds.size,
                    onDeleteSelected = onDeleteSelected,
                    onClearSelection = onClearSelection
                )
            }
        }

        items(accounts, key = { it.id }) { account ->
            val totpItem = remember(account) { account.toSteamTotpUiItem() }
            val totpData = remember(account) { account.toSteamTotpUiData() }
            TotpCodeCard(
                item = totpItem,
                parsedTotpData = totpData,
                onCardClick = { onOpenDetail(account) },
                onToggleSelect = { onToggleSelection(account) },
                onLongClick = { onToggleSelection(account) },
                isSelectionMode = selectionMode,
                isSelected = account.id in selectedIds,
                leadingContent = {
                    SteamAvatarImage(
                        account = account,
                        size = 40.dp
                    )
                },
                onCopyCode = { code ->
                    if (code.isNotBlank()) {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(
                            context,
                            context.getString(R.string.verification_code_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SteamTokenSelectionBar(
    selectedCount: Int,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_selected_accounts_count, selectedCount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearSelection) {
                Text(stringResource(R.string.cancel))
            }
            TextButton(onClick = onDeleteSelected) {
                Text(
                    text = stringResource(R.string.delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun SteamAccountDetailContent(
    account: SteamAccount,
    pendingLogins: List<SteamPendingLogin>,
    pendingScannedQr: String?,
    onScannedQrHandled: () -> Unit,
    onRefreshLogins: () -> Unit,
    onRespondPending: (SteamPendingLogin, Boolean) -> Unit,
    onRespondQr: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val totpItem = remember(account) { account.toSteamTotpUiItem() }
    val totpData = remember(account) { account.toSteamTotpUiData() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            TotpCodeCard(
                item = totpItem,
                parsedTotpData = totpData,
                leadingContent = {
                    SteamAvatarImage(
                        account = account,
                        size = 40.dp
                    )
                },
                onCopyCode = { code ->
                    copySteamText(
                        context = context,
                        clipboard = clipboard,
                        label = context.getString(R.string.steam_code_label),
                        value = code
                    )
                }
            )
        }
        item {
            SteamAccountCredentialCard(
                account = account,
                context = context,
                clipboard = clipboard
            )
        }
        item {
            SteamLoginApprovalSection(
                account = account,
                pendingLogins = pendingLogins,
                pendingScannedQr = pendingScannedQr,
                onScannedQrHandled = onScannedQrHandled,
                onRefresh = onRefreshLogins,
                onRespondPending = onRespondPending,
                onRespondQr = onRespondQr
            )
        }
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

@Composable
private fun SteamAccountCredentialCard(
    account: SteamAccount,
    context: Context,
    clipboard: ClipboardManager
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_credentials_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            SteamDetailInfoRow(
                label = stringResource(R.string.steam_account_label),
                value = account.accountName.ifBlank { account.steamId },
                context = context,
                clipboard = clipboard
            )
            SteamDetailInfoRow(
                label = stringResource(R.string.steam_id_label),
                value = account.steamId,
                context = context,
                clipboard = clipboard
            )
            SteamDetailInfoRow(
                label = stringResource(R.string.steam_device_label),
                value = account.deviceId,
                context = context,
                clipboard = clipboard
            )
        }
    }
}

@Composable
private fun SteamDetailInfoRow(
    label: String,
    value: String,
    context: Context,
    clipboard: ClipboardManager,
    copyable: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value.ifBlank { stringResource(R.string.steam_empty_field) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (copyable && value.isNotBlank()) {
            IconButton(
                onClick = {
                    copySteamText(
                        context = context,
                        clipboard = clipboard,
                        label = label,
                        value = value
                    )
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.copy)
                )
            }
        }
    }
}

@Composable
private fun SteamAvatarImage(
    account: SteamAccount,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var avatar by remember(account.steamId) { mutableStateOf<ImageBitmap?>(null) }
    val fallbackText = remember(account.displayName, account.accountName, account.steamId) {
        account.displayName
            .ifBlank { account.accountName }
            .ifBlank { account.steamId }
            .take(1)
            .uppercase()
    }

    LaunchedEffect(account.steamId) {
        avatar = loadSteamAvatar(context, account.steamId)
    }

    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        tonalElevation = 2.dp
    ) {
        val snapshot = avatar
        if (snapshot != null) {
            Image(
                bitmap = snapshot,
                contentDescription = stringResource(R.string.steam_account_avatar_description),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = fallbackText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private fun SteamAccount.toSteamTotpUiItem(): SecureItem {
    return SecureItem(
        id = id,
        itemType = ItemType.TOTP,
        title = displayName.ifBlank { accountName.ifBlank { steamId } },
        itemData = "steam://${sharedSecret}"
    )
}

private fun SteamAccount.toSteamTotpUiData(): TotpData {
    return TotpData(
        secret = "steam://${sharedSecret}",
        issuer = "Steam",
        accountName = accountName.ifBlank { steamId },
        period = 30,
        digits = 5,
        otpType = OtpType.STEAM,
        link = "https://store.steampowered.com",
        associatedApp = "com.valvesoftware.android.steam.community",
        steamDeviceId = deviceId,
        steamSharedSecretBase64 = sharedSecret,
        steamRevocationCode = revocationCode.orEmpty(),
        steamIdentitySecret = identitySecret.orEmpty(),
        steamTokenGid = tokenGid.orEmpty(),
        steamRawJson = rawSteamGuardJson
    )
}

private fun copySteamText(
    context: Context,
    clipboard: ClipboardManager,
    label: String,
    value: String
) {
    if (value.isBlank()) return
    clipboard.setText(AnnotatedString(value))
    Toast.makeText(context, label, Toast.LENGTH_SHORT).show()
}

private suspend fun loadSteamAvatar(context: Context, steamId: String): ImageBitmap? = withContext(Dispatchers.IO) {
    val cacheFile = steamAvatarCacheFile(context, steamId)
    val cachedAvatar = readSteamAvatarCache(cacheFile)
    if (cachedAvatar != null && !isSteamAvatarCacheExpired(cacheFile)) {
        return@withContext cachedAvatar
    }

    val freshAvatar = runCatching {
        val avatarUrl = fetchSteamAvatarUrl(steamId) ?: return@runCatching null
        downloadSteamAvatarBytes(avatarUrl)?.also { bytes ->
            cacheFile.parentFile?.mkdirs()
            cacheFile.writeBytes(bytes)
        }?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull()

    freshAvatar ?: cachedAvatar
}

private fun fetchSteamAvatarUrl(steamId: String): String? {
    val normalizedSteamId = steamId.trim()
    if (normalizedSteamId.isBlank() || normalizedSteamId.any { !it.isDigit() }) {
        return null
    }

    val connection = (URL("https://steamcommunity.com/profiles/$normalizedSteamId/?xml=1")
        .openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_AVATAR_TIMEOUT_MS
        readTimeout = STEAM_AVATAR_TIMEOUT_MS
        requestMethod = "GET"
    }
    return try {
        connection.inputStream.use { stream ->
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching {
                    setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                }
                runCatching {
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                }
                runCatching {
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                }
            }
            val document = factory.newDocumentBuilder().parse(stream)
            document.documentElement.normalize()
            listOf("avatarFull", "avatarMedium", "avatarIcon").firstNotNullOfOrNull { tag ->
                document.getElementsByTagName(tag)
                    ?.item(0)
                    ?.textContent
                    ?.trim()
                    ?.takeIf { it.startsWith("https://") }
            }
        }
    } finally {
        connection.disconnect()
    }
}

private fun downloadSteamAvatarBytes(avatarUrl: String): ByteArray? {
    val connection = (URL(avatarUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_AVATAR_TIMEOUT_MS
        readTimeout = STEAM_AVATAR_TIMEOUT_MS
        requestMethod = "GET"
    }
    return try {
        connection.inputStream.use { stream ->
            stream.readBytes()
        }
    } finally {
        connection.disconnect()
    }
}

private fun steamAvatarCacheFile(context: Context, steamId: String): File {
    val safeSteamId = steamId.filter { it.isLetterOrDigit() }.ifBlank { "unknown" }
    return File(File(context.cacheDir, "steam_avatars"), "$safeSteamId.png")
}

private fun readSteamAvatarCache(cacheFile: File): ImageBitmap? {
    if (!cacheFile.isFile) return null
    return runCatching {
        BitmapFactory.decodeFile(cacheFile.absolutePath)?.asImageBitmap()
    }.getOrNull()
}

private fun isSteamAvatarCacheExpired(cacheFile: File): Boolean {
    if (!cacheFile.isFile) return true
    val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
    return ageMs > STEAM_AVATAR_CACHE_TTL_MS
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            modifier = Modifier.width(120.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SteamConfirmationsContent(
    account: SteamAccount?,
    confirmations: List<SteamConfirmation>,
    selectedIds: Set<String>,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRespond: (SteamConfirmation, Boolean) -> Unit,
    onRespondSelected: (Boolean) -> Unit
) {
    var pendingAction by remember { mutableStateOf<ConfirmationActionRequest?>(null) }
    val selectedConfirmations = confirmations.filter { it.id in selectedIds }
    val allSelected = confirmations.isNotEmpty() &&
        confirmations.all { it.id in selectedIds }

    pendingAction?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    stringResource(
                        if (request.accept) {
                            R.string.steam_approve_confirmation_title
                        } else {
                            R.string.steam_reject_confirmation_title
                        }
                    )
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.steam_confirmation_count, request.confirmations.size))
                    request.confirmations.take(8).forEach { confirmation ->
                        Text(
                            text = confirmation.headline.ifBlank {
                                confirmation.summary.ifBlank { confirmation.id }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (request.confirmations.size > 8) {
                        Text(
                            text = stringResource(
                                R.string.steam_more_items,
                                request.confirmations.size - 8
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (request.confirmations.size == 1) {
                            onRespond(request.confirmations.first(), request.accept)
                        } else {
                            onRespondSelected(request.accept)
                        }
                        pendingAction = null
                    }
                ) {
                    Text(
                        stringResource(
                            if (request.accept) R.string.steam_approve else R.string.steam_reject
                        )
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh, enabled = account?.canUseConfirmations == true) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.refresh))
                    }
                    OutlinedButton(
                        onClick = {
                            if (allSelected) {
                                onClearSelection()
                            } else {
                                onSelectAll()
                            }
                        },
                        enabled = account?.canUseConfirmations == true && confirmations.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = if (allSelected) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (allSelected) R.string.deselect_all else R.string.select_all
                            )
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = {
                            pendingAction = ConfirmationActionRequest(
                                confirmations = selectedConfirmations,
                                accept = true
                            )
                        },
                        enabled = selectedConfirmations.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.steam_approve))
                    }
                    OutlinedButton(
                        onClick = {
                            pendingAction = ConfirmationActionRequest(
                                confirmations = selectedConfirmations,
                                accept = false
                            )
                        },
                        enabled = selectedConfirmations.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.steam_reject))
                    }
                }
            }
        }
        if (account == null || !account.canUseConfirmations) {
            item { EmptyState(stringResource(R.string.steam_no_confirmation_session)) }
        } else if (confirmations.isEmpty()) {
            item { EmptyState(stringResource(R.string.steam_no_confirmations)) }
        } else {
            items(confirmations, key = { it.id }) { confirmation ->
                ConfirmationRow(
                    confirmation = confirmation,
                    selected = confirmation.id in selectedIds,
                    onToggle = { onToggle(confirmation.id) },
                    onRespond = { target, accept ->
                        pendingAction = ConfirmationActionRequest(
                            confirmations = listOf(target),
                            accept = accept
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun ConfirmationRow(
    confirmation: SteamConfirmation,
    selected: Boolean,
    onToggle: () -> Unit,
    onRespond: (SteamConfirmation, Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = confirmation.headline.ifBlank { confirmation.type },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = confirmation.summary.ifBlank { confirmation.id },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onRespond(confirmation, true) }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.steam_approve))
            }
            IconButton(onClick = { onRespond(confirmation, false) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.steam_reject))
            }
        }
    }
}

@Composable
private fun SteamLoginApprovalSection(
    account: SteamAccount,
    pendingLogins: List<SteamPendingLogin>,
    pendingScannedQr: String?,
    onScannedQrHandled: () -> Unit,
    onRefresh: () -> Unit,
    onRespondPending: (SteamPendingLogin, Boolean) -> Unit,
    onRespondQr: (String, Boolean) -> Unit
) {
    var qrText by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<LoginActionRequest?>(null) }
    var pendingQrAction by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var scannedQrAction by remember { mutableStateOf<String?>(null) }
    var autoPromptedClientIds by remember(account.id) { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(pendingScannedQr) {
        val qr = pendingScannedQr?.trim().orEmpty()
        if (qr.isNotBlank()) {
            qrText = qr
            scannedQrAction = qr
            onScannedQrHandled()
        }
    }

    LaunchedEffect(account.id, account.canApproveLogins, pendingLogins) {
        val activeIds = pendingLogins.map { it.clientId }.toSet()
        val promptedActiveIds = autoPromptedClientIds.intersect(activeIds)
        if (promptedActiveIds != autoPromptedClientIds) {
            autoPromptedClientIds = promptedActiveIds
        }
        if (account.canApproveLogins && pendingAction == null) {
            val login = pendingLogins.firstOrNull { it.clientId !in promptedActiveIds }
            if (login != null) {
                autoPromptedClientIds = promptedActiveIds + login.clientId
                pendingAction = LoginActionRequest(login)
            }
        }
    }

    pendingAction?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(stringResource(R.string.steam_login_request_title))
            },
            text = {
                LoginActionDetails(request.login)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onRespondPending(request.login, false)
                            pendingAction = null
                        }
                    ) {
                        Text(stringResource(R.string.steam_reject))
                    }
                    TextButton(
                        onClick = {
                            onRespondPending(request.login, true)
                            pendingAction = null
                        }
                    ) {
                        Text(stringResource(R.string.steam_approve))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    pendingQrAction?.let { (rawQr, approve) ->
        AlertDialog(
            onDismissRequest = { pendingQrAction = null },
            title = {
                Text(
                    stringResource(
                        if (approve) {
                            R.string.steam_approve_qr_login_title
                        } else {
                            R.string.steam_reject_qr_login_title
                        }
                    )
                )
            },
            text = {
                Text(rawQr, maxLines = 4, overflow = TextOverflow.Ellipsis)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRespondQr(rawQr, approve)
                        pendingQrAction = null
                    }
                ) {
                    Text(stringResource(if (approve) R.string.steam_approve else R.string.steam_reject))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingQrAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    scannedQrAction?.let { rawQr ->
        AlertDialog(
            onDismissRequest = { scannedQrAction = null },
            title = { Text(stringResource(R.string.steam_qr_login_title)) },
            text = {
                Text(rawQr, maxLines = 4, overflow = TextOverflow.Ellipsis)
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onRespondQr(rawQr, false)
                            scannedQrAction = null
                        }
                    ) {
                        Text(stringResource(R.string.steam_reject))
                    }
                    TextButton(
                        onClick = {
                            onRespondQr(rawQr, true)
                            scannedQrAction = null
                        }
                    ) {
                        Text(stringResource(R.string.steam_approve))
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { scannedQrAction = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_login_approval_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = account.canApproveLogins) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.refresh))
                }
            }

            if (!account.canApproveLogins) {
                EmptyState(stringResource(R.string.steam_no_login_session))
            } else if (pendingLogins.isEmpty()) {
                EmptyState(stringResource(R.string.steam_no_pending_logins))
            } else {
                pendingLogins.forEach { login ->
                    PendingLoginRow(
                        login = login,
                        onOpenDetails = { target ->
                            pendingAction = LoginActionRequest(target)
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = qrText,
                    onValueChange = { qrText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.steam_qr_link_label)) },
                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { pendingQrAction = qrText to true },
                        enabled = account.canApproveLogins && qrText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.steam_approve))
                    }
                    OutlinedButton(
                        onClick = { pendingQrAction = qrText to false },
                        enabled = account.canApproveLogins && qrText.isNotBlank()
                    ) {
                        Text(stringResource(R.string.steam_reject))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoginActionDetails(login: SteamPendingLogin) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailLine(
            stringResource(R.string.steam_device_label),
            login.deviceName.ifBlank { stringResource(R.string.steam_unknown_device) }
        )
        DetailLine(stringResource(R.string.steam_location_label), login.location.ifBlank { "-" })
        DetailLine(stringResource(R.string.steam_time_label), formatSteamLoginTime(login.detectedAtMillis))
        DetailLine(stringResource(R.string.steam_ip_label), login.ip.ifBlank { "-" })
        DetailLine(stringResource(R.string.steam_client_label), login.clientId.toString())
    }
}

@Composable
private fun PendingLoginRow(
    login: SteamPendingLogin,
    onOpenDetails: (SteamPendingLogin) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetails(login) },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = login.deviceName.ifBlank { stringResource(R.string.steam_login_fallback_title) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOf(login.location.ifBlank { "-" }, formatSteamLoginTime(login.detectedAtMillis))
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank {
                            stringResource(R.string.steam_client_id_fallback, login.clientId)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { onOpenDetails(login) }) {
                Text(stringResource(R.string.details))
            }
        }
    }
}

private fun formatSteamLoginTime(timestampMillis: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(timestampMillis))
}

@Composable
private fun SteamEmptyAccountContent(
    onAddAccount: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.steam_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.steam_empty_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onAddAccount) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.steam_add_account_button))
            }
        }
    }
}

@Composable
private fun SteamAddMethodDialog(
    onDismissRequest: () -> Unit,
    onSelectMaFile: () -> Unit,
    onSelectLogin: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.steam_add_account_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSelectMaFile,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_add_method_mafile))
                }
                OutlinedButton(
                    onClick = onSelectLogin,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Login, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_add_method_login))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SteamMaFileImportDialog(
    onDismissRequest: () -> Unit,
    onImportMaFile: (Uri, Uri?, String, String) -> Unit,
) {
    var maFileUri by remember { mutableStateOf<Uri?>(null) }
    var manifestUri by remember { mutableStateOf<Uri?>(null) }
    var maFilePassword by remember { mutableStateOf("") }
    var maFileDisplayName by remember { mutableStateOf("") }
    val maFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        maFileUri = uri
    }
    val manifestPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        manifestUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.steam_mafile_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { maFilePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.UploadFile, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.steam_mafile_button))
                }
                OutlinedButton(
                    onClick = { manifestPicker.launch("application/json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.steam_manifest_button))
                }
                Text(
                    text = maFileUri?.lastPathSegment ?: stringResource(R.string.steam_no_mafile_selected),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = maFilePassword,
                    onValueChange = { maFilePassword = it },
                    label = { Text(stringResource(R.string.steam_mafile_password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = maFileDisplayName,
                    onValueChange = { maFileDisplayName = it },
                    label = { Text(stringResource(R.string.steam_display_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    maFileUri?.let { uri ->
                        onImportMaFile(uri, manifestUri, maFilePassword, maFileDisplayName)
                    }
                },
                enabled = maFileUri != null
            ) {
                Text(stringResource(R.string.steam_import_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun SteamLoginImportDialog(
    pendingChallenge: SteamLoginChallengeUi?,
    onDismissRequest: () -> Unit,
    onBeginLogin: (String, String) -> Unit,
    onSubmitLoginCode: (String) -> Unit
) {
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var challengeCode by remember { mutableStateOf("") }
    val waitingForCode = pendingChallenge != null
    val requiresCode = pendingChallenge?.requiresCode == true

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                stringResource(
                    if (waitingForCode) {
                        R.string.steam_verification_required
                    } else {
                        R.string.steam_login_title
                    }
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (pendingChallenge == null) {
                    OutlinedTextField(
                        value = loginName,
                        onValueChange = { loginName = it },
                        label = { Text(stringResource(R.string.steam_login_account_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text(stringResource(R.string.steam_login_password_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                } else {
                    if (pendingChallenge.canPoll) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(20.dp),
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = stringResource(R.string.steam_login_waiting_approval),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = pendingChallenge.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (requiresCode) {
                        OutlinedTextField(
                            value = challengeCode,
                            onValueChange = { challengeCode = it },
                            label = { Text(stringResource(R.string.steam_code_label)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!waitingForCode || requiresCode) {
                TextButton(
                    onClick = {
                        if (waitingForCode) {
                            onSubmitLoginCode(challengeCode)
                        } else {
                            onBeginLogin(loginName, loginPassword)
                        }
                    },
                    enabled = if (waitingForCode) {
                        challengeCode.isNotBlank()
                    } else {
                        loginName.isNotBlank() && loginPassword.isNotBlank()
                    }
                ) {
                    Text(
                        stringResource(
                            if (waitingForCode) {
                                R.string.steam_submit_code_button
                            } else {
                                R.string.steam_login_button
                            }
                        )
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun badgeCountText(count: Int): String {
    return if (count > 99) {
        "99+"
    } else {
        count.toString()
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
