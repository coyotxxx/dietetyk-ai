package pl.filebit.dietetyk.core.safety

import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.NutritionProfile

/** Sygnał alarmowy wykryty w danych. */
enum class RedFlag {
    RAPID_WEIGHT_LOSS,      // waga leci niebezpiecznie szybko
    PROLONGED_UNDEREATING,  // dni pod bezpiecznym minimum kcal
    SEVERE_RESTRICTION,     // skrajnie niskie kcal (możliwy wzorzec zaburzeń)
    MEDICAL_CRITICAL,       // krytyczna flaga medyczna (ciąża/ED/cukrzyca…)
    UNDERWEIGHT             // BMI poniżej normy
}

data class RedFlagResult(
    val flags: List<RedFlag>,
    /** True → AI ma się zatrzymać i skierować do lekarza/specjalisty. */
    val refer: Boolean,
    val message: String
) {
    companion object { val NONE = RedFlagResult(emptyList(), false, "") }
}

/**
 * Wykrywa sytuacje, w których AI-dietetyk MUSI się zatrzymać i skierować do specjalisty
 * (decyzja Claude+Fable, ARCHITECTURE.md §safety). Guardrail W KODZIE — AI nie może go „zagadać".
 *
 * Zasada bezpieczeństwa: przy niepewności bias w stronę FLAGOWANIA (dla rodziny to realni bliscy).
 * Reużywa [MedicalFlagger] (BMI + stany) i dokłada wzorce dynamiczne z logów (tempo utraty, głodzenie).
 */
object RedFlagDetector {

    private const val DANGEROUS_LOSS_KG_PER_WEEK = 2.0   // powyżej tego = alarm (nie tylko warning 1.5)
    private const val MIN_DAYS_UNDER_FLOOR = 4           // ile dni pod minimum uruchamia flagę
    private const val SEVERE_KCAL = 1000                 // skrajnie niskie spożycie

    fun detect(
        profile: NutritionProfile,
        clinical: ClinicalContext = ClinicalContext.NONE,
        weightTrend: WeightTrend = WeightTrend.NO_DATA,
        recentIntake: List<DailyEnergyLog> = emptyList(),
        currentWeightKg: Double? = null
    ): RedFlagResult {
        val flags = mutableListOf<RedFlag>()
        val reasons = mutableListOf<String>()

        // 1) Flagi medyczne + BMI (reużycie MedicalFlagger)
        val medFlags = MedicalFlagger.analyze(profile, clinical, currentWeightKg)
        if (MedicalFlagger.anyCritical(medFlags)) {
            flags += RedFlag.MEDICAL_CRITICAL
            reasons += medFlags.first { it.severity == MedicalSeverity.CRITICAL }.recommendation
        }
        if (medFlags.any { it.code == "low_bmi" || it.code == "very_low_bmi" }) {
            flags += RedFlag.UNDERWEIGHT
        }

        // 2) Niebezpiecznie szybka utrata wagi (trend, nie pojedynczy pomiar)
        val slope = weightTrend.slopeKgPerWeek
        if (weightTrend.hasEnoughData && slope != null && slope <= -DANGEROUS_LOSS_KG_PER_WEEK) {
            flags += RedFlag.RAPID_WEIGHT_LOSS
            reasons += "Waga spada w tempie ${"%.1f".format(-slope)} kg/tydz — to zbyt szybko i wymaga oceny lekarza."
        }

        // 3) Przedłużone głodzenie / skrajne restrykcje (z kompletnych dni)
        val floor = SafetyGuard.minKcal(profile.gender)
        val complete = recentIntake.filter { it.isComplete && it.kcalConsumed > 0 }
        val daysUnderFloor = complete.count { it.kcalConsumed < floor }
        val daysSevere = complete.count { it.kcalConsumed < SEVERE_KCAL }
        if (daysUnderFloor >= MIN_DAYS_UNDER_FLOOR) {
            flags += RedFlag.PROLONGED_UNDEREATING
            reasons += "Przez $daysUnderFloor dni jadłeś poniżej bezpiecznego minimum ($floor kcal)."
        }
        if (daysSevere >= 2) {
            flags += RedFlag.SEVERE_RESTRICTION
            reasons += "Kilka dni skrajnie niskiego spożycia (<$SEVERE_KCAL kcal) — to niebezpieczne."
        }

        // Skierowanie: każda z tych flag = zatrzymaj i skieruj do specjalisty.
        val refer = flags.any {
            it == RedFlag.MEDICAL_CRITICAL || it == RedFlag.RAPID_WEIGHT_LOSS ||
                it == RedFlag.SEVERE_RESTRICTION || it == RedFlag.PROLONGED_UNDEREATING ||
                (it == RedFlag.UNDERWEIGHT && medFlags.any { f -> f.code == "very_low_bmi" })
        }

        val message = if (refer)
            "To wykracza poza to, co powinna prowadzić aplikacja. " + reasons.joinToString(" ") +
                " Proszę, skonsultuj się z lekarzem lub dietetykiem klinicznym."
        else reasons.joinToString(" ")

        return RedFlagResult(flags = flags.distinct(), refer = refer, message = message)
    }
}
