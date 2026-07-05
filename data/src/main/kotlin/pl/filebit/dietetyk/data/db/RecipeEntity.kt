package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/** Cache wygenerowanych przepisów (3 warianty jako JSON), klucz = znormalizowana nazwa dania. */
@Entity(tableName = "recipe_cache")
data class RecipeEntity(
    @PrimaryKey val mealKey: String,
    val json: String,
    val updatedAt: Long = 0
)

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipe_cache WHERE mealKey = :key")
    suspend fun get(key: String): RecipeEntity?

    @Upsert
    suspend fun upsert(recipe: RecipeEntity)
}
