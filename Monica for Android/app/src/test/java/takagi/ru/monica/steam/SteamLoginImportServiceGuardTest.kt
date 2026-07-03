package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.steam.network.SteamProtoReader
import takagi.ru.monica.steam.network.SteamProtoWriter
import takagi.ru.monica.steam.service.SteamLoginImportService

class SteamLoginImportServiceGuardTest {
    @Test
    fun authApiChallengeTypesSeparateCodesFromMobileApproval() {
        assertTrue(SteamLoginImportService.isCodeChallengeType(2))
        assertTrue(SteamLoginImportService.isCodeChallengeType(3))
        assertFalse(SteamLoginImportService.isCodeChallengeType(4))
        assertFalse(SteamLoginImportService.isCodeChallengeType(5))

        assertFalse(SteamLoginImportService.isPollingChallengeType(2))
        assertFalse(SteamLoginImportService.isPollingChallengeType(3))
        assertTrue(SteamLoginImportService.isPollingChallengeType(4))
        assertTrue(SteamLoginImportService.isPollingChallengeType(5))
    }

    @Test
    fun steamLoginImportKeepsMobileApprovalPollingPath() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()

        assertTrue(source.contains("\"platform_type\" to \"3\""))
        assertTrue(source.contains("STEAM_WEBSITE_ID = \"Mobile\""))
        assertTrue(source.contains("pollPendingSession"))
        assertTrue(source.contains("codeAlreadyAccepted = updateEResult == 29"))
        assertTrue(source.contains("method = \"AddAuthenticator\""))
        assertTrue(source.contains("method = \"FinalizeAddAuthenticator\""))
        assertTrue(source.contains("writeFixed64(1, steamIdLong)"))
        assertFalse(source.contains("URL_ADD_AUTHENTICATOR"))
        assertTrue(viewModelSource.contains("startPendingLoginPolling"))
        assertTrue(viewModelSource.contains("SteamLoginImportService.isPollingChallengeType"))
        assertTrue(viewModelSource.contains("SteamLoginImportService.isAddAuthenticatorActivationType"))
    }

    @Test
    fun steamProtoSupportsSteamFixed64Fields() {
        val steamId = 76561198000000000L
        val writer = SteamProtoWriter().apply {
            writeFixed64(1, steamId)
        }

        val fields = SteamProtoReader(writer.toByteArray()).parse()

        assertEquals(steamId, fields[1]?.asFixed64)
        assertEquals(steamId.toString(), fields[1]?.asFixed64UnsignedString)
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
