package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

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
    private val maxToolRounds = 8

    suspend fun send(
        systemPrompt: String,
        history: MutableList<JsonObject>,
        userMessage: String,
        handler: ToolHandler,
        model: String = ClaudeConfig.MODEL_CHAT,
        maxTokens: Int = ClaudeConfig.DEFAULT_MAX_TOKENS
    ): String {
        history += ClaudeMessages.userText(userMessage)

        var rounds = 0
        while (true) {
            val request = ClaudeMessages.buildRequest(model, maxTokens, systemPrompt, history, tools)
            val turn = ClaudeMessages.parseResponse(api.send(request))
            history += ClaudeMessages.assistantTurn(turn.rawContent)

            if (turn.stopReason != "tool_use" || turn.toolUses.isEmpty()) return turn.text

            if (++rounds > maxToolRounds) {
                return turn.text.ifBlank { "Przepraszam, coś poszło nie tak przy wykonywaniu akcji — spróbujmy jeszcze raz." }
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
