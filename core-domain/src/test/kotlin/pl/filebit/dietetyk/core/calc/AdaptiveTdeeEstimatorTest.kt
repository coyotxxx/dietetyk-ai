package pl.filebit.dietetyk.core.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.WeightSample

class AdaptiveTdeeEstimatorTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L
    private val formula = 2759  // Mifflin baseline (jak w GoalPipelineTest)

    private fun logs(count: Int, complete: Boolean = true, kcal: (Int) -> Int) =
        (1..count).map { DailyEnergyLog(dateMs = now - it * day, kcalConsumed = kcal(it), isComplete = complete) }

    @Test
    fun `za malo dni kompletnych - tylko wzor + prosba o logi`() {
        val est = AdaptiveTdeeEstimator.estimate(
            formulaTdeeKcal = formula,
            intakeLogs = logs(5) { 2200 },
            weightSamples = listOf(WeightSample(now - 14 * day, 80.4), WeightSample(now, 79.0)),
            nowMs = now
        )
        assertEquals(TdeeSource.FORMULA_ONLY, est.source)
        assertEquals("używa wzoru", formula, est.tdeeKcal)
        assertNull(est.measuredTdeeKcal)
        assertEquals("brakuje 3 dni do 8", 3, est.daysNeeded)
        assertEquals(0.0, est.confidence, 0.0001)
    }

    @Test
    fun `dosc danych i staly spadek wagi - blend wzoru z pomiarem`() {
        val est = AdaptiveTdeeEstimator.estimate(
            formulaTdeeKcal = formula,
            intakeLogs = logs(10) { 2200 },
            // −1.4 kg przez 14 dni ⇒ −0.7 kg/tydz ⇒ measured = 2200 + 0.7×1100 = 2970
            weightSamples = listOf(WeightSample(now - 14 * day, 80.4), WeightSample(now, 79.0)),
            nowMs = now
        )
        assertEquals(TdeeSource.ADAPTIVE_BLENDED, est.source)
        assertEquals("measured ≈ 2970", 2970.0, est.measuredTdeeKcal!!.toDouble(), 20.0)
        assertTrue("ufność w (0.4, 0.7)", est.confidence in 0.4..0.7)
        assertTrue("blend między wzorem a pomiarem",
            est.tdeeKcal in formula..est.measuredTdeeKcal!!)
        assertEquals("do pełnej ufności brakuje 2 dni", 2, est.daysNeeded)
    }

    @Test
    fun `brak pomiarow wagi - nie liczy metabolizmu, prosi o wage`() {
        val est = AdaptiveTdeeEstimator.estimate(
            formulaTdeeKcal = formula,
            intakeLogs = logs(10) { 2200 },
            weightSamples = emptyList(),
            nowMs = now
        )
        assertEquals(TdeeSource.FORMULA_ONLY, est.source)
        assertNull(est.measuredTdeeKcal)
        assertTrue("komunikat prosi o wagę", est.note.contains("wag"))
    }

    @Test
    fun `dziki rozrzut spozycia obniza ufnosc`() {
        val steady = AdaptiveTdeeEstimator.estimate(
            formula, logs(10) { 2500 },
            listOf(WeightSample(now - 14 * day, 80.0), WeightSample(now, 80.0)), now
        )
        val wild = AdaptiveTdeeEstimator.estimate(
            formula, logs(10) { i -> if (i % 2 == 0) 3800 else 1200 },
            listOf(WeightSample(now - 14 * day, 80.0), WeightSample(now, 80.0)), now
        )
        assertTrue("rozrzut 1200↔3800 daje niższą ufność niż stabilne 2500",
            wild.confidence < steady.confidence)
    }

    @Test
    fun `stabilna waga - measured blisko sredniego spozycia`() {
        // waga płaska ⇒ TDEE ≈ spożycie
        val est = AdaptiveTdeeEstimator.estimate(
            formula, logs(12) { 2600 },
            listOf(WeightSample(now - 14 * day, 80.0), WeightSample(now, 80.0)), now
        )
        assertEquals("measured ≈ spożycie przy płaskiej wadze", 2600.0, est.measuredTdeeKcal!!.toDouble(), 20.0)
    }
}
