package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Produkt spożywczy — wartości na 100 g produktu SUROWEGO (przed obróbką).
 * `source`: "seed" (baza wbudowana) | "off" (OpenFoodFacts) | "user" | "scan".
 * `preference`: smak użytkownika — 0=NEUTRAL, 1=PREFER (❤️ lubię, preferuj w planach + skrót logowania),
 *   2=AVOID (🚫 nie jem — walidator NIGDY nie zaplanuje). Jedno źródło prawdy o smaku (dawniej `favorite`).
 */
@Entity(tableName = "food_products", indices = [Index(value = ["nameNorm"])])
data class FoodProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameNorm: String,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val category: String,
    val source: String = "seed",
    val preference: Int = Pref.NEUTRAL,
    val barcode: String? = null,
    val imageUrl: String? = null
)

/** Poziomy smaku (jedna oś: nie jem ← obojętne → lubię). Prostota: 3 stany, nie 5. */
object Pref { const val NEUTRAL = 0; const val PREFER = 1; const val AVOID = 2 }

@Dao
interface FoodProductDao {
    @Query("SELECT COUNT(*) FROM food_products")
    suspend fun count(): Int

    @Query("SELECT * FROM food_products WHERE nameNorm LIKE '%' || :q || '%' ORDER BY length(nameNorm) LIMIT :limit")
    suspend fun search(q: String, limit: Int = 12): List<FoodProductEntity>

    @Query("SELECT * FROM food_products")
    suspend fun all(): List<FoodProductEntity>

    /** Reaktywna lista wszystkich produktów (lubię ❤️ na górze, nie jem 🚫 na dole, potem kategoria/nazwa). */
    @Query("SELECT * FROM food_products ORDER BY CASE preference WHEN 1 THEN 0 WHEN 0 THEN 1 ELSE 2 END, category, name")
    fun observeAll(): Flow<List<FoodProductEntity>>

    /** Lubiane (❤️) — do wstrzyknięcia w DietitianContext (AI preferuje je w planach). */
    @Query("SELECT * FROM food_products WHERE preference = 1 ORDER BY name")
    suspend fun preferred(): List<FoodProductEntity>

    /** Nielubiane (🚫) — twardy guardrail: walidator NIGDY nie zaplanuje tych produktów. */
    @Query("SELECT * FROM food_products WHERE preference = 2 ORDER BY name")
    suspend fun avoided(): List<FoodProductEntity>

    @Query("UPDATE food_products SET preference = :pref WHERE id = :id")
    suspend fun setPreference(id: Long, pref: Int)

    /** Ustaw smak po nazwie (znormalizowanej) — dla toola AI `set_food_preference`. */
    @Query("UPDATE food_products SET preference = :pref WHERE nameNorm = :nameNorm")
    suspend fun setPreferenceByNorm(nameNorm: String, pref: Int): Int

    @Insert
    suspend fun insert(item: FoodProductEntity): Long

    @androidx.room.Update
    suspend fun update(item: FoodProductEntity)

    /** Produkt po kodzie kreskowym — do deduplikacji przy skanowaniu. */
    @Query("SELECT * FROM food_products WHERE barcode = :barcode LIMIT 1")
    suspend fun byBarcode(barcode: String): FoodProductEntity?

    @Query("DELETE FROM food_products WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert
    suspend fun insertAll(items: List<FoodProductEntity>)
}
