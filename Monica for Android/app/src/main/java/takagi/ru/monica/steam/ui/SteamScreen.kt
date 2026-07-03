package takagi.ru.monica.steam.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.network.SteamPendingLogin

private data class ConfirmationActionRequest(
    val confirmations: List<SteamConfirmation>,
    val accept: Boolean
)

private data class LoginActionRequest(
    val login: SteamPendingLogin,
    val approve: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedTab by remember { mutableStateOf(0) }
    var deleteTarget by remember { mutableStateOf<SteamAccount?>(null) }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (deleteTarget != null) {
        val account = deleteTarget!!
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete Steam account") },
            text = { Text(account.displayName) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAccount(account.id)
                        deleteTarget = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            SteamHeader(
                accounts = uiState.accounts,
                selectedAccount = selectedAccount,
                showStandaloneSettingsEntry = showStandaloneSettingsEntry,
                onOpenStandaloneSettings = onOpenStandaloneSettings,
                onSelectAccount = viewModel::selectAccount,
                onDeleteAccount = { deleteTarget = it }
            )
            if (uiState.loading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            ScrollableTabRow(selectedTabIndex = selectedTab) {
                listOf("Code", "Confirm", "Login", "Import").forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> SteamCodeTab(
                    account = selectedAccount,
                    code = uiState.currentCode,
                    secondsRemaining = uiState.secondsRemaining
                )
                1 -> SteamConfirmationsTab(
                    account = selectedAccount,
                    confirmations = uiState.confirmations,
                    selectedIds = uiState.selectedConfirmationIds,
                    onRefresh = { viewModel.refreshConfirmations() },
                    onToggle = viewModel::toggleConfirmation,
                    onRespond = viewModel::respondConfirmation,
                    onRespondSelected = viewModel::respondSelectedConfirmations
                )
                2 -> SteamLoginApprovalTab(
                    account = selectedAccount,
                    pendingLogins = uiState.pendingLogins,
                    onRefresh = { viewModel.refreshPendingLogins() },
                    onRespondPending = viewModel::respondPendingLogin,
                    onRespondQr = viewModel::respondQr
                )
                else -> SteamImportTab(
                    pendingChallenge = uiState.pendingLoginChallenge,
                    onImportMaFile = viewModel::importMaFile,
                    onBeginLogin = viewModel::beginSteamLogin,
                    onSubmitLoginCode = viewModel::submitSteamLoginCode
                )
            }
        }

        if (uiState.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun SteamHeader(
    accounts: List<SteamAccount>,
    selectedAccount: SteamAccount?,
    showStandaloneSettingsEntry: Boolean,
    onOpenStandaloneSettings: () -> Unit,
    onSelectAccount: (Long) -> Unit,
    onDeleteAccount: (SteamAccount) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Steam", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    selectedAccount?.displayName ?: "No account",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (showStandaloneSettingsEntry) {
                IconButton(onClick = onOpenStandaloneSettings) {
                    Icon(Icons.Default.Settings, contentDescription = "Settings")
                }
            }
        }
        if (accounts.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { account ->
                    AssistChip(
                        onClick = { onSelectAccount(account.id) },
                        label = {
                            Text(
                                account.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = if (account.id == selectedAccount?.id) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        }
                    )
                }
                selectedAccount?.let { account ->
                    IconButton(onClick = { onDeleteAccount(account) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun SteamCodeTab(
    account: SteamAccount?,
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
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        account?.displayName ?: "Import a Steam account",
                        style = MaterialTheme.typography.titleMedium
                    )
                    SelectionContainer {
                        Text(
                            code.ifBlank { "-----" },
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
                        Text("$secondsRemaining s")
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
                            Text("Copy")
                        }
                    }
                }
            }
        }
        if (account != null) {
            item {
                AccountDetails(account)
            }
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
            DetailLine("Steam ID", account.steamId)
            DetailLine("Account", account.accountName)
            DetailLine("Device", account.deviceId.ifBlank { "-" })
            DetailLine("Confirmations", if (account.canUseConfirmations) "Ready" else "Missing session")
            DetailLine("Login approval", if (account.canApproveLogins) "Ready" else "Missing session")
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(120.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SteamConfirmationsTab(
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
                Text(if (request.accept) "Approve confirmation" else "Reject confirmation")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${request.confirmations.size} item(s)")
                    request.confirmations.take(8).forEach { confirmation ->
                        Text(
                            confirmation.headline.ifBlank { confirmation.summary.ifBlank { confirmation.id } },
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (request.confirmations.size > 8) {
                        Text(
                            "+${request.confirmations.size - 8} more",
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
                    Text(if (request.accept) "Approve" else "Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
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
                    Text("Refresh")
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
                    Text("Approve")
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
                    Text("Reject")
                }
            }
        }
        if (account == null || !account.canUseConfirmations) {
            item { EmptyState("No confirmation session") }
        } else if (confirmations.isEmpty()) {
            item { EmptyState("No confirmations") }
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
                Text(confirmation.headline.ifBlank { confirmation.type }, fontWeight = FontWeight.SemiBold)
                Text(
                    confirmation.summary.ifBlank { confirmation.id },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { onRespond(confirmation, true) }) {
                Icon(Icons.Default.Check, contentDescription = "Approve")
            }
            IconButton(onClick = { onRespond(confirmation, false) }) {
                Icon(Icons.Default.Close, contentDescription = "Reject")
            }
        }
    }
}

@Composable
private fun SteamLoginApprovalTab(
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
                Text(if (request.approve) "Approve Steam login" else "Reject Steam login")
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
                    Text(if (request.approve) "Approve" else "Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingAction = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    pendingQrAction?.let { (rawQr, approve) ->
        AlertDialog(
            onDismissRequest = { pendingQrAction = null },
            title = {
                Text(if (approve) "Approve Steam QR login" else "Reject Steam QR login")
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
                    Text(if (approve) "Approve" else "Reject")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingQrAction = null }) {
                    Text("Cancel")
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
                    Text("Refresh")
                }
            }
        }
        if (account == null || !account.canApproveLogins) {
            item { EmptyState("No login session") }
        } else if (pendingLogins.isEmpty()) {
            item { EmptyState("No pending logins") }
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
                        label = { Text("Steam QR link") },
                        leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { pendingQrAction = qrText to true },
                            enabled = account?.canApproveLogins == true && qrText.isNotBlank()
                        ) {
                            Text("Approve")
                        }
                        OutlinedButton(
                            onClick = { pendingQrAction = qrText to false },
                            enabled = account?.canApproveLogins == true && qrText.isNotBlank()
                        ) {
                            Text("Reject")
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
        DetailLine("Device", login.deviceName.ifBlank { "Unknown device" })
        DetailLine("IP", login.ip.ifBlank { "-" })
        DetailLine("Location", login.location.ifBlank { "-" })
        DetailLine("Client", login.clientId.toString())
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
                Text(login.deviceName.ifBlank { "Steam login" }, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(login.ip, login.location).filter { it.isNotBlank() }.joinToString(" · ").ifBlank {
                        "client ${login.clientId}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { onRespond(login, true) }) {
                Icon(Icons.Default.Check, contentDescription = "Approve")
            }
            IconButton(onClick = { onRespond(login, false) }) {
                Icon(Icons.Default.Close, contentDescription = "Reject")
            }
        }
    }
}

@Composable
private fun SteamImportTab(
    pendingChallenge: SteamLoginChallengeUi?,
    onImportMaFile: (Uri, Uri?, String, String) -> Unit,
    onBeginLogin: (String, String, String) -> Unit,
    onSubmitLoginCode: (String, String) -> Unit
) {
    var maFileUri by remember { mutableStateOf<Uri?>(null) }
    var manifestUri by remember { mutableStateOf<Uri?>(null) }
    var maFilePassword by remember { mutableStateOf("") }
    var maFileDisplayName by remember { mutableStateOf("") }
    var loginName by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginDisplayName by remember { mutableStateOf("") }
    var challengeCode by remember { mutableStateOf("") }
    val maFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        maFileUri = uri
    }
    val manifestPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        manifestUri = uri
    }

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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("maFile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { maFilePicker.launch("*/*") }) {
                            Icon(Icons.Default.UploadFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("maFile")
                        }
                        OutlinedButton(onClick = { manifestPicker.launch("application/json") }) {
                            Text("manifest.json")
                        }
                    }
                    Text(maFileUri?.lastPathSegment ?: "No maFile selected", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedTextField(
                        value = maFilePassword,
                        onValueChange = { maFilePassword = it },
                        label = { Text("Encrypted maFile password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = maFileDisplayName,
                        onValueChange = { maFileDisplayName = it },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            maFileUri?.let { uri ->
                                onImportMaFile(uri, manifestUri, maFilePassword, maFileDisplayName)
                            }
                        },
                        enabled = maFileUri != null
                    ) {
                        Text("Import")
                    }
                }
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
                    Text("Steam login", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = loginName,
                        onValueChange = { loginName = it },
                        label = { Text("Account") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = loginPassword,
                        onValueChange = { loginPassword = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = loginDisplayName,
                        onValueChange = { loginDisplayName = it },
                        label = { Text("Display name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = { onBeginLogin(loginName, loginPassword, loginDisplayName) },
                        enabled = loginName.isNotBlank() && loginPassword.isNotBlank()
                    ) {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Login")
                    }
                    if (pendingChallenge != null) {
                        Text(pendingChallenge.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = challengeCode,
                            onValueChange = { challengeCode = it },
                            label = { Text("Code") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = { onSubmitLoginCode(challengeCode, loginDisplayName) },
                            enabled = challengeCode.isNotBlank()
                        ) {
                            Text("Submit")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
