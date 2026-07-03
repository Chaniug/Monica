package takagi.ru.monica.steam.network

import java.math.BigInteger
import takagi.ru.monica.steam.core.SteamLoginApprovalSigner
import takagi.ru.monica.steam.data.SteamAccount

data class SteamAuthorizedDevice(
    val tokenId: String,
    val description: String,
    val platformType: Int,
    val loggedIn: Boolean,
    val firstSeen: SteamAuthorizedDeviceUsage?,
    val lastSeen: SteamAuthorizedDeviceUsage?
)

data class SteamAuthorizedDeviceUsage(
    val timeSeconds: Long,
    val country: String,
    val state: String,
    val city: String
) {
    val location: String
        get() = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
}

class SteamAuthorizedDeviceService(
    private val api: SteamApiClient = SteamApiClient()
) {
    fun fetch(account: SteamAccount): List<SteamAuthorizedDevice> {
        val token = account.accessToken ?: return emptyList()
        val request = SteamProtoWriter().apply {
            writeBool(1, false)
        }
        val fields = SteamProtoReader(
            api.callProtobuf(
                iface = "IAuthenticationService",
                method = "EnumerateTokens",
                request = request,
                accessToken = token
            )
        ).parseAll()

        return fields
            .filter { it.number == 1 && it.bytes != null }
            .mapNotNull { field ->
                field.bytes?.let(::parseDevice)
            }
    }

    fun revoke(account: SteamAccount, device: SteamAuthorizedDevice): Boolean {
        val token = account.accessToken ?: return false
        val tokenId = parseUnsigned64AsSignedLong(device.tokenId) ?: return false
        val steamId = account.steamId.toLongOrNull() ?: return false
        val request = SteamProtoWriter().apply {
            writeFixed64(1, tokenId)
            writeFixed64(2, steamId)
            writeVarint(3, 1L)
            writeBytes(
                4,
                SteamLoginApprovalSigner.tokenSignature(
                    sharedSecretBase64 = account.sharedSecret,
                    tokenId = tokenId
                )
            )
        }
        api.callProtobuf(
            iface = "IAuthenticationService",
            method = "RevokeRefreshToken",
            request = request,
            accessToken = token
        )
        return true
    }

    private fun parseDevice(bytes: ByteArray): SteamAuthorizedDevice? {
        val fields = SteamProtoReader(bytes).parse()
        val tokenId = fields[1]?.asFixed64UnsignedString.orEmpty()
        val description = fields[2]?.asString.orEmpty()
        if (tokenId.isBlank() && description.isBlank()) return null
        return SteamAuthorizedDevice(
            tokenId = tokenId,
            description = description,
            platformType = fields[4]?.asInt ?: 0,
            loggedIn = fields[5]?.asBool ?: false,
            firstSeen = fields[9]?.bytes?.let(::parseUsage),
            lastSeen = fields[10]?.bytes?.let(::parseUsage)
        )
    }

    private fun parseUsage(bytes: ByteArray): SteamAuthorizedDeviceUsage {
        val fields = SteamProtoReader(bytes).parse()
        return SteamAuthorizedDeviceUsage(
            timeSeconds = fields[1]?.asLong ?: 0L,
            country = fields[4]?.asString.orEmpty(),
            state = fields[5]?.asString.orEmpty(),
            city = fields[6]?.asString.orEmpty()
        )
    }

    private fun parseUnsigned64AsSignedLong(value: String): Long? {
        val big = runCatching { BigInteger(value.trim()) }.getOrNull() ?: return null
        if (big < BigInteger.ZERO || big > UNSIGNED_LONG_MAX) return null
        return if (big > SIGNED_LONG_MAX) {
            big.subtract(UNSIGNED_LONG_BASE).longValueExact()
        } else {
            big.longValueExact()
        }
    }

    companion object {
        private val UNSIGNED_LONG_MAX = BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE)
        private val SIGNED_LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE)
        private val UNSIGNED_LONG_BASE = BigInteger.ONE.shiftLeft(64)
    }
}
