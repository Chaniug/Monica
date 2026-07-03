package takagi.ru.monica.steam.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import takagi.ru.monica.R
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.ui.screens.QrScannerScreen

@Composable
fun SteamQrScannerScreen(
    initialAccountId: Long?,
    onQrCodeScanned: (String, Long?) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: SteamViewModel = viewModel(
        factory = remember(context) { SteamViewModel.factory(context) }
    )
    val uiState by viewModel.uiState.collectAsState()
    val rememberedAccountId = remember(context) { readLastSteamQrAccountId(context) }
    var selectedAccountId by rememberSaveable(initialAccountId, rememberedAccountId) {
        mutableStateOf(initialAccountId ?: rememberedAccountId)
    }
    var showAccountPicker by remember { mutableStateOf(false) }

    LaunchedEffect(initialAccountId, rememberedAccountId, uiState.accounts) {
        val existingIds = uiState.accounts.map { it.id }.toSet()
        selectedAccountId = when {
            initialAccountId != null && initialAccountId in existingIds -> initialAccountId
            selectedAccountId != null && selectedAccountId in existingIds -> selectedAccountId
            rememberedAccountId != null && rememberedAccountId in existingIds -> rememberedAccountId
            uiState.selectedAccountId != null && uiState.selectedAccountId in existingIds -> uiState.selectedAccountId
            else -> uiState.accounts.firstOrNull()?.id
        }
    }

    val selectedAccount = uiState.accounts.firstOrNull { it.id == selectedAccountId }

    if (showAccountPicker) {
        SteamQrAccountPickerDialog(
            accounts = uiState.accounts,
            selectedAccountId = selectedAccountId,
            onSelectAccount = { account ->
                selectedAccountId = account.id
                saveLastSteamQrAccountId(context, account.id)
                showAccountPicker = false
            },
            onDismissRequest = { showAccountPicker = false }
        )
    }

    QrScannerScreen(
        onQrCodeScanned = { qrData ->
            val accountId = selectedAccount?.id ?: selectedAccountId
            saveLastSteamQrAccountId(context, accountId)
            onQrCodeScanned(qrData, accountId)
        },
        onNavigateBack = onNavigateBack,
        modifier = modifier,
        title = stringResource(R.string.scan_qr_code_title),
        subtitle = stringResource(R.string.qr_align_hint),
        bottomContent = { launchGallery ->
            SteamQrScannerBottomContent(
                selectedAccount = selectedAccount,
                onSelectAccount = { showAccountPicker = true },
                onPickFromGallery = launchGallery
            )
        }
    )
}

@Composable
private fun SteamQrScannerBottomContent(
    selectedAccount: SteamAccount?,
    onSelectAccount: () -> Unit,
    onPickFromGallery: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalButton(
            onClick = onSelectAccount,
            modifier = Modifier
                .weight(1f)
                .height(72.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.steam_account_label),
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = selectedAccount?.displayNameForQr()
                        ?: stringResource(R.string.steam_no_login_session),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        val albumInteractionSource = remember { MutableInteractionSource() }
        val albumPressed by albumInteractionSource.collectIsPressedAsState()
        val albumContainerColor by animateColorAsState(
            targetValue = if (albumPressed) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
            } else {
                MaterialTheme.colorScheme.primaryContainer
            },
            label = "SteamQrAlbumContainerColor"
        )
        Surface(
            modifier = Modifier
                .size(72.dp)
                .clickable(
                    interactionSource = albumInteractionSource,
                    indication = null,
                    onClick = onPickFromGallery
                ),
            shape = RoundedCornerShape(18.dp),
            color = albumContainerColor,
            tonalElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = stringResource(R.string.steam_qr_album_select)
                )
                Text(
                    text = stringResource(R.string.steam_qr_album_select),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SteamQrAccountPickerDialog(
    accounts: List<SteamAccount>,
    selectedAccountId: Long?,
    onSelectAccount: (SteamAccount) -> Unit,
    onDismissRequest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.steam_switch_account)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { account ->
                    OutlinedButton(
                        onClick = { onSelectAccount(account) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = account.displayNameForQr(),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (account.id == selectedAccountId) {
                            Text(stringResource(R.string.steam_selected_account_marker))
                        }
                    }
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

private fun SteamAccount.displayNameForQr(): String {
    return displayName.ifBlank { accountName.ifBlank { steamId } }
}
