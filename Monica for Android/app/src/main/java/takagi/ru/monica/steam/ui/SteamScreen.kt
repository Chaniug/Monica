package takagi.ru.monica.steam.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.UploadFile
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.network.SteamPendingLogin
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.password.PasswordTopActionsDropdownMenu

private enum class SteamSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    CODE(R.string.steam_section_code, Icons.Default.Key),
    CONFIRMATIONS(R.string.steam_section_confirmations, Icons.Default.Check),
    LOGIN_APPROVAL(R.string.steam_section_login_approval, Icons.Default.Login)
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
    val login: SteamPendingLogin,
    val approve: Boolean
)

@Composable
fun SteamScreen(
    showStandaloneSettingsEntry: Boolean,
    onOpenStandaloneSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccount = uiState.accounts.firstOrNull { it.id == uiState.selectedAccountId }
        ?: uiState.accounts.firstOrNull()
    var selectedSection by rememberSaveable { mutableStateOf(SteamSection.CODE) }
    var showTopActionsMenu by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var addAccountMethod by remember { mutableStateOf<SteamAddAccountMethod?>(null) }
    var deleteTarget by remember { mutableStateOf<SteamAccount?>(null) }
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

    LaunchedEffect(selectedAccount?.id, selectedAccount?.canUseConfirmations) {
        if (selectedAccount?.canUseConfirmations == true) {
            viewModel.refreshConfirmations(silent = true)
        }
    }

    LaunchedEffect(uiState.accounts.size) {
        if (uiState.accounts.isNotEmpty()) {
            showAddAccountDialog = false
            addAccountMethod = null
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
            onDismissRequest = { addAccountMethod = null },
            onBeginLogin = viewModel::beginSteamLogin,
            onSubmitLoginCode = viewModel::submitSteamLoginCode
        )
        null -> Unit
    }

    deleteTarget?.let { account ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.steam_delete_account_title)) },
            text = { Text(stringResource(R.string.steam_delete_account_message, account.displayName)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account.id)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
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
                actions = {
                    selectedAccount?.let { account ->
                        TextButton(
                            onClick = { showTopActionsMenu = true },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsEsports,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = account.displayName,
                                modifier = Modifier.widthIn(max = 72.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { showAddAccountDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.steam_add_account_button),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box {
                        IconButton(onClick = { showTopActionsMenu = true }) {
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
                            accounts = uiState.accounts,
                            selectedAccount = selectedAccount,
                            selectedSection = selectedSection,
                            pendingConfirmationCount = pendingConfirmationCount,
                            showStandaloneSettingsEntry = showStandaloneSettingsEntry,
                            onSelectSection = { section ->
                                selectedSection = section
                                when (section) {
                                    SteamSection.CONFIRMATIONS -> viewModel.refreshConfirmations()
                                    SteamSection.LOGIN_APPROVAL -> viewModel.refreshPendingLogins()
                                    SteamSection.CODE -> Unit
                                }
                                showTopActionsMenu = false
                            },
                            onRefresh = {
                                when (selectedSection) {
                                    SteamSection.CONFIRMATIONS -> viewModel.refreshConfirmations()
                                    SteamSection.LOGIN_APPROVAL -> viewModel.refreshPendingLogins()
                                    SteamSection.CODE -> Unit
                                }
                                showTopActionsMenu = false
                            },
                            onAddAccount = {
                                showAddAccountDialog = true
                                showTopActionsMenu = false
                            },
                            onSelectAccount = { account ->
                                viewModel.selectAccount(account.id)
                                selectedSection = SteamSection.CODE
                                showTopActionsMenu = false
                            },
                            onDeleteAccount = { account ->
                                deleteTarget = account
                                showTopActionsMenu = false
                            },
                            onOpenStandaloneSettings = {
                                showTopActionsMenu = false
                                onOpenStandaloneSettings()
                            }
                        )
                    }
                }
            )
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
                if (selectedAccount == null) {
                    SteamEmptyAccountContent(
                        onAddAccount = { showAddAccountDialog = true }
                    )
                } else {
                    when (selectedSection) {
                        SteamSection.CODE -> SteamCodeContent(
                            account = selectedAccount,
                            code = uiState.currentCode,
                            secondsRemaining = uiState.secondsRemaining
                        )
                        SteamSection.CONFIRMATIONS -> SteamConfirmationsContent(
                            account = selectedAccount,
                            confirmations = uiState.confirmations,
                            selectedIds = uiState.selectedConfirmationIds,
                            onRefresh = { viewModel.refreshConfirmations() },
                            onToggle = viewModel::toggleConfirmation,
                            onRespond = viewModel::respondConfirmation,
                            onRespondSelected = viewModel::respondSelectedConfirmations
                        )
                        SteamSection.LOGIN_APPROVAL -> SteamLoginApprovalContent(
                            account = selectedAccount,
                            pendingLogins = uiState.pendingLogins,
                            onRefresh = { viewModel.refreshPendingLogins() },
                            onRespondPending = viewModel::respondPendingLogin,
                            onRespondQr = viewModel::respondQr
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
    accounts: List<SteamAccount>,
    selectedAccount: SteamAccount?,
    selectedSection: SteamSection,
    pendingConfirmationCount: Int,
    showStandaloneSettingsEntry: Boolean,
    onSelectSection: (SteamSection) -> Unit,
    onRefresh: () -> Unit,
    onAddAccount: () -> Unit,
    onSelectAccount: (SteamAccount) -> Unit,
    onDeleteAccount: (SteamAccount) -> Unit,
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
            if (selectedSection == SteamSection.CONFIRMATIONS ||
                selectedSection == SteamSection.LOGIN_APPROVAL
            ) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.refresh)) },
                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    onClick = onRefresh
                )
            }
            HorizontalDivider()
            accounts.forEach { account ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = account.displayName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = if (account.id == selectedAccount.id) {
                                Icons.Default.Check
                            } else {
                                Icons.Default.SportsEsports
                            },
                            contentDescription = null
                        )
                    },
                    onClick = { onSelectAccount(account) }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_add_account_button)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onAddAccount
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_delete_account_menu)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { onDeleteAccount(selectedAccount) }
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.steam_add_account_button)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = onAddAccount
            )
        }
        if (showStandaloneSettingsEntry) {
            HorizontalDivider()
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
    account: SteamAccount,
    code: String,
    secondsRemaining: Int
) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = account.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    SelectionContainer {
                        Text(
                            text = code.ifBlank { "-----" },
                            style = MaterialTheme.typography.displayMedium,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LinearProgressIndicator(
                        progress = { secondsRemaining / 30f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.steam_seconds_remaining, secondsRemaining))
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(
                            onClick = {
                                if (code.isNotBlank()) {
                                    clipboard.setText(AnnotatedString(code))
                                }
                            },
                            enabled = code.isNotBlank()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.copy))
                        }
                    }
                }
            }
        }
        item {
            AccountDetails(account)
        }
    }
}

@Composable
private fun AccountDetails(account: SteamAccount) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailLine(stringResource(R.string.steam_id_label), account.steamId)
            DetailLine(stringResource(R.string.steam_account_label), account.accountName)
            DetailLine(stringResource(R.string.steam_device_label), account.deviceId.ifBlank { "-" })
            DetailLine(
                stringResource(R.string.steam_confirmations_label),
                if (account.canUseConfirmations) {
                    stringResource(R.string.steam_status_ready)
                } else {
                    stringResource(R.string.steam_status_missing_session)
                }
            )
            DetailLine(
                stringResource(R.string.steam_login_approval_label),
                if (account.canApproveLogins) {
                    stringResource(R.string.steam_status_ready)
                } else {
                    stringResource(R.string.steam_status_missing_session)
                }
            )
        }
    }
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
    onRespond: (SteamConfirmation, Boolean) -> Unit,
    onRespondSelected: (Boolean) -> Unit
) {
    var pendingAction by remember { mutableStateOf<ConfirmationActionRequest?>(null) }
    val selectedConfirmations = confirmations.filter { it.id in selectedIds }

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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = account?.canUseConfirmations == true) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.refresh))
                }
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
private fun SteamLoginApprovalContent(
    account: SteamAccount?,
    pendingLogins: List<SteamPendingLogin>,
    onRefresh: () -> Unit,
    onRespondPending: (SteamPendingLogin, Boolean) -> Unit,
    onRespondQr: (String, Boolean) -> Unit
) {
    var qrText by remember { mutableStateOf("") }
    var pendingAction by remember { mutableStateOf<LoginActionRequest?>(null) }
    var pendingQrAction by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    pendingAction?.let { request ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = {
                Text(
                    stringResource(
                        if (request.approve) {
                            R.string.steam_approve_login_title
                        } else {
                            R.string.steam_reject_login_title
                        }
                    )
                )
            },
            text = {
                LoginActionDetails(request.login)
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRespondPending(request.login, request.approve)
                        pendingAction = null
                    }
                ) {
                    Text(
                        stringResource(
                            if (request.approve) R.string.steam_approve else R.string.steam_reject
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onRefresh, enabled = account?.canApproveLogins == true) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.refresh))
                }
            }
        }
        if (account == null || !account.canApproveLogins) {
            item { EmptyState(stringResource(R.string.steam_no_login_session)) }
        } else if (pendingLogins.isEmpty()) {
            item { EmptyState(stringResource(R.string.steam_no_pending_logins)) }
        } else {
            items(pendingLogins, key = { it.clientId }) { login ->
                PendingLoginRow(
                    login = login,
                    onRespond = { target, approve ->
                        pendingAction = LoginActionRequest(target, approve)
                    }
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
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
                            enabled = account?.canApproveLogins == true && qrText.isNotBlank()
                        ) {
                            Text(stringResource(R.string.steam_approve))
                        }
                        OutlinedButton(
                            onClick = { pendingQrAction = qrText to false },
                            enabled = account?.canApproveLogins == true && qrText.isNotBlank()
                        ) {
                            Text(stringResource(R.string.steam_reject))
                        }
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
        DetailLine(stringResource(R.string.steam_ip_label), login.ip.ifBlank { "-" })
        DetailLine(stringResource(R.string.steam_location_label), login.location.ifBlank { "-" })
        DetailLine(stringResource(R.string.steam_client_label), login.clientId.toString())
    }
}

@Composable
private fun PendingLoginRow(
    login: SteamPendingLogin,
    onRespond: (SteamPendingLogin, Boolean) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = login.deviceName.ifBlank { stringResource(R.string.steam_login_fallback_title) },
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = listOf(login.ip, login.location)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank {
                            stringResource(R.string.steam_client_id_fallback, login.clientId)
                        },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onRespond(login, true) }) {
                Icon(Icons.Default.Check, contentDescription = stringResource(R.string.steam_approve))
            }
            IconButton(onClick = { onRespond(login, false) }) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.steam_reject))
            }
        }
    }
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
                imageVector = Icons.Default.SportsEsports,
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
    onBeginLogin: (String, String, String) -> Unit,
    onSubmitLoginCode: (String, String) -> Unit
) {
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginDisplayName by remember { mutableStateOf("") }
    var challengeCode by remember { mutableStateOf("") }
    val waitingForCode = pendingChallenge != null

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
                OutlinedTextField(
                    value = loginDisplayName,
                    onValueChange = { loginDisplayName = it },
                    label = { Text(stringResource(R.string.steam_display_name_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                if (pendingChallenge != null) {
                    Text(
                        text = pendingChallenge.message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = challengeCode,
                        onValueChange = { challengeCode = it },
                        label = { Text(stringResource(R.string.steam_code_label)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (waitingForCode) {
                        onSubmitLoginCode(challengeCode, loginDisplayName)
                    } else {
                        onBeginLogin(loginName, loginPassword, loginDisplayName)
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
