package takagi.ru.monica.steam.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import takagi.ru.monica.steam.data.SteamAccount

object SteamMaFileBackupCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(account: SteamAccount): String {
        val root = runCatching {
            json.parseToJsonElement(account.rawSteamGuardJson).jsonObject.toMutableMap()
        }.getOrElse {
            mutableMapOf()
        }

        fun putString(key: String, value: String?) {
            val normalized = value?.trim().orEmpty()
            if (normalized.isNotBlank()) {
                root[key] = JsonPrimitive(normalized)
            }
        }

        putString("steamid", account.steamId)
        putString("account_name", account.accountName.ifBlank { account.displayName })
        putString("device_id", account.deviceId)
        putString("shared_secret", account.sharedSecret)
        putString("identity_secret", account.identitySecret)
        putString("revocation_code", account.revocationCode)
        putString("token_gid", account.tokenGid)
        putString("access_token", account.accessToken)
        putString("refresh_token", account.refreshToken)
        putString("steamLoginSecure", account.steamLoginSecure)

        val session = (root["Session"] as? JsonObject)
            ?.toMutableMap()
            ?: (root["session"] as? JsonObject)
                ?.toMutableMap()
            ?: mutableMapOf()

        fun putSessionString(key: String, value: String?) {
            val normalized = value?.trim().orEmpty()
            if (normalized.isNotBlank()) {
                session[key] = JsonPrimitive(normalized)
            }
        }

        putSessionString("SteamID", account.steamId)
        putSessionString("AccountName", account.accountName.ifBlank { account.displayName })
        putSessionString("DeviceID", account.deviceId)
        putSessionString("AccessToken", account.accessToken)
        putSessionString("OAuthToken", account.accessToken)
        putSessionString("RefreshToken", account.refreshToken)
        val steamLoginSecure = account.steamLoginSecure
            ?.takeIf { it.isNotBlank() }
            ?: account.accessToken?.let { "${account.steamId}||$it" }
        putSessionString(
            "SteamLoginSecure",
            steamLoginSecure
        )
        if (session.isNotEmpty()) {
            root["Session"] = JsonObject(session)
        }

        return JsonObject(root).toString()
    }

    fun fileName(account: SteamAccount): String {
        val rawName = account.steamId
            .ifBlank { account.accountName }
            .ifBlank { account.displayName }
            .ifBlank { "steam_${account.id}" }
        val safeName = rawName.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
            .ifBlank { "steam_${account.id}" }
        return "$safeName.maFile"
    }
}
