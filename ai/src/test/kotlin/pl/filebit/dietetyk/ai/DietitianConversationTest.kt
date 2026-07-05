package pl.filebit.dietetyk.ai

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DietitianConversationTest {

    /** Fake API oddające zaskryptowaną sekwencję odpowiedzi. */
    private class ScriptedApi(private val responses: List<String>) : ClaudeApi {
        var calls = 0
        override suspend fun send(requestBody: String): String = responses[calls++]
    }

    private class RecordingHandler : ToolHandler {
        val called = mutableListOf<String>()
        override suspend fun handle(name: String, input: JsonObject): ToolResult {
            called += name
            return ToolResult("2200 kcal (B165/W250/T60)")
        }
    }

    @Test
    fun `petla wykonuje narzedzie i zwraca finalna wypowiedz`() = runTest {
        val toolUseResp = """
            {"role":"assistant","content":[
              {"type":"text","text":"Chwila, policzę."},
              {"type":"tool_use","id":"toolu_1","name":"calculate_targets","input":{}}
            ],"stop_reason":"tool_use"}
        """.trimIndent()
        val finalResp = """
            {"role":"assistant","content":[{"type":"text","text":"Twój cel to 2200 kcal."}],
             "stop_reason":"end_turn"}
        """.trimIndent()

        val api = ScriptedApi(listOf(toolUseResp, finalResp))
        val handler = RecordingHandler()
        val history = mutableListOf<JsonObject>()

        val text = DietitianConversation(api).send(
            systemPrompt = "jesteś dietetykiem",
            history = history,
            userMessage = "policz mój cel",
            handler = handler
        )

        assertEquals("Twój cel to 2200 kcal.", text)
        assertEquals("narzędzie wywołane raz", listOf("calculate_targets"), handler.called)
        assertEquals("2 zapytania do API (tool_use → end_turn)", 2, api.calls)
        // historia: user → assistant(tool_use) → user(tool_result) → assistant(final)
        assertEquals(4, history.size)
        assertTrue("dostęp do wszystkich narzędzi katalogu", ClaudeToolMapper.toolsJson().size >= 13)
    }
}
