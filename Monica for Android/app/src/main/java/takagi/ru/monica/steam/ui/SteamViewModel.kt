package takagi.ru.monica.steam.ui

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.BufferedReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.R
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.steam.core.SteamTotp
import takagi.ru.monica.steam.data.SteamAccount
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.diagnostics.SteamDiagLogger
import takagi.ru.monica.steam.importer.SteamMaFileParser
import takagi.ru.monica.steam.network.SteamAuthorizedDevice
import takagi.ru.monica.steam.network.SteamAuthorizedDeviceService
import takagi.ru.monica.steam.network.SteamBatchResult
import takagi.ru.monica.steam.network.SteamConfirmation
import takagi.ru.monica.steam.network.SteamConfirmationService
import takagi.ru.monica.steam.network.SteamLoginApprovalService
import takagi.ru.monica.steam.network.SteamPendingLogin
import takagi.ru.monica.steam.network.SteamQrChallenge
import takagi.ru.monica.steam.service.SteamLoginImportService

data class SteamUiState(
    val accounts: List<SteamAccount> = emptyList(),
    val selectedAccountId: Long? = null,
    val currentCode: String = "",
    val secondsRemaining: Int = 30,
    val periodProgress: Float = 1f,
    val confirmations: List<SteamConfirmation> = emptyList(),
    val pendingLogins: List<SteamPendingLogin> = emptyList(),
    val authorizedDevices: List<SteamAuthorizedDevice> = emptyList(),
    val selectedConfirmationIds: Set<String> = emptySet(),
    val pendingLoginChallenge: SteamLoginChallengeUi? = null,
    val loading: Boolean = false,
    val message: String? = null
)

data class SteamLoginChallengeUi(
    val pendingSessionId: String,
    val steamId: String,
    val confirmationType: Int,
    val message: String,
    val requiresCode: Boolean,
    val canPoll: Boolean
)

class SteamViewModel(
    private val appContext: Context,
    private val repository: SteamAccountRepository,
    private val parser: SteamMaFileParser = SteamMaFileParser(),
    private val confirmationService: SteamConfirmationService = SteamConfirmationService(),
    private val authorizedDeviceService: SteamAuthorizedDeviceService = SteamAuthorizedDeviceService(),
    private val loginApprovalService: SteamLoginApprovalService = SteamLoginApprovalService(),
    private val loginImportService: SteamLoginImportService = SteamLoginImportService()
) : ViewModel() {
    private val _uiState = MutableStateFlow(SteamUiState())
    val uiState: StateFlow<SteamUiState> = _uiState.asStateFlow()
    private var pendingLoginPollJob: Job? = null

    init {
        SteamDiagLogger.initialize(appContext.applicationContext)
        viewModelScope.launch {
            repository.observeAccounts().collect { accounts ->
                updateForAccounts(accounts, System.currentTimeMillis())
            }
        }
        viewModelScope.launch {
            while (isActive) {
                updateCodeTick(System.currentTimeMillis())
                delay(CODE_TICK_INTERVAL_MS)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                val account = selectedAccount()
                if (account?.canApproveLogins == true) {
                    refreshPendingLogins(silent = true)
                }
            }
        }
    }

    fun selectAccount(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.select(id)
        }
    }

    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateSortOrders(items)
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun importMaFile(
        maFileUri: Uri,
        manifestUri: Uri?,
        password: String,
        displayName: String
    ) {
        viewModelScope.launch {
            setLoading(true)
            runCatching {
                val maFileText = readText(maFileUri)
                val manifestText = manifestUri?.let { readText(it) }
                val payload = parser.parse(
                    maFileContent = maFileText,
                    fileName = maFileUri.lastPathSegment,
                    manifestContent = manifestText,
                    password = password.takeIf { it.isNotEmpty() },
                    displayNameOverride = displayName
                )
                repository.upsertFromMaFile(payload)
            }.onSuccess {
                setMessage(R.string.steam_account_imported)
            }.onFailure { error ->
                setMessage(error.message ?: appContext.getString(R.string.steam_import_failed))
            }
            setLoading(false)
        }
    }

    fun beginSteamLogin(userName: String, password: String) {
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            setLoading(true)
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.beginLogin(userName, password)
            }) {
                is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                    val challenge = result.toChallengeUi()
                    _uiState.value = _uiState.value.copy(
                        pendingLoginChallenge = challenge,
                        message = challenge.message
                    )
                    if (challenge.canPoll) {
                        startPendingLoginPolling(challenge.pendingSessionId)
                    }
                }
                is SteamLoginImportService.LoginResult.ReadyForImport -> {
                    pendingLoginPollJob?.cancel()
                    saveLoginResult(result)
                    _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
                    setMessage(R.string.steam_account_imported)
                }
                is SteamLoginImportService.LoginResult.Failure -> setMessage(result.message)
            }
            setLoading(false)
        }
    }

    fun submitSteamLoginCode(code: String) {
        val challenge = _uiState.value.pendingLoginChallenge ?: return
        if (!challenge.requiresCode) return
        viewModelScope.launch {
            pendingLoginPollJob?.cancel()
            setLoading(true)
            when (val result = withContext(Dispatchers.IO) {
                loginImportService.submitSteamGuardCode(
                    pendingSessionId = challenge.pendingSessionId,
                    code = code,
                    confirmationType = challenge.confirmationType
                )
            }) {
                is SteamLoginImportService.LoginResult.ReadyForImport -> {
                    pendingLoginPollJob?.cancel()
                    saveLoginResult(result)
                    _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
                    setMessage(R.string.steam_account_imported)
                }
                is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                    val challengeUi = result.toChallengeUi(fallbackType = challenge.confirmationType)
                    _uiState.value = _uiState.value.copy(pendingLoginChallenge = challengeUi)
                    setMessage(challengeUi.message)
                    if (challengeUi.canPoll) {
                        startPendingLoginPolling(challengeUi.pendingSessionId)
                    }
                }
                is SteamLoginImportService.LoginResult.Failure -> setMessage(result.message)
            }
            setLoading(false)
        }
    }

    fun cancelSteamLoginChallenge() {
        pendingLoginPollJob?.cancel()
        pendingLoginPollJob = null
        _uiState.value.pendingLoginChallenge?.pendingSessionId?.let { sessionId ->
            loginImportService.clearPendingSession(sessionId)
        }
        _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
    }

    fun deleteAccount(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
            _uiState.value = _uiState.value.copy(
                confirmations = emptyList(),
                pendingLogins = emptyList(),
                authorizedDevices = emptyList(),
                selectedConfirmationIds = emptySet()
            )
        }
    }

    fun refreshConfirmations(silent: Boolean = false) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            if (!silent) setLoading(true)
            runCatching {
                withContext(Dispatchers.IO) { confirmationService.fetch(account) }
            }.onSuccess { confirmations ->
                _uiState.value = _uiState.value.copy(
                    confirmations = confirmations,
                    selectedConfirmationIds = _uiState.value.selectedConfirmationIds.intersect(confirmations.map { it.id }.toSet())
                )
            }.onFailure { error ->
                if (!silent) setMessage(
                    error.message ?: appContext.getString(R.string.steam_cannot_refresh_confirmations)
                )
            }
            if (!silent) setLoading(false)
        }
    }

    fun toggleConfirmation(id: String) {
        val selected = _uiState.value.selectedConfirmationIds.toMutableSet()
        if (!selected.add(id)) selected.remove(id)
        _uiState.value = _uiState.value.copy(selectedConfirmationIds = selected)
    }

    fun selectAllConfirmations() {
        val ids = _uiState.value.confirmations.map { it.id }.toSet()
        if (ids.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(selectedConfirmationIds = ids)
        }
    }

    fun clearSelectedConfirmations() {
        _uiState.value = _uiState.value.copy(selectedConfirmationIds = emptySet())
    }

    fun respondSelectedConfirmations(accept: Boolean) {
        val account = selectedAccount() ?: return
        val selectedIds = _uiState.value.selectedConfirmationIds
        val confirmations = _uiState.value.confirmations.filter { it.id in selectedIds }
        if (confirmations.isEmpty()) return
        viewModelScope.launch {
            setLoading(true)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    confirmationService.respondMultiple(account, confirmations, accept)
                }
            }.getOrElse { SteamBatchResult(ok = 0, failed = confirmations.size) }
            setMessage(R.string.steam_batch_done, result.ok, result.failed)
            _uiState.value = _uiState.value.copy(selectedConfirmationIds = emptySet())
            refreshConfirmations(silent = true)
            setLoading(false)
        }
    }

    fun respondConfirmation(confirmation: SteamConfirmation, accept: Boolean) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            setLoading(true)
            val ok = runCatching {
                withContext(Dispatchers.IO) { confirmationService.respond(account, confirmation, accept) }
            }.getOrDefault(false)
            setMessage(if (ok) R.string.steam_done else R.string.steam_confirmation_failed)
            refreshConfirmations(silent = true)
            setLoading(false)
        }
    }

    fun refreshPendingLogins(silent: Boolean = false) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            if (!silent) setLoading(true)
            runCatching {
                withContext(Dispatchers.IO) { loginApprovalService.pendingLogins(account) }
            }.onSuccess { pending ->
                val previousSeenTimes = _uiState.value.pendingLogins.associate {
                    it.clientId to it.detectedAtMillis
                }
                val now = System.currentTimeMillis()
                val pendingWithSeenTimes = pending.map { login ->
                    login.copy(detectedAtMillis = previousSeenTimes[login.clientId] ?: now)
                }
                _uiState.value = _uiState.value.copy(pendingLogins = pendingWithSeenTimes)
            }.onFailure { error ->
                if (!silent) setMessage(
                    error.message ?: appContext.getString(R.string.steam_cannot_refresh_logins)
                )
            }
            if (!silent) setLoading(false)
        }
    }

    fun refreshAuthorizedDevices(accountId: Long, silent: Boolean = false) {
        viewModelScope.launch {
            val account = withContext(Dispatchers.IO) { repository.getAccount(accountId) } ?: return@launch
            if (account.accessToken.isNullOrBlank()) {
                _uiState.value = _uiState.value.copy(authorizedDevices = emptyList())
                return@launch
            }
            if (!silent) setLoading(true)
            runCatching {
                withContext(Dispatchers.IO) { authorizedDeviceService.fetch(account) }
            }.onSuccess { devices ->
                if (_uiState.value.accounts.any { it.id == accountId }) {
                    _uiState.value = _uiState.value.copy(authorizedDevices = devices)
                }
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(authorizedDevices = emptyList())
                if (!silent) setMessage(
                    error.message ?: appContext.getString(R.string.steam_cannot_refresh_authorized_devices)
                )
            }
            if (!silent) setLoading(false)
        }
    }

    fun revokeAuthorizedDevice(accountId: Long, device: SteamAuthorizedDevice) {
        viewModelScope.launch {
            val account = withContext(Dispatchers.IO) { repository.getAccount(accountId) } ?: return@launch
            setLoading(true)
            val ok = runCatching {
                withContext(Dispatchers.IO) { authorizedDeviceService.revoke(account, device) }
            }.getOrDefault(false)
            setMessage(if (ok) R.string.steam_done else R.string.steam_login_response_failed)
            refreshAuthorizedDevices(accountId, silent = true)
            setLoading(false)
        }
    }

    fun respondPendingLogin(login: SteamPendingLogin, approve: Boolean) {
        val account = selectedAccount() ?: return
        viewModelScope.launch {
            setLoading(true)
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    loginApprovalService.respondToSession(
                        account = account,
                        clientId = login.clientId,
                        version = login.version,
                        approve = approve
                    )
                }
            }.getOrDefault(false)
            setMessage(if (ok) R.string.steam_done else R.string.steam_login_response_failed)
            refreshPendingLogins(silent = true)
            setLoading(false)
        }
    }

    fun respondQr(rawQr: String, approve: Boolean) {
        val account = selectedAccount() ?: return
        val challenge = SteamQrChallenge.parse(rawQr)
        if (challenge == null) {
            setMessage(R.string.steam_invalid_qr_link)
            return
        }
        viewModelScope.launch {
            setLoading(true)
            val ok = runCatching {
                withContext(Dispatchers.IO) { loginApprovalService.respondToQr(account, challenge, approve) }
            }.getOrDefault(false)
            setMessage(if (ok) R.string.steam_done else R.string.steam_qr_response_failed)
            setLoading(false)
        }
    }

    private suspend fun saveLoginResult(
        result: SteamLoginImportService.LoginResult.ReadyForImport
    ) {
        val payload = parser.parseSteamGuardJson(
            steamId = result.steamId,
            deviceId = result.payload.deviceId,
            steamGuardJson = result.payload.steamGuardJson,
            accessToken = result.accessToken,
            refreshToken = result.refreshToken,
            displayNameOverride = null
        )
        withContext(Dispatchers.IO) {
            repository.upsertFromMaFile(payload)
        }
    }

    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).use { reader -> reader.readText() }
        }.orEmpty()
    }

    private fun SteamLoginImportService.LoginResult.ChallengeRequired.toChallengeUi(
        fallbackType: Int = 0
    ): SteamLoginChallengeUi {
        val codeChallenge = challenges.firstOrNull {
            SteamLoginImportService.isCodeChallengeType(it.confirmationType)
        }
        val pollingChallenge = challenges.firstOrNull {
            SteamLoginImportService.isPollingChallengeType(it.confirmationType)
        }
        val selectedChallenge = codeChallenge ?: pollingChallenge ?: challenges.firstOrNull()
        val selectedType = selectedChallenge?.confirmationType ?: fallbackType
        val pollingManualCodeType = pollingChallenge?.let {
            SteamLoginImportService.manualCodeTypeForPollingChallenge(it.confirmationType)
        }
        val confirmationType = pollingManualCodeType
            ?: codeChallenge?.confirmationType
            ?: selectedType
        val requiresCode = codeChallenge != null ||
            pollingManualCodeType != null ||
            SteamLoginImportService.isCodeChallengeType(confirmationType)
        val canPoll = pollingChallenge != null ||
            SteamLoginImportService.isPollingChallengeType(selectedType)
        val serverMessage = message
            ?: selectedChallenge?.associatedMessage?.takeIf { it.isNotBlank() }
        val challengeMessage = if (requiresCode && canPoll) {
            appContext.getString(R.string.steam_login_code_or_approve_message)
        } else {
            serverMessage
            ?: appContext.getString(
                when {
                    SteamLoginImportService.isAddAuthenticatorEmailActivationType(confirmationType) ->
                        R.string.steam_activation_email_message
                    SteamLoginImportService.isAddAuthenticatorActivationType(confirmationType) ->
                        R.string.steam_activation_sms_message
                    requiresCode && canPoll -> R.string.steam_login_code_or_approve_message
                    canPoll -> R.string.steam_login_waiting_approval
                    else -> R.string.steam_verification_required
                }
            )
        }
        return SteamLoginChallengeUi(
            pendingSessionId = pendingSessionId,
            steamId = steamId,
            confirmationType = confirmationType,
            message = challengeMessage,
            requiresCode = requiresCode,
            canPoll = canPoll
        )
    }

    private fun startPendingLoginPolling(pendingSessionId: String) {
        pendingLoginPollJob?.cancel()
        pendingLoginPollJob = viewModelScope.launch {
            repeat(40) {
                delay(3_000L)
                val result = withContext(Dispatchers.IO) {
                    loginImportService.pollPendingSession(pendingSessionId)
                }
                when (result) {
                    is SteamLoginImportService.LoginResult.ReadyForImport -> {
                        setLoading(true)
                        saveLoginResult(result)
                        _uiState.value = _uiState.value.copy(pendingLoginChallenge = null)
                        setMessage(R.string.steam_account_imported)
                        setLoading(false)
                        pendingLoginPollJob = null
                        return@launch
                    }
                    is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                        _uiState.value = _uiState.value.copy(
                            pendingLoginChallenge = result.toChallengeUi()
                        )
                    }
                    is SteamLoginImportService.LoginResult.Failure -> {
                        setMessage(result.message)
                        pendingLoginPollJob = null
                        return@launch
                    }
                }
            }
            setMessage(R.string.steam_login_approval_timeout)
            pendingLoginPollJob = null
        }
    }

    private fun selectedAccount(): SteamAccount? {
        val state = _uiState.value
        return state.accounts.firstOrNull { it.id == state.selectedAccountId }
            ?: state.accounts.firstOrNull()
    }

    private fun updateForAccounts(accounts: List<SteamAccount>, nowMillis: Long) {
        val selected = accounts.firstOrNull { it.selected } ?: accounts.firstOrNull()
        val previous = _uiState.value
        val selectedChanged = previous.selectedAccountId != selected?.id
        val nowSeconds = nowMillis / 1000L
        _uiState.value = previous.copy(
            accounts = accounts,
            selectedAccountId = selected?.id,
            currentCode = selected?.let { SteamTotp.generateAuthCode(it.sharedSecret, nowSeconds) }.orEmpty(),
            secondsRemaining = secondsRemaining(nowMillis),
            periodProgress = periodProgress(nowMillis),
            confirmations = if (selectedChanged) emptyList() else previous.confirmations,
            pendingLogins = if (selectedChanged) emptyList() else previous.pendingLogins,
            authorizedDevices = if (selectedChanged) emptyList() else previous.authorizedDevices,
            selectedConfirmationIds = if (selectedChanged) emptySet() else previous.selectedConfirmationIds
        )
    }

    private fun updateCodeTick(nowMillis: Long) {
        val account = selectedAccount()
        val nowSeconds = nowMillis / 1000L
        _uiState.value = _uiState.value.copy(
            currentCode = account?.let { SteamTotp.generateAuthCode(it.sharedSecret, nowSeconds) }.orEmpty(),
            secondsRemaining = secondsRemaining(nowMillis),
            periodProgress = periodProgress(nowMillis)
        )
    }

    private fun secondsRemaining(nowMillis: Long): Int {
        val remainingMillis = CODE_PERIOD_MS - Math.floorMod(nowMillis, CODE_PERIOD_MS)
        return ((remainingMillis + 999L) / 1000L).toInt().coerceIn(1, 30)
    }

    private fun periodProgress(nowMillis: Long): Float {
        val remainingMillis = CODE_PERIOD_MS - Math.floorMod(nowMillis, CODE_PERIOD_MS)
        return (remainingMillis.toFloat() / CODE_PERIOD_MS.toFloat()).coerceIn(0f, 1f)
    }

    private fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(loading = loading)
    }

    private fun setMessage(message: String) {
        _uiState.value = _uiState.value.copy(message = message)
    }

    private fun setMessage(@StringRes resId: Int, vararg formatArgs: Any) {
        _uiState.value = _uiState.value.copy(message = appContext.getString(resId, *formatArgs))
    }

    companion object {
        private const val CODE_PERIOD_MS = 30_000L
        private const val CODE_TICK_INTERVAL_MS = 250L

        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val database = SteamDatabase.getDatabase(appContext)
                    val securityManager = SecurityManager(appContext)
                    return SteamViewModel(
                        appContext = appContext,
                        repository = SteamAccountRepository(database.steamAccountDao(), securityManager)
                    ) as T
                }
            }
        }
    }
}
