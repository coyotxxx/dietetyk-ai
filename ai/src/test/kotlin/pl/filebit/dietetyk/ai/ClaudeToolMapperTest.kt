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
        // log_meal ma param 'meal' typu object; calculate_targets nie ma paramów
        val logMeal = ClaudeToolMapper.toolsJson().first {
            it.jsonObject["name"]!!.jsonPrimitive.content == "log_meal"
        }.jsonObject
        val mealType = logMeal["input_schema"]!!.jsonObject["properties"]!!.jsonObject["meal"]!!
            .jsonObject["type"]!!.jsonPrimitive.content
        assertEquals("object", mealType)
    }
}
