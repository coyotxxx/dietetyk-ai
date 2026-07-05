package pl.filebit.dietetyk.core.adapt

import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.NeatSnapshot
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.model.RecoverySnapshot
import pl.filebit.dietetyk.core.safety.RedFlag
import pl.filebit.dietetyk.core.safety.RedFlagDetector

/** Werdykt wizyty kontrolnej — JEDEN głos (mózg decyduje, AI komunikuje). */
enum class CheckInVerdict {
    REFER_TO_DOCTOR,   // guardrail: kieruj do specjalisty (nadrzędne)
    NEEDS_MORE_DATA,   // za mało danych do decyzji
    HOLD,              // plan działa — trzymaj
    ADJUST_KCAL,       // korekta kalorii (małymi krokami)
    DIET_BREAK,        // przerwa/refeed (regeneracja hormonalna)
    DELOAD,            // tydzień lżejszy
    SIMPLIFY_PLAN      // uprość plan (niska adherence)
}

data class CheckInReport(
    val verdict: CheckInVerdict,
    val kcalDelta: Int,
    val newKcal: Int,
    /** Krótkie zdanie-werdykt dla AI (do zakomunikowania po ludzku). */
    val headline: String,
    /** Pełne wyjaśnienie „dlaczego". */
    val detail: String,
    val redFlags: List<RedFlag> = emptyList(),
    /** Szczegół korekty z silnika (null gdy REFER / brak danych). */
    val adjustment: AdjustmentDecision? = null,
    val confidence: Confidence
)

/**
 * Silnik cotygodniowej wizyty kontrolnej. Orkiestruje deterministyczne komponenty w JEDEN werdykt:
 *  1) [RedFlagDetector] — jeśli trzeba skierować do lekarza, to jest NADRZĘDNE (AI nie może zagadać),
 *  2) inaczej [CalorieAdjustmentEngine] — korekta małymi krokami, mapowana na werdykt wizyty.
 *
 * NOWY komponent (nie z GymTrackera) — realizuje wizytę „jak u dietetyka" z decyzji Claude+Fable.
 * Zwraca gotowy raport; AI go KOMUNIKUJE (rozmowa), nie wymyśla liczb.
 */
object CheckInEngine {

    fun run(
        profile: NutritionProfile,
        clinical: ClinicalContext = ClinicalContext.NONE,
        currentKcal: Int,
        weightTrend: WeightTrend,
        adherence14d: AdherenceSummary,
        recentIntake: List<DailyEnergyLog> = emptyList(),
        recovery: RecoverySnapshot = RecoverySnapshot.EMPTY,
        hydrationAdherencePct: Int = 100,
        neat: NeatSnapshot = NeatSnapshot.EMPTY,
        cutDurationDays: Int = 0,
        daysSinceLastRefeed: Int = 999,
        realWorkouts14d: Int? = null,
        hasTrainingPlan: Boolean = false,
        currentWeightKg: Double? = null
    ): CheckInReport {

        // 1) GUARDRAIL nadrzędny — sygnały alarmowe do lekarza.
        val red = RedFlagDetector.detect(profile, clinical, weightTrend, recentIntake, currentWeightKg)
        if (red.refer) {
            return CheckInReport(
                verdict = CheckInVerdict.REFER_TO_DOCTOR, kcalDelta = 0, newKcal = currentKcal,
                headline = "To warto omówić z lekarzem.",
                detail = red.message, redFlags = red.flags, confidence = Confidence.HIGH
            )
        }

        // 2) Korekta deterministyczna → werdykt wizyty.
        val adj = CalorieAdjustmentEngine.analyze(
            goal = profile.goal, currentKcal = currentKcal, weightTrend = weightTrend,
            adherence14d = adherence14d, recovery = recovery, hydrationAdherencePct = hydrationAdherencePct,
            neat = neat, cutDurationDays = cutDurationDays, daysSinceLastRefeed = daysSinceLastRefeed,
            realWorkouts14d = realWorkouts14d, hasTrainingPlan = hasTrainingPlan
        )

        val verdict = when (adj.action) {
            AdjustmentAction.NEEDS_MORE_DATA -> CheckInVerdict.NEEDS_MORE_DATA
            AdjustmentAction.HOLD -> CheckInVerdict.HOLD
            AdjustmentAction.DECREASE_KCAL, AdjustmentAction.INCREASE_KCAL -> CheckInVerdict.ADJUST_KCAL
            AdjustmentAction.REFEED_DAY -> CheckInVerdict.DIET_BREAK
            AdjustmentAction.DELOAD -> CheckInVerdict.DELOAD
            AdjustmentAction.SIMPLIFY_PLAN -> CheckInVerdict.SIMPLIFY_PLAN
        }

        val headline = when (verdict) {
            CheckInVerdict.HOLD -> "Plan działa — trzymamy kurs."
            CheckInVerdict.ADJUST_KCAL ->
                if (adj.kcalDeltaProposed >= 0) "Dokładam ${adj.kcalDeltaProposed} kcal."
                else "Zdejmuję ${-adj.kcalDeltaProposed} kcal."
            CheckInVerdict.DIET_BREAK -> "Czas na krótką przerwę/regenerację."
            CheckInVerdict.DELOAD -> "Odpuśćmy tydzień — regeneracja."
            CheckInVerdict.SIMPLIFY_PLAN -> "Uprośćmy plan, żeby był wykonalny."
            CheckInVerdict.NEEDS_MORE_DATA -> "Potrzebuję jeszcze trochę danych."
            CheckInVerdict.REFER_TO_DOCTOR -> "To warto omówić z lekarzem."
        }

        return CheckInReport(
            verdict = verdict, kcalDelta = adj.kcalDeltaProposed, newKcal = adj.newKcal,
            headline = headline, detail = adj.explanation, redFlags = red.flags,
            adjustment = adj, confidence = adj.confidence
        )
    }
}
