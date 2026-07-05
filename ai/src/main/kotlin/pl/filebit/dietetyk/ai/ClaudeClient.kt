package pl.filebit.dietetyk.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Nisko-poziomowy klient: wysyła gotowy JSON żądania, zwraca surowe ciało odpowiedzi. */
interface ClaudeApi {
    /** @throws ClaudeApiException gdy odpowiedź nie jest 2xx. */
    suspend fun send(requestBody: String): String
}

class ClaudeApiException(val code: Int, message: String) : RuntimeException(message)

/**
 * Klient HTTP Claude (OkHttp). BYOK — klucz API wstrzykiwany (BuildConfig/ustawienia).
 * Nagłówki wg referencji: `x-api-key`, `anthropic-version: 2023-06-01`. Timeouty pod dłuższe
 * odpowiedzi (wizyty/plany). Streaming można dodać później; na start blokująco na Dispatchers.IO.
 */
class ClaudeHttpApi(private val apiKey: String) : ClaudeApi {

    private val client: OkHttpClient = defaultClient()

    override suspend fun send(requestBody: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(ClaudeConfig.ENDPOINT)
            .header("x-api-key", apiKey)
            .header("anthropic-version", ClaudeConfig.ANTHROPIC_VERSION)
            .header("content-type", "application/json")
            .post(requestBody.toRequestBody(JSON_MEDIA))
            .build()
        client.newCall(request).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw ClaudeApiException(resp.code, "Claude API ${resp.code}: $body")
            body
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(240, TimeUnit.SECONDS)   // wizyty/plany bywają długie
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
