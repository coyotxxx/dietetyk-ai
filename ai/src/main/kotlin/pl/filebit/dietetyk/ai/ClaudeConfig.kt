package pl.filebit.dietetyk.ai

/**
 * Konfiguracja klienta Claude. Model wg decyzji Macieja (2026-07-05): jak w GymTrackerze —
 * **Sonnet 4.6** (`claude-sonnet-4-6`) do wszystkiego.
 */
object ClaudeConfig {
    const val ENDPOINT = "https://api.anthropic.com/v1/messages"
    const val ANTHROPIC_VERSION = "2023-06-01"

    /** Model rozmowy (jak GymTracker). */
    const val MODEL_CHAT = "claude-sonnet-4-6"
    /** Model wizyt/planów — na razie ten sam. */
    const val MODEL_VISIT = "claude-sonnet-4-6"

    const val DEFAULT_MAX_TOKENS = 8192
}
