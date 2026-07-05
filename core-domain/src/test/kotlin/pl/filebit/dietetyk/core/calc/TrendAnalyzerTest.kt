package pl.filebit.dietetyk.core.calc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.WeightSample

/**
 * Port testów TrendAnalyzer z GymTrackera — golden-referencja wartości (slope, kierunki, flagi).
 * Pinowane wartości pochodzą ze sprawdzonego produkcyjnie kodu → strażnik regresji przy porcie.
 */
class TrendAnalyzerTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun m(daysAgo: Int, weight: Double) = WeightSample(dateMs = now - daysAgo * day, weightKg = weight)

    /** 3 pomiary w poprzednim tygodniu + 3 w ostatnim. */
    private fun series(prevWeek: Double, recentWeek: Double) = listOf(
        m(12, prevWeek), m(10, prevWeek), m(8, prevWeek),
        m(5, recentWeek), m(3, recentWeek), m(1, recentWeek)
    )

    @Test
    fun `mniej niz 3 pomiary - NO_DATA`() {
        val t = TrendAnalyzer.analyze(listOf(m(2, 80.0), m(1, 79.0)), nowMs = now)
        assertEquals(TrendDirection.INSUFFICIENT_DATA, t.direction)
        assertFalse("za mało danych", t.hasEnoughData)
    }

    @Test
    fun `waga spada szybko - FALLING i isFastLoss`() {
        val t = TrendAnalyzer.analyze(series(prevWeek = 81.0, recentWeek = 79.0), nowMs = now)
        assertEquals("slope ~ −2 kg/tydz → FALLING", TrendDirection.FALLING, t.direction)
        assertTrue("< −1.5 → szybki spadek", t.isFastLoss)
    }

    @Test
    fun `waga stoi - FLAT i isStagnationLikely`() {
        val t = TrendAnalyzer.analyze(series(prevWeek = 80.0, recentWeek = 80.0), nowMs = now)
        assertEquals("brak zmiany → FLAT", TrendDirection.FLAT, t.direction)
        assertTrue("|slope| < 0.1 → stagnacja", t.isStagnationLikely)
        assertFalse("stagnacja to nie szybki spadek", t.isFastLoss)
    }

    @Test
    fun `waga rosnie szybko - RISING i isFastGain`() {
        val t = TrendAnalyzer.analyze(series(prevWeek = 80.0, recentWeek = 80.7), nowMs = now)
        assertEquals("slope > +0.1 → RISING", TrendDirection.RISING, t.direction)
        assertTrue("> 0.5 → szybki wzrost", t.isFastGain)
    }

    @Test
    fun `rowne gladkie chudniecie NIE jest early plateau`() {
        val data = (0..14).map { i -> m(28 - i * 2, 85.0 - i * (2.0 / 14)) } // 85→83 przez 28 dni
        val t = TrendAnalyzer.analyze(data, nowMs = now)
        assertEquals("realnie spada", TrendDirection.FALLING, t.direction)
        assertFalse("równe chudnięcie ~0.5 kg/tydz to NIE plateau", t.isEarlyPlateau)
    }

    @Test
    fun `rzadkie pomiary nie daja falszywego fast loss`() {
        val data = listOf(
            m(31, 87.0), m(29, 87.0), m(23, 86.6), m(20, 86.45),
            m(14, 86.35), m(10, 85.3), m(0, 84.25)  // tylko 1 pomiar w ostatnim tygodniu
        )
        val t = TrendAnalyzer.analyze(data, nowMs = now)
        assertFalse("regresja ~−1.0 kg/tydz NIE jest fast loss (<−1.5)", t.isFastLoss)
        assertEquals("kierunek FALLING", TrendDirection.FALLING, t.direction)
        assertTrue("slope w realnym przedziale (−1.3..−0.7)", t.slopeKgPerWeek!! in -1.3..-0.7)
    }
}
