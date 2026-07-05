package pl.filebit.dietetyk.core.model

/**
 * Podstawowe typy domenowe silnika diety (czysty Kotlin — zero Androida).
 * W GymTrackerze enumy żyły przy encjach Room; tu są oddzielone od warstwy danych,
 * żeby `:core-domain` nie zależał od Androida. `:data` mapuje encje Room na te typy.
 */

/** Płeć — parametr wzorów BMR/tkanki tłuszczowej. */
enum class Gender { MALE, FEMALE }

/**
 * TAG posiłku (kontekst treningowy, dopasowanie przepisu, domyślne godziny).
 * v2.73 (POSIŁKI N): tożsamością posiłku jest NUMER slotu (1..N), `MealType` to tylko tag. Patrz [MealSlots].
 */
enum class MealType {
    BREAKFAST,  // śniadanie
    LUNCH,      // obiad
    DINNER,     // kolacja
    SNACK       // przekąska (po treningu, drugie śniadanie)
}
