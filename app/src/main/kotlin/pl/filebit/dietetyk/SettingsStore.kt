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

    /** Wypite ml wody dla danego dnia (klucz np. "water_20260705"). Resetuje się per dzień. */
    fun waterMl(dayKey: String): Int = prefs.getInt("water_$dayKey", 0)
    fun setWaterMl(dayKey: String, ml: Int) { prefs.edit().putInt("water_$dayKey", ml.coerceAtLeast(0)).apply() }

    /** Czy pokazano już ekran powitalny (onboarding). */
    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(v) { prefs.edit().putBoolean("onboarding_done", v).apply() }

    /** Imię użytkownika (do spersonalizowanego powitania). */
    var userName: String
        get() = prefs.getString("user_name", "").orEmpty()
        set(v) { prefs.edit().putString("user_name", v).apply() }

    /** Zjedzone posiłki danego dnia (po nazwie) — status na Dziś. */
    fun eatenMeals(dayKey: String): Set<String> = prefs.getStringSet("eaten_$dayKey", emptySet()) ?: emptySet()
    fun markMealEaten(dayKey: String, name: String) {
        prefs.edit().putStringSet("eaten_$dayKey", eatenMeals(dayKey) + name).apply()
    }

    private companion object { const val KEY_API = "claude_api_key" }
}
