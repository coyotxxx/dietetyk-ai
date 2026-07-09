package pl.filebit.dietetyk.data.mapper

import pl.filebit.dietetyk.core.model.DietPreference
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.model.VarietyMode
import pl.filebit.dietetyk.core.model.WeightSample
import pl.filebit.dietetyk.data.db.ProfileEntity
import pl.filebit.dietetyk.data.db.WeightEntity

/**
 * Mappery encja Room ↔ model `:core-domain`. To JEDYNE miejsce, gdzie encja spotyka model —
 * reguła: encja Room NIGDY nie jest parametrem funkcji core (ARCHITECTURE.md).
 */

fun ProfileEntity.toModel(): NutritionProfile = NutritionProfile(
    gender = gender, ageYears = ageYears, heightCm = heightCm, weightKg = weightKg,
    activityLevel = activityLevel, daysPerWeek = daysPerWeek, goal = goal, paceKgPerWeek = paceKgPerWeek,
    goalWeightKg = goalWeightKg, mealsPerDay = mealsPerDay, dietaryPrefs = dietaryPrefs,
    allergens = allergens?.split(";")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList(),
    dietType = dietType?.let { runCatching { DietPreference.valueOf(it) }.getOrNull() } ?: DietPreference.STANDARD,
    varietyMode = runCatching { VarietyMode.valueOf(varietyMode) }.getOrNull() ?: VarietyMode.SAME_DAILY
)

fun NutritionProfile.toEntity(updatedAt: Long, dirty: Boolean = true): ProfileEntity = ProfileEntity(
    id = 0, gender = gender, ageYears = ageYears, heightCm = heightCm, weightKg = weightKg,
    activityLevel = activityLevel, daysPerWeek = daysPerWeek, goal = goal, paceKgPerWeek = paceKgPerWeek,
    goalWeightKg = goalWeightKg, mealsPerDay = mealsPerDay, dietaryPrefs = dietaryPrefs,
    allergens = allergens.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(";"),
    dietType = dietType.name,
    varietyMode = varietyMode.name,
    updatedAt = updatedAt, dirty = dirty
)

fun WeightEntity.toModel(): WeightSample = WeightSample(dateMs = dateMs, weightKg = weightKg, waistCm = waistCm, bodyFatPct = bodyFatPct)

fun WeightSample.toEntity(updatedAt: Long, dirty: Boolean = true): WeightEntity =
    WeightEntity(dateMs = dateMs, weightKg = weightKg, waistCm = waistCm, bodyFatPct = bodyFatPct, updatedAt = updatedAt, dirty = dirty)
