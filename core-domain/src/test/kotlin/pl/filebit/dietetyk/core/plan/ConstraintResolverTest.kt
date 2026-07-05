package pl.filebit.dietetyk.core.plan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.DietPreference
import pl.filebit.dietetyk.core.model.FoodCategory
import pl.filebit.dietetyk.core.model.FoodProductModel
import pl.filebit.dietetyk.core.model.Gender

class ConstraintResolverTest {

    private fun p(name: String, cat: FoodCategory = FoodCategory.OTHER) =
        FoodProductModel(name = name, category = cat, kcalPer100g = 100, proteinPer100g = 5.0, carbsPer100g = 5.0, fatPer100g = 2.0)

    @Test
    fun `alergia na laktoze wykrywa nabial po nazwie`() {
        val c = DietConstraint.Allergy("laktoza")
        assertTrue(c.isViolated(listOf(p("Jogurt naturalny"))))
        assertFalse(c.isViolated(listOf(p("Pierś z kurczaka"))))
    }

    @Test
    fun `weganizm lamie miesо i nabial`() {
        val c = DietConstraint.DietPreferenceConstraint(DietPreference.VEGAN)
        assertTrue(c.isViolated(listOf(p("Pierś z kurczaka"))))
        assertTrue(c.isViolated(listOf(p("Twaróg chudy", FoodCategory.DAIRY))))
        assertFalse(c.isViolated(listOf(p("Ryż biały", FoodCategory.CARBS))))
    }

    @Test
    fun `pescetarianizm dopuszcza ryby ale nie mieso`() {
        val c = DietConstraint.DietPreferenceConstraint(DietPreference.PESCATARIAN)
        assertFalse("łosoś OK", c.isViolated(listOf(p("Łosoś"))))
        assertTrue("kurczak nie", c.isViolated(listOf(p("Pierś z kurczaka"))))
    }

    @Test
    fun `resolve dodaje bezpieczne minimum kcal wg plci`() {
        val male = ConstraintResolver.resolve(Gender.MALE, DietPreferences())
        val female = ConstraintResolver.resolve(Gender.FEMALE, DietPreferences())
        assertTrue(male.filterIsInstance<DietConstraint.SafetyKcalMin>().any { it.minKcal == 1500 })
        assertTrue(female.filterIsInstance<DietConstraint.SafetyKcalMin>().any { it.minKcal == 1200 })
    }

    @Test
    fun `toPromptText rozdziela HARD i SOFT`() {
        val constraints = ConstraintResolver.resolve(
            Gender.MALE, DietPreferences(allergies = listOf("laktoza"), dislikedFoods = listOf("brokuły")))
        val text = ConstraintResolver.toPromptText(constraints)
        assertTrue(text.contains("HARD CONSTRAINTS"))
        assertTrue(text.contains("SOFT CONSTRAINTS"))
        assertTrue(text.contains("laktoza"))
    }
}
