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
            "Kroki podaj jako TABLICĘ krótkich, imperatywnych zdań — każdy element = jedna czynność, " +
            "W KOLEJNOŚCI wykonania, z konkretnymi parametrami (temperatura, czas). 3-6 kroków na wariant.\n" +
            "Zwróć DOKŁADNIE taki JSON (wartości to tablice stringów):\n" +
            "{\"tradycyjnie\":[\"krok 1\",\"krok 2\",\"...\"]," +
            "\"airfryer\":[\"krok 1 z temp. i czasem\",\"...\"]," +
            "\"thermomix\":[\"krok 1 z ustawieniami\",\"...\"]}"
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
