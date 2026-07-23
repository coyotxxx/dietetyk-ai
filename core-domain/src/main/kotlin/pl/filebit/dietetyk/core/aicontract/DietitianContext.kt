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
    val waterTargetMl: Int = 2000,
    /** ITEMIZACJA — każdy realnie zalogowany dziś wpis (dyrektywa: AI ma dostęp do WSZYSTKICH danych,
     *  nie tylko sumy). Dzięki temu AI widzi np. 5 identycznych wierszy i samo rozpozna duplikaty. */
    val loggedMeals: List<LoggedMeal> = emptyList()
)

/** Pojedynczy REALNIE zalogowany wpis (wiersz energy_logs) — surowe dane, żeby AI mogło je zweryfikować. */
data class LoggedMeal(
    val id: Long,
    val timeHm: String,      // godzina zalogowania „HH:mm"
    val kcal: Int,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0
)

/**
 * Pojedynczy posiłek ZAPLANOWANY na dziś (z aktualnego planu). Liczby policzone przez kod (baza produktów) —
 * AI ich nie wymyśla; dzięki temu widzi, co znaczy „zjadłem wszystko", i loguje to bez zgadywania.
 */
data class PlannedMeal(
    val name: String,
    val timeHint: String = "",
    val kcal: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0
)

/** Zwięzły przegląd planu JEDNEGO dnia (nazwy+kcal posiłków) — do sekcji PLAN TYGODNIA w kontekście,
 *  żeby AI widziała plan KAŻDEGO dnia (nie tylko dziś) i nie zgadywała/halucynowała o innych dniach. */
data class DayPlanBrief(
    val dow: Int,
    val meals: List<PlannedMeal>
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
    val favoriteProducts: List<String> = emptyList(), // ❤️ lubiane — AI preferuje je w planach
    val avoidedProducts: List<String> = emptyList(),  // 🚫 nielubiane — AI NIGDY nie planuje (twardy guardrail walidatora)
    /** Czy istnieje już zapisany plan. false = PIERWSZY plan → mocniej trzymaj lubianych (pierwsze wrażenie). */
    val hasActivePlan: Boolean = false,
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
    /** Posiłki zaplanowane na dziś (z aktualnego planu). Puste = brak planu na dziś. */
    val plannedMealsToday: List<PlannedMeal> = emptyList(),
    /** PLAN CAŁEGO TYGODNIA (wszystkie dni z zapisanym planem) — żeby AI widziała stan planu na KAŻDY dzień
     *  i nie zgadywała, gdy user pyta o piątek/inny dzień. To ŹRÓDŁO PRAWDY o planie (nie notatki pamięci). */
    val weeklyPlan: List<DayPlanBrief> = emptyList(),
    /** Dzień tygodnia DZIŚ (1=Pn…7=Nd) — żeby AI podawało poprawny `dayOfWeek` do narzędzi planu
     *  (update_plan_meal/save_diet_plan), gdy user mówi „tylko dziś/jutro". Bez tego AI zgadywało (myliło dni). */
    val todayDow: Int = 0,
    /** TRUE = cel policzony na ZAŁOŻONEJ wadze (brak realnego pomiaru i brak wagi w profilu).
     *  AI MUSI to ujawnić i zdobyć realną wagę — cel jest tymczasowy, nie wolno go podawać jako pewnik. */
    val weightIsPlaceholder: Boolean = false,
    /** Ostrzeżenie o możliwym błędzie integralności danych dnia (np. suma >150% celu, podejrzane duplikaty).
     *  Kod tylko FLAGUJE — AI ma to ZBADAĆ (get_day_log) i naprawić z userem, nigdy zbagatelizować. */
    val todayDataWarning: String? = null,
    // === WIZYTA / BEZPIECZEŃSTWO ===
    val lastCheckIn: CheckInReport? = null,
    /** Sygnał alarmowy z RedFlagDetector — jeśli refer=true, AI MUSI kierować do lekarza (nadrzędne). */
    val referToDoctor: Boolean = false,
    val redFlagMessage: String = ""
) {
    /** Czy user aktywnie loguje (do decyzji o tonie/proaktywności). */
    val isEngaged: Boolean get() = (daysSinceLastLog ?: Int.MAX_VALUE) <= 2
}
