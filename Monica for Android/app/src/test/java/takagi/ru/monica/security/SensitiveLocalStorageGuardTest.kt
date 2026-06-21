package takagi.ru.monica.security

import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveLocalStorageGuardTest {

    @Test
    fun noteDrafts_useProtectedStorageWithLegacyMigration() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/notes/domain/NoteDraftStore.kt")

        assertTrue(source.contains("SecurityManager(context.applicationContext)"))
        assertTrue(source.contains("securityManager.putProtectedString(key(noteId, \"content\"), content)"))
        assertTrue(source.contains("securityManager.getProtectedString(key(noteId, \"content\"))"))
        assertTrue(source.contains("securityManager.removeProtectedString(key(noteId, \"content\"))"))
        assertFalse(source.contains(".putString(key(noteId, \"content\"), content)"))
    }

    @Test
    fun passwordGenerationHistory_isStoredAsEncryptedPayload() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/data/PasswordHistoryManager.kt")

        assertTrue(source.contains("migrateLegacyHistoryIfNeeded"))
        assertTrue(source.contains("securityManager.encryptDataLegacyCompat(json.encodeToString(history))"))
        assertTrue(source.contains("securityManager.decryptData(raw)"))
        assertFalse(source.contains("preferences[HISTORY_KEY] = json.encodeToString"))
    }

    @Test
    fun commonAccountSensitiveFields_areStoredAsEncryptedPayloads() {
        val source = projectFile("app/src/main/java/takagi/ru/monica/data/CommonAccountPreferences.kt")

        assertTrue(source.contains("migrateSensitivePreferencesIfNeeded"))
        assertTrue(source.contains("protectedPreferenceValue(CardWalletDataCodec.encodeBillingAddress(address))"))
        assertTrue(source.contains("protectedPreferenceValue(encodeTemplates"))
        assertTrue(source.contains("securityManager.encryptDataLegacyCompat(it)"))
        assertFalse(source.contains("preferences[KEY_TEMPLATES_JSON] = encodeTemplates"))
        assertFalse(source.contains("preferences[KEY_BILLING_ADDRESS_JSON] = CardWalletDataCodec.encodeBillingAddress(address)"))
    }

    @Test
    fun importAndBitwardenSyncLogs_doNotExposeSensitiveRawValues() {
        val importManager = projectFile("app/src/main/java/takagi/ru/monica/util/DataExportImportManager.kt")
        val cipherSync = projectFile("app/src/main/java/takagi/ru/monica/bitwarden/service/CipherSyncProcessor.kt")

        assertFalse(importManager.contains("第一行: ${'$'}firstLine"))
        assertFalse(importManager.contains("内容: ${'$'}fields"))
        assertFalse(importManager.contains("读取第${'$'}{lineCount}行: ${'$'}currentLine"))
        assertFalse(importManager.contains("解析CSV行失败: ${'$'}line"))
        assertFalse(cipherSync.contains("SSH_FIELD_DUMP"))
        assertFalse(cipherSync.contains("SSH_RESOLVE"))
        assertFalse(cipherSync.contains("resolvedPrivateKey.take"))
    }

    @Test
    fun importAndMediaLogs_doNotExposeTitlesUrisOrLocalPaths() {
        val importViewModel = projectFile("app/src/main/java/takagi/ru/monica/viewmodel/DataExportImportViewModel.kt")
        val imageManager = projectFile("app/src/main/java/takagi/ru/monica/util/ImageManager.kt")
        val noteScreen = projectFile("app/src/main/java/takagi/ru/monica/ui/screens/AddEditNoteScreen.kt")
        val dualPhotoPicker = projectFile("app/src/main/java/takagi/ru/monica/ui/components/DualPhotoPicker.kt")
        val keepassViewModel = projectFile("app/src/main/java/takagi/ru/monica/ui/screens/KeePassKdbxViewModel.kt")
        val accessibilityService = projectFile("app/src/main/java/takagi/ru/monica/service/MonicaAccessibilityService.kt")
        val autofillPreferences = projectFile("app/src/main/java/takagi/ru/monica/autofill_ng/AutofillPreferences.kt")

        assertFalse(importViewModel.contains("成功插入到PasswordEntry表: ${'$'}{exportItem.title}"))
        assertFalse(importViewModel.contains("跳过重复条目: ${'$'}{aegisEntry.name}"))
        assertFalse(importViewModel.contains("成功插入Steam Guard: ${'$'}title"))
        assertFalse(imageManager.contains("uri=${'$'}uri"))
        assertFalse(imageManager.contains("path=${'$'}{file.absolutePath}"))
        assertFalse(noteScreen.contains("tempPath=${'$'}tempPath"))
        assertFalse(noteScreen.contains("uri=${'$'}tempUri"))
        assertFalse(dualPhotoPicker.contains("tempPath=${'$'}tempPath"))
        assertFalse(dualPhotoPicker.contains("uri=${'$'}tempUri"))
        assertFalse(keepassViewModel.contains("Starting local KDBX import from uri=${'$'}sourceUri"))
        assertFalse(keepassViewModel.contains("Failed to parse otpauth URI: ${'$'}uri"))
        assertFalse(accessibilityService.contains("url=${'$'}url"))
        assertFalse(autofillPreferences.contains("id=${'$'}normalized, passwordId=${'$'}passwordId"))
    }

    @Test
    fun persistentMdbxDiagnostics_redactSensitiveMetadata() {
        val logger = projectFile("app/src/main/java/takagi/ru/monica/mdbx/MdbxDiagLogger.kt")

        assertTrue(logger.contains("name|filePath|workingCopy|cacheCopy|treeUri|uri|externalUri|localCopy"))
        assertTrue(logger.contains("rows=<redacted>"))
        assertTrue(logger.contains("(content|file)://"))
        assertTrue(logger.contains("<path>"))
    }

    private fun projectFile(relativePath: String): String {
        val start = Paths.get("").toAbsolutePath()
        var cursor = start
        while (cursor.parent != null) {
            val candidate = cursor.resolve(relativePath).toFile()
            if (candidate.exists()) {
                return candidate.readText()
            }
            cursor = cursor.parent
        }
        error("Project file not found from $start: $relativePath")
    }
}
