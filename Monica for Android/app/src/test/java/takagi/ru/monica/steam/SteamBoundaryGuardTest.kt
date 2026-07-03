package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.BottomNavContentTab
import takagi.ru.monica.data.BottomNavVisibility

class SteamBoundaryGuardTest {
    @Test
    fun steamDockIsPresentButHiddenByDefault() {
        val oldVisibleOrder = listOf(
            BottomNavContentTab.VAULT_V2,
            BottomNavContentTab.PASSWORDS,
            BottomNavContentTab.AUTHENTICATOR,
            BottomNavContentTab.CARD_WALLET,
            BottomNavContentTab.PASSKEY,
            BottomNavContentTab.NOTES,
            BottomNavContentTab.SEND
        )

        assertEquals(oldVisibleOrder, BottomNavContentTab.DEFAULT_ORDER.take(oldVisibleOrder.size))
        assertEquals(BottomNavContentTab.STEAM, BottomNavContentTab.DEFAULT_ORDER.last())
        assertFalse(BottomNavVisibility().isVisible(BottomNavContentTab.STEAM))
        assertEquals(5, BottomNavVisibility().visibleCount())
    }

    @Test
    fun steamDoesNotChangeMainPasswordDatabaseSchema() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/data/PasswordDatabase.kt").readText()

        assertTrue(source.contains("version = 72"))
        assertFalse(source.contains("SteamAccountEntity::class"))
        assertFalse(source.contains("abstract fun steamAccountDao"))
    }

    @Test
    fun steamRepositoryDoesNotDependOnSecureItems() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()

        assertFalse(source.contains("SecureItemRepository"))
        assertFalse(source.contains("SecureItemDao"))
        assertFalse(source.contains("ItemType.TOTP"))
        assertTrue(source.contains("SecurityManager"))
    }

    @Test
    fun steamLocalStorageEncryptsAccountFieldsAndMigratesExistingRows() {
        val repositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountRepository.kt"
        ).readText()
        val daoSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamAccountDao.kt"
        ).readText()
        val databaseSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/data/SteamDatabase.kt"
        ).readText()

        assertTrue(repositorySource.contains("steamId = encrypt(payload.steamId)"))
        assertTrue(repositorySource.contains("accountName = encrypt(payload.accountName)"))
        assertTrue(repositorySource.contains("displayName = encrypt(payload.displayName)"))
        assertTrue(repositorySource.contains("deviceId = encrypt(payload.deviceId)"))
        assertTrue(repositorySource.contains("sharedSecret = encrypt(payload.sharedSecret)"))
        assertTrue(repositorySource.contains("rawSteamGuardJson = encrypt(payload.rawJson)"))
        assertTrue(repositorySource.contains("displayName = encrypt(displayName.trim().ifBlank { existingPlain.accountName })"))

        assertTrue(repositorySource.contains("steamId = decrypt(entity.steamId).orEmpty()"))
        assertTrue(repositorySource.contains("accountName = decrypt(entity.accountName).orEmpty()"))
        assertTrue(repositorySource.contains("displayName = decrypt(entity.displayName).orEmpty()"))
        assertTrue(repositorySource.contains("deviceId = decrypt(entity.deviceId).orEmpty()"))
        assertTrue(repositorySource.contains("findExistingBySteamId"))
        assertFalse(daoSource.contains("getBySteamId"))

        assertTrue(databaseSource.contains("version = 2"))
        assertTrue(databaseSource.contains(".addMigrations(migration1To2(context.applicationContext))"))
        assertTrue(databaseSource.contains("encryptExistingSteamRows"))
        assertTrue(databaseSource.contains("\"steam_id\""))
        assertTrue(databaseSource.contains("\"accountName\""))
        assertTrue(databaseSource.contains("\"displayName\""))
        assertTrue(databaseSource.contains("\"deviceId\""))
        assertTrue(databaseSource.contains("\"rawSteamGuardJson\""))
        assertTrue(databaseSource.contains("securityManager.encryptDataLegacyCompat(value)"))
    }

    @Test
    fun steamLoginImportLogsDoNotPersistRawAccountData() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()

        assertFalse(source.contains("payload=${'$'}beginPayload"))
        assertFalse(source.contains("phoneHint=${'$'}phoneHint"))
        assertFalse(source.contains("user=${'$'}userName"))
        assertFalse(source.contains("steamId=${'$'}steamId"))
        assertFalse(source.contains("Legacy RSA response invalid: ${'$'}response"))
        assertFalse(source.contains("message=${'$'}{responseMessage ?: \"\"}"))
    }

    @Test
    fun steamPageDoesNotUseLegacyTotpImportWritePath() {
        val steamSources = listOf(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt",
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt",
            "app/src/main/java/takagi/ru/monica/steam/importer/SteamMaFileParser.kt"
        ).joinToString("\n") { projectFile(it).readText() }

        assertFalse(steamSources.contains("DataExportImportViewModel"))
        assertFalse(steamSources.contains("importSteamMaFile"))
        assertFalse(steamSources.contains("insertSteamGuardEntry"))
    }

    @Test
    fun steamPageUsesMonicaTopBarAndLocalizedMenuInsteadOfWideTabs() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt")
            .readText()
            .replace("\r\n", "\n")

        assertTrue(source.contains("ExpressiveTopBar"))
        assertTrue(source.contains("PasswordTopActionsDropdownMenu"))
        assertFalse(source.contains("ScrollableTabRow"))
        assertFalse(source.contains("listOf(\"Code\", \"Confirm\", \"Login\", \"Import\")"))
        assertFalse(source.contains("Text(\""))
        assertFalse(source.contains("var searchQuery"))
        assertFalse(source.contains("var isSearchExpanded"))
        assertFalse(source.contains("SteamSection.IMPORT"))
        assertFalse(source.contains("SteamAccountSelector("))
        assertFalse(source.contains("SteamImportContent("))
        assertTrue(source.contains("if (selectedAccount == null)"))
        assertTrue(source.contains("BadgedBox"))
        assertTrue(source.contains("pendingConfirmationCount"))
        assertTrue(source.contains("CONFIRMATIONS(R.string.steam_section_confirmations, Icons.Default.VerifiedUser)"))
        assertTrue(source.contains("val targetSection = when (selectedSection)"))
        assertTrue(source.contains("SteamSection.CODE -> SteamSection.CONFIRMATIONS"))
        assertTrue(source.contains("SteamSection.CONFIRMATIONS -> SteamSection.CODE"))
        assertTrue(source.contains("imageVector = targetSection.icon"))
        assertTrue(source.contains("contentDescription = stringResource(targetSection.labelRes)"))
        assertTrue(source.contains("if (targetSection == SteamSection.CONFIRMATIONS && pendingConfirmationCount > 0)"))
        assertTrue(source.contains("SteamAddMethodDialog"))
        assertTrue(source.contains("SteamEmptyAccountContent"))
        assertTrue(source.contains("private fun SteamCodeContent(\n    accounts: List<SteamAccount>,"))
        assertTrue(source.contains("selectedTokenAccountIds"))
        assertFalse(source.contains("SteamTokenSelectionBar"))
        assertTrue(source.contains("SteamAccountDetailContent"))
        assertTrue(source.contains("SteamLoginApprovalSection"))
        assertTrue(source.contains("SteamAvatarImage"))
        assertTrue(source.contains("STEAM_AVATAR_CACHE_TTL_MS"))
        assertTrue(source.contains("steamAvatarCacheFile"))
        assertTrue(source.contains("readSteamAvatarCache"))
        assertTrue(source.contains("freshAvatar ?: cachedAvatar"))
        assertTrue(source.contains("floatingActionButton = {"))
        assertTrue(source.contains("val tokenQrAccount = remember("))
        assertTrue(source.contains("selectedSection == SteamSection.CODE"))
        assertTrue(source.contains("selectedTokenAccountIds.isEmpty()"))
        assertTrue(source.contains("readLastSteamQrAccountId(context)"))
        assertTrue(source.contains("saveLastSteamQrAccountId(context, accountId)"))
        assertTrue(source.contains("AnimatedVisibility("))
        assertTrue(source.contains("FloatingActionButton("))
        assertTrue(source.contains("rememberLastSteamQrAccount(account.id)"))
        assertFalse(source.contains("SteamAccountDetailHeader"))
        assertFalse(source.contains("SteamAccountPasswordLoginCard"))
        assertFalse(source.contains("R.string.steam_password_not_saved"))
        assertFalse(source.contains("R.string.steam_password_login_section"))
        assertFalse(source.contains("var showAccountMenu"))
        assertFalse(source.contains("SteamAccountSwitchSheet"))
        assertFalse(source.contains("R.string.steam_switch_account"))
        assertFalse(source.contains("rememberModalBottomSheetState"))
        assertFalse(source.contains("Modifier.widthIn(max = 72.dp)"))
        assertFalse(source.contains("SteamAccountSwitchMenu"))
        assertFalse(source.contains("SteamSection.values().forEach"))
        assertFalse(source.contains("steam_more_options_with_confirmations"))

        val topBarAccountAction = source
            .substringAfter("actions = {")
            .substringBefore("if (detailAccount == null && selectedAccount != null)")
        assertTrue(topBarAccountAction.contains("Icons.Default.Refresh"))
        assertTrue(topBarAccountAction.contains("viewModel.refreshConfirmations()"))
        assertTrue(topBarAccountAction.contains("showAddAccountDialog = true"))
        assertTrue(topBarAccountAction.contains("Icons.Default.Add"))
        assertTrue(topBarAccountAction.contains("R.string.steam_add_account_button"))
        assertTrue(topBarAccountAction.indexOf("Icons.Default.Refresh") < topBarAccountAction.indexOf("Icons.Default.Add"))

        val topActionsMenu = source
            .substringAfter("private fun SteamTopActionsMenu(")
            .substringBefore("@Composable\nprivate fun SteamCodeContent(")
        assertFalse(topActionsMenu.contains("accounts: List<SteamAccount>"))
        assertFalse(topActionsMenu.contains("accounts.forEach"))
        assertFalse(topActionsMenu.contains("onSelectAccount"))
        assertFalse(topActionsMenu.contains("onSelectSection"))
        assertFalse(topActionsMenu.contains("SteamSection.values"))
        assertFalse(topActionsMenu.contains("pendingConfirmationCount"))
        assertFalse(topActionsMenu.contains("onAddAccount"))
        assertFalse(topActionsMenu.contains("onDeleteAccount"))
        assertFalse(topActionsMenu.contains("R.string.steam_add_account_button"))
        assertFalse(topActionsMenu.contains("R.string.steam_delete_account_menu"))
        assertFalse(topActionsMenu.contains("R.string.refresh"))
        assertFalse(topActionsMenu.contains("onRefresh"))
        assertTrue(topActionsMenu.contains("R.string.nav_settings"))

        val codeContent = source
            .substringAfter("private fun SteamCodeContent(")
            .substringBefore("@Composable\nprivate fun SteamAccountDetailContent(")
        assertTrue(codeContent.contains("accounts: List<SteamAccount>"))
        assertTrue(codeContent.contains("selectedAccountIds: List<Long>"))
        assertTrue(codeContent.contains("onToggleSelection: (SteamAccount) -> Unit"))
        assertTrue(codeContent.contains("onSelectAll: () -> Unit"))
        assertTrue(codeContent.contains("onDeleteSelected: () -> Unit"))
        assertTrue(codeContent.contains("onOpenDetail: (SteamAccount) -> Unit"))
        assertTrue(codeContent.contains("appSettings: AppSettings"))
        assertTrue(codeContent.contains("rememberTotpTickerMillis(appSettings.validatorSmoothProgress)"))
        assertTrue(codeContent.contains("appSettings.validatorUnifiedProgressBar == UnifiedProgressBarMode.ENABLED"))
        assertTrue(codeContent.contains("UnifiedProgressBar("))
        assertTrue(codeContent.contains("style = appSettings.validatorProgressBarStyle"))
        assertTrue(codeContent.contains("smoothProgress = appSettings.validatorSmoothProgress"))
        assertTrue(codeContent.contains("timeOffset = (appSettings.totpTimeOffset * 1000).toLong()"))
        assertTrue(codeContent.contains("LazyColumn("))
        assertFalse(codeContent.contains("LazyVerticalGrid("))
        assertFalse(codeContent.contains("columns = GridCells.Fixed(2)"))
        assertTrue(codeContent.contains("SwipeActions("))
        assertTrue(codeContent.contains("onSwipeRight = { onToggleSelection(account) }"))
        assertTrue(codeContent.contains("allowSwipeLeft = false"))
        assertTrue(codeContent.contains("SelectionActionBar("))
        assertTrue(codeContent.contains("onSelectAll = onSelectAll"))
        assertTrue(codeContent.contains("if (selectionMode)"))
        assertTrue(codeContent.contains("onOpenDetail(account)"))
        assertTrue(codeContent.contains("copyCode(SteamTotp.generateAuthCode(account.sharedSecret"))
        assertTrue(codeContent.contains("isSelectionMode = false"))
        assertTrue(codeContent.contains("sharedProgressTimeMillis = sharedProgressTimeMillis"))
        assertTrue(codeContent.contains("appSettings = appSettings"))
        assertFalse(codeContent.contains("onLongClick = { onToggleSelection(account) }"))
        assertTrue(codeContent.contains("SteamAvatarImage("))
    }

    @Test
    fun steamPageSupportsQrScanSmoothProgressAndBulkSelection() {
        val screenSource = projectFile("app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt")
            .readText()
        val viewModelSource = projectFile("app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt")
            .readText()
        val mainActivitySource = projectFile("app/src/main/java/takagi/ru/monica/MainActivity.kt")
            .readText()
        val navSource = projectFile("app/src/main/java/takagi/ru/monica/navigation/Screens.kt")
            .readText()
        val steamQrScannerSource = projectFile("app/src/main/java/takagi/ru/monica/steam/ui/SteamQrScannerScreen.kt")
            .readText()
        val bottomNavSource = projectFile("app/src/main/java/takagi/ru/monica/ui/main/navigation/BottomNavModel.kt")
            .readText()

        assertTrue(navSource.contains("object SteamQrScan : Screen(\"steam_qr_scan?accountId={accountId}\")"))
        assertTrue(navSource.contains("const val ARG_ACCOUNT_ID = \"accountId\""))
        assertTrue(navSource.contains("fun createRoute(accountId: Long? = null)"))
        assertTrue(mainActivitySource.contains("steam_qr_result"))
        assertTrue(mainActivitySource.contains("steam_qr_account_id"))
        assertTrue(mainActivitySource.contains("Screen.SteamQrScan.createRoute(accountId)"))
        assertTrue(mainActivitySource.contains("SteamQrScannerScreen("))
        assertTrue(screenSource.contains("pendingSteamQrResult"))
        assertTrue(screenSource.contains("pendingSteamQrAccountId"))
        assertTrue(screenSource.contains("onScanSteamQrCode"))
        assertTrue(screenSource.contains("pendingScannedQr"))
        assertTrue(screenSource.contains("R.string.steam_qr_login_title"))
        assertTrue(screenSource.contains("R.string.scan_qr_code"))
        assertTrue(screenSource.contains("autoPromptedClientIds"))
        assertTrue(screenSource.contains("R.string.steam_login_request_title"))
        assertTrue(screenSource.contains("R.string.steam_time_label"))
        assertTrue(screenSource.contains("formatSteamLoginTime(login.detectedAtMillis)"))
        assertTrue(screenSource.contains("R.string.select_all"))

        assertTrue(steamQrScannerSource.contains("initialAccountId: Long?"))
        assertTrue(steamQrScannerSource.contains("onQrCodeScanned: (String, Long?) -> Unit"))
        assertTrue(steamQrScannerSource.contains("readLastSteamQrAccountId(context)"))
        assertTrue(steamQrScannerSource.contains("rememberSaveable(initialAccountId, rememberedAccountId)"))
        assertTrue(steamQrScannerSource.contains("initialAccountId != null && initialAccountId in existingIds"))
        assertTrue(steamQrScannerSource.contains("rememberedAccountId != null && rememberedAccountId in existingIds"))
        assertTrue(steamQrScannerSource.contains("saveLastSteamQrAccountId(context, account.id)"))
        assertTrue(steamQrScannerSource.contains("saveLastSteamQrAccountId(context, accountId)"))
        assertTrue(steamQrScannerSource.contains("SteamQrScannerBottomContent("))
        assertTrue(steamQrScannerSource.contains("MonicaModalBottomSheet("))
        assertTrue(steamQrScannerSource.contains("navigationBarsPadding()"))
        assertTrue(steamQrScannerSource.contains("LazyColumn("))
        assertTrue(steamQrScannerSource.contains("SteamQrAccountOptionRow("))
        assertTrue(steamQrScannerSource.contains("heightIn(max = 360.dp)"))
        assertTrue(steamQrScannerSource.contains(".height(58.dp)"))
        assertFalse(steamQrScannerSource.contains("DialogProperties"))
        assertFalse(steamQrScannerSource.contains("AlertDialog("))
        assertFalse(steamQrScannerSource.contains("OutlinedButton("))
        assertFalse(steamQrScannerSource.contains("TextButton("))
        assertTrue(steamQrScannerSource.contains(".weight(1f)"))
        assertTrue(steamQrScannerSource.contains(".height(72.dp)"))
        assertTrue(steamQrScannerSource.contains(".size(72.dp)"))
        assertTrue(steamQrScannerSource.contains("indication = null"))
        assertTrue(steamQrScannerSource.contains("collectIsPressedAsState()"))
        assertTrue(steamQrScannerSource.contains("R.string.steam_qr_album_select"))

        assertTrue(viewModelSource.contains("CODE_TICK_INTERVAL_MS = 250L"))
        assertTrue(viewModelSource.contains("periodProgress"))
        assertTrue(screenSource.contains("TotpCodeCard"))
        assertTrue(screenSource.contains("toSteamTotpUiData"))
        assertTrue(screenSource.contains("steam://${'$'}{sharedSecret}"))
        assertFalse(screenSource.contains("AccountDetails(account)"))

        assertTrue(viewModelSource.contains("fun selectAllConfirmations()"))
        assertTrue(viewModelSource.contains("fun clearSelectedConfirmations()"))
        assertTrue(bottomNavSource.contains("SteamDockIcon"))
        assertFalse(bottomNavSource.contains("SportsEsports"))
        assertFalse(screenSource.contains("SportsEsports"))
    }

    @Test
    fun steamDockUsesFixedSteamLabelAndControllerIcon() {
        val bottomNavSource = projectFile("app/src/main/java/takagi/ru/monica/ui/main/navigation/BottomNavModel.kt")
            .readText()
        val quickSetupSource = projectFile("app/src/main/java/takagi/ru/monica/ui/screens/QuickSetupScreen.kt")
            .readText()
        val settingsSource = projectFile("app/src/main/java/takagi/ru/monica/ui/screens/SettingsScreen.kt")
            .readText()
        val iconSource = projectFile("app/src/main/java/takagi/ru/monica/ui/main/navigation/SteamDockIcon.kt")
            .readText()
        val zhStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()
        val defaultStrings = projectFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(bottomNavSource.contains("object Steam : BottomNavItem(BottomNavContentTab.STEAM, SteamDockIcon)"))
        assertTrue(quickSetupSource.contains("BottomNavContentTab.STEAM -> SteamDockIcon"))
        assertTrue(settingsSource.contains("BottomNavContentTab.STEAM -> SteamDockIcon"))
        assertTrue(iconSource.contains("name = \"SteamDockIcon\""))
        assertTrue(iconSource.contains("quadTo(129f, 800f, 86.5f, 757f)"))
        assertFalse(bottomNavSource.contains("BottomNavContentTab.STEAM, Icons.Default.VerifiedUser"))
        assertFalse(quickSetupSource.contains("BottomNavContentTab.STEAM -> Icons.Default.VerifiedUser"))
        assertFalse(settingsSource.contains("BottomNavContentTab.STEAM -> Icons.Default.VerifiedUser"))
        assertTrue(zhStrings.contains("<string name=\"nav_steam_short\">Steam</string>"))
        assertTrue(defaultStrings.contains("<string name=\"nav_steam_short\">Steam</string>"))
    }

    @Test
    fun steamConfirmationPageUsesSlimSwipeSelectionLayout() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt")
            .readText()
            .replace("\r\n", "\n")
        val confirmationContent = source
            .substringAfter("private fun SteamConfirmationsContent(")
            .substringBefore("@Composable\nprivate fun SteamLoginApprovalSection(")

        assertTrue(confirmationContent.contains("SteamConfirmationAccountCard("))
        assertTrue(confirmationContent.contains("modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp)"))
        assertTrue(confirmationContent.contains("LazyColumn("))
        assertTrue(confirmationContent.contains("SwipeActions("))
        assertTrue(confirmationContent.contains("onSwipeRight = { onToggle(confirmation.id) }"))
        assertTrue(confirmationContent.contains("allowSwipeLeft = false"))
        assertTrue(confirmationContent.contains("SelectionActionBar("))
        assertTrue(confirmationContent.contains("onSelectAll = onSelectAll"))
        assertTrue(confirmationContent.contains("onDelete = null"))
        assertTrue(confirmationContent.contains("FloatingActionButton("))
        assertTrue(confirmationContent.contains("showBulkActionDialog = true"))
        assertTrue(confirmationContent.contains("R.string.steam_confirmation_action_title"))
        assertTrue(confirmationContent.contains("SteamConfirmationItemImage("))
        assertTrue(confirmationContent.contains("ContentScale.Fit"))
        assertTrue(confirmationContent.contains("loadSteamConfirmationImage("))
        assertFalse(confirmationContent.contains("Button(onClick = onRefresh"))
        assertFalse(confirmationContent.contains("Checkbox("))
        assertFalse(confirmationContent.contains("onRespond(confirmation, true)"))
        assertFalse(confirmationContent.contains("onRespond(confirmation, false)"))

        val selectionBarSource = projectFile("app/src/main/java/takagi/ru/monica/ui/common/selection/SelectionActionBar.kt")
            .readText()
        assertTrue(selectionBarSource.contains("onDelete: (() -> Unit)? = null"))
        assertTrue(selectionBarSource.contains("onDelete?.let"))

        val serviceSource = projectFile("app/src/main/java/takagi/ru/monica/steam/network/SteamConfirmationService.kt")
            .readText()
        assertTrue(serviceSource.contains("val imageUrl: String"))
        assertTrue(serviceSource.contains("imageUrl = imageUrl()"))
        assertTrue(serviceSource.contains("\"image_url\""))
    }

    private fun projectFile(path: String): File {
        var dir = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
        while (
            dir.parentFile != null &&
            !File(dir, "settings.gradle").exists() &&
            !File(dir, "settings.gradle.kts").exists()
        ) {
            dir = dir.parentFile!!.canonicalFile
        }
        return File(dir, path)
    }
}
