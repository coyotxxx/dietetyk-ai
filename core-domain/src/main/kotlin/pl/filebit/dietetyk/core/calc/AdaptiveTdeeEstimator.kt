package pl.filebit.dietetyk.core.calc

import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.WeightSample
import kotlin.math.sqrt

/** Skąd pochodzi użyta wartość TDEE. */
enum class TdeeSource {
    FORMULA_ONLY,       // za mało danych → wzór Mifflin (wstępny)
    ADAPTIVE_BLENDED,   // mieszanka wzoru i realnego pomiaru (ufność 0..~0.7)
    MEASURED_DOMINANT   // pomiar dominuje (ufność ≥0.7)
}

data class TdeeEstimate(
    /** Wartość do użycia (blend wzoru i pomiaru ważony ufnością). */
    val tdeeKcal: Int,
    val source: TdeeSource,
    /** 0..1 — jak bardzo ufamy pomiarowi. */
    val confidence: Double,
    /** Czysta estymata z realnego spożycia i trendu wagi (null gdy za mało danych). */
    val measuredTdeeKcal: Int?,
    /** Wartość wzorowa (Mifflin) — baseline. */
    val formulaTdeeKcal: Int,
    val completeDays: Int,
    /** Ile jeszcze dni KOMPLETNYCH trzeba zalogować do sensownej kalibracji (0 = mamy dość). */
    val daysNeeded: Int,
    /** Komunikat dla AI do przekazania userowi po ludzku. */
    val note: String
)

/**
 * Adaptacyjny TDEE — kalibruje zapotrzebowanie do REALNEGO metabolizmu usera, nie do wzoru.
 *
 * Zasada (decyzja Claude+Fable, ARCHITECTURE.md §4): PRÓBKA, nie ciągłość. Nie wymaga codziennych
 * logów — wystarczy dość dni KOMPLETNYCH w oknie + wiarygodny sygnał wagi. Fizyka, nie formuła:
 *   TDEE ≈ średnie spożycie − (Δmasa_trendowa × 7700) / dni  ⟺  avgIntake − slopeKgTydz × 1100.
 *
 * Ufność jest CIĄGŁA (0..1), więc adaptacja włącza się STOPNIOWO. Luka danych jest komunikowana
 * jako zadanie dla usera (`daysNeeded`), nie jako cicha porażka.
 */
object AdaptiveTdeeEstimator {

    private const val WINDOW_DAYS = 14
    private const val WEIGHT_WINDOW_DAYS = 21   // wagi bywają rzadsze — szersze okno na trend
    private const val MIN_COMPLETE_DAYS = 8
    private const val FULL_CONFIDENCE_DAYS = 12
    private const val KCAL_PER_KG_WEEK = 1100.0 // 7700 kcal/kg ÷ 7 dni
    private const val DAY_MS = 24L * 3600 * 1000

    fun estimate(
        formulaTdeeKcal: Int,
        intakeLogs: List<DailyEnergyLog>,
        weightSamples: List<WeightSample>,
        nowMs: Long = System.currentTimeMillis()
    ): TdeeEstimate {
        val complete = intakeLogs
            .filter { it.isComplete && it.kcalConsumed > 0 && it.dateMs >= nowMs - WINDOW_DAYS * DAY_MS }
            .sortedBy { it.dateMs }
        val completeDays = complete.size

        // Za mało dni kompletnych → tylko wzór, poproś usera o dalsze logi.
        if (completeDays < MIN_COMPLETE_DAYS) {
            val needed = MIN_COMPLETE_DAYS - completeDays
            return TdeeEstimate(
                tdeeKcal = formulaTdeeKcal, source = TdeeSource.FORMULA_ONLY, confidence = 0.0,
                measuredTdeeKcal = null, formulaTdeeKcal = formulaTdeeKcal,
                completeDays = completeDays, daysNeeded = needed,
                note = "Mam $completeDays z $MIN_COMPLETE_DAYS potrzebnych dni pełnych logów — " +
                    "zaloguj jeszcze $needed kompletne, a przeliczę Twoje realne zapotrzebowanie."
            )
        }

        // Sygnał wagi: trend (slope kg/tydz) z pomiarów w oknie wagowym. Potrzeba ≥2 punktów.
        val weightPts = weightSamples
            .filter { it.weightKg > 0 && it.dateMs >= nowMs - WEIGHT_WINDOW_DAYS * DAY_MS }
            .sortedBy { it.dateMs }
        val slopeKgPerWeek = leastSquaresSlopeKgPerWeek(weightPts)
        if (slopeKgPerWeek == null) {
            return TdeeEstimate(
                tdeeKcal = formulaTdeeKcal, source = TdeeSource.FORMULA_ONLY, confidence = 0.0,
                measuredTdeeKcal = null, formulaTdeeKcal = formulaTdeeKcal,
                completeDays = completeDays, daysNeeded = 0,
                note = "Mam Twoje logi jedzenia, ale za mało pomiarów wagi, żeby policzyć realne " +
                    "zapotrzebowanie. Zważ się kilka razy w tym tygodniu."
            )
        }

        val intakes = complete.map { it.kcalConsumed }
        val avgIntake = intakes.average()
        val measuredTdee = (avgIntake - slopeKgPerWeek * KCAL_PER_KG_WEEK).toInt()

        // Ufność ciągła: baza z liczby dni (8→0.4, 12+→0.8) × kara za rozrzut spożycia × kara za rozpiętość wagi.
        val base = 0.4 + 0.4 * ((completeDays - MIN_COMPLETE_DAYS).coerceIn(0, FULL_CONFIDENCE_DAYS - MIN_COMPLETE_DAYS) /
            (FULL_CONFIDENCE_DAYS - MIN_COMPLETE_DAYS).toDouble())
        val cv = coefficientOfVariation(intakes)
        // rozrzut spożycia: cv≤0.20 → brak kary; cv≥0.50 → ×0.5 (np. dni 1200↔4000)
        val cvFactor = 1.0 - ((cv - 0.20).coerceIn(0.0, 0.30) / 0.30) * 0.5
        // rozpiętość pomiarów wagi <7 dni → słabszy trend
        val weightSpanDays = (weightPts.last().dateMs - weightPts.first().dateMs) / DAY_MS
        val weightFactor = if (weightSpanDays < 7) 0.6 else 1.0
        val confidence = (base * cvFactor * weightFactor).coerceIn(0.0, 0.85)

        val blended = (formulaTdeeKcal * (1 - confidence) + measuredTdee * confidence).toInt()
        val source = if (confidence >= 0.7) TdeeSource.MEASURED_DOMINANT else TdeeSource.ADAPTIVE_BLENDED
        val daysNeeded = if (confidence < 0.8) (FULL_CONFIDENCE_DAYS - completeDays).coerceAtLeast(0) else 0

        val note = buildString {
            append("Twoje realne zapotrzebowanie z pomiarów: $measuredTdee kcal")
            append(" (wzór szacował $formulaTdeeKcal). Pewność ${(confidence * 100).toInt()}%.")
            if (daysNeeded > 0) append(" Jeszcze $daysNeeded pełnych dni podniesie dokładność.")
        }

        return TdeeEstimate(
            tdeeKcal = blended, source = source, confidence = confidence,
            measuredTdeeKcal = measuredTdee, formulaTdeeKcal = formulaTdeeKcal,
            completeDays = completeDays, daysNeeded = daysNeeded, note = note
        )
    }

    /** Slope kg/tydz przez regresję liniową (≥2 punkty, niezerowa wariancja czasu). */
    private fun leastSquaresSlopeKgPerWeek(pts: List<WeightSample>): Double? {
        if (pts.size < 2) return null
        val x0 = pts.first().dateMs
        val xs = pts.map { (it.dateMs - x0) / DAY_MS.toDouble() }  // dni
        val ys = pts.map { it.weightKg }
        val mx = xs.average(); val my = ys.average()
        var cov = 0.0; var varx = 0.0
        for (i in xs.indices) { val dx = xs[i] - mx; cov += dx * (ys[i] - my); varx += dx * dx }
        if (varx == 0.0) return null
        return (cov / varx) * 7.0
    }

    private fun coefficientOfVariation(values: List<Int>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        if (mean == 0.0) return 0.0
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }
}
