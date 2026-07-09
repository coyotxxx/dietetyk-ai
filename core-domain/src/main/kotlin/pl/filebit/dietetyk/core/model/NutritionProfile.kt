package pl.filebit.dietetyk.core.model

/**
 * Czysty model profilu żywieniowego — wszystko, czego silnik potrzebuje do BMR/TDEE/BMI/celu.
 *
 * Zastępuje w `:core-domain` rozdzielone w GymTrackerze encje Room `UserProfile`+`UserDietProfile`
 * (JEDEN scalony model — filozofia „jeden user, jeden cel"). Warstwa `:data` mapuje encje Room na
 * ten model na granicy. Reguła: encja Room NIGDY nie jest parametrem funkcji core.
 *
 * `weightKg` nullable — podczas wywiadu waga może jeszcze nie być podana; najświeższa REALNA waga
 * pomiarowa żyje osobno w [WeightSample] i ma pierwszeństwo w obliczeniach, gdy dostępna.
 */
data class NutritionProfile(
    val gender: Gender,
    val ageYears: Int,
    val heightCm: Int,
    val weightKg: Double? = null,
    val activityLevel: ActivityLevel = ActivityLevel.MODERATE,
    /** Liczba treningów/tydz — używane przez [pl.filebit.dietetyk.core.safety.SafetyGuard] (min białko). */
    val daysPerWeek: Int = 0,
    val goal: DietGoalType = DietGoalType.MAINTAIN,
    /** Docelowe tempo zmiany masy (kg/tydz, wartość dodatnia — kierunek wynika z [goal]). */
    val paceKgPerWeek: Double = 0.5,
    /** Waga docelowa w kg (null = nieustawiona). */
    val goalWeightKg: Double? = null,
    /** Preferowana liczba posiłków dziennie (2–8; null = domyślne). */
    val mealsPerDay: Int? = null,
    /** Alergie/nietolerancje/preferencje (krótki tekst zebrany przez AI) — MIĘKKI kontekst dla promptu. */
    val dietaryPrefs: String? = null,
    /**
     * Alergie/nietolerancje STRUKTURALNIE (kanoniczne tokeny: „laktoza", „gluten", „orzechy", „jaja",
     * „ryby", „owoce_morza", „soja", „sezam" lub wolny wyraz). TWARDE źródło bezpieczeństwa — z tego
     * budujemy [pl.filebit.dietetyk.core.plan.DietConstraint.Allergy] egzekwowany w walidatorze planu.
     * Osobno od [dietaryPrefs], bo alergenu NIE WOLNO zgubić w parsowaniu wolnego tekstu (apka rodzinna).
     */
    val allergens: List<String> = emptyList(),
    /** Typ diety (wegetarianizm/weganizm/keto…) — TWARDE ograniczenie doboru produktów. */
    val dietType: DietPreference = DietPreference.STANDARD,
    /** Kadencja różnorodności planu (to samo codziennie vs różnicuj dni). */
    val varietyMode: VarietyMode = VarietyMode.SAME_DAILY
)
