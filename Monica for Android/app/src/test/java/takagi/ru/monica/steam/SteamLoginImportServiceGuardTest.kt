package takagi.ru.monica.steam

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

        assertEquals(3, SteamLoginImportService.manualCodeTypeForPollingChallenge(4))
        assertEquals(2, SteamLoginImportService.manualCodeTypeForPollingChallenge(5))
        assertNull(SteamLoginImportService.manualCodeTypeForPollingChallenge(2))
        assertNull(SteamLoginImportService.manualCodeTypeForPollingChallenge(3))
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
    fun steamLoginImportAllowsCodeOrApprovalAndUsesSteamAccountName() {
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamViewModel.kt"
        ).readText()
        val screenSource = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/ui/SteamScreen.kt"
        ).readText()
        val loginDialogSource = screenSource.substringAfter("private fun SteamLoginImportDialog")
            .substringBefore("private fun badgeCountText")

        assertTrue(viewModelSource.contains("SteamLoginImportService.manualCodeTypeForPollingChallenge"))
        assertTrue(viewModelSource.contains("displayNameOverride = null"))
        assertTrue(viewModelSource.contains("requiresCode && canPoll"))
        assertFalse(viewModelSource.contains("fun beginSteamLogin(userName: String, password: String, displayName"))
        assertFalse(viewModelSource.contains("fun submitSteamLoginCode(code: String, displayName"))

        assertTrue(loginDialogSource.contains("onBeginLogin: (String, String) -> Unit"))
        assertTrue(loginDialogSource.contains("onSubmitLoginCode: (String) -> Unit"))
        assertTrue(loginDialogSource.contains("pendingChallenge.canPoll"))
        assertTrue(loginDialogSource.contains("PasswordDatabase.getDatabase(context)"))
        assertTrue(loginDialogSource.contains("SecurityManager(context)"))
        assertTrue(loginDialogSource.contains("PasswordEntryPickerBottomSheet("))
        assertTrue(loginDialogSource.contains("R.string.autofill_select_password"))
        assertTrue(loginDialogSource.contains("R.string.steam_login_fill_from_password_applied"))
        assertTrue(loginDialogSource.contains("passwordEntriesForPicker.filter { !it.isDeleted && !it.isArchived }"))
        assertTrue(loginDialogSource.contains("pickerSecurityManager.decryptData(entry.username)"))
        assertTrue(loginDialogSource.contains("pickerSecurityManager.decryptData(entry.password)"))
        assertTrue(loginDialogSource.contains("if (showSteamPasswordPicker && pendingChallenge == null)"))
        assertFalse(loginDialogSource.contains("loginDisplayName"))
        assertFalse(loginDialogSource.contains("steam_display_name_label"))
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

    @Test
    fun steamLoginGuardCodeAndPollingUseAuthApiProtobufShape() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/steam/service/SteamLoginImportService.kt"
        ).readText()

        assertTrue(source.contains("submitSteamGuardCodeWithProtobuf"))
        assertTrue(source.contains("method = \"UpdateAuthSessionWithSteamGuardCode\""))
        assertTrue(source.contains("writeUint64(1, clientIdLong)"))
        assertTrue(source.contains("writeFixed64(2, steamIdLong)"))
        assertTrue(source.contains("writeString(3, code.trim())"))
        assertTrue(source.contains("writeVarint(4, confirmationType.toLong())"))

        assertTrue(source.contains("pollForTokenWithProtobuf"))
        assertTrue(source.contains("method = \"PollAuthSessionStatus\""))
        assertTrue(source.contains("writeUint64(1, clientId)"))
        assertTrue(source.contains("writeBytes(2, authIds.requestId)"))
        assertTrue(source.contains("decodeAuthApiRequestIdBytes"))
        assertTrue(source.contains("parseUnsigned64AsSignedLong"))
        assertTrue(source.contains("pollForTokenWithForm"))
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
