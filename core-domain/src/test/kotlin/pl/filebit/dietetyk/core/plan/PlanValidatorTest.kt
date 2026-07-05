package pl.filebit.dietetyk.core.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.AiDayPlan
import pl.filebit.dietetyk.core.model.AiMealRecipe
import pl.filebit.dietetyk.core.model.AiRecipeIngredient
import pl.filebit.dietetyk.core.model.FoodCategory
import pl.filebit.dietetyk.core.model.FoodProductModel

class PlanValidatorTest {

    private val kurczak = FoodProductModel(1, "Pierś z kurczaka", FoodCategory.PROTEIN, 165, 31.0, 0.0, 3.6)
    private val ryz = FoodProductModel(2, "Ryż biały", FoodCategory.CARBS, 350, 7.0, 78.0, 0.6)   // surowy
    private val oliwa = FoodProductModel(3, "Oliwa z oliwek", FoodCategory.FAT, 884, 0.0, 0.0, 100.0)
    private val jogurt = FoodProductModel(4, "Jogurt naturalny", FoodCategory.DAIRY, 60, 5.0, 4.0, 3.0)

    private val base = listOf(kurczak, ryz, oliwa, jogurt).associateBy { it.name.lowercase() }

    private fun ctx(
        expectedMeals: Int = 1, targetKcal: Int = 540, targetProtein: Int = 60,
        constraints: List<DietConstraint> = emptyList(),
        enforceKcal: Boolean = true, enforceMacros: Boolean = true
    ) = ValidationContext(
        expectedMealsCount = expectedMeals, targetKcal = targetKcal, targetProteinG = targetProtein,
        perMealProteinMinG = 25, maxCookingMinutesPerMeal = 45, productsByName = base,
        constraints = constraints, enforceDailyKcal = enforceKcal, enforceDailyMacros = enforceMacros
    )

    // Kurczak 200g (330 kcal, 62 B) + Ryż 60g (210 kcal, 4.2 B) = 540 kcal, 66 B
    private val goodMeal = AiMealRecipe("Kurczak z ryżem", 540, 25, listOf(
        AiRecipeIngredient("Pierś z kurczaka", 200), AiRecipeIngredient("Ryż biały", 60)))

    @Test
    fun `poprawny plan trafiajacy w cel jest walidny`() {
        val r = PlanValidator.validate(AiDayPlan(listOf(goodMeal)), ctx())
        assertTrue("brak błędów: ${r.errors.map { it.code }}", r.isValid)
        assertTrue(r.correctedTotal.totalKcal in 500..560)
    }

    @Test
    fun `zla liczba posilkow to blad`() {
        val r = PlanValidator.validate(AiDayPlan(listOf(goodMeal)), ctx(expectedMeals = 2))
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.code == "meals_count_mismatch" })
    }

    @Test
    fun `HARD constraint (alergia) blokuje plan`() {
        val meal = AiMealRecipe("Jogurt", 120, 2, listOf(AiRecipeIngredient("Jogurt naturalny", 200)))
        val r = PlanValidator.validate(AiDayPlan(listOf(meal)),
            ctx(constraints = listOf(DietConstraint.Allergy("laktoza")), enforceKcal = false, enforceMacros = false))
        assertFalse(r.isValid)
        assertTrue(r.errors.any { it.code == "hard_constraint_violation" })
    }

    @Test
    fun `nieznany produkt to ostrzezenie, nie blad`() {
        val meal = AiMealRecipe("Coś", 300, 10, listOf(
            AiRecipeIngredient("Pierś z kurczaka", 150), AiRecipeIngredient("Marsjański proszek", 50)))
        val r = PlanValidator.validate(AiDayPlan(listOf(meal)), ctx(enforceKcal = false, enforceMacros = false))
        assertTrue(r.warnings.any { it.code == "unknown_product" })
    }

    @Test
    fun `posilek zbyt tlusty (ponad 55 proc kcal) to blad`() {
        val meal = AiMealRecipe("Sama oliwa", 884, 1, listOf(AiRecipeIngredient("Oliwa z oliwek", 100)))
        val r = PlanValidator.validate(AiDayPlan(listOf(meal)), ctx(enforceKcal = false, enforceMacros = false))
        assertTrue(r.errors.any { it.code == "slot_fat_too_high" })
    }

    @Test
    fun `suma dnia poza tolerancja 7 proc to blad`() {
        // cel 3000, a plan daje ~540 → duże odchylenie
        val r = PlanValidator.validate(AiDayPlan(listOf(goodMeal)), ctx(targetKcal = 3000, targetProtein = 0))
        assertTrue(r.errors.any { it.code == "daily_kcal_off_target" })
    }
}
