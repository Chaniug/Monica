package takagi.ru.monica.steam.network

import android.net.Uri
import takagi.ru.monica.steam.core.SteamLoginApprovalSigner
import takagi.ru.monica.steam.data.SteamAccount

data class SteamQrChallenge(
    val version: Int,
    val clientId: Long
) {
    companion object {
        fun parse(raw: String): SteamQrChallenge? {
            val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
            val segments = uri.pathSegments ?: return null
            val index = segments.indexOf("q")
            if (index < 0 || segments.size < index + 3) return null
            val version = segments[index + 1].toIntOrNull() ?: return null
            val clientId = segments[index + 2].toLongOrNull() ?: return null
            return SteamQrChallenge(version = version, clientId = clientId)
        }
    }
}

data class SteamPendingLogin(
    val clientId: Long,
    val version: Int,
    val ip: String,
    val city: String,
    val country: String,
    val deviceName: String,
    val detectedAtMillis: Long = System.currentTimeMillis()
) {
    val location: String
        get() = listOf(city, country).filter { it.isNotBlank() }.joinToString(", ")
}

class SteamLoginApprovalService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun pendingLogins(account: SteamAccount): List<SteamPendingLogin> {
        val token = account.accessToken ?: return emptyList()
        val ids = pendingLoginClientIds(token)
        return ids.mapNotNull { clientId ->
            runCatching { sessionInfo(account, clientId) }.getOrNull()
        }
    }

    fun sessionInfo(account: SteamAccount, clientId: Long): SteamPendingLogin? {
        val token = account.accessToken ?: return null
        val request = SteamProtoWriter().apply {
            writeVarint(1, clientId)
        }
        val fields = SteamProtoReader(
            api.callProtobuf(
                iface = "IAuthenticationService",
                method = "GetAuthSessionInfo",
                request = request,
                accessToken = token
            )
        ).parse()
        return SteamPendingLogin(
            clientId = clientId,
            version = fields[8]?.asInt ?: 0,
            ip = fields[1]?.asString.orEmpty(),
            city = fields[3]?.asString.orEmpty(),
            country = fields[5]?.asString.orEmpty(),
            deviceName = fields[7]?.asString.orEmpty()
        )
    }

    fun respondToQr(account: SteamAccount, challenge: SteamQrChallenge, approve: Boolean): Boolean {
        return respondToSession(
            account = account,
            clientId = challenge.clientId,
            version = challenge.version,
            approve = approve
        )
    }

    fun respondToSession(
        account: SteamAccount,
        clientId: Long,
        version: Int,
        approve: Boolean
    ): Boolean {
        val token = requireNotNull(account.accessToken) { "access token required" }
        val signature = SteamLoginApprovalSigner.signature(
            sharedSecretBase64 = account.sharedSecret,
            version = version,
            clientId = clientId,
            steamId = account.steamId.toLong()
        )
        val request = SteamProtoWriter().apply {
            writeVarint(1, version.toLong())
            writeVarint(2, clientId)
            writeFixed64(3, account.steamId.toLong())
            writeBytes(4, signature)
            writeBool(5, approve)
            writeVarint(6, 1L)
        }
        val responseFields = SteamProtoReader(
            api.callProtobuf(
                iface = "IAuthenticationService",
                method = "UpdateAuthSessionWithMobileConfirmation",
                request = request,
                accessToken = token
            )
        ).parse()
        return responseFields.isEmpty() || (responseFields[1]?.asBool ?: true)
    }

    private fun pendingLoginClientIds(accessToken: String): List<Long> {
        val bytes = api.callProtobuf(
            iface = "IAuthenticationService",
            method = "GetAuthSessionsForAccount",
            request = SteamProtoWriter(),
            accessToken = accessToken,
            useGet = true
        )
        return SteamProtoReader(bytes).parseAll().flatMap { field ->
            if (field.number != 1) {
                emptyList()
            } else if (field.varint != null) {
                listOf(field.varint)
            } else if (field.bytes != null) {
                SteamProtoReader.decodePackedVarints(field.bytes)
            } else {
                emptyList()
            }
        }
    }
}
