package pl.filebit.dietetyk.core.calc

import pl.filebit.dietetyk.core.model.WeightSample
import kotlin.math.abs

enum class TrendDirection {
    INSUFFICIENT_DATA,
    FALLING,        // waga maleje (np. redukcja idzie dobrze)
    FLAT,           // ±0.1 kg/tydz — stagnacja
    RISING          // waga rośnie (masa / niezamierzony przyrost)
}

data class WeightTrend(
    val sampleCount: Int,
    val avg7Days: Double?,
    val avg14Days: Double?,
    val avg28Days: Double?,
    /** Tempo zmiany w kg/tydz (slope dopasowany liniowo). */
    val slopeKgPerWeek: Double?,
    val direction: TrendDirection,
    val isStagnationLikely: Boolean,    // 14d slope w przedziale [-0.1, +0.1]
    val isFastLoss: Boolean,            // <-1.5 kg/tydz
    val isFastGain: Boolean,            // >+0.5 kg/tydz
    /** Wczesny sygnał plateau — 7 dni bez wyraźnej zmiany (|slope|<0.1). Wymaga ≥3 wpisów w 7 dniach. */
    val isEarlyPlateau: Boolean = false,
    /** Ile dni waga "stoi" (od ostatniego wpisu z różnicą ≥0.2 kg vs aktualna). */
    val daysWithoutProgress: Int = 0
) {
    val hasEnoughData: Boolean get() = direction != TrendDirection.INSUFFICIENT_DATA

    companion object {
        val NO_DATA = WeightTrend(
            sampleCount = 0, avg7Days = null, avg14Days = null, avg28Days = null,
            slopeKgPerWeek = null, direction = TrendDirection.INSUFFICIENT_DATA,
            isStagnationLikely = false, isFastLoss = false, isFastGain = false,
            isEarlyPlateau = false, daysWithoutProgress = 0
        )
    }
}

/**
 * Analizuje pomiary wagi w czasie. Pure function — testowalne, deterministyczne.
 * Przeniesione z GymTrackera (slope przez regresję liniową — odporny na nierówne odstępy pomiarów).
 *
 * Wejście: lista [WeightSample] (data + waga). Liczy średnie 7/14/28 dni, slope (kg/tydz),
 * kierunek, flagi tempa i wczesny plateau.
 */
object TrendAnalyzer {

    fun analyze(measurements: List<WeightSample>, nowMs: Long = System.currentTimeMillis()): WeightTrend {
        val withWeight = measurements
            .filter { it.weightKg > 0 }
            .sortedBy { it.dateMs }
        if (withWeight.size < 3) return WeightTrend.NO_DATA

        val ms7d = 7L * 24 * 3600 * 1000
        val ms14d = 14L * 24 * 3600 * 1000
        val ms28d = 28L * 24 * 3600 * 1000

        fun avgWindow(windowMs: Long): Double? {
            val window = withWeight.filter { it.dateMs >= nowMs - windowMs }
                .map { it.weightKg }
            return window.takeIf { it.isNotEmpty() }?.average()
        }

        val avg7 = avgWindow(ms7d)
        val avg14 = avgWindow(ms14d)
        val avg28 = avgWindow(ms28d)

        // Slope przez REGRESJĘ LINIOWĄ (kg/tydz) — używa WSZYSTKICH punktów w oknie, odporny na
        // nierówne odstępy. Stara metoda 'recent.avg - previous.avg' zawyżała tempo przy rzadkich pomiarach.
        fun slopePerWeek(windowMs: Long): Double? {
            val pts = withWeight.filter { it.dateMs >= nowMs - windowMs }
            if (pts.size < 2) return null
            val x0 = pts.first().dateMs
            val xs = pts.map { (it.dateMs - x0) / (24.0 * 3600 * 1000) }  // dni
            val ys = pts.map { it.weightKg }
            if (ys.size != xs.size) return null
            val mx = xs.average(); val my = ys.average()
            var cov = 0.0; var varx = 0.0
            for (i in xs.indices) { val dx = xs[i] - mx; cov += dx * (ys[i] - my); varx += dx * dx }
            if (varx == 0.0) return null
            return (cov / varx) * 7.0  // kg/dzień → kg/tydz
        }
        // Okno 14 dni (recency); fallback 28 dni gdy w 14d <2 pomiary.
        val slope = slopePerWeek(ms14d) ?: slopePerWeek(ms28d)

        val recentWeek = withWeight.filter { it.dateMs >= nowMs - ms7d }.map { it.weightKg }

        val direction = when {
            slope == null -> TrendDirection.INSUFFICIENT_DATA
            slope < -0.1 -> TrendDirection.FALLING
            slope > 0.1 -> TrendDirection.RISING
            else -> TrendDirection.FLAT
        }

        val isStagnation = slope != null && abs(slope) < 0.1
        val isFastLoss = slope != null && slope < -1.5
        val isFastGain = slope != null && slope > 0.5

        // Wczesny plateau — 7 dni bez wyraźnej zmiany. Wymaga ≥3 pomiarów w 7 dniach.
        // Dodatkowo slope > -0.2, żeby NIE mylić równego, gładkiego chudnięcia z plateau.
        val recent7 = recentWeek
        val isEarlyPlateau = recent7.size >= 3 &&
            (recent7.max() - recent7.min()) < 0.5 &&
            slope != null && slope > -0.2

        // daysWithoutProgress: liczba dni od ostatniej zmiany ≥0.2 kg
        val latest = withWeight.last().weightKg
        val lastChange = withWeight.findLast { w -> abs(w.weightKg - latest) >= 0.2 }
        val daysWithoutProgress = if (lastChange != null) {
            val daysDiff = ((withWeight.last().dateMs - lastChange.dateMs) / (24L * 3600 * 1000)).toInt()
            daysDiff.coerceAtLeast(0)
        } else 0

        return WeightTrend(
            sampleCount = withWeight.size,
            avg7Days = avg7,
            avg14Days = avg14,
            avg28Days = avg28,
            slopeKgPerWeek = slope,
            direction = direction,
            isStagnationLikely = isStagnation,
            isFastLoss = isFastLoss,
            isFastGain = isFastGain,
            isEarlyPlateau = isEarlyPlateau,
            daysWithoutProgress = daysWithoutProgress
        )
    }
}
