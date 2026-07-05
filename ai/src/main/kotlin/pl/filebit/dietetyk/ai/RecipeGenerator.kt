package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonArray

/**
 * Generator przepisów na żądanie — jednorazowe wywołanie Claude (bez pętli narzędzi).
 * Zwraca surowy JSON z trzema wariantami przygotowania.
 */
class RecipeGenerator(private val api: ClaudeApi) {

    suspend fun generate(mealName: String, ingredients: String): String {
        val system = "Jesteś dietetykiem-kucharzem. Piszesz zwięźle, po polsku, zwykłym tekstem (bez markdown). " +
            "Odpowiadasz WYŁĄCZNIE surowym obiektem JSON, bez komentarza przed ani po."
        val prompt = "Podaj przepis na danie \"$mealName\" ze składników: $ingredients.\n" +
            "Zwróć DOKŁADNIE taki JSON:\n" +
            "{\"tradycyjnie\":\"kroki przygotowania na kuchence/w piekarniku, 2-5 zdań\"," +
            "\"airfryer\":\"kroki w air fryerze z temperaturą i czasem, 2-5 zdań\"," +
            "\"thermomix\":\"kroki w Thermomixie z ustawieniami, 2-5 zdań\"}"
        val request = ClaudeMessages.buildRequest(
            model = ClaudeConfig.MODEL_CHAT,
            maxTokens = 1500,
            systemPrompt = system,
            messages = listOf(ClaudeMessages.userText(prompt)),
            tools = JsonArray(emptyList())
        )
        val response = api.send(request)
        return ClaudeMessages.parseResponse(response).text.trim()
    }
}
