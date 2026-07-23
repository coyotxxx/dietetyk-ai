package pl.filebit.dietetyk.core.calc

import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.safety.MedicalFlag
import pl.filebit.dietetyk.core.safety.MedicalFlagger
import pl.filebit.dietetyk.core.safety.SafetyGuard
import pl.filebit.dietetyk.core.safety.SafetyResult
import kotlin.math.abs

data class DailyMacroGoal(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val breakdown: GoalBreakdown,
    /** Ostrzeżenia SafetyGuard (np. zbyt agresywny deficyt) — do pokazania w UI. */
    val safetyWarnings: List<String> = emptyList(),
    /** Flagi medyczne — do pokazania z disclaimerem. */
    val medicalFlags: List<MedicalFlag> = emptyList(),
    /** True gdy SafetyGuard cap'nął kcal lub makro (zostały zmienione na bezpieczne). */
    val wasCapped: Boolean = false
)

/** Skąd wzięła się waga użyta do obliczeń — bramka wagi (nie licz po cichu na placeholderze). */
enum class WeightSource {
    MEASURED,   // realny pomiar (log_measurement)
    DECLARED,   // podana w profilu/wywiadzie (może „z głowy")
    ASSUMED     // BRAK wagi → placeholder ASSUMED_WEIGHT_KG. Cel TYMCZASOWY, wymaga realnej wagi.
}

/** Pełen breakdown obliczeń — żeby user (i AI) wiedzieli SKĄD biorą się liczby. */
data class GoalBreakdown(
    val weightKg: Double,
    val genderLabel: String,
    val tdeeKcal: Int,
    val tdeeFormulaText: String,
    val deficitOrSurplus: Int,
    val deficitLabel: String,
    val isManualOverride: Boolean,
    val proteinPerKg: Double,
    val fatPerKg: Double,
    val carbsCalculation: String,
    /** Źródło wagi. ASSUMED = policzone na placeholderze — cel niepewny, zdobądź realną wagę. */
    val weightSource: WeightSource = WeightSource.DECLARED
)

/**
 * Rdzeń celu żywieniowego: profil → TDEE (BMR Mifflin-St Jeor × aktywność) → deficyt/nadwyżka wg celu
 * → makro (białko/tłuszcz g/kg, węgle = reszta) → guardraile [SafetyGuard]. Zwraca liczby + pełny
 * breakdown „skąd te liczby". Czysta funkcja.
 *
 * Przeniesione z GymTrackera (`computeDailyGoal`). Zmiany vs oryginał (patrz ARCHITECTURE.md):
 *  - wejście: czysty [NutritionProfile] zamiast encji Room `UserProfile`+`UserDietProfile`,
 *  - JEDEN cel ([DietGoalType]) — usunięty fallback na `WeightGoalType` i uproszczony wzór TDEE
 *    (Dietetyk ma zawsze komplet danych z wywiadu → zawsze pełny Mifflin),
 *  - `avgDailyCardioKcal` → ogólny [activityEnergyKcal] (np. z Health Connect).
 *
 * TDEE tu jest STARTOWY (Mifflin). Kalibracja do realnego metabolizmu → osobny `AdaptiveTdeeEstimator`.
 */
object GoalPipeline {

    /** Placeholder wagi, gdy user nie podał żadnej — cel liczony na tym jest TYMCZASOWY (WeightSource.ASSUMED). */
    const val ASSUMED_WEIGHT_KG = 75.0

    fun compute(
        profile: NutritionProfile,
        clinical: ClinicalContext = ClinicalContext.NONE,
        manualKcalOverride: Int? = null,
        customDeficit: Int? = null,
        /** Dodatkowa energia z aktywności (np. kroki/cardio z Health Connect), kcal/dzień. */
        activityEnergyKcal: Int = 0,
        /** Najświeższa REALNA waga pomiarowa — ma pierwszeństwo przed [NutritionProfile.weightKg]. */
        latestMeasuredWeightKg: Double? = null,
        /** Carb cycling: true=dzień treningowy (mniej tłuszczu→więcej węgli), false=wolny, null=płasko. */
        isTrainingDay: Boolean? = null
    ): DailyMacroGoal {
        // BRAMKA WAGI: nie licz po cichu na placeholderze — zapamiętaj ŹRÓDŁO wagi.
        val weightSource = when {
            latestMeasuredWeightKg != null -> WeightSource.MEASURED
            profile.weightKg != null -> WeightSource.DECLARED
            else -> WeightSource.ASSUMED
        }
        val weight = latestMeasuredWeightKg ?: profile.weightKg ?: ASSUMED_WEIGHT_KG

        // === KROK 1: TDEE = BMR (Mifflin-St Jeor) × mnożnik aktywności (+ ewentualna aktywność) ===
        val maleConst = if (profile.gender == Gender.MALE) 5.0 else -161.0
        val bmr = 10.0 * weight + 6.25 * profile.heightCm - 5.0 * profile.ageYears + maleConst
        val bmrEstimate = bmr.toInt()
        val activityMult = profile.activityLevel.tdeeMultiplier
        val tdee = (bmr * activityMult + activityEnergyKcal).toInt()
        val tdeeFormula = if (activityEnergyKcal > 0)
            "BMR (Mifflin) %.0f × aktywność %.3f + aktywność %d kcal/dzień = %d kcal".format(
                bmr, activityMult, activityEnergyKcal, tdee)
        else
            "BMR (Mifflin) %.0f × aktywność %.3f = %d kcal".format(bmr, activityMult, tdee)

        // === KROK 2: deficyt/nadwyżka wg celu (8 typów) ===
        // 1 kg ≈ 7700 kcal → tempo (pace × 7700 / 7) dotyczy tylko celów wagowych.
        val paceKcal = (abs(profile.paceKgPerWeek) * 7700.0 / 7.0).toInt()
        val goalAdjustment = when (profile.goal) {
            DietGoalType.FAT_LOSS -> -paceKcal
            DietGoalType.MUSCLE_GAIN -> paceKcal
            DietGoalType.RECOMP -> -300          // mały deficyt
            DietGoalType.MAINTAIN -> 0
            DietGoalType.STRENGTH -> 0           // maintenance + dużo węgli (CNS)
            DietGoalType.ENDURANCE -> 100        // lekki surplus (regeneracja)
            DietGoalType.HEALTH -> 0
            DietGoalType.EVENT_PREP -> -paceKcal // agresywny deficyt
        }
        val effectiveDeficitRaw = customDeficit ?: goalAdjustment
        // Twardy CAP tempa redukcji: max 1.5 kg/tydz (ochrona mięśni + metabolizmu). 1.5 × 1100 = 1650.
        val maxSafeDeficit = -(SafetyGuard.MAX_LOSS_KG_PER_WEEK * 1100).toInt()
        val deficitCapped = effectiveDeficitRaw < maxSafeDeficit
        val effectiveDeficit = if (deficitCapped) maxSafeDeficit else effectiveDeficitRaw
        val deficitLabel = when {
            effectiveDeficit == 0 -> "Brak (utrzymanie wagi)"
            effectiveDeficit <= -750 -> "Agresywny deficyt %d kcal (~%s kg/tydz, ryzyko utraty masy mięśniowej)".format(
                effectiveDeficit, "%.1f".format(-effectiveDeficit / 1100.0))
            effectiveDeficit <= -500 -> "Klasyczna redukcja %d kcal (~%s kg/tydz)".format(
                effectiveDeficit, "%.1f".format(-effectiveDeficit / 1100.0))
            effectiveDeficit < 0 -> "Łagodny deficyt %d kcal (~%s kg/tydz, ochrona masy mięśniowej)".format(
                effectiveDeficit, "%.1f".format(-effectiveDeficit / 1100.0))
            effectiveDeficit <= 300 -> "Łagodna nadwyżka +$effectiveDeficit kcal (lean bulk, ~%s kg/tydz)".format(
                "%.2f".format(effectiveDeficit / 1100.0))
            else -> "Nadwyżka +$effectiveDeficit kcal (~%s kg/tydz, większy zysk masy)".format(
                "%.2f".format(effectiveDeficit / 1100.0))
        }

        // === KROK 3: total kcal ===
        val rawKcal = tdee + effectiveDeficit
        val finalKcal = manualKcalOverride ?: rawKcal

        // === KROK 4: makro wg celu (białko/tłuszcz g/kg, węgle = reszta) ===
        val (proteinPerKg, fatPerKg) = when (profile.goal) {
            DietGoalType.FAT_LOSS -> 2.2 to 0.8
            DietGoalType.EVENT_PREP -> 2.4 to 0.7
            DietGoalType.RECOMP -> 2.2 to 0.9
            DietGoalType.MUSCLE_GAIN -> 1.8 to 1.0
            DietGoalType.STRENGTH -> 2.0 to 1.0
            DietGoalType.MAINTAIN -> 2.0 to 1.0
            DietGoalType.HEALTH -> 1.6 to 1.0
            DietGoalType.ENDURANCE -> 1.6 to 0.9
        }
        val rawProteinG = (weight * proteinPerKg).toInt()
        val rawFatG = (weight * fatPerKg).toInt()

        // === SAFETYGUARD: hard-limity ===
        val warnings = mutableListOf<String>()
        var wasCapped = false

        // BRAMKA WAGI: cel na założonej wadze → ostrzeżenie (UI + AI). Nie blokuje liczenia, ale nie jest ciche.
        if (weightSource == WeightSource.ASSUMED) {
            warnings += "Cel policzony na ZAŁOŻONEJ wadze ${ASSUMED_WEIGHT_KG.toInt()} kg (brak realnej wagi) — podaj wagę, żeby był trafny."
        }

        if (deficitCapped) {
            wasCapped = true
            warnings += "Tempo redukcji ograniczone do bezpiecznych 1.5 kg/tydz (deficyt %d → %d kcal). "
                .format(effectiveDeficitRaw, effectiveDeficit) +
                "Szybsza utrata to ryzyko mięśni i spowolnienia metabolizmu."
        }

        val kcalResult = SafetyGuard.validateKcal(finalKcal, profile.gender, weight, bmrEstimate)
        val safeKcal = if (kcalResult is SafetyResult.Block) {
            wasCapped = true; warnings += kcalResult.message; kcalResult.cappedValue
        } else {
            kcalResult.warningMessage?.let { warnings += it }; finalKcal
        }

        val proteinResult = SafetyGuard.validateProtein(rawProteinG, weight, profile.daysPerWeek)
        val safeProteinG = if (proteinResult is SafetyResult.Block) {
            wasCapped = true; warnings += proteinResult.message; proteinResult.cappedValue
        } else {
            proteinResult.warningMessage?.let { warnings += it }; rawProteinG
        }

        val fatResult = SafetyGuard.validateFat(rawFatG, weight)
        val safeFatG = if (fatResult is SafetyResult.Block) {
            wasCapped = true; warnings += fatResult.message; fatResult.cappedValue
        } else rawFatG

        val weeklyKgChange = effectiveDeficit / 1100.0
        SafetyGuard.validateDeficitRate(weeklyKgChange).warningMessage?.let { warnings += it }

        // === KROK 5: węgle = reszta (z ENDURANCE carb-floor 5 g/kg i carb cyclingiem) ===
        val isEndurance = profile.goal == DietGoalType.ENDURANCE
        val proteinKcal = safeProteinG * 4
        val cycledFatG = when (isTrainingDay) {
            true -> maxOf((weight * 0.6).toInt(), (safeFatG * 0.8).toInt())
            false -> minOf((weight * 2.5).toInt(), (safeFatG * 1.2).toInt())
            null -> safeFatG
        }
        val (finalFatG, carbsG, carbsCalc) = if (isEndurance) {
            val minCarbsG = (weight * 5.0).toInt()
            val minCarbsKcal = minCarbsG * 4
            val remainingKcal = safeKcal - proteinKcal - minCarbsKcal
            val maxFatGFromKcal = (remainingKcal / 9).coerceAtLeast(0)
            val fatMin = (weight * 0.6).toInt()
            if (maxFatGFromKcal < fatMin) {
                val carbsKcal = (safeKcal - proteinKcal - fatMin * 9).coerceAtLeast(0)
                val c = carbsKcal / 4
                Triple(fatMin, c, "ENDURANCE (carbs=reszta gdy tłuszcz<min): " +
                    "($safeKcal − $proteinKcal − ${fatMin * 9}) / 4 = ${c}g")
            } else {
                Triple(maxFatGFromKcal, minCarbsG,
                    "ENDURANCE (carbs ≥5g/kg=${minCarbsG}g, tłuszcz=reszta): ${maxFatGFromKcal}g tłuszcz")
            }
        } else {
            val fatKcal = cycledFatG * 9
            val carbsKcal = (safeKcal - proteinKcal - fatKcal).coerceAtLeast(0)
            val c = carbsKcal / 4
            Triple(cycledFatG, c, "($safeKcal - $proteinKcal - $fatKcal) / 4 = ${c}g")
        }

        val medicalFlags = MedicalFlagger.analyze(profile, clinical, weight)

        return DailyMacroGoal(
            kcal = safeKcal,
            proteinG = safeProteinG,
            carbsG = carbsG,
            fatG = finalFatG,
            safetyWarnings = warnings,
            medicalFlags = medicalFlags,
            wasCapped = wasCapped,
            breakdown = GoalBreakdown(
                weightKg = weight,
                genderLabel = if (profile.gender == Gender.MALE) "mężczyzna" else "kobieta",
                tdeeKcal = tdee,
                tdeeFormulaText = tdeeFormula,
                deficitOrSurplus = effectiveDeficit,
                deficitLabel = deficitLabel,
                isManualOverride = manualKcalOverride != null,
                proteinPerKg = proteinPerKg,
                fatPerKg = if (isEndurance && weight > 0) finalFatG / weight else fatPerKg,
                carbsCalculation = carbsCalc,
                weightSource = weightSource
            )
        )
    }
}
