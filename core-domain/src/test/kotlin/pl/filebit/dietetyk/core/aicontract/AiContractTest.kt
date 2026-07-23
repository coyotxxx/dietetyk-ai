package pl.filebit.dietetyk.core.aicontract

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.calc.GoalPipeline
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile

class AiContractTest {

    private val profile = NutritionProfile(Gender.MALE, 30, 180, 80.0, goal = DietGoalType.FAT_LOSS)

    // === CareState ===

    @Test
    fun `wywiad kompletny gdy brak otwartych tematow`() {
        assertFalse(CareState(CareStage.INTERVIEW, listOf(InterviewTopic.GOAL_AND_WHY)).interviewComplete)
        assertTrue(CareState(CareStage.INTERVIEW, emptyList()).interviewComplete)
    }

    @Test
    fun `zalegajaca wizyta podnosi miekki priorytet, nigdy nie blokuje`() {
        assertTrue(CareState(CareStage.ACTIVE, canDefer()).canDeferGoals)
        assertTrue(CareState(CareStage.CHECKIN_DUE, checkInDue = true, checkInOverdueDays = 6).checkInNudgePriority == 2)
        assertTrue(CareState(CareStage.ACTIVE, checkInDue = false).checkInNudgePriority == 0)
    }

    private fun canDefer() = emptyList<InterviewTopic>()

    // === System prompt (persona 2.7a + zasady) ===

    @Test
    fun `system prompt zawiera kluczowe zasady`() {
        val sp = DietitianPrompt.systemPrompt()
        assertTrue("jedno pytanie na raz", sp.contains("JEDNO pytanie"))
        assertTrue("liczby z narzędzi, nie z głowy", sp.contains("z głowy"))
        assertTrue("kieruj do lekarza", sp.contains("LEKARZA"))
        assertTrue("bez żargonu", sp.contains("żargon"))
    }

    // === Render kontekstu ===

    @Test
    fun `render kontekstu pokazuje cel, pamiec i skierowanie do lekarza`() {
        val goal = GoalPipeline.compute(profile, latestMeasuredWeightKg = 80.0)
        val ctx = DietitianContext(
            careState = CareState(CareStage.ACTIVE),
            profile = profile, clinical = ClinicalContext.NONE,
            memoryNotes = listOf("nie znosi twarogu"),
            currentGoal = goal, latestWeightKg = 80.0,
            referToDoctor = true, redFlagMessage = "Objawy wymagają oceny."
        )
        val text = DietitianPrompt.renderContext(ctx)
        assertTrue("cel kcal w kontekście", text.contains("${goal.kcal} kcal"))
        assertTrue("pamięć epizodyczna", text.contains("twarogu"))
        assertTrue("skierowanie nadrzędne", text.contains("SKIERUJ DO LEKARZA"))
    }

    @Test
    fun `guidance wywiadu wymienia otwarte tematy`() {
        val g = DietitianPrompt.renderCareGuidance(
            CareState(CareStage.INTERVIEW, listOf(InterviewTopic.GOAL_AND_WHY, InterviewTopic.HEALTH)))
        assertTrue(g.contains("WYWIAD"))
        assertTrue(g.contains("cel"))
        assertTrue(g.contains("zdrowie"))
    }

    // === Katalog narzędzi ===

    @Test
    fun `propose_adjustment nie przyjmuje surowej wartosci kcal (guardrail strukturalny)`() {
        val tool = AiToolCatalog.byName("propose_adjustment")
        assertNotNull(tool)
        val paramNames = tool!!.params.map { it.name }
        assertTrue("kierunek", "direction" in paramNames)
        assertTrue("siła", "magnitude" in paramNames)
        assertFalse("brak surowej wartości kcal", paramNames.any { it.contains("kcal") })
    }

    @Test
    fun `katalog ma kluczowe narzedzia dajace AI dostep do danych`() {
        listOf("calculate_targets", "get_history", "run_checkin", "save_diet_plan", "log_meal", "save_visit_note")
            .forEach { assertNotNull("brak narzędzia $it", AiToolCatalog.byName(it)) }
        assertTrue("calculate_targets zwraca liczby", AiToolCatalog.byName("calculate_targets")!!.emitsNumbers)
        assertTrue("get_history czyta dane", AiToolCatalog.byName("get_history")!!.readsData)
    }

    @Test
    fun `update_plan_meal pozwala edytowac pojedynczy posilek`() {
        val tool = AiToolCatalog.byName("update_plan_meal")
        assertNotNull("brak update_plan_meal", tool)
        val names = tool!!.params.map { it.name }
        assertTrue("identyfikacja po indeksie", "mealIndex" in names)
        assertTrue("identyfikacja po nazwie", "mealName" in names)
        assertTrue("nowe składniki", "ingredients" in names)
        assertTrue("zakres dnia", "dayOfWeek" in names)
        assertTrue("mutuje plan", tool.mutating)
    }

    @Test
    fun `system prompt uczy edycji jednego posilku zamiast calego dnia`() {
        val sp = DietitianPrompt.systemPrompt()
        assertTrue("wskazuje update_plan_meal", sp.contains("update_plan_meal"))
        assertTrue("zakazuje mowic ze nie moze edytowac", sp.contains("nie możesz edytować jednego posiłku"))
    }

    @Test
    fun `narzedzia wgladu w plan i sprzatania pamieci istnieja`() {
        assertNotNull("get_plan_day", AiToolCatalog.byName("get_plan_day"))
        assertNotNull("delete_memory", AiToolCatalog.byName("delete_memory"))
        assertTrue("get_plan_day czyta dane", AiToolCatalog.byName("get_plan_day")!!.readsData)
        assertTrue("delete_memory mutuje", AiToolCatalog.byName("delete_memory")!!.mutating)
    }

    @Test
    fun `system prompt wymaga uczciwosci o stanie planu (plan nad notatka)`() {
        val sp = DietitianPrompt.systemPrompt()
        assertTrue("uczciwość o planie", sp.contains("UCZCIWOŚĆ O STANIE PLANU"))
        assertTrue("notatka nie jest wykonaniem", sp.contains("NOTATKA ≠ WYKONANIE"))
    }
}
