package pl.filebit.dietetyk.core.model

/**
 * Jedno źródło prawdy dla slotów posiłkowych.
 *
 * v2.73.0 (POSIŁKI N): tożsamością posiłku jest NUMER slotu (1..N), a `MealType`
 * pełni rolę TAGU (kontekst trening, dopasowanie przepisu, domyślne godziny).
 * Etykieta w UI jest spójna — „Posiłek N" (z opcją własnej nazwy per slot).
 *
 * Przeniesione z GymTrackera — czysty Kotlin.
 */
object MealSlots {

    const val MIN_MEALS = 2
    const val MAX_MEALS = 8

    /**
     * Tag-typy dla N slotów (z powtórzeniami — np. 5 posiłków = 2 przekąski).
     * UWAGA: to TYLKO tag; posiłki rozróżnia numer slotu, nie ten typ.
     */
    fun typesFor(mealsCount: Int): List<MealType> = when (mealsCount.coerceIn(MIN_MEALS, MAX_MEALS)) {
        2 -> listOf(MealType.BREAKFAST, MealType.DINNER)
        3 -> listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
        4 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.DINNER)
        5 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.DINNER)
        6 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.DINNER)
        7 -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.SNACK, MealType.DINNER)
        else -> listOf(MealType.BREAKFAST, MealType.SNACK, MealType.LUNCH, MealType.SNACK, MealType.SNACK, MealType.SNACK, MealType.SNACK, MealType.DINNER) // 8
    }

    /** Tag-typ dla slotu (1-based) przy N posiłkach. Slot poza zakresem → SNACK. */
    fun mealTypeForSlot(slot: Int, mealsCount: Int): MealType =
        typesFor(mealsCount).getOrElse(slot - 1) { MealType.SNACK }

    /** Spójna etykieta slotu w UI: „Posiłek N". */
    fun label(slot: Int): String = "Posiłek $slot"

    /** Etykieta w mianowniku dla TAGU mealType (np. dopasowanie przepisu/godziny). */
    fun polishLabel(t: MealType): String = when (t) {
        MealType.BREAKFAST -> "śniadanie"
        MealType.LUNCH -> "obiad"
        MealType.DINNER -> "kolacja"
        MealType.SNACK -> "przekąska"
    }
}
