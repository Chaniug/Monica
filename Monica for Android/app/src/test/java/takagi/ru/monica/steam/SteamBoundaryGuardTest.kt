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
