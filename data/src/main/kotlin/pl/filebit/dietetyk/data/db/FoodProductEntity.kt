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
 * `favorite`: ulubiony — sygnał dla AI (preferuj w planach) + skrót do logowania.
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
    val favorite: Boolean = false,
    val barcode: String? = null,
    val imageUrl: String? = null
)

@Dao
interface FoodProductDao {
    @Query("SELECT COUNT(*) FROM food_products")
    suspend fun count(): Int

    @Query("SELECT * FROM food_products WHERE nameNorm LIKE '%' || :q || '%' ORDER BY length(nameNorm) LIMIT :limit")
    suspend fun search(q: String, limit: Int = 12): List<FoodProductEntity>

    @Query("SELECT * FROM food_products")
    suspend fun all(): List<FoodProductEntity>

    /** Reaktywna lista wszystkich produktów (ulubione na górze, potem kategoria/nazwa). */
    @Query("SELECT * FROM food_products ORDER BY favorite DESC, category, name")
    fun observeAll(): Flow<List<FoodProductEntity>>

    /** Ulubione — do wstrzyknięcia w DietitianContext (AI preferuje je w planach). */
    @Query("SELECT * FROM food_products WHERE favorite = 1 ORDER BY name")
    suspend fun favorites(): List<FoodProductEntity>

    @Query("UPDATE food_products SET favorite = :fav WHERE id = :id")
    suspend fun setFavorite(id: Long, fav: Boolean)

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
