package pl.filebit.dietetyk.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class UpdateInfo(val version: String, val apkUrl: String, val notes: String)

/** Sprawdza najnowsze wydanie na GitHub (repo publiczne — bez tokena). */
object UpdateChecker {
    private const val API = "https://api.github.com/repos/coyotxxx/dietetyk-ai/releases/latest"
    private val http = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun latest(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(API)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DietetykAI")
            .build()
        val body = runCatching {
            http.newCall(req).execute().use { if (!it.isSuccessful) null else it.body?.string() }
        }.getOrNull() ?: return@withContext null

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
        val tag = root["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
        val latestVer = tag.removePrefix("v")
        if (!isNewer(latestVer, currentVersion)) return@withContext null

        val apk = root["assets"]?.jsonArray
            ?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            ?.get("browser_download_url")?.jsonPrimitive?.content
            ?: return@withContext null
        val notes = root["body"]?.jsonPrimitive?.content ?: ""
        UpdateInfo(latestVer, apk, notes)
    }

    /** Porównanie wersji semver-owo (np. 0.1.13 > 0.1.12). */
    fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".", "-").mapNotNull { it.toIntOrNull() }
        val c = current.split(".", "-").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0}; val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
