package takagi.ru.monica.steam

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import takagi.ru.monica.steam.importer.SteamMaFileCrypto
import takagi.ru.monica.steam.importer.SteamMaFileParser

class SteamMaFileParserTest {
    private val plainMaFile = """
        {
          "shared_secret": "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=",
          "identity_secret": "YWJjZGVmZ2hpamtsbW5vcHFyc3Q=",
          "account_name": "tester",
          "steamid": "76561198000000000",
          "device_id": "android:device",
          "revocation_code": "R12345",
          "token_gid": "gid",
          "Session": {
            "OAuthToken": "access-token",
            "RefreshToken": "refresh-token",
            "SteamLoginSecure": "76561198000000000||access-token"
          }
        }
    """.trimIndent()

    @Test
    fun parsesPlainMaFileToSteamPayload() {
        val payload = SteamMaFileParser().parse(plainMaFile)

        assertEquals("76561198000000000", payload.steamId)
        assertEquals("tester", payload.accountName)
        assertEquals("android:device", payload.deviceId)
        assertEquals("access-token", payload.accessToken)
        assertEquals("refresh-token", payload.refreshToken)
        assertEquals("YWJjZGVmZ2hpamtsbW5vcHFyc3Q=", payload.identitySecret)
    }

    @Test
    fun decryptsSdaEncryptedMaFileWithManifestEntry() {
        val salt = SteamMaFileCrypto.randomSaltBase64()
        val iv = SteamMaFileCrypto.randomIvBase64()
        val cipher = SteamMaFileCrypto.encryptForTests("pass-key", salt, iv, plainMaFile)
        val manifest = """
            {
              "encrypted": true,
              "entries": [
                {
                  "filename": "76561198000000000.maFile",
                  "steamid": "76561198000000000",
                  "encryption_salt": "$salt",
                  "encryption_iv": "$iv"
                }
              ]
            }
        """.trimIndent()

        val payload = SteamMaFileParser().parse(
            maFileContent = cipher,
            fileName = "76561198000000000.maFile",
            manifestContent = manifest,
            password = "pass-key"
        )

        assertEquals("tester", payload.accountName)
        assertEquals("MTIzNDU2Nzg5MDEyMzQ1Njc4OTA=", payload.sharedSecret)
    }

    @Test
    fun sdaCryptoMatchesPbkdf2ReferenceVectors() {
        val salt = Base64.getEncoder().encodeToString("salt".toByteArray(Charsets.UTF_8))

        assertEquals(
            "4b007901b765489abead49d926f721d065a429c1",
            SteamMaFileCrypto.deriveKey("password", salt, iterations = 4096, keySizeBits = 160).toHex()
        )
        assertEquals(
            "0c60c80f961f0e71f3a9b524af6012062fe037a6",
            SteamMaFileCrypto.deriveKey("password", salt, iterations = 1, keySizeBits = 160).toHex()
        )
    }

    @Test
    fun wrongEncryptedMaFilePasswordReturnsNullAtCryptoLayer() {
        val salt = SteamMaFileCrypto.randomSaltBase64()
        val iv = SteamMaFileCrypto.randomIvBase64()
        val cipher = SteamMaFileCrypto.encryptForTests("right", salt, iv, plainMaFile)

        assertNull(SteamMaFileCrypto.decrypt("wrong", salt, iv, cipher))
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
