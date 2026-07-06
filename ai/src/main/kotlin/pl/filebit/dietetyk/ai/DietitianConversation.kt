package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Prowadzi rozmowę z Claude z pętlą tool-use: wysyła kontekst + narzędzia, a gdy AI woła narzędzie —
 * wykonuje je przez [ToolHandler] i oddaje wynik, aż AI skończy turę. To tu realizuje się „AI decyduje
 * o wszystkim": model sam wybiera, których narzędzi użyć i kiedy.
 *
 * `history` jest mutowana (rośnie o tury) — warstwa `:app` trzyma ją per-rozmowa i persystuje.
 */
class DietitianConversation(
    private val api: ClaudeApi,
    private val tools: JsonArray = ClaudeToolMapper.toolsJson()
) {
    /** Bezpiecznik przed zapętleniem tool-use (gdyby model nie kończył tury). */
    private val maxToolRounds = 12

    companion object {
        const val MAX_HISTORY = 40

        private fun JsonObject.blocks(): JsonArray = this["content"] as? JsonArray ?: JsonArray(emptyList())
        private fun JsonObject.role(): String? = this["role"]?.jsonPrimitive?.content
        private fun JsonArray.hasType(type: String): Boolean =
            any { (it as? JsonObject)?.get("type")?.jsonPrimitive?.content == type }

        /**
         * Usuwa niespójności tool_use/tool_result, które inaczej dają API 400 „tool_use without tool_result".
         * Odzyskuje historie zepsute przez wcześniejszy bailout (assistant z tool_use bez wyniku) —
         * bez potrzeby „Wyczyść rozmowę". Usuwa też osierocone tool_result (bez poprzedzającego tool_use).
         */
        fun sanitizeHistory(h: MutableList<JsonObject>) {
            var i = 0
            while (i < h.size) {
                val m = h[i]
                val blocks = m.blocks()
                if (m.role() == "assistant" && blocks.hasType("tool_use")) {
                    val next = h.getOrNull(i + 1)
                    val nextIsResult = next?.role() == "user" && next.blocks().hasType("tool_result")
                    if (!nextIsResult) { h.removeAt(i); continue }   // wiszący tool_use → usuń
                }
                if (m.role() == "user" && blocks.hasType("tool_result")) {
                    val prev = h.getOrNull(i - 1)
                    val prevIsToolUse = prev?.role() == "assistant" && prev.blocks().hasType("tool_use")
                    if (!prevIsToolUse) { h.removeAt(i); continue }  // osierocony tool_result → usuń
                }
                i++
            }
        }
        /** Utnij historię do ostatnich [max] tur; okno MUSI zaczynać się od wiadomości user typu text
         * (inaczej wiszący tool_result → 400). Nie przecina par tool_use/tool_result. */
        fun trimHistory(history: MutableList<JsonObject>, max: Int) {
            while (history.size > max) history.removeAt(0)
            while (history.isNotEmpty()) {
                val m = history.first()
                val role = m["role"]?.jsonPrimitive?.content
                val firstType = (m["content"] as? JsonArray)?.firstOrNull()?.jsonObject?.get("type")?.jsonPrimitive?.content
                if (role == "user" && firstType == "text") break
                history.removeAt(0)
            }
        }
    }

    suspend fun send(
        systemPrompt: String,
        history: MutableList<JsonObject>,
        userMessage: String,
        handler: ToolHandler,
        model: String = ClaudeConfig.MODEL_CHAT,
        maxTokens: Int = ClaudeConfig.DEFAULT_MAX_TOKENS,
        imageB64: String? = null
    ): String {
        // Napraw ewentualne niespójności tool_use/tool_result (odzysk zepsutej historii) PRZED oknem.
        sanitizeHistory(history)
        // Okno kontekstu: trzymaj tylko ostatnie tury do API (koszt/limit tokenów).
        // Trwała historia (Room + prefs) jest pełna; stan trwały niesie DietitianContext.
        trimHistory(history, MAX_HISTORY)
        history += if (imageB64 != null) ClaudeMessages.userContent(userMessage, imageB64)
                   else ClaudeMessages.userText(userMessage)

        var rounds = 0
        while (true) {
            val request = ClaudeMessages.buildRequest(model, maxTokens, systemPrompt, history, tools)
            val turn = ClaudeMessages.parseResponse(api.send(request))
            history += ClaudeMessages.assistantTurn(turn.rawContent)

            if (turn.stopReason != "tool_use" || turn.toolUses.isEmpty()) return turn.text

            if (++rounds > maxToolRounds) {
                // Zdejmij świeżo dodany assistant-turn z wiszącym tool_use (bez wyników) —
                // inaczej historia zostaje niespójna i każdy kolejny send da API 400.
                history.removeAt(history.lastIndex)
                return turn.text.ifBlank { "Nie udało mi się dokończyć tej akcji — spróbujmy jeszcze raz." }
            }

            val results = turn.toolUses.map { use ->
                val result = runCatching { handler.handle(use.name, use.input) }
                    .getOrElse { ToolResult("Błąd narzędzia ${use.name}: ${it.message}", isError = true) }
                use to result
            }
            history += ClaudeMessages.toolResults(results)
        }
    }
}
