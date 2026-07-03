package takagi.ru.monica.steam.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class SteamMaFilePayload(
    val steamId: String,
    val accountName: String,
    val displayName: String,
    val deviceId: String,
    val sharedSecret: String,
    val identitySecret: String?,
    val revocationCode: String?,
    val tokenGid: String?,
    val accessToken: String?,
    val refreshToken: String?,
    val steamLoginSecure: String?,
    val rawJson: String
)

data class SteamMaFileManifestEntry(
    val filename: String,
    val steamId: String?,
    val salt: String?,
    val iv: String?
)

class SteamMaFileParser(
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun parseSteamGuardJson(
        steamId: String,
        deviceId: String,
        steamGuardJson: String,
        accessToken: String?,
        refreshToken: String?,
        displayNameOverride: String? = null
    ): SteamMaFilePayload {
        val root = json.parseToJsonElement(steamGuardJson).jsonObject
        val accountName = root.stringAny("account_name", "accountName", "AccountName") ?: steamId
        val sharedSecret = root.stringAny("shared_secret", "sharedSecret")
            ?: error("Steam Guard payload missing shared_secret")
        return SteamMaFilePayload(
            steamId = steamId,
            accountName = accountName,
            displayName = displayNameOverride?.trim()?.takeIf { it.isNotBlank() } ?: accountName,
            deviceId = deviceId.ifBlank { root.stringAny("device_id", "deviceId").orEmpty() },
            sharedSecret = sharedSecret,
            identitySecret = root.stringAny("identity_secret", "identitySecret"),
            revocationCode = root.stringAny("revocation_code", "revocationCode"),
            tokenGid = root.stringAny("token_gid", "tokenGid"),
            accessToken = accessToken,
            refreshToken = refreshToken,
            steamLoginSecure = accessToken?.let { "$steamId||$it" },
            rawJson = steamGuardJson
        )
    }

    fun parse(
        maFileContent: String,
        fileName: String? = null,
        manifestContent: String? = null,
        password: String? = null,
        displayNameOverride: String? = null
    ): SteamMaFilePayload {
        val trimmed = maFileContent.trim()
        val plainJson = if (trimmed.startsWith("{")) {
            trimmed
        } else {
            decryptEncryptedMaFile(trimmed, fileName, manifestContent, password)
        }

        val root = json.parseToJsonElement(plainJson).jsonObject
        val session = root.objectAny("Session", "session")
        val steamId = root.stringAny("steamid", "steam_id", "SteamID")
            ?: session?.stringAny("SteamID", "steamid", "steam_id")
            ?: error("maFile missing steamid")
        val accountName = root.stringAny("account_name", "accountName", "AccountName")
            ?: session?.stringAny("AccountName", "account_name")
            ?: steamId
        val sharedSecret = root.stringAny("shared_secret", "sharedSecret")
            ?: error("maFile missing shared_secret")
        val deviceId = root.stringAny("device_id", "deviceId")
            ?: session?.stringAny("DeviceID", "device_id", "deviceId")
            ?: ""
        val steamLoginSecure = session?.stringAny("SteamLoginSecure", "steamLoginSecure")
            ?: root.stringAny("steamLoginSecure", "steam_login_secure")
        val accessToken = root.stringAny("access_token", "accessToken", "oauth_token", "OAuthToken")
            ?: session?.stringAny("AccessToken", "access_token", "OAuthToken", "oauth_token")
            ?: steamLoginSecure?.substringAfter("||", missingDelimiterValue = "")?.takeIf { it.isNotBlank() }

        return SteamMaFilePayload(
            steamId = steamId,
            accountName = accountName,
            displayName = displayNameOverride?.trim()?.takeIf { it.isNotBlank() } ?: accountName,
            deviceId = deviceId,
            sharedSecret = sharedSecret,
            identitySecret = root.stringAny("identity_secret", "identitySecret"),
            revocationCode = root.stringAny("revocation_code", "revocationCode"),
            tokenGid = root.stringAny("token_gid", "tokenGid"),
            accessToken = accessToken,
            refreshToken = root.stringAny("refresh_token", "refreshToken")
                ?: session?.stringAny("RefreshToken", "refresh_token"),
            steamLoginSecure = steamLoginSecure,
            rawJson = plainJson
        )
    }

    fun parseManifest(manifestContent: String): List<SteamMaFileManifestEntry> {
        val root = json.parseToJsonElement(manifestContent).jsonObject
        val entries = root["entries"] as? JsonArray ?: return emptyList()
        return entries.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val filename = obj.stringAny("filename", "Filename") ?: return@mapNotNull null
            SteamMaFileManifestEntry(
                filename = filename,
                steamId = obj.stringAny("steamid", "SteamID", "steam_id"),
                salt = obj.stringAny("encryption_salt", "Salt", "salt"),
                iv = obj.stringAny("encryption_iv", "IV", "iv")
            )
        }
    }

    private fun decryptEncryptedMaFile(
        encryptedContent: String,
        fileName: String?,
        manifestContent: String?,
        password: String?
    ): String {
        val passKey = password?.takeIf { it.isNotEmpty() }
            ?: error("Encrypted maFile requires password")
        val manifest = manifestContent?.takeIf { it.isNotBlank() }
            ?: error("Encrypted maFile requires manifest.json")
        val entries = parseManifest(manifest)
        val normalizedFileName = fileName?.substringAfterLast('/')?.substringAfterLast('\\')
        val entry = entries.firstOrNull { it.filename == normalizedFileName }
            ?: entries.singleOrNull()
            ?: error("Cannot find matching maFile entry in manifest.json")
        val salt = entry.salt ?: error("Manifest entry missing encryption_salt")
        val iv = entry.iv ?: error("Manifest entry missing encryption_iv")
        return SteamMaFileCrypto.decrypt(passKey, salt, iv, encryptedContent)
            ?: error("Cannot decrypt maFile. Check password and manifest.json")
    }

    private fun JsonObject.objectAny(vararg keys: String): JsonObject? {
        keys.forEach { key ->
            (this[key] as? JsonObject)?.let { return it }
        }
        return null
    }

    private fun JsonObject.stringAny(vararg keys: String): String? {
        keys.forEach { key ->
            val value = this[key].stringOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun JsonElement?.stringOrNull(): String? {
        return (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }
}
