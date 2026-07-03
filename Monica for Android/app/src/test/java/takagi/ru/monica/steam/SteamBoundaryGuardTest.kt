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
        assertTrue(source.contains("SteamAddMethodDialog"))
        assertTrue(source.contains("SteamEmptyAccountContent"))
        assertTrue(source.contains("private fun SteamCodeContent(\n    accounts: List<SteamAccount>,"))
        assertTrue(source.contains("selectedTokenAccountIds"))
        assertTrue(source.contains("SteamTokenSelectionBar"))
        assertTrue(source.contains("SteamAccountDetailContent"))
        assertTrue(source.contains("SteamLoginApprovalSection"))
        assertTrue(source.contains("SteamAvatarImage"))
        assertTrue(source.contains("STEAM_AVATAR_CACHE_TTL_MS"))
        assertTrue(source.contains("steamAvatarCacheFile"))
        assertTrue(source.contains("readSteamAvatarCache"))
        assertTrue(source.contains("freshAvatar ?: cachedAvatar"))
        assertTrue(source.contains("floatingActionButton = {"))
        assertTrue(source.contains("FloatingActionButton(onClick = scanQr)"))
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

        val topBarAccountAction = source
            .substringAfter("actions = {")
            .substringBefore("if (detailAccount == null && (selectedAccount != null || showStandaloneSettingsEntry))")
        assertTrue(topBarAccountAction.contains("showAddAccountDialog = true"))
        assertTrue(topBarAccountAction.contains("Icons.Default.Add"))
        assertTrue(topBarAccountAction.contains("R.string.steam_add_account_button"))

        val topActionsMenu = source
            .substringAfter("private fun SteamTopActionsMenu(")
            .substringBefore("@Composable\nprivate fun SteamCodeContent(")
        assertFalse(topActionsMenu.contains("accounts: List<SteamAccount>"))
        assertFalse(topActionsMenu.contains("accounts.forEach"))
        assertFalse(topActionsMenu.contains("onSelectAccount"))
        assertFalse(topActionsMenu.contains("onAddAccount"))
        assertFalse(topActionsMenu.contains("onDeleteAccount"))
        assertFalse(topActionsMenu.contains("R.string.steam_add_account_button"))
        assertFalse(topActionsMenu.contains("R.string.steam_delete_account_menu"))

        val codeContent = source
            .substringAfter("private fun SteamCodeContent(")
            .substringBefore("@Composable\nprivate fun SteamTokenSelectionBar(")
        assertTrue(codeContent.contains("accounts: List<SteamAccount>"))
        assertTrue(codeContent.contains("selectedAccountIds: List<Long>"))
        assertTrue(codeContent.contains("onToggleSelection: (SteamAccount) -> Unit"))
        assertTrue(codeContent.contains("onDeleteSelected: () -> Unit"))
        assertTrue(codeContent.contains("onOpenDetail: (SteamAccount) -> Unit"))
        assertTrue(codeContent.contains("onCardClick = { onOpenDetail(account) }"))
        assertTrue(codeContent.contains("onLongClick = { onToggleSelection(account) }"))
        assertTrue(codeContent.contains("isSelectionMode = selectionMode"))
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
        val bottomNavSource = projectFile("app/src/main/java/takagi/ru/monica/ui/main/navigation/BottomNavModel.kt")
            .readText()

        assertTrue(navSource.contains("object SteamQrScan : Screen(\"steam_qr_scan\")"))
        assertTrue(mainActivitySource.contains("steam_qr_result"))
        assertTrue(mainActivitySource.contains("Screen.SteamQrScan.route"))
        assertTrue(screenSource.contains("pendingSteamQrResult"))
        assertTrue(screenSource.contains("onScanSteamQrCode"))
        assertTrue(screenSource.contains("pendingScannedQr"))
        assertTrue(screenSource.contains("R.string.steam_qr_login_title"))
        assertTrue(screenSource.contains("R.string.scan_qr_code"))
        assertTrue(screenSource.contains("autoPromptedClientIds"))
        assertTrue(screenSource.contains("R.string.steam_login_request_title"))
        assertTrue(screenSource.contains("R.string.steam_time_label"))
        assertTrue(screenSource.contains("formatSteamLoginTime(login.detectedAtMillis)"))
        assertTrue(screenSource.contains("R.string.select_all"))
        assertTrue(screenSource.contains("R.string.deselect_all"))

        assertTrue(viewModelSource.contains("CODE_TICK_INTERVAL_MS = 250L"))
        assertTrue(viewModelSource.contains("periodProgress"))
        assertTrue(screenSource.contains("TotpCodeCard"))
        assertTrue(screenSource.contains("toSteamTotpUiData"))
        assertTrue(screenSource.contains("steam://${'$'}{sharedSecret}"))
        assertFalse(screenSource.contains("AccountDetails(account)"))

        assertTrue(viewModelSource.contains("fun selectAllConfirmations()"))
        assertTrue(viewModelSource.contains("fun clearSelectedConfirmations()"))
        assertTrue(bottomNavSource.contains("Icons.Default.VerifiedUser"))
        assertFalse(bottomNavSource.contains("SportsEsports"))
        assertFalse(screenSource.contains("SportsEsports"))
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
