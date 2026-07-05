package pl.filebit.dietetyk.core.adapt

import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.calc.WeightTrend
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.NeatSnapshot
import pl.filebit.dietetyk.core.model.RecoverySnapshot

enum class AdjustmentAction {
    HOLD,               // zostaw jak jest — plan działa
    DECREASE_KCAL,      // obniż kcal (np. waga stoi w redukcji)
    INCREASE_KCAL,      // dodaj kcal (np. waga nie rośnie na masie / za szybki spadek)
    DELOAD,             // tydzień regeneracji
    SIMPLIFY_PLAN,      // niska adherence — uprość plan zamiast zmieniać kcal
    REFEED_DAY,         // 1 dzień +200-400 kcal głównie z węgli (regeneracja) / diet break
    NEEDS_MORE_DATA     // za mało wagi/dni do decyzji
}

enum class Confidence { LOW, MEDIUM, HIGH }

data class AdjustmentDecision(
    val action: AdjustmentAction,
    val kcalDeltaProposed: Int,
    val newKcal: Int,
    val reason: String,        // dla loga
    val explanation: String,   // dla usera (po ludzku)
    val warnings: List<String> = emptyList(),
    val confidence: Confidence
)

/** Kierunek strategii kalorycznej — 8 typów [DietGoalType] mapuje się na 3. */
enum class CalorieStrategy { CUT, BULK, MAINTAIN }

fun DietGoalType.toCalorieStrategy(): CalorieStrategy = when (this) {
    DietGoalType.FAT_LOSS, DietGoalType.EVENT_PREP -> CalorieStrategy.CUT
    DietGoalType.MUSCLE_GAIN -> CalorieStrategy.BULK
    // RECOMP/STRENGTH/HEALTH/ENDURANCE celują w stabilną wagę → monitorujemy jak utrzymanie.
    DietGoalType.RECOMP, DietGoalType.MAINTAIN, DietGoalType.STRENGTH,
    DietGoalType.HEALTH, DietGoalType.ENDURANCE -> CalorieStrategy.MAINTAIN
}

/**
 * Silnik regułowy automatycznej korekty kcal. Filozofia: „nie zgaduj → mierz → analizuj trend →
 * koryguj MAŁYMI krokami → wyjaśnij". Każda decyzja ma wyjaśnienie po ludzku (AI je KOMUNIKUJE).
 *
 * Przeniesione z GymTrackera. Zmiany vs oryginał: wejście `goal: DietGoalType` zamiast encji Room
 * `UserProfile`+`dietGoal` (JEDEN cel, usunięty fallback `WeightGoalType`); usunięty nieużywany
 * `adherence7d`. Wszystkie liczby pochodzą z deterministycznych wejść, nigdy z AI.
 */
object CalorieAdjustmentEngine {

    fun analyze(
        goal: DietGoalType,
        currentKcal: Int,
        weightTrend: WeightTrend,
        adherence14d: AdherenceSummary,
        recovery: RecoverySnapshot = RecoverySnapshot.EMPTY,
        hydrationAdherencePct: Int = 100,
        neat: NeatSnapshot = NeatSnapshot.EMPTY,
        /** Dni od początku obecnej redukcji. ≥56 + brak refeed → propose diet break (ISSN/Helms). */
        cutDurationDays: Int = 0,
        /** Dni od ostatniego refeed/diet break. Domyślnie 999 (brak). */
        daysSinceLastRefeed: Int = 999,
        /** Liczba ukończonych treningów w 14 dniach (pewne źródło). null = brak danych → fallback do adherence. */
        realWorkouts14d: Int? = null,
        /** Czy istnieje aktywny plan treningowy. Bez planu nie wnioskujemy „nie trenujesz". */
        hasTrainingPlan: Boolean = false
    ): AdjustmentDecision {

        // === Brak danych → poczekaj ===
        if (!weightTrend.hasEnoughData || adherence14d.sampleDays < 7) {
            return AdjustmentDecision(
                action = AdjustmentAction.NEEDS_MORE_DATA, kcalDeltaProposed = 0, newKcal = currentKcal,
                reason = "insufficient_data",
                explanation = "Potrzeba minimum 7 dni z zalogowanymi posiłkami i co najmniej 3 pomiarów wagi w 14 dniach. " +
                    "Aktualnie: ${adherence14d.sampleDays} dni z dietą, ${weightTrend.sampleCount} pomiarów wagi. " +
                    "Loguj dalej — silnik włączy się automatycznie.",
                confidence = Confidence.LOW
            )
        }

        val strategy = goal.toCalorieStrategy()

        // === Recovery override — przed cięciem kcal sprawdzamy regenerację ===
        if (recovery.hasEnoughData && strategy == CalorieStrategy.CUT) {
            if (recovery.badSleep && recovery.highStress) {
                return AdjustmentDecision(
                    AdjustmentAction.HOLD, 0, currentKcal, "cut_recovery_poor_sleep_stress",
                    "Średnia z 7 dni: sen %.1fh + stres %.1f/5 — regeneracja zła. NIE tnę kcal. Najpierw popraw sen i obniż stres.".format(
                        recovery.avgSleepHours ?: 0.0, recovery.avgStress ?: 0.0),
                    listOf("Zła regeneracja blokuje korekty diety. Sen/stres są fundamentem."), Confidence.HIGH
                )
            }
            if (recovery.highHunger && weightTrend.isStagnationLikely) {
                return AdjustmentDecision(
                    AdjustmentAction.REFEED_DAY, +300, currentKcal + 300, "cut_high_hunger_refeed",
                    "Średnia 7d: głód %.1f/5. Waga stoi — refeed day +300 kcal (głównie węgle) odbuduje leptynę. Następne dni: wracamy do bazowych kcal.".format(
                        recovery.avgHunger ?: 0.0),
                    confidence = Confidence.HIGH
                )
            }
            if (recovery.highSoreness && recovery.lowEnergy) {
                return AdjustmentDecision(
                    AdjustmentAction.DELOAD, 0, currentKcal, "cut_recovery_deload",
                    "Średnia 7d: soreness %.1f/5 + energia %.1f/5 — sygnał DELOAD. Tydzień lżejszy/odpoczynek.".format(
                        recovery.avgSoreness ?: 0.0, recovery.avgEnergy ?: 0.0),
                    confidence = Confidence.HIGH
                )
            }
            if (recovery.highDifficulty) {
                return AdjustmentDecision(
                    AdjustmentAction.SIMPLIFY_PLAN, 0, currentKcal, "cut_high_difficulty",
                    "Średnia 7d: trudność trzymania planu %.1f/5. NIE tnę kcal — uprośćmy plan: prostsze posiłki, mniej składników, krótsze gotowanie.".format(
                        recovery.avgDifficulty ?: 0.0),
                    confidence = Confidence.HIGH
                )
            }
            if (neat.hasEnoughData && neat.significantStepsDrop && weightTrend.isStagnationLikely) {
                return AdjustmentDecision(
                    AdjustmentAction.HOLD, 0, currentKcal, "cut_neat_drop",
                    "Waga stoi, ale Twoje kroki spadły z ${neat.avg30dSteps}/dzień (30d) do ${neat.avg14dSteps}/dzień (14d) — " +
                        "${100 - neat.avg14dSteps * 100 / neat.avg30dSteps}% mniej. To NEAT spadł, nie metabolizm. Wróć do kroków, nie tnij jedzenia.",
                    listOf("Spadek kroków powoduje stagnację — najpierw popraw NEAT, dopiero potem kcal."), Confidence.HIGH
                )
            }
            if (hydrationAdherencePct < 60 && weightTrend.isStagnationLikely) {
                return AdjustmentDecision(
                    AdjustmentAction.HOLD, 0, currentKcal, "cut_low_hydration",
                    "Adherence wody w 14d: ${hydrationAdherencePct}%. Niskie nawodnienie zatrzymuje wodę i maskuje spadek tłuszczu. NIE tnę — popraw nawodnienie.",
                    listOf("Niskie nawodnienie może mieć większy wpływ na pomiary niż dieta."), Confidence.MEDIUM
                )
            }
        }

        val highAdherence = adherence14d.avgKcalPct in 85..115 && adherence14d.avgProteinPct >= 80
        val lowAdherence = adherence14d.avgKcalPct < 70 || adherence14d.avgProteinPct < 60
        val workoutsCompletionPct = if (adherence14d.workoutsPlanned > 0)
            adherence14d.workoutsDone * 100 / adherence14d.workoutsPlanned else 100
        val trainingActive = when {
            realWorkouts14d != null && hasTrainingPlan -> realWorkouts14d >= 2
            realWorkouts14d != null && !hasTrainingPlan -> true
            else -> adherence14d.workoutsPlanned == 0 || adherence14d.workoutsDone >= 2
        }

        return when (strategy) {
            CalorieStrategy.CUT -> analyzeCut(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence,
                workoutsCompletionPct, trainingActive, realWorkouts14d, cutDurationDays, daysSinceLastRefeed)
            CalorieStrategy.BULK -> analyzeBulk(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence, workoutsCompletionPct)
            CalorieStrategy.MAINTAIN -> analyzeMaintain(currentKcal, weightTrend, adherence14d, highAdherence, lowAdherence)
        }
    }

    private fun analyzeCut(
        currentKcal: Int, trend: WeightTrend, adherence: AdherenceSummary,
        highAdherence: Boolean, lowAdherence: Boolean, workoutsPct: Int,
        trainingActive: Boolean = true, realWorkouts14d: Int? = null,
        cutDurationDays: Int = 0, daysSinceLastRefeed: Int = 999
    ): AdjustmentDecision {
        // Diet break automatyczny po 8 tyg redukcji (ISSN/Helms/RP).
        val cutWeeks = cutDurationDays / 7
        val weightDropped = trend.direction == TrendDirection.FALLING ||
            (trend.avg14Days != null && trend.avg28Days != null && trend.avg14Days < trend.avg28Days)
        if (cutDurationDays >= 56 && daysSinceLastRefeed >= 21 && highAdherence && weightDropped && !trend.isFastLoss) {
            val maintenanceKcal = currentKcal + 500
            return AdjustmentDecision(
                AdjustmentAction.REFEED_DAY, +500, maintenanceKcal, "cut_diet_break_8w",
                "Już $cutWeeks tyg na redukcji — czas na 'diet break' (7-14 dni). Wróć do maintenance (~$maintenanceKcal kcal), " +
                    "żeby odbudować leptynę, zatrzymać metabolic adaptation i odpocząć psychicznie. " +
                    "Badania (Helms, ISSN): cykliczne diet breaks zachowują mięśnie i przyspieszają długoterminową redukcję.",
                listOf("Po 7-14 dniach maintenance wracaj do tych samych kcal co teraz — silnik wykryje nowy stan i ponownie zaproponuje korekty."),
                Confidence.HIGH
            )
        }
        // 1. Spadek za szybki — chronimy mięśnie
        if (trend.isFastLoss) {
            return AdjustmentDecision(
                AdjustmentAction.INCREASE_KCAL, +150, currentKcal + 150, "cut_fast_loss",
                "Tracisz wagę za szybko (${"%.1f".format(-trend.slopeKgPerWeek!!)} kg/tydz). " +
                    "Powyżej 1.5 kg/tydz to ryzyko utraty masy mięśniowej + spadku metabolizmu. Dodaję +150 kcal.",
                confidence = Confidence.HIGH
            )
        }
        // 2. Niska adherence → uprość plan
        if (lowAdherence) {
            return AdjustmentDecision(
                AdjustmentAction.SIMPLIFY_PLAN, 0, currentKcal, "cut_low_adherence",
                "Średnia zgodność z planem to ${adherence.avgKcalPct}% kcal, ${adherence.avgProteinPct}% białka. " +
                    "Najpierw poprawmy plan żeby był bardziej wykonalny — NIE obniżam kcal. Sugestia: prostsze posiłki, mniej składników.",
                listOf("Niska zgodność z planem — sprawdź czy posiłki nie są za skomplikowane."), Confidence.HIGH
            )
        }
        // Nie trenujesz w redukcji → chroń mięśnie
        if (!trainingActive) {
            val slope = trend.slopeKgPerWeek
            if (slope != null && slope <= -0.4) {
                return AdjustmentDecision(
                    AdjustmentAction.INCREASE_KCAL, +150, currentKcal + 150, "cut_no_training_protect_muscle",
                    "Nie trenujesz (${realWorkouts14d ?: adherence.workoutsDone} treningów w 14 dni), a chudniesz ${"%.2f".format(-slope)} kg/tydz. " +
                        "Bez treningu siłowego taki deficyt zżera mięśnie — łagodzę go o +150 kcal i trzymaj wysokie białko (≥2 g/kg masy).",
                    listOf("Redukcja bez treningu siłowego = ryzyko utraty mięśni. Białko + minimalny ruch to ochrona."), Confidence.HIGH
                )
            }
            return AdjustmentDecision(
                AdjustmentAction.HOLD, 0, currentKcal, "cut_no_training_hold",
                "Nie trenujesz, więc nie obniżam dalej kalorii — bez treningu cięcie zżera mięśnie. " +
                    "Utrzymaj wysokie białko (≥2 g/kg) i codzienne kroki.", confidence = Confidence.MEDIUM
            )
        }
        // 3. Stagnacja 14d + adherence wysokie + treningi → obniż
        if (trend.isStagnationLikely && highAdherence && workoutsPct >= 80) {
            return AdjustmentDecision(
                AdjustmentAction.DECREASE_KCAL, -150, currentKcal - 150, "cut_stagnation_high_adherence",
                "Średnia waga z 14 dni nie spada (${"%.1f".format(trend.avg14Days ?: 0.0)} kg). " +
                    "Zgodność z dietą: ${adherence.avgKcalPct}%, treningi: ${adherence.workoutsDone}/${adherence.workoutsPlanned}. " +
                    "Wszystko wykonujesz dobrze, ale waga stoi — czas na małą korektę. Obniżam o 150 kcal.",
                confidence = Confidence.HIGH
            )
        }
        // 3b. Wczesny plateau 7d
        if (trend.isEarlyPlateau && !trend.isStagnationLikely && highAdherence && workoutsPct >= 80) {
            return AdjustmentDecision(
                AdjustmentAction.DECREASE_KCAL, -100, currentKcal - 100, "cut_early_plateau_7d",
                "Waga w ostatnich 7 dniach praktycznie stoi (${trend.daysWithoutProgress} dni bez zmiany ≥0.2 kg). " +
                    "Zgodność ${adherence.avgKcalPct}%, treningi ${adherence.workoutsDone}/${adherence.workoutsPlanned} — plan się 'zatyka'. " +
                    "Mała korekta −100 kcal teraz. Jeśli waga ruszy w 4-5 dni — wracamy.",
                listOf("Wczesny sygnał — jeśli to tylko retencja wody, waga ruszy sama."), Confidence.MEDIUM
            )
        }
        // 4. Stagnacja + treningi NIE
        if (trend.isStagnationLikely && highAdherence && workoutsPct < 80) {
            return AdjustmentDecision(
                AdjustmentAction.HOLD, 0, currentKcal, "cut_stagnation_low_workouts",
                "Waga stoi, dieta OK, ale wykonano tylko $workoutsPct% planowanych treningów. Najpierw zwiększ frekwencję — NIE obniżam kcal.",
                listOf("Niska frekwencja treningów — dieta nie zadziała sama."), Confidence.MEDIUM
            )
        }
        // 5. Spada powoli — plan działa
        if (trend.direction == TrendDirection.FALLING && !trend.isFastLoss) {
            return AdjustmentDecision(
                AdjustmentAction.HOLD, 0, currentKcal, "cut_progressing",
                "Tracisz wagę w tempie ${"%.2f".format(-trend.slopeKgPerWeek!!)} kg/tydz — idealnie. Plan działa, kontynuuj.",
                confidence = Confidence.HIGH
            )
        }
        // 6. Przejadasz cel — problem to spożycie, nie cel
        if (trend.direction != TrendDirection.FALLING && adherence.avgKcalPct > 115) {
            val rising = trend.direction == TrendDirection.RISING
            return AdjustmentDecision(
                AdjustmentAction.HOLD, 0, currentKcal, "cut_overeating",
                "Jesz średnio ${adherence.avgKcalPct}% celu kalorii (powyżej planu), a waga ${if (rising) "rośnie" else "stoi"} — " +
                    "na redukcji nie ma wtedy deficytu. NIE obniżam targetu ($currentKcal kcal), bo i tak go przekraczasz. Wróćmy do trzymania kalorii.",
                listOf("Spożycie powyżej celu = brak deficytu mimo 'redukcji'."), Confidence.HIGH
            )
        }
        return AdjustmentDecision(
            AdjustmentAction.HOLD, 0, currentKcal, "cut_default_hold",
            "Trzymaj plan. Waga porusza się ale nie ma jednoznacznego sygnału do korekty. Sprawdzimy za tydzień.",
            confidence = Confidence.MEDIUM
        )
    }

    private fun analyzeBulk(
        currentKcal: Int, trend: WeightTrend, adherence: AdherenceSummary,
        highAdherence: Boolean, lowAdherence: Boolean, workoutsPct: Int
    ): AdjustmentDecision {
        if (workoutsPct < 70) {
            return AdjustmentDecision(
                AdjustmentAction.HOLD, 0, currentKcal, "bulk_low_workouts",
                "Wykonano tylko $workoutsPct% planowanych treningów. Bez treningów dodatkowe kcal idą w tłuszcz, nie mięśnie. NIE zwiększam.",
                listOf("Niska frekwencja treningów — bulk bez treningów = przyrost tłuszczu."), Confidence.HIGH
            )
        }
        if (trend.isFastGain) {
            return AdjustmentDecision(
                AdjustmentAction.DECREASE_KCAL, -150, currentKcal - 150, "bulk_fast_gain",
                "Przyrost wagi ${"%.1f".format(trend.slopeKgPerWeek!!)} kg/tydz — większość to tłuszcz. Zalecane lean bulk = 0.3 kg/tydz. Obniżam o 150 kcal.",
                confidence = Confidence.HIGH
            )
        }
        if (trend.isStagnationLikely && highAdherence && workoutsPct >= 80) {
            return AdjustmentDecision(
                AdjustmentAction.INCREASE_KCAL, +150, currentKcal + 150, "bulk_stagnation",
                "Waga nie rośnie przez 14 dni mimo dobrej diety i treningów. Dodaję +150 kcal żeby wymusić anaboliczną nadwyżkę.",
                confidence = Confidence.HIGH
            )
        }
        if (trend.direction == TrendDirection.RISING && !trend.isFastGain) {
            return AdjustmentDecision(
                AdjustmentAction.HOLD, 0, currentKcal, "bulk_progressing",
                "Waga rośnie w tempie ${"%.2f".format(trend.slopeKgPerWeek!!)} kg/tydz — lean bulk idealny. Kontynuuj.",
                confidence = Confidence.HIGH
            )
        }
        if (lowAdherence) {
            return AdjustmentDecision(
                AdjustmentAction.SIMPLIFY_PLAN, 0, currentKcal, "bulk_low_adherence",
                "Zgodność z dietą: ${adherence.avgKcalPct}% kcal, ${adherence.avgProteinPct}% białka. " +
                    "Nie jesz ile zaplanowane — zanim zwiększymy kcal, popraw wykonalność planu.",
                confidence = Confidence.HIGH
            )
        }
        return AdjustmentDecision(
            AdjustmentAction.HOLD, 0, currentKcal, "bulk_default_hold", "Trzymaj plan, sprawdzimy za tydzień.", confidence = Confidence.MEDIUM
        )
    }

    private fun analyzeMaintain(
        currentKcal: Int, trend: WeightTrend, adherence: AdherenceSummary,
        highAdherence: Boolean, lowAdherence: Boolean
    ): AdjustmentDecision {
        if (trend.isFastLoss || trend.isFastGain) {
            val delta = if (trend.isFastLoss) +100 else -100
            return AdjustmentDecision(
                if (delta > 0) AdjustmentAction.INCREASE_KCAL else AdjustmentAction.DECREASE_KCAL,
                delta, currentKcal + delta, "maintain_drift",
                "Waga się ${if (trend.isFastLoss) "obniża" else "podnosi"} szybciej niż chcesz przy utrzymaniu. Korekta ${if (delta > 0) "+" else ""}$delta kcal.",
                confidence = Confidence.MEDIUM
            )
        }
        return AdjustmentDecision(
            AdjustmentAction.HOLD, 0, currentKcal, "maintain_stable", "Waga stabilna w pobliżu celu — plan działa.", confidence = Confidence.HIGH
        )
    }
}
