package pl.filebit.dietetyk.core.safety

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.Gender

/**
 * Port testów SafetyGuard z GymTrackera — golden-referencja.
 * Ostatnia bramka: po decyzji silnika kcal NIGDY nie spada poniżej bezpiecznego minimum.
 */
class SafetyGuardTest {

    @Test
    fun `floor kcal zalezy od plci`() {
        assertEquals("mężczyzna: floor 1500 kcal", 1500, SafetyGuard.minKcal(Gender.MALE))
        assertEquals("kobieta: floor 1200 kcal", 1200, SafetyGuard.minKcal(Gender.FEMALE))
    }

    @Test
    fun `zbyt niskie kcal sa blokowane z capped value`() {
        val r = SafetyGuard.validateKcal(target = 1000, gender = Gender.MALE, weightKg = 80.0)
        assertTrue("1000 kcal < 1500 floor → Block", r is SafetyResult.Block)
        assertEquals("capped do bezpiecznego minimum", 1500, (r as SafetyResult.Block).cappedValue)
    }

    @Test
    fun `rozsadne kcal przechodza`() {
        val r = SafetyGuard.validateKcal(target = 2500, gender = Gender.MALE, weightKg = 80.0)
        assertEquals("2500 kcal dla 80 kg → Pass", SafetyResult.Pass, r)
    }

    @Test
    fun `BMR jako floor gdy wyzszy od absolutnego minimum`() {
        val r = SafetyGuard.validateKcal(
            target = 1600, gender = Gender.MALE, weightKg = 80.0, bmrEstimate = 1800)
        assertTrue("1600 < BMR 1800 → Block", r is SafetyResult.Block)
        assertEquals("floor podniesiony do BMR", 1800, (r as SafetyResult.Block).cappedValue)
    }

    @Test
    fun `ekstremalnie wysokie kcal daja ostrzezenie`() {
        // maxKcal(80 kg) = 4800; 6000 > max → Warn
        val r = SafetyGuard.validateKcal(target = 6000, gender = Gender.MALE, weightKg = 80.0)
        assertTrue("6000 kcal powyżej max → Warn", r is SafetyResult.Warn)
    }
}
