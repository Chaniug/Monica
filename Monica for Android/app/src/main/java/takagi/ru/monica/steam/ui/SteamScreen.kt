package takagi.ru.monica.steam.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
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
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.UnifiedProgressBarMode
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.core.SteamTotp
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamAuthorizedDevice
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.network.SteamPendingLogin
import takagi.ru.monica.ui.common.selection.SelectionActionBar
import takagi.ru.monica.ui.components.ExpressiveTopBar
import takagi.ru.monica.ui.components.MonicaModalBottomSheet
import takagi.ru.monica.ui.components.PasswordEntryPickerBottomSheet
import takagi.ru.monica.ui.components.TotpCodeCard
import takagi.ru.monica.ui.components.UnifiedProgressBar
import takagi.ru.monica.ui.gestures.SwipeActions
import takagi.ru.monica.ui.password.PasswordTopActionsDropdownMenu
import takagi.ru.monica.ui.rememberTotpTickerMillis
import takagi.ru.monica.utils.SettingsManager

private const val STEAM_AVATAR_TIMEOUT_MS = 4_000
private const val STEAM_AVATAR_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L
private const val STEAM_CONFIRMATION_IMAGE_TIMEOUT_MS = 4_000
private const val STEAM_CONFIRMATION_IMAGE_CACHE_TTL_MS = 3L * 24L * 60L * 60L * 1000L

private enum class SteamSection(
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    CODE(R.string.steam_section_code, Icons.Default.Key),
    CONFIRMATIONS(R.string.steam_section_confirmations, Icons.Default.VerifiedUser)
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
    pendingSteamQrAccountId: Long? = null,
    onConsumePendingSteamQrResult: () -> Unit = {},
    onScanSteamQrCode: ((Long?) -> Unit)? = null
) {
    val context = LocalContext.current
    val viewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val settingsManager = remember { SettingsManager(context.applicationContext) }
    val appSettings by settingsManager.settingsFlow.collectAsState(initial = AppSettings())
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccount = uiState.accounts.firstOrNull { it.id == uiState.selectedAccountId }
        ?: uiState.accounts.firstOrNull()
    var detailAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    val detailAccount = uiState.accounts.firstOrNull { it.id == detailAccountId }
    val topBarTitleEndPadding = when {
        detailAccount != null && showStandaloneSettingsEntry -> 64.dp
        detailAccount != null -> 0.dp
        else -> 180.dp
    }
    var selectedSection by rememberSaveable { mutableStateOf(SteamSection.CODE) }
    var showTopActionsMenu by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }
    var addAccountMethod by remember { mutableStateOf<SteamAddAccountMethod?>(null) }
    var selectedTokenAccountIds by rememberSaveable { mutableStateOf<List<Long>>(emptyList()) }
    var lastSteamQrAccountId by remember { mutableStateOf(readLastSteamQrAccountId(context)) }
    var deleteRequest by remember { mutableStateOf<SteamDeleteAccountsRequest?>(null) }
    var scannedQrPayload by remember { mutableStateOf<String?>(null) }
    val pendingConfirmationCount = if (selectedAccount?.canUseConfirmations == true) {
        uiState.confirmations.size
    } else {
        0
    }
    val tokenQrAccount = remember(
        uiState.accounts,
        lastSteamQrAccountId,
        selectedAccount?.id,
        detailAccount,
        selectedSection,
        selectedTokenAccountIds
    ) {
        if (detailAccount == null &&
            selectedSection == SteamSection.CODE &&
            selectedTokenAccountIds.isEmpty()
        ) {
            val approvableAccounts = uiState.accounts.filter { it.canApproveLogins }
            approvableAccounts.firstOrNull { it.id == lastSteamQrAccountId }
                ?: approvableAccounts.firstOrNull { it.id == selectedAccount?.id }
                ?: approvableAccounts.firstOrNull()
        } else {
            null
        }
    }

    fun rememberLastSteamQrAccount(accountId: Long?) {
        lastSteamQrAccountId = accountId
        saveLastSteamQrAccountId(context, accountId)
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
        if (uiState.accounts.isNotEmpty() && lastSteamQrAccountId != null && lastSteamQrAccountId !in existingIds) {
            rememberLastSteamQrAccount(null)
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

    LaunchedEffect(detailAccount?.id, detailAccount?.accessToken) {
        detailAccount?.let { account ->
            viewModel.refreshAuthorizedDevices(account.id, silent = true)
        }
    }

    LaunchedEffect(uiState.accounts.size) {
        if (uiState.accounts.isNotEmpty()) {
            showAddAccountDialog = false
            addAccountMethod = null
        }
    }

    LaunchedEffect(pendingSteamQrResult, pendingSteamQrAccountId, uiState.accounts, selectedAccount?.id) {
        val qr = pendingSteamQrResult?.trim().orEmpty()
        if (qr.isNotBlank()) {
            val targetAccount = uiState.accounts.firstOrNull { it.id == pendingSteamQrAccountId }
                ?: selectedAccount
            if (pendingSteamQrAccountId == null || targetAccount?.id == pendingSteamQrAccountId) {
                targetAccount?.let { account ->
                    viewModel.selectAccount(account.id)
                    detailAccountId = account.id
                    rememberLastSteamQrAccount(account.id)
                }
                scannedQrPayload = qr
                onConsumePendingSteamQrResult()
            }
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
                collapsedTitleEndPadding = topBarTitleEndPadding,
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
                    if (detailAccount == null &&
                        selectedAccount != null &&
                        selectedSection == SteamSection.CONFIRMATIONS
                    ) {
                        IconButton(onClick = { viewModel.refreshConfirmations() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.refresh),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
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
                    if (detailAccount == null && selectedAccount != null) {
                        val targetSection = when (selectedSection) {
                            SteamSection.CODE -> SteamSection.CONFIRMATIONS
                            SteamSection.CONFIRMATIONS -> SteamSection.CODE
                        }
                        IconButton(
                            onClick = {
                                selectedSection = targetSection
                                if (targetSection == SteamSection.CONFIRMATIONS) {
                                    viewModel.refreshConfirmations()
                                }
                            }
                        ) {
                            BadgedBox(
                                badge = {
                                    if (targetSection == SteamSection.CONFIRMATIONS && pendingConfirmationCount > 0) {
                                        Badge {
                                            Text(badgeCountText(pendingConfirmationCount))
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = targetSection.icon,
                                    contentDescription = stringResource(targetSection.labelRes),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    val shouldShowTopActionsMenu = detailAccount == null && showStandaloneSettingsEntry
                    if (shouldShowTopActionsMenu) {
                        Box {
                            IconButton(
                                onClick = {
                                    showTopActionsMenu = true
                                }
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
                                showStandaloneSettingsEntry = showStandaloneSettingsEntry,
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
                                showStandaloneSettingsEntry = showStandaloneSettingsEntry,
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
            val scanQr = onScanSteamQrCode
            val detailQrAccount = detailAccount?.takeIf { it.canApproveLogins }
            val account = detailQrAccount ?: tokenQrAccount
            AnimatedVisibility(
                visible = scanQr != null && account != null,
                enter = fadeIn(animationSpec = tween(160)) +
                    scaleIn(initialScale = 0.9f, animationSpec = tween(180)),
                exit = fadeOut(animationSpec = tween(120)) +
                    scaleOut(targetScale = 0.9f, animationSpec = tween(140))
            ) {
                if (scanQr != null && account != null) {
                    FloatingActionButton(
                        onClick = {
                            val freshAccount = detailQrAccount
                                ?: uiState.accounts.firstOrNull {
                                    it.canApproveLogins && it.id == readLastSteamQrAccountId(context)
                                }
                                ?: account
                            rememberLastSteamQrAccount(freshAccount.id)
                            scanQr(freshAccount.id)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = stringResource(R.string.scan_qr_code)
                        )
                    }
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
                if (detailAccount != null) {
                    SteamAccountDetailContent(
                        account = detailAccount,
                        pendingLogins = uiState.pendingLogins,
                        authorizedDevices = uiState.authorizedDevices,
                        pendingScannedQr = scannedQrPayload,
                        onScannedQrHandled = { scannedQrPayload = null },
                        onRefreshLogins = { viewModel.refreshPendingLogins() },
                        onRefreshAuthorizedDevices = { viewModel.refreshAuthorizedDevices(detailAccount.id) },
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
                            appSettings = appSettings,
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
                            onSelectAll = {
                                selectedTokenAccountIds = if (selectedTokenAccountIds.size == uiState.accounts.size) {
                                    emptyList()
                                } else {
                                    uiState.accounts.map { it.id }
                                }
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
                            accounts = uiState.accounts,
                            confirmations = uiState.confirmations,
                            selectedIds = uiState.selectedConfirmationIds,
                            onSelectAccount = viewModel::selectAccount,
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
                LinearProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SteamTopActionsMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    showStandaloneSettingsEntry: Boolean,
    onOpenStandaloneSettings: () -> Unit
) {
    PasswordTopActionsDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest
    ) {
        if (showStandaloneSettingsEntry) {
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
    appSettings: AppSettings,
    onToggleSelection: (SteamAccount) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onOpenDetail: (SteamAccount) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val selectedIds = selectedAccountIds.toSet()
    val selectionMode = selectedIds.isNotEmpty()
    val sharedProgressTimeMillis = rememberTotpTickerMillis(appSettings.validatorSmoothProgress)
    val sharedTickSeconds = sharedProgressTimeMillis / 1000L

    fun copyCode(code: String) {
        if (code.isNotBlank()) {
            clipboard.setText(AnnotatedString(code))
            Toast.makeText(
                context,
                context.getString(R.string.verification_code_copied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (appSettings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED &&
                accounts.isNotEmpty()
            ) {
                UnifiedProgressBar(
                    style = appSettings.validatorProgressBarStyle,
                    currentSeconds = sharedTickSeconds,
                    period = 30,
                    smoothProgress = appSettings.validatorSmoothProgress,
                    timeOffset = (appSettings.totpTimeOffset * 1000).toLong()
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = if (selectionMode) 112.dp else 80.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(accounts, key = { it.id }) { account ->
                    val totpItem = remember(account) { account.toSteamTotpUiItem() }
                    val totpData = remember(account) { account.toSteamTotpUiData() }
                    SwipeActions(
                        onSwipeLeft = {
                            if (account.id !in selectedIds) {
                                onToggleSelection(account)
                            }
                        },
                        onSwipeRight = { onToggleSelection(account) },
                        isSwiped = account.id in selectedIds,
                        allowSwipeLeft = false,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TotpCodeCard(
                            item = totpItem,
                            parsedTotpData = totpData,
                            onCardClick = {
                                if (selectionMode) {
                                    onToggleSelection(account)
                                } else {
                                    onOpenDetail(account)
                                }
                            },
                            onToggleSelect = { onToggleSelection(account) },
                            onLongClick = {
                                copyCode(SteamTotp.generateAuthCode(account.sharedSecret, System.currentTimeMillis() / 1000L))
                            },
                            isSelectionMode = false,
                            isSelected = account.id in selectedIds,
                            leadingContent = {
                                SteamAvatarImage(
                                    account = account,
                                    size = 40.dp
                                )
                            },
                            onCopyCode = ::copyCode,
                            sharedTickSeconds = sharedTickSeconds,
                            sharedProgressTimeMillis = sharedProgressTimeMillis,
                            appSettings = appSettings
                        )
                    }
                }
            }
        }

        if (selectionMode) {
            SelectionActionBar(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                selectedCount = selectedIds.size,
                onExit = onClearSelection,
                onSelectAll = onSelectAll,
                onDelete = onDeleteSelected
            )
        }
    }
}

@Composable
private fun SteamAccountDetailContent(
    account: SteamAccount,
    pendingLogins: List<SteamPendingLogin>,
    authorizedDevices: List<SteamAuthorizedDevice>,
    pendingScannedQr: String?,
    onScannedQrHandled: () -> Unit,
    onRefreshLogins: () -> Unit,
    onRefreshAuthorizedDevices: () -> Unit,
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
            SteamAuthorizedDevicesSection(
                account = account,
                devices = authorizedDevices,
                onRefresh = onRefreshAuthorizedDevices
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

private suspend fun loadSteamConfirmationImage(context: Context, imageUrl: String): ImageBitmap? =
    withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeSteamImageUrl(imageUrl)
        if (!normalizedUrl.startsWith("https://") && !normalizedUrl.startsWith("http://")) {
            return@withContext null
        }

        val cacheFile = steamConfirmationImageCacheFile(context, normalizedUrl)
        val cachedImage = readSteamAvatarCache(cacheFile)
        if (cachedImage != null && !isSteamConfirmationImageCacheExpired(cacheFile)) {
            return@withContext cachedImage
        }

        val freshImage = runCatching {
            downloadSteamConfirmationImageBytes(normalizedUrl)?.also { bytes ->
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(bytes)
            }?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }
        }.getOrNull()

        freshImage ?: cachedImage
    }

private fun normalizeSteamImageUrl(imageUrl: String): String {
    val trimmed = imageUrl.trim()
    return when {
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("/") -> "https://steamcommunity.com$trimmed"
        else -> trimmed
    }
}

private fun downloadSteamConfirmationImageBytes(imageUrl: String): ByteArray? {
    val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = STEAM_CONFIRMATION_IMAGE_TIMEOUT_MS
        readTimeout = STEAM_CONFIRMATION_IMAGE_TIMEOUT_MS
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

private fun steamConfirmationImageCacheFile(context: Context, imageUrl: String): File {
    val safeName = imageUrl
        .hashCode()
        .toUInt()
        .toString(16)
    return File(File(context.cacheDir, "steam_confirmation_images"), "$safeName.png")
}

private fun isSteamConfirmationImageCacheExpired(cacheFile: File): Boolean {
    if (!cacheFile.isFile) return true
    val ageMs = System.currentTimeMillis() - cacheFile.lastModified()
    return ageMs > STEAM_CONFIRMATION_IMAGE_CACHE_TTL_MS
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
    accounts: List<SteamAccount>,
    confirmations: List<SteamConfirmation>,
    selectedIds: Set<String>,
    onSelectAccount: (Long) -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRespond: (SteamConfirmation, Boolean) -> Unit,
    onRespondSelected: (Boolean) -> Unit
) {
    var pendingAction by remember { mutableStateOf<ConfirmationActionRequest?>(null) }
    var showBulkActionDialog by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    val selectedConfirmations = confirmations.filter { it.id in selectedIds }
    val selectionMode = selectedIds.isNotEmpty()

    if (showAccountPicker) {
        SteamConfirmationAccountPickerSheet(
            accounts = accounts,
            selectedAccountId = account?.id,
            onSelectAccount = { selected ->
                onSelectAccount(selected.id)
                showAccountPicker = false
            },
            onDismissRequest = { showAccountPicker = false }
        )
    }

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

    if (showBulkActionDialog) {
        AlertDialog(
            onDismissRequest = { showBulkActionDialog = false },
            title = { Text(stringResource(R.string.steam_confirmation_action_title)) },
            text = {
                Text(stringResource(R.string.steam_confirmation_action_message, selectedConfirmations.size))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingAction = ConfirmationActionRequest(
                            confirmations = selectedConfirmations,
                            accept = true
                        )
                        showBulkActionDialog = false
                    }
                ) {
                    Text(stringResource(R.string.steam_approve))
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showBulkActionDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                    TextButton(
                        onClick = {
                            pendingAction = ConfirmationActionRequest(
                                confirmations = selectedConfirmations,
                                accept = false
                            )
                            showBulkActionDialog = false
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.steam_reject),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SteamConfirmationAccountCard(
                account = account,
                onClick = { showAccountPicker = true },
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 10.dp,
                    end = 16.dp,
                    bottom = if (selectionMode) 144.dp else 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (account == null || !account.canUseConfirmations) {
                    item { EmptyState(stringResource(R.string.steam_no_confirmation_session)) }
                } else if (confirmations.isEmpty()) {
                    item { EmptyState(stringResource(R.string.steam_no_confirmations)) }
                } else {
                    items(confirmations, key = { it.id }) { confirmation ->
                        SwipeActions(
                            onSwipeLeft = {},
                            onSwipeRight = { onToggle(confirmation.id) },
                            isSwiped = confirmation.id in selectedIds,
                            allowSwipeLeft = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ConfirmationRow(
                                confirmation = confirmation,
                                selected = confirmation.id in selectedIds,
                                selectionMode = selectionMode,
                                onClick = {
                                    if (selectionMode) {
                                        onToggle(confirmation.id)
                                    } else {
                                        pendingAction = ConfirmationActionRequest(
                                            confirmations = listOf(confirmation),
                                            accept = true
                                        )
                                    }
                                },
                                onLongClick = { onToggle(confirmation.id) }
                            )
                        }
                    }
                }
            }
        }

        if (selectionMode) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SelectionActionBar(
                    selectedCount = selectedConfirmations.size,
                    onExit = onClearSelection,
                    onSelectAll = onSelectAll,
                    onDelete = null
                )

                Spacer(modifier = Modifier.weight(1f))

                FloatingActionButton(
                    onClick = { showBulkActionDialog = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.steam_confirmation_action_title)
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamConfirmationAccountCard(
    account: SteamAccount?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (account != null) {
                SteamAvatarImage(account = account, size = 36.dp)
            } else {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Icon(
                        imageVector = Icons.Default.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.padding(8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.steam_current_confirmation_account),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = account?.displayName
                        ?.ifBlank { account.accountName }
                        ?.ifBlank { account.steamId }
                        ?: stringResource(R.string.steam_empty_field),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = stringResource(
                    if (account?.canUseConfirmations == true) {
                        R.string.steam_status_ready
                    } else {
                        R.string.steam_status_missing_session
                    }
                ),
                style = MaterialTheme.typography.labelMedium,
                color = if (account?.canUseConfirmations == true) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }
    }
}

@Composable
private fun SteamAuthorizedDevicesSection(
    account: SteamAccount,
    devices: List<SteamAuthorizedDevice>,
    onRefresh: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.steam_authorized_devices_label),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !account.accessToken.isNullOrBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.refresh)
                    )
                }
            }

            if (account.accessToken.isNullOrBlank()) {
                EmptyState(stringResource(R.string.steam_no_authorized_device_session))
            } else if (devices.isEmpty()) {
                EmptyState(stringResource(R.string.steam_no_authorized_devices))
            } else {
                devices.forEach { device ->
                    SteamAuthorizedDeviceRow(device)
                }
            }
        }
    }
}

@Composable
private fun SteamAuthorizedDeviceRow(device: SteamAuthorizedDevice) {
    val lastSeen = device.lastSeen
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.description.ifBlank { stringResource(R.string.steam_unknown_device) },
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (device.loggedIn) {
                            R.string.steam_device_logged_in
                        } else {
                            R.string.steam_device_logged_out
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (device.loggedIn) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Text(
                text = steamPlatformLabel(device.platformType),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (lastSeen != null) {
                Text(
                    text = listOf(
                        lastSeen.location.takeIf { it.isNotBlank() },
                        lastSeen.timeSeconds.takeIf { it > 0L }?.let {
                            formatSteamLoginTime(it * 1000L)
                        }
                    ).filterNotNull().joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun steamPlatformLabel(platformType: Int): String {
    return when (platformType) {
        1 -> stringResource(R.string.steam_platform_client)
        2 -> stringResource(R.string.steam_platform_web)
        3 -> stringResource(R.string.steam_platform_mobile)
        else -> stringResource(R.string.steam_platform_unknown)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SteamConfirmationAccountPickerSheet(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (SteamAccount) -> Unit,
    onDismissRequest: () -> Unit
) {
    MonicaModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.steam_switch_account),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(accounts, key = { it.id }) { steamAccount ->
                    SteamConfirmationAccountOptionRow(
                        account = steamAccount,
                        selected = steamAccount.id == selectedAccountId,
                        onClick = { onSelectAccount(steamAccount) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SteamConfirmationAccountOptionRow(
    account: SteamAccount,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SteamAvatarImage(account = account, size = 34.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.displayName.ifBlank { account.accountName }.ifBlank { account.steamId },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        if (account.canUseConfirmations) {
                            R.string.steam_status_ready
                        } else {
                            R.string.steam_status_missing_session
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (account.canUseConfirmations) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    maxLines = 1
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.steam_selected_account_marker),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfirmationRow(
    confirmation: SteamConfirmation,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SteamConfirmationItemImage(confirmation = confirmation)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = confirmation.headline.ifBlank { confirmation.type },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = confirmation.summary.ifBlank { confirmation.id },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (selectionMode || selected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.select_all),
                    tint = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SteamConfirmationItemImage(confirmation: SteamConfirmation) {
    val context = LocalContext.current
    var image by remember(confirmation.imageUrl) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(confirmation.imageUrl) {
        image = loadSteamConfirmationImage(context, confirmation.imageUrl)
    }

    Surface(
        modifier = Modifier.size(48.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        val snapshot = image
        if (snapshot != null) {
            Image(
                bitmap = snapshot,
                contentDescription = stringResource(R.string.steam_confirmation_item_image),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
    val context = LocalContext.current
    val pickerSecurityManager = remember(context) { SecurityManager(context) }
    val passwordDatabase = remember(context) { PasswordDatabase.getDatabase(context) }
    val passwordEntriesForPicker by passwordDatabase.passwordEntryDao()
        .getAllPasswordEntries()
        .collectAsState(initial = emptyList())
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var challengeCode by remember { mutableStateOf("") }
    var showSteamPasswordPicker by remember { mutableStateOf(false) }
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
                    OutlinedButton(
                        onClick = { showSteamPasswordPicker = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.autofill_select_password))
                    }
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

    if (showSteamPasswordPicker && pendingChallenge == null) {
        PasswordEntryPickerBottomSheet(
            visible = true,
            title = stringResource(R.string.select_password_to_bind),
            passwords = passwordEntriesForPicker.filter { !it.isDeleted && !it.isArchived },
            onDismiss = { showSteamPasswordPicker = false },
            onSelect = { entry ->
                val resolvedUsername = runCatching { pickerSecurityManager.decryptData(entry.username) }
                    .getOrNull()
                    ?.trim()
                    .takeUnless { it.isNullOrBlank() }
                    ?: entry.username.trim()
                val resolvedPassword = runCatching { pickerSecurityManager.decryptData(entry.password) }
                    .getOrNull()
                    ?.trim()
                    .takeUnless { it.isNullOrBlank() }
                    ?: entry.password.trim()

                loginName = resolvedUsername
                loginPassword = resolvedPassword
                showSteamPasswordPicker = false
                Toast.makeText(
                    context,
                    context.getString(R.string.steam_login_fill_from_password_applied),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
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
