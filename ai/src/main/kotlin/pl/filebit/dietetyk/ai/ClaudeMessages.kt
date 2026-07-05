package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Wywołanie narzędzia przez AI. */
data class ToolUse(val id: String, val name: String, val input: JsonObject)

/** Sparsowana tura asystenta. */
data class AssistantTurn(
    val text: String,
    val toolUses: List<ToolUse>,
    val stopReason: String,
    /** Surowa tablica `content` — do odesłania z powrotem w historii (wymóg tool-use). */
    val rawContent: JsonArray
)

/**
 * Składanie żądań i parsowanie odpowiedzi Messages API Claude. Budujemy JSON bezpośrednio
 * (buildJson*) zamiast polimorficznej serializacji — prościej i odporniej na warianty bloków.
 */
object ClaudeMessages {

    private val json = Json { ignoreUnknownKeys = true }

    /** Blok wiadomości usera z tekstem — start rozmowy / kolejna wypowiedź. */
    fun userText(text: String): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", text) }) })
    }

    /** Wiadomość user z wynikami narzędzi (po turze tool_use asystenta). */
    fun toolResults(results: List<Pair<ToolUse, ToolResult>>): JsonObject = buildJsonObject {
        put("role", "user")
        put("content", buildJsonArray {
            results.forEach { (use, res) ->
                add(buildJsonObject {
                    put("type", "tool_result")
                    put("tool_use_id", use.id)
                    put("content", res.content)
                    if (res.isError) put("is_error", true)
                })
            }
        })
    }

    fun buildRequest(
        model: String,
        maxTokens: Int,
        systemPrompt: String,
        messages: List<JsonObject>,
        tools: JsonArray
    ): String = buildJsonObject {
        put("model", model)
        put("max_tokens", maxTokens)
        put("system", systemPrompt)
        put("messages", buildJsonArray { messages.forEach { add(it) } })
        put("tools", tools)
    }.toString()

    fun parseResponse(body: String): AssistantTurn {
        val root = json.parseToJsonElement(body).jsonObject
        val content = root["content"]?.jsonArray ?: JsonArray(emptyList())
        val textBuilder = StringBuilder()
        val toolUses = mutableListOf<ToolUse>()
        for (block in content) {
            val obj = block.jsonObject
            when (obj["type"]?.jsonPrimitive?.content) {
                "text" -> textBuilder.append(obj["text"]?.jsonPrimitive?.content ?: "")
                "tool_use" -> toolUses += ToolUse(
                    id = obj["id"]?.jsonPrimitive?.content ?: "",
                    name = obj["name"]?.jsonPrimitive?.content ?: "",
                    input = obj["input"]?.jsonObject ?: JsonObject(emptyMap())
                )
            }
        }
        val stopReason = root["stop_reason"]?.jsonPrimitive?.content ?: "end_turn"
        return AssistantTurn(textBuilder.toString(), toolUses, stopReason, content)
    }

    /** Tura asystenta do odesłania w historii. */
    fun assistantTurn(rawContent: JsonArray): JsonObject = buildJsonObject {
        put("role", "assistant")
        put("content", rawContent)
    }
}
