package pl.filebit.dietetyk.core.calc

import pl.filebit.dietetyk.core.model.Gender
import kotlin.math.log10
import kotlin.math.round

/** Metoda użyta do oszacowania tkanki tłuszczowej (od najdokładniejszej). */
enum class BodyFatMethod(val label: String) {
    /** US Navy — z obwodów (talia, kark, +biodra u kobiet) + wzrost. Najbliżej wagi impedancyjnej. */
    NAVY("US Navy (obwody)"),
    /** Deurenberg — z BMI + wiek + płeć. Fallback gdy brak obwodów. */
    DEURENBERG("Deurenberg (BMI)")
}

data class BodyFatEstimate(val percent: Double, val method: BodyFatMethod)

/**
 * Szacowanie % tkanki tłuszczowej z danych, które apka już ma, gdy user nie wpisał pomiaru
 * z wagi impedancyjnej. Realna wpisana wartość ZAWSZE ma pierwszeństwo — estymata jest tylko
 * fallbackiem (oznaczanym w UI jako „~ szac.").
 *
 * Kolejność: US Navy (dokładniejszy, gdy są obwody) → Deurenberg (z BMI). Czysta funkcja.
 * Przeniesione z GymTrackera v2.69.0.
 */
object BodyFatEstimator {

    fun estimate(
        weightKg: Double?,
        heightCm: Int,
        ageYears: Int,
        gender: Gender,
        waistCm: Double? = null,
        neckCm: Double? = null,
        hipsCm: Double? = null
    ): BodyFatEstimate? {
        navy(heightCm, gender, waistCm, neckCm, hipsCm)?.let { return it }
        return deurenberg(weightKg, heightCm, ageYears, gender)
    }

    /** US Navy body fat (jednostki metryczne, log10). Wymaga talii+karku (+bioder u kobiet) i wzrostu. */
    private fun navy(
        heightCm: Int, gender: Gender, waistCm: Double?, neckCm: Double?, hipsCm: Double?
    ): BodyFatEstimate? {
        val h = heightCm.toDouble()
        if (h <= 0 || waistCm == null || neckCm == null) return null
        val pct = when (gender) {
            Gender.MALE -> {
                val d = waistCm - neckCm
                if (d <= 0) return null
                495.0 / (1.0324 - 0.19077 * log10(d) + 0.15456 * log10(h)) - 450.0
            }
            Gender.FEMALE -> {
                if (hipsCm == null) return null
                val d = waistCm + hipsCm - neckCm
                if (d <= 0) return null
                495.0 / (1.29579 - 0.35004 * log10(d) + 0.22100 * log10(h)) - 450.0
            }
        }
        return pct.takeIf { it.isFinite() && it in 2.0..70.0 }
            ?.let { BodyFatEstimate(round1(it), BodyFatMethod.NAVY) }
    }

    /** Deurenberg (1991): BF% = 1.20·BMI + 0.23·wiek − 10.8·płeć(M=1,K=0) − 5.4. */
    private fun deurenberg(
        weightKg: Double?, heightCm: Int, ageYears: Int, gender: Gender
    ): BodyFatEstimate? {
        if (weightKg == null || weightKg <= 0 || heightCm <= 0) return null
        val hM = heightCm / 100.0
        val bmi = weightKg / (hM * hM)
        val sex = if (gender == Gender.MALE) 1 else 0
        val pct = 1.20 * bmi + 0.23 * ageYears - 10.8 * sex - 5.4
        return pct.takeIf { it.isFinite() && it in 2.0..70.0 }
            ?.let { BodyFatEstimate(round1(it), BodyFatMethod.DEURENBERG) }
    }

    private fun round1(x: Double): Double = round(x * 10) / 10.0
}
