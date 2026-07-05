package pl.filebit.dietetyk.core.model

/**
 * Dzienne spożycie energii — minimalne wejście dla adaptacyjnej estymaty TDEE.
 *
 * `isComplete` = czy dzień jest zalogowany w całości (wszystkie posiłki). TYLKO dni kompletne
 * wchodzą do estymaty metabolizmu — dzień częściowy zaniżyłby spożycie i skłamał wynik.
 *
 * To wąski model pod estymator; pełny `DayLog` (z mikroskładnikami — szew z ARCHITECTURE.md)
 * dojdzie w inkremencie logowania.
 */
data class DailyEnergyLog(
    val dateMs: Long,
    val kcalConsumed: Int,
    val isComplete: Boolean
)
