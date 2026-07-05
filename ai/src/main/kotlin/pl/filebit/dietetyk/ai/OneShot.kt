package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonArray

/** Jednorazowe zapytanie do Claude (bez pętli narzędzi). Zwraca sam tekst odpowiedzi. */
object OneShot {
    suspend fun ask(api: ClaudeApi, system: String, user: String, maxTokens: Int = 400): String {
        val request = ClaudeMessages.buildRequest(
            model = ClaudeConfig.MODEL_CHAT,
            maxTokens = maxTokens,
            systemPrompt = system,
            messages = listOf(ClaudeMessages.userText(user)),
            tools = JsonArray(emptyList())
        )
        return ClaudeMessages.parseResponse(api.send(request)).text.trim()
    }
}
