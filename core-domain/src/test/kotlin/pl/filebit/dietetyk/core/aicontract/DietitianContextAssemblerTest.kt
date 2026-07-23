package pl.filebit.dietetyk.core.aicontract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.model.AdherenceSummary
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DailyEnergyLog
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.model.WeightSample

class DietitianContextAssemblerTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L
    private val profile = NutritionProfile(Gender.MALE, 30, 180, 80.0, goal = DietGoalType.FAT_LOSS)

    // ≥3 pomiary — TrendAnalyzer wymaga próbki do policzenia trendu
    private fun weights() = listOf(
        WeightSample(now - 14 * day, 80.6), WeightSample(now - 7 * day, 80.3), WeightSample(now, 80.0))
    private fun logs(kcal: Int, count: Int = 10) = (1..count).map { DailyEnergyLog(now - it * day, kcal, true) }

    @Test
    fun `assembler spina caly silnik w komplet kontekstu`() {
        val ctx = DietitianContextAssembler.assemble(
            careState = CareState(CareStage.ACTIVE),
            profile = profile, clinical = ClinicalContext.NONE,
            weightSamples = weights(), energyLogs = logs(2100),
            memoryNotes = listOf("nie znosi twarogu"),
            adherence14d = AdherenceSummary(sampleDays = 12, avgKcalPct = 98, avgProteinPct = 90),
            today = DaySnapshot(kcalConsumed = 1450, mealsEaten = 2, mealsPlanned = 3),
            lastCheckIn = null, daysSinceLastLog = 1, nowMs = now
        )
        assertNotNull("cel policzony", ctx.currentGoal)
        assertTrue("kcal sensowne", ctx.currentGoal!!.kcal in 1500..2600)
        assertTrue("trend ma dane", ctx.weightTrend.hasEnoughData)
        assertNotNull("adaptacyjny TDEE policzony", ctx.tdeeEstimate)
        assertEquals("10 dni pełnych logów", 10, ctx.completeLogDays14d)
        assertEquals("waga = najświeższa", 80.0, ctx.latestWeightKg)
        assertTrue("pamięć przekazana", ctx.memoryNotes.contains("nie znosi twarogu"))
        assertFalse("brak sygnału do lekarza przy normie", ctx.referToDoctor)
        assertTrue("user zaangażowany (log 1 dzień temu)", ctx.isEngaged)
    }

    @Test
    fun `glodzenie ponizej minimum ustawia skierowanie do lekarza w kontekscie`() {
        val ctx = DietitianContextAssembler.assemble(
            careState = CareState(CareStage.ACTIVE),
            profile = profile, clinical = ClinicalContext.NONE,
            weightSamples = weights(), energyLogs = logs(1100),  // <1500 floor przez 10 dni
            memoryNotes = emptyList(),
            adherence14d = AdherenceSummary(sampleDays = 10),
            today = DaySnapshot(), lastCheckIn = null, daysSinceLastLog = 0, nowMs = now
        )
        assertTrue("red-flag → skierowanie", ctx.referToDoctor)
        assertTrue("komunikat wypełniony", ctx.redFlagMessage.isNotBlank())
    }

    // === v1.20 — hotfix: itemizacja, ostrzeżenie o integralności, placeholder wagi ===
    private fun baseCtx(
        today: DaySnapshot = DaySnapshot(),
        weightIsPlaceholder: Boolean = false,
        todayDataWarning: String? = null
    ) = DietitianContextAssembler.assemble(
        careState = CareState(CareStage.ACTIVE),
        profile = profile, clinical = ClinicalContext.NONE,
        weightSamples = weights(), energyLogs = logs(2100),
        memoryNotes = emptyList(), adherence14d = AdherenceSummary(),
        today = today, lastCheckIn = null, daysSinceLastLog = 0, nowMs = now
    ).copy(weightIsPlaceholder = weightIsPlaceholder, todayDataWarning = todayDataWarning)

    @Test
    fun `render pokazuje itemizacje dzisiejszych wpisow`() {
        val ctx = baseCtx(today = DaySnapshot(
            kcalConsumed = 914, mealsEaten = 2, mealsPlanned = 4,
            loggedMeals = listOf(
                LoggedMeal(1, "07:15", 457, 17, 60, 16),
                LoggedMeal(2, "10:11", 457, 17, 60, 16)  // duplikat
            )
        ))
        val out = DietitianPrompt.renderContext(ctx)
        assertTrue("itemizacja obecna", out.contains("DZISIEJSZE WPISY"))
        assertTrue("wpis z godziną i kcal", out.contains("[07:15] 457 kcal"))
        assertTrue("drugi (duplikat) też widoczny", out.contains("[10:11] 457 kcal"))
    }

    @Test
    fun `render ostrzega o mozliwym bledzie danych dnia`() {
        val ctx = baseCtx(todayDataWarning = "Dziś zalogowano 19 wpisów na łącznie 6456 kcal — to możliwy błąd apki.")
        val out = DietitianPrompt.renderContext(ctx)
        assertTrue("nagłówek ostrzeżenia", out.contains("MOŻLIWY BŁĄD DANYCH DNIA"))
        assertTrue("treść przekazana", out.contains("6456 kcal"))
    }

    @Test
    fun `render podaje dzien tygodnia dla narzedzi planu`() {
        val out = DietitianPrompt.renderContext(baseCtx().copy(todayDow = 4))  // czwartek
        assertTrue("dziś = dayOfWeek 4", out.contains("dayOfWeek 4"))
        assertTrue("jutro = dayOfWeek 5", out.contains("dayOfWeek 5"))
        assertTrue("nazwa dnia", out.contains("czwartek"))
    }

    @Test
    fun `render ujawnia ze cel stoi na zalozonej wadze`() {
        val out = DietitianPrompt.renderContext(baseCtx(weightIsPlaceholder = true))
        assertTrue("ujawnia placeholder wagi", out.contains("nie mam realnej wagi"))
        assertTrue("cel jako tymczasowy", out.contains("WARTOŚĆ TYMCZASOWA"))
    }

    @Test
    fun `systemPrompt zakazuje kompensacji i nakazuje badanie danych`() {
        val sp = DietitianPrompt.systemPrompt()
        assertTrue("zakaz kompensacji", sp.contains("ZAKAZ KOMPENSACJI"))
        assertTrue("najpierw integralność danych", sp.contains("NAJPIERW INTEGRALNOŚĆ DANYCH"))
        assertTrue("wskazuje get_day_log", sp.contains("get_day_log"))
    }

    // === v1.21 — model slotów: precyzja logowania + narzędzia sprzątające ===
    @Test
    fun `systemPrompt uczy nie logowac z niejednoznacznego Tak i sprzatac duplikaty`() {
        val sp = DietitianPrompt.systemPrompt()
        assertTrue("Tak nie jest raportem zjedzenia", sp.contains("NIE jest raportem zjedzenia"))
        assertTrue("edycja planu != logowanie", sp.contains("EDYCJA PLANU ≠ LOGOWANIE"))
        assertTrue("ponowne log_planned_day bezpieczne (replace)", sp.contains("zastępuje"))
        assertTrue("sprzątanie przez reset_day", sp.contains("reset_day"))
    }

    @Test
    fun `katalog narzedzi ma sprzatanie i itemizacje`() {
        val names = AiToolCatalog.all.map { it.name }
        assertTrue("get_day_log", names.contains("get_day_log"))
        assertTrue("reset_day", names.contains("reset_day"))
        assertTrue("delete_meal_log", names.contains("delete_meal_log"))
        assertTrue("reset_day mutuje", AiToolCatalog.byName("reset_day")!!.mutating)
        assertTrue("delete_meal_log mutuje", AiToolCatalog.byName("delete_meal_log")!!.mutating)
    }

}