package pl.filebit.dietetyk.core.plan

import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DietPreference
import pl.filebit.dietetyk.core.model.FoodProductModel
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.MedicalCondition

/** Preferencje żywieniowe usera (wejście do budowy ograniczeń) — czysty model, bez Room. */
data class DietPreferences(
    val allergies: List<String> = emptyList(),
    val intolerances: List<String> = emptyList(),
    val dietPreference: DietPreference = DietPreference.STANDARD,
    val dislikedFoods: List<String> = emptyList(),
    val cookingTimeMaxMin: Int = 30,
    val weeklyBudgetPln: Int? = null
)

/**
 * Buduje listę [DietConstraint] z płci + preferencji + kontekstu klinicznego i sprawdza posiłki/dni.
 * Przeniesione z GymTrackera jako czysty `object` (usunięty `javax.inject`; encje Room → czyste modele).
 */
object ConstraintResolver {

    fun resolve(
        gender: Gender,
        prefs: DietPreferences,
        clinical: ClinicalContext = ClinicalContext.NONE
    ): List<DietConstraint> {
        val list = mutableListOf<DietConstraint>()

        // === HARD ===
        val safetyKcalMin = if (gender == Gender.FEMALE) 1200 else 1500
        list += DietConstraint.SafetyKcalMin(safetyKcalMin)
        prefs.allergies.filter { it.isNotBlank() }.forEach { list += DietConstraint.Allergy(it) }
        prefs.intolerances.filter { it.isNotBlank() }.forEach { list += DietConstraint.Intolerance(it) }
        if (prefs.dietPreference != DietPreference.STANDARD) {
            list += DietConstraint.DietPreferenceConstraint(prefs.dietPreference)
        }
        clinical.conditions.forEach { list += DietConstraint.MedicalCondition(conditionLabel(it)) }

        // === SOFT ===
        prefs.dislikedFoods.filter { it.isNotBlank() }.forEach { list += DietConstraint.DislikedFood(it) }
        list += DietConstraint.CookingTimeMax(prefs.cookingTimeMaxMin)
        prefs.weeklyBudgetPln?.let { list += DietConstraint.WeeklyBudget(it) }
        list += DietConstraint.VarietyMin(uniqueProductsPerWeek = 10)

        return list
    }

    fun checkAllHard(constraints: List<DietConstraint>, products: List<FoodProductModel>): List<ConstraintViolation> =
        checkByPriority(constraints, products, ConstraintPriority.HARD)

    fun checkAllSoft(constraints: List<DietConstraint>, products: List<FoodProductModel>): List<ConstraintViolation> =
        checkByPriority(constraints, products, ConstraintPriority.SOFT)

    private fun checkByPriority(
        constraints: List<DietConstraint>, products: List<FoodProductModel>, priority: ConstraintPriority
    ): List<ConstraintViolation> = constraints
        .filter { it.priority == priority }
        .mapNotNull { c ->
            val violators = products.filter { p -> c.isViolated(listOf(p)) }
            if (violators.isNotEmpty()) ConstraintViolation(c, violators.map { it.name }) else null
        }

    /** Tekst dla promptu AI: sekcja HARD (zakaz) + SOFT (preferencja). */
    fun toPromptText(constraints: List<DietConstraint>): String {
        val hardList = constraints.filter { it.priority == ConstraintPriority.HARD }
        val softList = constraints.filter { it.priority == ConstraintPriority.SOFT }
        return buildString {
            if (hardList.isNotEmpty()) {
                append("=== HARD CONSTRAINTS (NIE WOLNO ŁAMAĆ — naruszenie = błąd) ===\n")
                hardList.forEach { append("- ${it.description}\n") }
            }
            if (softList.isNotEmpty()) {
                append("\n=== SOFT CONSTRAINTS (preferencje — preferuj, ale jeśli kolidują z HARD lub niemożliwe — zignoruj i wyjaśnij) ===\n")
                softList.forEach { append("- ${it.description}\n") }
                append("\nJeśli musisz zignorować SOFT constraint, dodaj do odpowiedzi pole `softConstraintViolations`: tablica stringów z opisem co zignorowałeś i dlaczego.\n")
            }
        }
    }

    private fun conditionLabel(c: MedicalCondition): String = when (c) {
        MedicalCondition.DIABETES -> "cukrzyca"
        MedicalCondition.KIDNEY_DISEASE -> "choroby nerek"
        MedicalCondition.LIVER_DISEASE -> "choroby wątroby"
        MedicalCondition.HEART_DISEASE -> "choroby serca"
        MedicalCondition.HYPERTENSION -> "nadciśnienie"
        MedicalCondition.PREGNANCY -> "ciąża"
        MedicalCondition.BREASTFEEDING -> "karmienie piersią"
        MedicalCondition.EATING_DISORDER -> "zaburzenia odżywiania"
    }
}
