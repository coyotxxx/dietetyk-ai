package pl.filebit.dietetyk.core.adapt

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.DietGoalType

/**
 * Port testów silnika korekt z GymTrackera — reguły „trend → małe kroki → wyjaśnij".
 * WeightTrend budowany wprost (izolacja od TrendAnalyzer).
 */
class CalorieAdjustmentEngineTest {

    private fun trend(
        direction: TrendDirection, slope: Double,
        stagnation: Boolean = false, fastLoss: Boolean = false, fastGain: Boolean = false,
        avg14: Double = 80.0, avg28: Double = 80.0
    ) = WeightTrend(
        sampleCount = 6, avg7Days = avg14, avg14Days = avg14, avg28Days = avg28,
        slopeKgPerWeek = slope, direction = direction,
        isStagnationLikely = stagnation, isFastLoss = fastLoss, isFastGain = fastGain
    )

    private val high = AdherenceSummary(sampleDays = 14, avgKcalPct = 100, avgProteinPct = 90, workoutsPlanned = 4, workoutsDone = 4)
    private val low = AdherenceSummary(sampleDays = 14, avgKcalPct = 50, avgProteinPct = 50, workoutsPlanned = 4, workoutsDone = 4)

    private val stagnant = trend(TrendDirection.FLAT, 0.0, stagnation = true)

    @Test
    fun `brak danych - NEEDS_MORE_DATA`() {
        val d = CalorieAdjustmentEngine.analyze(DietGoalType.FAT_LOSS, 2000, WeightTrend.NO_DATA, high)
        assertEquals(AdjustmentAction.NEEDS_MORE_DATA, d.action)
    }

    @Test
    fun `redukcja + stagnacja + wysoka adherence + treningi = obniz o 150`() {
        val d = CalorieAdjustmentEngine.analyze(DietGoalType.FAT_LOSS, 2000, stagnant, high)
        assertEquals(AdjustmentAction.DECREASE_KCAL, d.action)
        assertEquals(-150, d.kcalDeltaProposed)
        assertEquals(1850, d.newKcal)
    }

    @Test
    fun `redukcja + za szybki spadek = dodaj 150 (ochrona miesni)`() {
        val d = CalorieAdjustmentEngine.analyze(
            DietGoalType.FAT_LOSS, 2000, trend(TrendDirection.FALLING, -2.0, fastLoss = true), high)
        assertEquals(AdjustmentAction.INCREASE_KCAL, d.action)
        assertEquals(+150, d.kcalDeltaProposed)
    }

    @Test
    fun `redukcja + niska adherence = uprosc plan (nie tnij)`() {
        val d = CalorieAdjustmentEngine.analyze(DietGoalType.FAT_LOSS, 2000, stagnant, low)
        assertEquals(AdjustmentAction.SIMPLIFY_PLAN, d.action)
        assertEquals(0, d.kcalDeltaProposed)
    }

    @Test
    fun `redukcja + spada powoli = HOLD (plan dziala)`() {
        val d = CalorieAdjustmentEngine.analyze(
            DietGoalType.FAT_LOSS, 2000, trend(TrendDirection.FALLING, -0.5), high)
        assertEquals(AdjustmentAction.HOLD, d.action)
    }

    @Test
    fun `masa + stagnacja + treningi = dodaj 150`() {
        val d = CalorieAdjustmentEngine.analyze(DietGoalType.MUSCLE_GAIN, 3000, stagnant, high)
        assertEquals(AdjustmentAction.INCREASE_KCAL, d.action)
        assertEquals(+150, d.kcalDeltaProposed)
    }

    @Test
    fun `utrzymanie + za szybki przyrost = obniz o 100`() {
        val d = CalorieAdjustmentEngine.analyze(
            DietGoalType.MAINTAIN, 2500, trend(TrendDirection.RISING, 0.7, fastGain = true), high)
        assertEquals(AdjustmentAction.DECREASE_KCAL, d.action)
        assertEquals(-100, d.kcalDeltaProposed)
    }

    @Test
    fun `RECOMP mapuje sie na utrzymanie - stabilna waga = HOLD`() {
        val d = CalorieAdjustmentEngine.analyze(DietGoalType.RECOMP, 2400, trend(TrendDirection.FLAT, 0.0), high)
        assertEquals(AdjustmentAction.HOLD, d.action)
    }

    @Test
    fun `strategia z celu diety`() {
        assertEquals(CalorieStrategy.CUT, DietGoalType.FAT_LOSS.toCalorieStrategy())
        assertEquals(CalorieStrategy.CUT, DietGoalType.EVENT_PREP.toCalorieStrategy())
        assertEquals(CalorieStrategy.BULK, DietGoalType.MUSCLE_GAIN.toCalorieStrategy())
        assertEquals(CalorieStrategy.MAINTAIN, DietGoalType.HEALTH.toCalorieStrategy())
    }
}
