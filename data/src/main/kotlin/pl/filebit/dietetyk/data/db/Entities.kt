package pl.filebit.dietetyk.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import pl.filebit.dietetyk.core.model.ActivityLevel
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender

/**
 * Encje Room. NIGDY nie wychodzą poza `:data` — mapper na granicy zamienia je na modele `:core-domain`.
 * Każda encja ma szew synchronizacji (`updatedAt` + `dirty`) pod przyszłą furtkę WWW (SyncApi).
 */

/** Profil użytkownika — pojedynczy wiersz (id=0). Mapuje się na `NutritionProfile`. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 0,
    val gender: Gender,
    val ageYears: Int,
    val heightCm: Int,
    val weightKg: Double?,
    val activityLevel: ActivityLevel,
    val daysPerWeek: Int,
    val goal: DietGoalType,
    val paceKgPerWeek: Double,
    val updatedAt: Long = 0,
    val dirty: Boolean = false
)

/** Pomiar wagi w czasie. Mapuje się na `WeightSample`. */
@Entity(tableName = "weight_samples")
data class WeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMs: Long,
    val weightKg: Double,
    val waistCm: Double? = null,
    val bodyFatPct: Double? = null,
    val updatedAt: Long = 0,
    val dirty: Boolean = false
)
