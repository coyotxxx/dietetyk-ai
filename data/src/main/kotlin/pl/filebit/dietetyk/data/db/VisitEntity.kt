package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Zapis wizyty kontrolnej — historia decyzji dietetyka (delta wagi, adherencja, werdykt). */
@Entity(tableName = "visit_reports")
data class VisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val deltaKg: Double?,
    val adherencePct: Int?,
    val decisionText: String
)

@Dao
interface VisitDao {
    @Query("SELECT * FROM visit_reports ORDER BY dateMs DESC")
    fun observe(): Flow<List<VisitEntity>>

    @Query("SELECT * FROM visit_reports ORDER BY dateMs DESC")
    suspend fun all(): List<VisitEntity>

    @Query("SELECT MAX(dateMs) FROM visit_reports")
    suspend fun latestDateMs(): Long?

    @Insert
    suspend fun insert(v: VisitEntity): Long
}
