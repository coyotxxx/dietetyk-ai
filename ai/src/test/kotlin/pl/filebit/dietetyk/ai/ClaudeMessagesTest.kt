package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeMessagesTest {

    @Test
    fun `buildRequest zawiera model, max_tokens, system, messages i tools`() {
        val req = ClaudeMessages.buildRequest(
            model = "claude-sonnet-5", maxTokens = 1024, systemPrompt = "jesteś dietetykiem",
            messages = listOf(ClaudeMessages.userText("cześć")),
            tools = ClaudeToolMapper.toolsJson()
        )
        val o = Json.parseToJsonElement(req).jsonObject
        assertEquals("claude-sonnet-5", o["model"]!!.jsonPrimitive.content)
        assertEquals("1024", o["max_tokens"]!!.jsonPrimitive.content)
        assertEquals("jesteś dietetykiem", o["system"]!!.jsonPrimitive.content)
        assertTrue(o.containsKey("messages"))
        assertTrue(o.containsKey("tools"))
    }

    @Test
    fun `parseResponse wyciaga tekst`() {
        val body = """
            {"id":"msg_1","model":"claude-sonnet-5","role":"assistant",
             "content":[{"type":"text","text":"Cześć Maciej!"}],"stop_reason":"end_turn",
             "usage":{"input_tokens":10,"output_tokens":5}}
        """.trimIndent()
        val turn = ClaudeMessages.parseResponse(body)
        assertEquals("Cześć Maciej!", turn.text)
        assertEquals("end_turn", turn.stopReason)
        assertTrue(turn.toolUses.isEmpty())
    }

    @Test
    fun `parseResponse wyciaga wywolanie narzedzia`() {
        val body = """
            {"id":"msg_2","role":"assistant",
             "content":[
               {"type":"text","text":"Policzę Twój cel."},
               {"type":"tool_use","id":"toolu_9","name":"calculate_targets","input":{}}
             ],"stop_reason":"tool_use"}
        """.trimIndent()
        val turn = ClaudeMessages.parseResponse(body)
        assertEquals("tool_use", turn.stopReason)
        assertEquals(1, turn.toolUses.size)
        assertEquals("calculate_targets", turn.toolUses.first().name)
        assertEquals("toolu_9", turn.toolUses.first().id)
    }
}
