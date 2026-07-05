package pl.filebit.dietetyk.core.model

/**
 * Wejścia deterministyczne dla silnika korekt i wizyty kontrolnej — liczone przez kod (`:data`),
 * NIGDY przez AI. Czyste DTO (bez Room/Android). Odpowiedniki z GymTrackera, okrojone do pól,
 * których faktycznie używa logika (bez metryk zegarkowych — dojdą, gdy będą potrzebne).
 */

/** Zgodność z planem w oknie (adherence). Procenty względem celu (100 = idealnie). */
data class AdherenceSummary(
    val sampleDays: Int = 0,
    val avgKcalPct: Int = 0,
    val avgProteinPct: Int = 0,
    val avgCarbsPct: Int = 0,
    val avgFatPct: Int = 0,
    val workoutsPlanned: Int = 0,
    val workoutsDone: Int = 0
)

/** Subiektywna regeneracja (sen/stres/głód/energia/soreness/trudność) — średnie z okna + progi. */
data class RecoverySnapshot(
    val sampleDays: Int = 0,
    val avgSleepHours: Double? = null,
    val avgStress: Double? = null,
    val avgHunger: Double? = null,
    val avgEnergy: Double? = null,
    val avgSoreness: Double? = null,
    val avgDifficulty: Double? = null,
    /** avgSleep <6h lub słaba jakość snu. */
    val badSleep: Boolean = false,
    /** avgStress ≥4. */
    val highStress: Boolean = false,
    /** avgHunger ≥4. */
    val highHunger: Boolean = false,
    /** avgEnergy ≤2. */
    val lowEnergy: Boolean = false,
    /** avgSoreness ≥4. */
    val highSoreness: Boolean = false,
    /** avgDifficulty (trzymanie planu) ≥4. */
    val highDifficulty: Boolean = false
) {
    val hasEnoughData: Boolean get() = sampleDays >= 3

    companion object { val EMPTY = RecoverySnapshot() }
}

/** NEAT — aktywność spontaniczna (kroki). Spadek kroków ≠ spadek metabolizmu. */
data class NeatSnapshot(
    val avg14dSteps: Int = 0,
    val avg30dSteps: Int = 0
) {
    val hasEnoughData: Boolean get() = avg30dSteps > 0
    /** Kroki 14d spadły >30% względem 30d. */
    val significantStepsDrop: Boolean
        get() = avg30dSteps > 0 && avg14dSteps < avg30dSteps * 7 / 10

    companion object { val EMPTY = NeatSnapshot() }
}
