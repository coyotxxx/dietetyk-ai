package pl.filebit.dietetyk.core.model

/**
 * DTO planu dnia proponowanego przez AI — WEJŚCIE walidatora
 * [pl.filebit.dietetyk.core.plan.PlanValidator]. Czyste data class-y (bez adnotacji serializacji —
 * te dojdą w warstwie `:ai`, która parsuje JSON z Claude). AI deklaruje kcal/makro, ale walidator
 * i tak przelicza je z bazy produktów (AI nigdy nie dyktuje liczb).
 */
data class AiDayPlan(
    val meals: List<AiMealRecipe>
)

data class AiMealRecipe(
    val name: String,
    /** kcal DEKLAROWANE przez AI (weryfikowane vs baza). */
    val kcal: Int = 0,
    val prepMinutes: Int = 0,
    val ingredients: List<AiRecipeIngredient> = emptyList()
)

data class AiRecipeIngredient(
    val productName: String,
    val grams: Int
)
