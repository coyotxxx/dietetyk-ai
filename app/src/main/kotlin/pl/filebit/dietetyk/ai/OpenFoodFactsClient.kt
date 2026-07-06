package pl.filebit.dietetyk.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class OffProduct(
    val name: String,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val imageUrl: String? = null
)

/** Pobiera produkt z OpenFoodFacts (po kodzie kreskowym lub nazwie). Wartości na 100 g. */
class OpenFoodFactsClient {
    private val http = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(query: String?, barcode: String?): OffProduct? = withContext(Dispatchers.IO) {
        val url = if (!barcode.isNullOrBlank()) {
            "https://world.openfoodfacts.org/api/v2/product/${barcode.trim()}.json?fields=product_name,nutriments,image_front_thumb_url,image_front_small_url"
        } else if (!query.isNullOrBlank()) {
            val q = URLEncoder.encode(query.trim(), "UTF-8")
            "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$q&search_simple=1&action=process&json=1&page_size=5&fields=product_name,nutriments,image_front_thumb_url,image_front_small_url"
        } else return@withContext null

        val req = Request.Builder().url(url)
            .header("User-Agent", "DietetykAI/0.1 (kontakt.mixpremium@gmail.com)")
            .build()

        val body = runCatching {
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.string()
            }
        }.getOrNull() ?: return@withContext null

        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@withContext null
        val products: List<JsonObject> = if (!barcode.isNullOrBlank()) {
            listOfNotNull(root["product"]?.let { it as? JsonObject })
        } else {
            root["products"]?.jsonArray?.mapNotNull { it as? JsonObject } ?: emptyList()
        }

        for (p in products) {
            val n = p["nutriments"]?.let { it as? JsonObject } ?: continue
            val kcal = n["energy-kcal_100g"].num() ?: continue
            if (kcal <= 0) continue
            val name = p["product_name"].str()?.takeIf { it.isNotBlank() } ?: query ?: "Produkt"
            return@withContext OffProduct(
                name = name,
                kcal = kcal.toInt(),
                proteinG = n["proteins_100g"].num() ?: 0.0,
                carbsG = n["carbohydrates_100g"].num() ?: 0.0,
                fatG = n["fat_100g"].num() ?: 0.0,
                imageUrl = (p["image_front_small_url"].str() ?: p["image_front_thumb_url"].str())?.takeIf { it.isNotBlank() }
            )
        }
        null
    }

    private fun JsonElement?.num(): Double? =
        this?.let { runCatching { it.jsonPrimitive.content.toDouble() }.getOrNull() }

    private fun JsonElement?.str(): String? =
        this?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
}
