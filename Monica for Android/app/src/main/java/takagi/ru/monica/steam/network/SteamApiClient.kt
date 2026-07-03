package takagi.ru.monica.steam.network

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class SteamApiClient(
    private val client: OkHttpClient = OkHttpClient.Builder().build(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    fun callProtobuf(
        iface: String,
        method: String,
        request: SteamProtoWriter,
        accessToken: String? = null,
        useGet: Boolean = false,
        version: Int = 1
    ): ByteArray {
        val baseUrl = "https://api.steampowered.com/$iface/$method/v$version/"
        val encoded = Base64.getEncoder().encodeToString(request.toByteArray())
        val httpRequest = if (useGet) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("input_protobuf_encoded", encoded)
                .apply {
                    if (!accessToken.isNullOrBlank()) addQueryParameter("access_token", accessToken)
                }
                .build()
            Request.Builder().url(url).get()
        } else {
            val body = FormBody.Builder()
                .add("input_protobuf_encoded", encoded)
                .build()
            val url = baseUrl.toHttpUrl().newBuilder()
                .apply {
                    if (!accessToken.isNullOrBlank()) addQueryParameter("access_token", accessToken)
                }
                .build()
            Request.Builder().url(url).post(body)
        }.header("User-Agent", "okhttp/4.9.2")
            .header("Accept", "application/json, text/plain, */*")
            .build()

        client.newCall(httpRequest).execute().use { response ->
            val eResult = response.header("x-eresult")?.toIntOrNull() ?: 1
            if (!response.isSuccessful || eResult != 1) {
                val message = response.header("x-error_message")
                    ?: "Steam API failed: $iface/$method (${response.code}, eresult=$eResult)"
                throw SteamApiException(message, eResult)
            }
            return response.body?.bytes() ?: ByteArray(0)
        }
    }

    fun communityGetJson(
        path: String,
        query: Map<String, String>,
        cookies: Map<String, String> = emptyMap()
    ): JsonObject {
        val url = "https://steamcommunity.com$path".toHttpUrl().newBuilder()
            .apply { query.forEach { (key, value) -> addQueryParameter(key, value) } }
            .build()
        val request = Request.Builder()
            .url(url)
            .get()
            .headers(defaultCommunityHeaders(cookies))
            .build()
        return executeJson(request)
    }

    fun communityPostJson(
        path: String,
        form: Map<String, List<String>>,
        cookies: Map<String, String> = emptyMap()
    ): JsonObject {
        val body = FormBody.Builder().apply {
            form.forEach { (key, values) ->
                values.forEach { value -> add(key, value) }
            }
        }.build()
        val request = Request.Builder()
            .url("https://steamcommunity.com$path")
            .post(body)
            .headers(defaultCommunityHeaders(cookies))
            .build()
        return executeJson(request)
    }

    private fun executeJson(request: Request): JsonObject {
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SteamApiException("Steam community request failed: ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            if (body.isBlank() || !body.trimStart().startsWith("{")) {
                return JsonObject(emptyMap())
            }
            return json.parseToJsonElement(body).jsonObject
        }
    }

    private fun defaultCommunityHeaders(cookies: Map<String, String>): okhttp3.Headers {
        return okhttp3.Headers.Builder()
            .add("User-Agent", "okhttp/4.9.2")
            .add("Accept", "application/json, text/plain, */*")
            .add("X-Requested-With", "com.valvesoftware.android.steam.community")
            .apply {
                if (cookies.isNotEmpty()) {
                    add("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                }
            }
            .build()
    }
}

class SteamApiException(
    message: String,
    val eResult: Int? = null
) : Exception(message)
