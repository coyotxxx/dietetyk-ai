package pl.filebit.dietetyk.ui

import pl.filebit.dietetyk.core.model.NutritionProfile

/** Postęp wywiadu strukturalnego (design 1.2: „Wywiad N/9"). */
data class InterviewStep(val step: Int, val total: Int, val label: String)

/**
 * Wylicza krok wywiadu z zebranego profilu. Profil powstaje dopiero po zebraniu obowiązkowych
 * (cel, płeć, wiek, wzrost) — więc do tego momentu jesteśmy na początku, potem pasek rośnie.
 */
fun interviewStep(profile: NutritionProfile?, weightKg: Double?): InterviewStep {
    val total = 9
    if (profile == null) return InterviewStep(1, total, "Poznajemy się")
    var step = 4; var label = "Twoja waga"                                   // imię+cel+płeć+wiek+wzrost
    if (weightKg != null || profile.weightKg != null) { step = 5; label = "Aktywność i tryb życia" }
    if (profile.daysPerWeek > 0) { step = 6; label = "Alergie i preferencje" }
    if (!profile.dietaryPrefs.isNullOrBlank()) { step = 7; label = "Liczba posiłków" }
    if (profile.mealsPerDay != null) { step = 8; label = "Prawie gotowe — podsumowanie" }
    return InterviewStep(step, total, label)
}
