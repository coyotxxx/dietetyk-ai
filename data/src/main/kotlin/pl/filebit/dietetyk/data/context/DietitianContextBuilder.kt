package pl.filebit.dietetyk.data.context

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.dietetyk.core.aicontract.CareStage
import pl.filebit.dietetyk.core.aicontract.CareState
import pl.filebit.dietetyk.core.aicontract.DaySnapshot
import pl.filebit.dietetyk.core.aicontract.DietitianContext
import pl.filebit.dietetyk.core.aicontract.DietitianContextAssembler
import pl.filebit.dietetyk.core.aicontract.PlannedMeal
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.data.db.AiMemoryDao
import pl.filebit.dietetyk.data.db.EnergyLogDao
import pl.filebit.dietetyk.data.repository.ProfileRepository
import pl.filebit.dietetyk.data.repository.WeightRepository

/**
 * Buduje [DietitianContext] z bazy — POBIERA dane z repozytoriów i woła czysty [DietitianContextAssembler].
 * To warstwa I/O; cała logika liczenia jest w rdzeniu.
 *
 * Zwraca `null`, gdy nie ma jeszcze profilu → warstwa `:ai` startuje wtedy WYWIAD (systemPrompt +
 * renderCareGuidance dla stanu INTERVIEW), bez danych, których jeszcze nie ma.
 *
 * `adherence`/`today`/`lastCheckIn` na razie domyślne — wejdą z encjami planu/konsumpcji/wizyt.
 */
class DietitianContextBuilder(
    private val profileRepo: ProfileRepository,
    private val weightRepo: WeightRepository,
    private val energyLogDao: EnergyLogDao,
    private val aiMemoryDao: AiMemoryDao,
    private val foodProductDao: pl.filebit.dietetyk.data.db.FoodProductDao,
    private val planDao: pl.filebit.dietetyk.data.db.PlanDao
) {
    private val dayMs = 24L * 3600 * 1000
    private val zone: java.time.ZoneId get() = java.time.ZoneId.systemDefault()

    /** Początek dnia lokalnego (północ) dla danego momentu — do bucketowania logów po dniu kalendarzowym. */
    private fun dayStart(ms: Long): Long =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()

    private fun dowOf(ms: Long): Int =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDate().dayOfWeek.value

    suspend fun build(nowMs: Long): DietitianContext? {
        val profile = profileRepo.get() ?: return null  // brak profilu → tryb wywiadu (obsługa w :ai)

        val weights = weightRepo.since(nowMs - 60 * dayMs)
        // Surowe wiersze per POSIŁEK (log_meal wstawia jeden wiersz na posiłek).
        val rawLogs = energyLogDao.since(nowMs - 21 * dayMs)

        // Plan na dziś — żeby AI WIDZIAŁO co znaczy „zjadłem wszystko" i mogło to zalogować bez zgadywania.
        val planEntity = runCatching { planDao.get() }.getOrNull()
        val plannedToday = planEntity?.let { parseTodayPlanned(it.planJson, nowMs) } ?: emptyList()
        // Ile posiłków dziennie „powinno być" — do oznaczenia dnia jako KOMPLETNY (warunek dla TDEE/adherencji/red-flag).
        val plannedPerDay = profile.mealsPerDay?.takeIf { it > 0 }
            ?: plannedToday.size.takeIf { it > 0 } ?: 3

        // AGREGACJA per dzień kalendarzowy: sumuj kcal wszystkich posiłków, dzień KOMPLETNY = zalogowano
        // co najmniej tyle posiłków, ile w planie. To odblokowuje adaptacyjny TDEE, adherencję i guardrail głodzenia.
        val energyLogs: List<DailyEnergyLog> = rawLogs
            .groupBy { dayStart(it.dateMs) }
            .map { (ds, rows) ->
                val kcal = rows.sumOf { it.kcalConsumed }
                DailyEnergyLog(dateMs = ds, kcalConsumed = kcal, isComplete = kcal > 0 && rows.size >= plannedPerDay)
            }
            .sortedBy { it.dateMs }

        // TRZYMANIE PLANU (14d) — realne %, dotąd zaślepka 0/0. Liczone z dni KOMPLETNYCH względem celu,
        // żeby prompt nie kłamał „kcal 0%" przy 13 zalogowanych dniach (myliło AI: „pierwszy zalogowany dzień").
        val since14 = nowMs - 14 * dayMs
        val goal = pl.filebit.dietetyk.core.calc.GoalPipeline.compute(
            profile = profile,
            latestMeasuredWeightKg = weights.maxByOrNull { it.dateMs }?.weightKg
        )
        val completeDayMacros = rawLogs
            .filter { it.dateMs >= since14 }
            .groupBy { dayStart(it.dateMs) }
            .values.filter { it.size >= plannedPerDay && it.sumOf { r -> r.kcalConsumed } > 0 }
            .map { rows ->
                intArrayOf(
                    rows.sumOf { it.kcalConsumed }, rows.sumOf { it.proteinG },
                    rows.sumOf { it.carbsG }, rows.sumOf { it.fatG }
                )
            }
        val adherence = if (completeDayMacros.isEmpty()) AdherenceSummary()
        else {
            fun pct(sel: (IntArray) -> Int, target: Int): Int =
                if (target <= 0) 0 else (completeDayMacros.map { sel(it) * 100.0 / target }.average()).toInt()
            AdherenceSummary(
                sampleDays = completeDayMacros.size,
                avgKcalPct = pct({ it[0] }, goal.kcal),
                avgProteinPct = pct({ it[1] }, goal.proteinG),
                avgCarbsPct = pct({ it[2] }, goal.carbsG),
                avgFatPct = pct({ it[3] }, goal.fatG)
            )
        }

        // Migawka DZIŚ (realna — dotąd była pusta): zjedzone kcal/białko/posiłki + ile zaplanowano.
        val todayStart = dayStart(nowMs)
        val todayRows = rawLogs.filter { dayStart(it.dateMs) == todayStart }
        val todaySnapshot = DaySnapshot(
            kcalConsumed = todayRows.sumOf { it.kcalConsumed },
            proteinConsumedG = todayRows.sumOf { it.proteinG },
            mealsEaten = todayRows.size,
            mealsPlanned = plannedToday.size
        )
        // Pamięć miękka: tylko ŚWIEŻE notatki (≤21 dni) z wiekiem — stary kontekst wygasa,
        // żeby AI nie nawiązywało do stresu sprzed miesiąca („recency-aware", nie inwigilacja).
        val memoryNotes = runCatching {
            aiMemoryDao.recentEntries(20)
                .filter { nowMs - it.createdAt <= 21L * 24 * 3600 * 1000 }
                .map { e ->
                    val d = ((nowMs - e.createdAt) / (24L * 3600 * 1000)).toInt()
                    e.note + when { d <= 0 -> " (dziś)"; d == 1 -> " (wczoraj)"; else -> " ($d dni temu)" }
                }
        }.getOrDefault(emptyList())
        val favorites = runCatching { foodProductDao.preferred().map { it.name } }.getOrDefault(emptyList())
        val avoided = runCatching { foodProductDao.avoided().map { it.name } }.getOrDefault(emptyList())
        val hasPlan = planEntity != null
        val daysSinceLastLog = energyLogDao.latest()?.let { ((nowMs - it.dateMs) / dayMs).toInt() }

        // Stan opieki (uproszczony): mając profil jesteśmy w prowadzeniu. Harmonogram wizyt (CHECKIN_DUE)
        // i przejścia INTERVIEW→CONTRACT dojdą ze schedulerem.
        val careState = CareState(CareStage.ACTIVE)

        return DietitianContextAssembler.assemble(
            careState = careState,
            profile = profile,
            clinical = ClinicalContext.NONE,   // magazyn kontekstu klinicznego dojdzie z encją profilu rozszerzonego
            weightSamples = weights,
            energyLogs = energyLogs,
            memoryNotes = memoryNotes,
            adherence14d = adherence,
            today = todaySnapshot,
            lastCheckIn = null,
            daysSinceLastLog = daysSinceLastLog,
            nowMs = nowMs
        ).copy(
            favoriteProducts = favorites,
            avoidedProducts = avoided,
            hasActivePlan = hasPlan,
            plannedMealsToday = plannedToday
        )
    }

    /**
     * Wyciąga posiłki ZAPLANOWANE na dziś z planu (JSON). Format zapisu: {"days":{"<dow>":{"meals":[...]}}}
     * (stary format {"meals":[...]} = tylko dzisiejszy dzień). Każdy posiłek ma policzone kcal/makro z bazy.
     * Parser w :data (nie zależymy od :app/PlanData) — zależność szłaby w złą stronę.
     */
    private fun parseTodayPlanned(planJson: String, nowMs: Long): List<PlannedMeal> {
        val root = runCatching { kotlinx.serialization.json.Json.parseToJsonElement(planJson).jsonObject }.getOrNull()
            ?: return emptyList()
        val dow = dowOf(nowMs)
        val mealsArr: JsonArray? = run {
            val days = root["days"]?.jsonObject
            if (days != null) days[dow.toString()]?.jsonObject?.get("meals") as? JsonArray
            else root["meals"] as? JsonArray  // stary format = dzisiejszy dzień
        }
        return mealsArr?.mapNotNull { it as? JsonObject }?.map { m ->
            PlannedMeal(
                name = m["name"]?.jsonPrimitive?.content ?: "Posiłek",
                timeHint = m["timeHint"]?.jsonPrimitive?.content ?: "",
                kcal = m.intOf("kcal"),
                proteinG = m.intOf("proteinG"),
                carbsG = m.intOf("carbsG"),
                fatG = m.intOf("fatG")
            )
        } ?: emptyList()
    }

    private fun JsonObject.intOf(k: String): Int =
        this[k]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 0
}
