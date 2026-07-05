package pl.filebit.dietetyk

import android.content.Context

/**
 * Ustawienia (BYOK klucz Claude API). Domyślny klucz pochodzi z `BuildConfig.CLAUDE_API_KEY`,
 * wstrzykiwanego z `local.properties` (poza gitem) — źródło na GitHubie zostaje czyste.
 * User może nadpisać własnym kluczem w ustawieniach. Docelowo: EncryptedSharedPreferences.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("dietetyk_settings", Context.MODE_PRIVATE)

    /** Zapisany klucz, a gdy pusty — domyślny z BuildConfig (może być pusty w czystym buildzie). */
    var apiKey: String
        get() = prefs.getString(KEY_API, "").orEmpty().ifBlank { BuildConfig.CLAUDE_API_KEY }
        set(value) { prefs.edit().putString(KEY_API, value).apply() }

    private companion object { const val KEY_API = "claude_api_key" }
}
