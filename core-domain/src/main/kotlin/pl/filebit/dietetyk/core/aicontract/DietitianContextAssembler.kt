package pl.filebit.dietetyk.core.aicontract

import pl.filebit.dietetyk.core.adapt.CheckInReport
import pl.filebit.dietetyk.core.calc.AdaptiveTdeeEstimator
import pl.filebit.dietetyk.core.calc.GoalPipeline
import pl.filebit.dietetyk.core.calc.TrendAnalyzer
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.model.WeightSample
import pl.filebit.dietetyk.core.safety.RedFlagDetector

/**
 * Spina CAŁY silnik w jeden [DietitianContext] — to jest miejsce, które realizuje dyrektywę
 * „AI widzi WSZYSTKIE dane": z surowych danych (profil, wagi, logi, pamięć) liczy cel, adaptacyjny
 * metabolizm, trend, sygnały alarmowe i pakuje to w komplet, który dostaje AI.
 *
 * Czysta funkcja (testowalna) — warstwa `:data` tylko POBIERA dane z repozytoriów i woła `assemble`.
 * Wszystkie liczby policzone deterministycznie tutaj; AI ich nie wymyśla.
 */
object DietitianContextAssembler {

    private const val DAY_MS = 24L * 3600 * 1000
    private const val WINDOW_14D = 14L

    fun assemble(
        careState: CareState,
        profile: NutritionProfile,
        clinical: ClinicalContext,
        weightSamples: List<WeightSample>,
        energyLogs: List<DailyEnergyLog>,
        memoryNotes: List<String>,
        adherence14d: AdherenceSummary,
        today: DaySnapshot,
        lastCheckIn: CheckInReport?,
        daysSinceLastLog: Int?,
        nowMs: Long
    ): DietitianContext {
        val latestWeight = weightSamples.maxByOrNull { it.dateMs }?.weightKg

        val trend = TrendAnalyzer.analyze(weightSamples, nowMs)

        val goal = GoalPipeline.compute(
            profile = profile, clinical = clinical, latestMeasuredWeightKg = latestWeight
        )

        val tdeeEstimate = AdaptiveTdeeEstimator.estimate(
            formulaTdeeKcal = goal.breakdown.tdeeKcal,
            intakeLogs = energyLogs, weightSamples = weightSamples, nowMs = nowMs
        )

        val since14d = nowMs - WINDOW_14D * DAY_MS
        val complete14d = energyLogs.filter { it.isComplete && it.kcalConsumed > 0 && it.dateMs >= since14d }
        val avgIntake = complete14d.map { it.kcalConsumed }.takeIf { it.isNotEmpty() }?.average()?.toInt()

        val redFlag = RedFlagDetector.detect(profile, clinical, trend, energyLogs, latestWeight)

        return DietitianContext(
            careState = careState,
            profile = profile,
            clinical = clinical,
            memoryNotes = memoryNotes,
            currentGoal = goal,
            tdeeEstimate = tdeeEstimate,
            latestWeightKg = latestWeight,
            weightTrend = trend,
            adherence14d = adherence14d,
            completeLogDays14d = complete14d.size,
            avgIntakeKcal14d = avgIntake,
            daysSinceLastLog = daysSinceLastLog,
            today = today,
            lastCheckIn = lastCheckIn,
            referToDoctor = redFlag.refer,
            redFlagMessage = redFlag.message
        )
    }
}
