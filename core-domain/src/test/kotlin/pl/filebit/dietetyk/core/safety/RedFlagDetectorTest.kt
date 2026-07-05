package pl.filebit.dietetyk.core.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.MedicalCondition
import pl.filebit.dietetyk.core.model.NutritionProfile

class RedFlagDetectorTest {

    private val healthy = NutritionProfile(Gender.MALE, 30, 180, 80.0)  // BMI 24.7
    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun trend(slope: Double, fastLoss: Boolean = false) = WeightTrend(
        sampleCount = 6, avg7Days = 80.0, avg14Days = 80.0, avg28Days = 80.0,
        slopeKgPerWeek = slope, direction = if (slope < -0.1) TrendDirection.FALLING else TrendDirection.FLAT,
        isStagnationLikely = false, isFastLoss = fastLoss, isFastGain = false
    )

    private fun intake(count: Int, kcal: Int) =
        (1..count).map { DailyEnergyLog(now - it * day, kcal, true) }

    @Test
    fun `zdrowy profil bez wzorcow alarmowych - brak skierowania`() {
        val r = RedFlagDetector.detect(healthy, ClinicalContext.NONE, trend(-0.4), intake(7, 2000))
        assertTrue(r.flags.isEmpty())
        assertFalse(r.refer)
    }

    @Test
    fun `niebezpiecznie szybka utrata wagi - skieruj do lekarza`() {
        val r = RedFlagDetector.detect(healthy, ClinicalContext.NONE, trend(-2.5, fastLoss = true), intake(7, 2000))
        assertTrue(RedFlag.RAPID_WEIGHT_LOSS in r.flags)
        assertTrue(r.refer)
    }

    @Test
    fun `dni ponizej bezpiecznego minimum kcal - skieruj`() {
        // floor mężczyzny = 1500; 5 dni po 1200
        val r = RedFlagDetector.detect(healthy, ClinicalContext.NONE, trend(-0.5), intake(5, 1200))
        assertTrue(RedFlag.PROLONGED_UNDEREATING in r.flags)
        assertTrue(r.refer)
    }

    @Test
    fun `zadeklarowane zaburzenia odzywiania - skieruj (nadrzedne)`() {
        val r = RedFlagDetector.detect(
            healthy, ClinicalContext(setOf(MedicalCondition.EATING_DISORDER)), trend(-0.4), intake(7, 2000))
        assertTrue(RedFlag.MEDICAL_CRITICAL in r.flags)
        assertTrue(r.refer)
        assertTrue(r.message.contains("lekarz") || r.message.contains("specjalist"))
    }

    @Test
    fun `bardzo niska masa ciala (BMI pod 16) - skieruj`() {
        val skinny = NutritionProfile(Gender.MALE, 30, 180, 45.0)  // BMI 13.9
        val r = RedFlagDetector.detect(skinny, ClinicalContext.NONE, trend(-0.3), intake(7, 2000))
        assertTrue(r.refer)
    }
}
