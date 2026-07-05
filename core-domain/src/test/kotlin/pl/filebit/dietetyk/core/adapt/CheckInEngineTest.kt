package pl.filebit.dietetyk.core.adapt

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.MedicalCondition
import pl.filebit.dietetyk.core.model.NutritionProfile

class CheckInEngineTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private fun profile(goal: DietGoalType = DietGoalType.FAT_LOSS) =
        NutritionProfile(Gender.MALE, 30, 180, 80.0, goal = goal)
    private val high = AdherenceSummary(sampleDays = 14, avgKcalPct = 100, avgProteinPct = 90, workoutsPlanned = 4, workoutsDone = 4)
    private fun intake(count: Int, kcal: Int) = (1..count).map { DailyEnergyLog(now - it * day, kcal, true) }

    private val stagnant = WeightTrend(
        sampleCount = 6, avg7Days = 80.0, avg14Days = 80.0, avg28Days = 80.0, slopeKgPerWeek = 0.0,
        direction = TrendDirection.FLAT, isStagnationLikely = true, isFastLoss = false, isFastGain = false
    )

    @Test
    fun `red flag jest nadrzedny - wizyta konczy sie skierowaniem do lekarza`() {
        val r = CheckInEngine.run(
            profile(), ClinicalContext(setOf(MedicalCondition.EATING_DISORDER)),
            currentKcal = 2000, weightTrend = stagnant, adherence14d = high, recentIntake = intake(7, 2000)
        )
        assertEquals(CheckInVerdict.REFER_TO_DOCTOR, r.verdict)
        assertEquals("guardrail nie zmienia kcal", 0, r.kcalDelta)
    }

    @Test
    fun `redukcja + stagnacja = wizyta proponuje korekte kcal`() {
        val r = CheckInEngine.run(
            profile(), currentKcal = 2000, weightTrend = stagnant, adherence14d = high, recentIntake = intake(7, 2000))
        assertEquals(CheckInVerdict.ADJUST_KCAL, r.verdict)
        assertEquals(-150, r.kcalDelta)
        assertEquals(1850, r.newKcal)
    }

    @Test
    fun `plan dziala - wizyta trzyma kurs`() {
        val falling = stagnant.copy(direction = TrendDirection.FALLING, slopeKgPerWeek = -0.5, isStagnationLikely = false)
        val r = CheckInEngine.run(
            profile(), currentKcal = 2000, weightTrend = falling, adherence14d = high, recentIntake = intake(7, 2000))
        assertEquals(CheckInVerdict.HOLD, r.verdict)
    }

    @Test
    fun `za malo danych - wizyta prosi o wiecej`() {
        val r = CheckInEngine.run(
            profile(), currentKcal = 2000, weightTrend = WeightTrend.NO_DATA, adherence14d = AdherenceSummary(sampleDays = 2))
        assertEquals(CheckInVerdict.NEEDS_MORE_DATA, r.verdict)
    }
}
