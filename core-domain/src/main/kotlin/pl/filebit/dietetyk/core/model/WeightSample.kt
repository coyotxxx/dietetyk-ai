package pl.filebit.dietetyk.core.model

/**
 * Pojedynczy pomiar wagi w czasie — czyste wejście dla [pl.filebit.dietetyk.core.calc.TrendAnalyzer].
 *
 * Zastępuje androidowe `BodyMeasurement` (Room) w warstwie core; `:data` mapuje swoje pomiary
 * na tę parę (data + waga). `dateMs` = epoch millis.
 */
data class WeightSample(
    val dateMs: Long,
    val weightKg: Double,
    val waistCm: Double? = null,
    val bodyFatPct: Double? = null
)
