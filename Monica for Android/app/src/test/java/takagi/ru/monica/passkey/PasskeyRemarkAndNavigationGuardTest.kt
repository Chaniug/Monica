package takagi.ru.monica.passkey

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.PasskeyEntry

class PasskeyRemarkAndNavigationGuardTest {

    @Test
    fun remarkWinsWithoutChangingOriginalPasskeyIdentity() {
        val passkey = testPasskey(notes = "工作 GitHub")

        assertEquals("工作 GitHub", passkey.displayTitle())
        assertEquals("TakagiKuyomi", passkey.userDisplayName)
        assertEquals("takagi", passkey.userName)
    }

    @Test
    fun blankRemarkFallsBackToOriginalDisplayName() {
        assertEquals("TakagiKuyomi", testPasskey(notes = "   ").displayTitle())
    }

    @Test
    fun compactPasskeyCardNavigatesInsteadOfExpanding() {
        val listSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt"
        ).readText()
        val clickBody = listSource.substringAfter("onClick = {\n                            if (selectionMode)")
            .substringBefore("onLongClick =")
        val paneSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/passkey/PasskeyPane.kt"
        ).readText()

        assertFalse(clickBody.contains("expanded = !expanded"))
        assertTrue(clickBody.contains("onClick()"))
        assertTrue(paneSource.contains("passkey.managementRecordIdOrNull()?.let(onNavigateToPasskeyDetail)"))
    }

    @Test
    fun credentialSelectorUsesRemarkFirstTitle() {
        val provider = projectFile(
            "app/src/main/java/takagi/ru/monica/passkey/MonicaCredentialProviderService.kt"
        ).readText()
        val authActivity = projectFile(
            "app/src/main/java/takagi/ru/monica/passkey/PasskeyAuthActivity.kt"
        ).readText()

        assertTrue(provider.contains("passkey.displayTitle()"))
        assertTrue(authActivity.contains("title = passkey.displayTitle()"))
    }

    @Test
    fun detailPrioritizesCompactAccountHeroAndMovesBindingDown() {
        val detail = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/passkey/PasskeyDetailPanes.kt"
        ).readText()
        val paneBody = detail.substringAfter("internal fun PasskeyDetailPane(")
            .substringBefore("private fun PasskeyHeroCard(")
        val heroBody = detail.substringAfter("private fun PasskeyHeroCard(")
            .substringBefore("private fun PasskeyBindingCard(")

        assertTrue(heroBody.contains("rememberAutoMatchedSimpleIcon("))
        assertTrue(heroBody.contains("rememberFavicon("))
        assertTrue(heroBody.contains("Row(\n                modifier = Modifier.fillMaxWidth()"))
        assertFalse(paneBody.contains("title = stringResource(R.string.passkey_detail_account)"))
        assertTrue(
            paneBody.indexOf("R.string.passkey_detail_activity") <
                paneBody.indexOf("PasskeyBindingCard(")
        )
    }

    private fun testPasskey(notes: String) = PasskeyEntry(
        credentialId = "credential",
        rpId = "github.com",
        rpName = "GitHub",
        userId = "user-id",
        userName = "takagi",
        userDisplayName = "TakagiKuyomi",
        publicKey = "public-key",
        privateKeyAlias = "private-key",
        notes = notes,
    )

    private fun projectFile(relativePath: String): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, relativePath)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("Unable to find project file: $relativePath")
    }
}
