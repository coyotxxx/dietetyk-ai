package pl.filebit.dietetyk.ui

import pl.filebit.dietetyk.core.model.NutritionProfile

/** Postęp wywiadu strukturalnego (design 1.2: „Wywiad N/9"). */
data class InterviewStep(val step: Int, val total: Int, val label: String)

/**
 * Wylicza krok wywiadu z zebranego profilu. Profil powstaje dopiero po zebraniu obowiązkowych
 * (cel, płeć, wiek, wzrost) — więc do tego momentu jesteśmy na początku, potem pasek rośnie.
 */
fun interviewStep(profile: NutritionProfile?, weightKg: Double?): InterviewStep {
    val total = 7   // krótka ścieżka: imię/cel → podstawy → aktywność → bezpieczeństwo → smaki → posiłki/kadencja → podsumowanie
    if (profile == null) return InterviewStep(1, total, "Poznajemy się")
    var step = 3; var label = "Twoje podstawy"                               // imię+cel zebrane
    if (weightKg != null || profile.weightKg != null) { step = 4; label = "Aktywność" }
    if (profile.daysPerWeek > 0) { step = 5; label = "Alergie i smaki" }
    val tastesTouched = profile.allergens.isNotEmpty() || !profile.dietaryPrefs.isNullOrBlank() ||
        profile.dietType != pl.filebit.dietetyk.core.model.DietPreference.STANDARD
    if (tastesTouched) { step = 6; label = "Posiłki i kadencja" }
    if (profile.mealsPerDay != null) { step = 7; label = "Prawie gotowe" }
    return InterviewStep(step, total, label)
}
