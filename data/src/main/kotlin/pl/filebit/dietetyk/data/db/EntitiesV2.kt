package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** Dzienne spożycie energii (dla adaptacyjnego TDEE + red-flag). Mapuje się na `DailyEnergyLog`. */
@Entity(tableName = "energy_logs")
data class EnergyLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val kcalConsumed: Int,
    val isComplete: Boolean,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val updatedAt: Long = 0,
    val dirty: Boolean = false
)

/** Pamięć epizodyczna AI — „kartoteka pacjenta" (ustalenia z wizyt, przyzwyczajenia). */
@Entity(tableName = "ai_memory")
data class AiMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long = 0,
    val dirty: Boolean = false
)

@Dao
interface EnergyLogDao {
    @Query("SELECT * FROM energy_logs WHERE dateMs >= :sinceMs ORDER BY dateMs ASC")
    suspend fun since(sinceMs: Long): List<EnergyLogEntity>

    @Query("SELECT * FROM energy_logs ORDER BY dateMs DESC LIMIT 1")
    suspend fun latest(): EnergyLogEntity?

    @Insert
    suspend fun insert(log: EnergyLogEntity): Long
}

@Dao
interface AiMemoryDao {
    @Query("SELECT note FROM ai_memory ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentNotes(limit: Int = 20): List<String>

    @Insert
    suspend fun insert(memory: AiMemoryEntity): Long
}
