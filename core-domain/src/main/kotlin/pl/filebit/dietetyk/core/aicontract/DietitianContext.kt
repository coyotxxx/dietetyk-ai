package pl.filebit.dietetyk.core.aicontract

import pl.filebit.dietetyk.core.adapt.CheckInReport
import pl.filebit.dietetyk.core.calc.DailyMacroGoal
import pl.filebit.dietetyk.core.calc.TdeeEstimate
import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.NutritionProfile

/** Migawka „dzisiaj" — pulpit dnia. */
data class DaySnapshot(
    val kcalConsumed: Int = 0,
    val proteinConsumedG: Int = 0,
    val mealsEaten: Int = 0,
    val mealsPlanned: Int = 0,
    val waterMl: Int = 0,
    val waterTargetMl: Int = 2000
)

/**
 * KOMPLETNY kontekst, który AI-dietetyk widzi w KAŻDEJ rozmowie (dyrektywa Macieja 2026-07-05:
 * „AI ma dostęp do WSZYSTKICH danych"). Wszystkie liczby policzone deterministycznie przez
 * `:core-domain` — AI ich nie wymyśla, tylko INTERPRETUJE i DECYDUJE.
 *
 * To MODEL (czysty). Builder (który go wypełnia z repozytoriów) żyje w `:data`/`:app`.
 * Renderowanie do promptu: [DietitianPrompt].
 */
data class DietitianContext(
    val careState: CareState,
    // === KIM JEST ===
    val profile: NutritionProfile,
    val clinical: ClinicalContext,
    val memoryNotes: List<String> = emptyList(),   // pamięć epizodyczna: „nie znosi twarogu", „środy ciężkie"…
    val favoriteProducts: List<String> = emptyList(), // produkty oznaczone jako ulubione — AI preferuje je w planach
    // === CEL / KONTRAKT ===
    val currentGoal: DailyMacroGoal? = null,        // aktualne kcal/makro + breakdown „skąd te liczby"
    val tdeeEstimate: TdeeEstimate? = null,         // adaptacyjny metabolizm (realny vs wzór + ufność)
    // === POMIARY / TRENDY ===
    val latestWeightKg: Double? = null,
    val weightTrend: WeightTrend = WeightTrend.NO_DATA,
    // === PROWADZENIE ===
    val adherence14d: AdherenceSummary = AdherenceSummary(),
    val completeLogDays14d: Int = 0,
    val avgIntakeKcal14d: Int? = null,
    val daysSinceLastLog: Int? = null,
    val today: DaySnapshot = DaySnapshot(),
    // === WIZYTA / BEZPIECZEŃSTWO ===
    val lastCheckIn: CheckInReport? = null,
    /** Sygnał alarmowy z RedFlagDetector — jeśli refer=true, AI MUSI kierować do lekarza (nadrzędne). */
    val referToDoctor: Boolean = false,
    val redFlagMessage: String = ""
) {
    /** Czy user aktywnie loguje (do decyzji o tonie/proaktywności). */
    val isEngaged: Boolean get() = (daysSinceLastLog ?: Int.MAX_VALUE) <= 2
}
