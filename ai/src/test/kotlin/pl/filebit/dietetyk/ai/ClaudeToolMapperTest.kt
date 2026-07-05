package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.filebit.dietetyk.core.aicontract.AiToolCatalog

class ClaudeToolMapperTest {

    @Test
    fun `mapuje wszystkie narzedzia katalogu na format Claude`() {
        val tools = ClaudeToolMapper.toolsJson()
        assertEquals("tyle narzędzi ile w katalogu", AiToolCatalog.all.size, tools.size)
        tools.forEach { t ->
            val o = t.jsonObject
            assertTrue("ma name", o["name"] != null)
            assertTrue("ma description", o["description"] != null)
            assertEquals("input_schema.type=object", "object",
                o["input_schema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        }
    }

    @Test
    fun `propose_adjustment ma direction i magnitude jako wymagane, bez surowego kcal`() {
        val tool = ClaudeToolMapper.toolsJson().first {
            it.jsonObject["name"]!!.jsonPrimitive.content == "propose_adjustment"
        }.jsonObject
        val schema = tool["input_schema"]!!.jsonObject
        val props = schema["properties"]!!.jsonObject
        assertTrue("direction", props.containsKey("direction"))
        assertTrue("magnitude", props.containsKey("magnitude"))
        assertTrue("brak surowego kcal", props.keys.none { it.contains("kcal") })
        val required = schema["required"]!!.jsonArray.map { it.jsonPrimitive.content }.toSet()
        assertEquals(setOf("direction", "magnitude"), required)
    }

    @Test
    fun `typy semantyczne mapuja sie na JSON Schema`() {
        // save_profile: heightCm→integer, weightKg→number; log_meal: kcal→integer
        val saveProfile = ClaudeToolMapper.toolsJson().first {
            it.jsonObject["name"]!!.jsonPrimitive.content == "save_profile"
        }.jsonObject["input_schema"]!!.jsonObject["properties"]!!.jsonObject
        assertEquals("integer", saveProfile["heightCm"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("number", saveProfile["weightKg"]!!.jsonObject["type"]!!.jsonPrimitive.content)

        val logMeal = ClaudeToolMapper.toolsJson().first {
            it.jsonObject["name"]!!.jsonPrimitive.content == "log_meal"
        }.jsonObject["input_schema"]!!.jsonObject["properties"]!!.jsonObject
        assertEquals("integer", logMeal["kcal"]!!.jsonObject["type"]!!.jsonPrimitive.content)
    }
}
