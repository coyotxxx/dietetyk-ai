package pl.filebit.dietetyk.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/** Dzienne spożycie energii (dla adaptacyjnego TDEE + red-flag). Mapuje się na `DailyEnergyLog`.
 *  v18: model slotów — `source` PLANNED/AD_HOC + `slot` (który zaplanowany posiłek) + `deletedAt`
 *  (soft-delete). Dzięki temu log_planned_day jest IDEMPOTENTNY (replace, nie append) i można
 *  bezpiecznie sprzątać błędne wpisy bez utraty danych (deletedAt>0 = ukryty, odwracalny). */
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
    val dirty: Boolean = false,
    /** PLANNED = wpis z planu (log_planned_day, replace'owalny per slot); AD_HOC = coś spoza planu. */
    val source: String = "AD_HOC",
    /** Identyfikator zaplanowanego posiłku (nazwa) — do idempotentnego replace. null dla AD_HOC. */
    val slot: String? = null,
    /** Soft-delete: 0 = aktywny, >0 = usunięty (znacznik czasu). Nigdy nie kasujemy twardo. */
    val deletedAt: Long = 0
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
    // Wszystkie odczyty pomijają wpisy usunięte (deletedAt = 0).
    @Query("SELECT * FROM energy_logs WHERE dateMs >= :sinceMs AND deletedAt = 0 ORDER BY dateMs ASC")
    suspend fun since(sinceMs: Long): List<EnergyLogEntity>

    @Query("SELECT * FROM energy_logs WHERE deletedAt = 0 ORDER BY dateMs DESC LIMIT 1")
    suspend fun latest(): EnergyLogEntity?

    /** Aktywne wpisy z zakresu dnia [startMs, endMs). */
    @Query("SELECT * FROM energy_logs WHERE dateMs >= :startMs AND dateMs < :endMs AND deletedAt = 0 ORDER BY dateMs ASC")
    suspend fun activeInDay(startMs: Long, endMs: Long): List<EnergyLogEntity>

    @Insert
    suspend fun insert(log: EnergyLogEntity): Long

    /** IDEMPOTENCJA: przed zalogowaniem dnia z planu — soft-delete istniejących wpisów PLANNED tego dnia
     *  (opcjonalnie tylko dla danego slotu). Ponowne „zjadłam wszystko" = replace, nie duplikat. */
    @Query("UPDATE energy_logs SET deletedAt = :now WHERE dateMs >= :startMs AND dateMs < :endMs AND source = 'PLANNED' AND deletedAt = 0 AND (:slot IS NULL OR slot = :slot)")
    suspend fun softDeletePlannedInDay(startMs: Long, endMs: Long, now: Long, slot: String? = null)

    /** Soft-delete pojedynczego wpisu (narzędzie AI delete_meal_log). */
    @Query("UPDATE energy_logs SET deletedAt = :now WHERE id = :id AND deletedAt = 0")
    suspend fun softDeleteById(id: Long, now: Long): Int

    /** Soft-delete CAŁEGO dnia (narzędzie AI reset_day — sprzątanie skażonego dnia). Zwraca liczbę ukrytych. */
    @Query("UPDATE energy_logs SET deletedAt = :now WHERE dateMs >= :startMs AND dateMs < :endMs AND deletedAt = 0")
    suspend fun softDeleteDay(startMs: Long, endMs: Long, now: Long): Int

    /** Guard anty-retry: aktywny AD_HOC wpis o identycznych makrach w oknie czasu (do pominięcia duplikatu sieci). */
    @Query("SELECT COUNT(*) FROM energy_logs WHERE deletedAt = 0 AND source = 'AD_HOC' AND kcalConsumed = :kcal AND proteinG = :p AND carbsG = :c AND fatG = :f AND dateMs >= :sinceMs")
    suspend fun countRecentAdHocDup(kcal: Int, p: Int, c: Int, f: Int, sinceMs: Long): Int

    @Query("DELETE FROM energy_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface AiMemoryDao {
    @Query("SELECT note FROM ai_memory ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentNotes(limit: Int = 20): List<String>

    /** Notatki z datą — do recency-aware pamięci miękkiej (stary kontekst wygasa). */
    @Query("SELECT * FROM ai_memory ORDER BY createdAt DESC LIMIT :limit")
    suspend fun recentEntries(limit: Int = 20): List<AiMemoryEntity>

    @Insert
    suspend fun insert(memory: AiMemoryEntity): Long

    /** Usuń notatki zawierające fragment (do sprzątania nieaktualnych intencji, np. „jutro makaron"). Zwraca liczbę. */
    @Query("DELETE FROM ai_memory WHERE note LIKE '%' || :frag || '%'")
    suspend fun deleteContaining(frag: String): Int
}
