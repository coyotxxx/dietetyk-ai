package pl.filebit.dietetyk.data.context

import pl.filebit.dietetyk.core.aicontract.CareStage
import pl.filebit.dietetyk.core.aicontract.CareState
import pl.filebit.dietetyk.core.aicontract.DaySnapshot
import pl.filebit.dietetyk.core.aicontract.DietitianContext
import pl.filebit.dietetyk.core.aicontract.DietitianContextAssembler
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.data.db.AiMemoryDao
import pl.filebit.dietetyk.data.db.EnergyLogDao
import pl.filebit.dietetyk.data.mapper.toModel
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
    private val foodProductDao: pl.filebit.dietetyk.data.db.FoodProductDao
) {
    private val dayMs = 24L * 3600 * 1000

    suspend fun build(nowMs: Long): DietitianContext? {
        val profile = profileRepo.get() ?: return null  // brak profilu → tryb wywiadu (obsługa w :ai)

        val weights = weightRepo.since(nowMs - 60 * dayMs)
        val energyLogs = energyLogDao.since(nowMs - 21 * dayMs).map { it.toModel() }
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
            adherence14d = AdherenceSummary(),
            today = DaySnapshot(),
            lastCheckIn = null,
            daysSinceLastLog = daysSinceLastLog,
            nowMs = nowMs
        ).copy(favoriteProducts = favorites, avoidedProducts = avoided)
    }
}
