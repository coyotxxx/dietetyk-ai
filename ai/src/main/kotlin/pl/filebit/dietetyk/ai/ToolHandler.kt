package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonObject

/** Wynik wykonania narzędzia (treść do `tool_result`; `isError` → blok błędu). */
data class ToolResult(val content: String, val isError: Boolean = false)

/**
 * Wykonawca narzędzi AI. Implementuje go warstwa `:app` (podpięta do `:data` + `:core-domain`):
 * mapuje nazwę narzędzia z [pl.filebit.dietetyk.core.aicontract.AiToolCatalog] na realną akcję
 * (zapis profilu, liczenie celu, log posiłku, wizyta…). To tu AI faktycznie „działa" na danych.
 */
interface ToolHandler {
    suspend fun handle(name: String, input: JsonObject): ToolResult
}
