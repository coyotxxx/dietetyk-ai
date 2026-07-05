package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Aktualny plan diety (pojedynczy wiersz id=0). Plan trzymany jako JSON (`planJson`) — elastyczne,
 * bez sztywnego schematu posiłków na tym etapie. Docelowo znormalizowane encje posiłków/slotów.
 */
@Entity(tableName = "diet_plan")
data class PlanEntity(
    @PrimaryKey val id: Int = 0,
    val planJson: String,
    val targetKcal: Int,
    val updatedAt: Long = 0,
    val dirty: Boolean = false
)

@Dao
interface PlanDao {
    @Query("SELECT * FROM diet_plan WHERE id = 0")
    fun observe(): Flow<PlanEntity?>

    @Query("SELECT * FROM diet_plan WHERE id = 0")
    suspend fun get(): PlanEntity?

    @Upsert
    suspend fun upsert(plan: PlanEntity)
}
