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
        assertTrue(source.contains("private fun SteamCodeContent(\n    account: SteamAccount,"))
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
        assertTrue(screenSource.contains("R.string.select_all"))
        assertTrue(screenSource.contains("R.string.deselect_all"))

        assertTrue(viewModelSource.contains("CODE_TICK_INTERVAL_MS = 250L"))
        assertTrue(viewModelSource.contains("periodProgress"))
        assertTrue(screenSource.contains("animateFloatAsState"))
        assertTrue(screenSource.contains("SteamCodePeriodProgress"))

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
