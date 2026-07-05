package pl.filebit.dietetyk.core.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.ActivityLevel
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile

/**
 * Port testów computeDailyGoal z GymTrackera → golden-referencja rdzenia celu.
 * Kluczowe niezmienniki: TDEE bez podwójnego liczenia treningów, cap tempa redukcji, carb cycling.
 */
class GoalPipelineTest {

    private fun profile(
        daysPerWeek: Int = 4,
        goal: DietGoalType = DietGoalType.MAINTAIN
    ) = NutritionProfile(
        gender = Gender.MALE, ageYears = 30, heightCm = 180, weightKg = 80.0,
        activityLevel = ActivityLevel.MODERATE, daysPerWeek = daysPerWeek, goal = goal
    )

    private fun goalFor(daysPerWeek: Int = 4) =
        GoalPipeline.compute(profile(daysPerWeek), latestMeasuredWeightKg = 80.0)

    @Test
    fun `TDEE = BMR razy mnoznik aktywnosci, bez bonusu za treningi`() {
        // BMR (Mifflin) = 10×80 + 6.25×180 − 5×30 + 5 = 1780; TDEE = 1780 × 1.55 = 2759
        assertEquals("TDEE bez podwójnego liczenia treningów", 2759, goalFor().breakdown.tdeeKcal)
    }

    @Test
    fun `daysPerWeek NIE wplywa na TDEE (treningi sa juz w activityLevel)`() {
        assertEquals("TDEE identyczne dla 2 i 6 treningów/tydz",
            goalFor(daysPerWeek = 2).breakdown.tdeeKcal,
            goalFor(daysPerWeek = 6).breakdown.tdeeKcal)
    }

    private fun cutGoal(customDeficit: Int) = GoalPipeline.compute(
        profile(goal = DietGoalType.FAT_LOSS), customDeficit = customDeficit, latestMeasuredWeightKg = 80.0)

    @Test
    fun `agresywny deficyt jest ograniczony do 1_5 kg na tydzien`() {
        val g = cutGoal(-2000)  // -1.8 kg/tydz → cap do -1650
        assertTrue("warning o ograniczeniu tempa redukcji",
            g.safetyWarnings.any { it.contains("ograniczone do bezpiecznych 1.5") })
        assertTrue("cap oznaczony", g.wasCapped)
    }

    @Test
    fun `umiarkowany deficyt bez ograniczenia tempa`() {
        val g = cutGoal(-500)
        assertFalse("brak warningu o tempie przy łagodnym deficycie",
            g.safetyWarnings.any { it.contains("ograniczone do bezpiecznych 1.5") })
    }

    private fun maintainGoal(isTrainingDay: Boolean?) = GoalPipeline.compute(
        profile(goal = DietGoalType.MAINTAIN), latestMeasuredWeightKg = 80.0, isTrainingDay = isTrainingDay)

    @Test
    fun `carb cycling - dzien treningowy ma wiecej wegli i mniej tluszczu`() {
        val train = maintainGoal(true)
        val rest = maintainGoal(false)
        assertTrue("trening: więcej węgli", train.carbsG > rest.carbsG)
        assertTrue("trening: mniej tłuszczu", train.fatG < rest.fatG)
        assertEquals("kcal identyczne", train.kcal, rest.kcal)
        assertEquals("białko identyczne", train.proteinG, rest.proteinG)
    }

    @Test
    fun `carb cycling - null nie cykluje (plaskie makro = baza)`() {
        assertTrue("null = baza, tłuszcz > niż w dzień treningowy",
            maintainGoal(null).fatG > maintainGoal(true).fatG)
    }

    @Test
    fun `guardrail - niebezpiecznie niski cel kcal jest podniesiony do minimum`() {
        // manualny override 800 kcal < floor (BMR ~1780) → cap + warning
        val g = GoalPipeline.compute(profile(), manualKcalOverride = 800, latestMeasuredWeightKg = 80.0)
        assertTrue("cap zadziałał", g.wasCapped)
        assertTrue("kcal podniesione do bezpiecznego minimum", g.kcal >= 1500)
    }

    @Test
    fun `ENDURANCE wymusza min 5 g na kg wegli`() {
        val g = GoalPipeline.compute(profile(goal = DietGoalType.ENDURANCE), latestMeasuredWeightKg = 80.0)
        assertTrue("węgle ≥ 5 g/kg (≥400g dla 80kg)", g.carbsG >= 400)
    }

    @Test
    fun `makro sumuje sie do kcal (z tolerancja zaokraglen)`() {
        val g = goalFor()
        val fromMacros = g.proteinG * 4 + g.carbsG * 4 + g.fatG * 9
        assertEquals("suma makro ≈ kcal", g.kcal.toDouble(), fromMacros.toDouble(), 30.0)
    }
}
