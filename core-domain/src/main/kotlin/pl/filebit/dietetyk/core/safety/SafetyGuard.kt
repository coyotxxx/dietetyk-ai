package pl.filebit.dietetyk.core.safety

import pl.filebit.dietetyk.core.model.Gender

/**
 * Hard-limity bezpieczeństwa dietetycznego — OSTATNIA linia obrony przed niebezpieczną dietą.
 *
 * Inspiracja: ISSN, ACE, EFSA — minimalne i maksymalne wartości dla zdrowego dorosłego
 * (bez stanów medycznych). Wszystkie funkcje są pure (testable) i deterministic.
 *
 * Przeniesione z GymTrackera. ZMIANA sygnatury vs oryginał: przyjmuje `Gender` zamiast encji Room
 * `UserProfile` (odcięcie od Androida — patrz ARCHITECTURE.md).
 */
object SafetyGuard {

    /**
     * Minimum kcal = max(absolutny floor, BMR). ISSN/RP: nigdy poniżej BMR.
     * @param bmrEstimate najświeższe oszacowanie BMR (lub null → sam absolutny floor).
     */
    fun minKcal(gender: Gender, bmrEstimate: Int? = null): Int {
        val absoluteFloor = if (gender == Gender.FEMALE) 1200 else 1500
        return if (bmrEstimate != null) maxOf(absoluteFloor, bmrEstimate) else absoluteFloor
    }

    /** Maksymalne kcal — bez sensu jeść więcej (efekty: rozstrój żołądka, niepotrzebny tłuszcz). */
    fun maxKcal(weightKg: Double): Int = (weightKg * 60).toInt()

    /**
     * Min białko = 1.4 g/kg dla AKTYWNYCH (≥3 dni treningu/tydz), 0.8 g/kg dla sedentary (WHO).
     * ISSN 2017: 1.4-2.0 g/kg dla sportowców jako MINIMUM (nie cel).
     */
    fun minProteinG(weightKg: Double, daysPerWeek: Int = 0): Int {
        val perKg = if (daysPerWeek >= 3) 1.4 else 0.8
        return (weightKg * perKg).toInt()
    }
    /** Max białko — przekroczenie nie daje korzyści, obciąża nerki przy diecie wysokobiałkowej. */
    fun maxProteinG(weightKg: Double): Int = (weightKg * 3.0).toInt()

    /** Min tłuszcz — zdrowie hormonalne (testosteron, estrogen). */
    fun minFatG(weightKg: Double): Int = (weightKg * 0.6).toInt()
    fun maxFatG(weightKg: Double): Int = (weightKg * 2.5).toInt()

    /** Max tempo redukcji (kg/tydz). >1.5 = ryzyko utraty mięśni i metabolic damage. */
    const val MAX_LOSS_KG_PER_WEEK = 1.5
    const val MAX_GAIN_KG_PER_WEEK = 0.5

    /**
     * Główna walidacja kcal: Pass / Warn (ryzyko) / Block (niebezpieczne — użyj cappedValue).
     */
    fun validateKcal(target: Int, gender: Gender, weightKg: Double, bmrEstimate: Int? = null): SafetyResult {
        val min = minKcal(gender, bmrEstimate)
        val max = maxKcal(weightKg)
        return when {
            target < min -> SafetyResult.Block(
                message = "Cel $target kcal poniżej bezpiecznego minimum ($min kcal). " +
                    "Zbyt niskie kalorie powodują utratę masy mięśniowej, problemy hormonalne, spadek energii." +
                    (if (bmrEstimate != null && min == bmrEstimate) " Limit = Twoje BMR ($bmrEstimate kcal)." else ""),
                cappedValue = min
            )
            target > max -> SafetyResult.Warn(
                message = "$target kcal to dużo. Trudne do zjedzenia bez śmieciowego jedzenia."
            )
            else -> SafetyResult.Pass
        }
    }

    fun validateProtein(g: Int, weightKg: Double, daysPerWeek: Int = 0): SafetyResult {
        val min = minProteinG(weightKg, daysPerWeek)
        val max = maxProteinG(weightKg)
        return when {
            g < min -> SafetyResult.Block(
                message = "Białko ${g}g poniżej bezpiecznego minimum (${min}g). Ryzyko utraty masy mięśniowej.",
                cappedValue = min
            )
            g > max -> SafetyResult.Warn(
                message = "Białko ${g}g powyżej rozsądnego maximum (${max}g, 3 g/kg). " +
                    "Brak dodatkowych korzyści, obciąża nerki przy długotrwałym stosowaniu."
            )
            else -> SafetyResult.Pass
        }
    }

    fun validateFat(g: Int, weightKg: Double): SafetyResult {
        val min = minFatG(weightKg)
        return when {
            g < min -> SafetyResult.Block(
                message = "Tłuszcz ${g}g poniżej bezpiecznego minimum (${min}g, 0.6 g/kg). " +
                    "Niezbędne dla syntezy hormonów (testosteron, estrogen).",
                cappedValue = min
            )
            else -> SafetyResult.Pass
        }
    }

    fun validateDeficitRate(weeklyKgChange: Double): SafetyResult {
        return when {
            weeklyKgChange < -MAX_LOSS_KG_PER_WEEK -> SafetyResult.Warn(
                message = "Tempo redukcji jest agresywne. Zalecane max 1.5 kg/tydz — szybsza utrata " +
                    "to ryzyko mięśni i metabolizmu."
            )
            weeklyKgChange > MAX_GAIN_KG_PER_WEEK -> SafetyResult.Warn(
                message = "Tempo przyrostu to dużo. Większość przyrostu = tłuszcz. Lean bulk = max 0.3-0.5 kg/tydz."
            )
            else -> SafetyResult.Pass
        }
    }
}

sealed class SafetyResult {
    /** OK, nic nie robimy. */
    object Pass : SafetyResult()
    /** Działa ale ostrzegamy. */
    data class Warn(val message: String) : SafetyResult()
    /** Blokujemy — używamy cappedValue zamiast oryginalnej wartości. */
    data class Block(val message: String, val cappedValue: Int) : SafetyResult()

    val isBlocking: Boolean get() = this is Block
    val warningMessage: String? get() = when (this) {
        is Warn -> message
        is Block -> message
        Pass -> null
    }
}
