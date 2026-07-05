package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Produkt spożywczy — wartości na 100 g produktu SUROWEGO (przed obróbką).
 * `source`: "seed" (baza wbudowana) | "off" (OpenFoodFacts) | "user".
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
    val source: String = "seed"
)

@Dao
interface FoodProductDao {
    @Query("SELECT COUNT(*) FROM food_products")
    suspend fun count(): Int

    @Query("SELECT * FROM food_products WHERE nameNorm LIKE '%' || :q || '%' ORDER BY length(nameNorm) LIMIT :limit")
    suspend fun search(q: String, limit: Int = 12): List<FoodProductEntity>

    @Query("SELECT * FROM food_products")
    suspend fun all(): List<FoodProductEntity>

    @Insert
    suspend fun insertAll(items: List<FoodProductEntity>)
}
