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

    /** Historia rozmowy dla API Claude (JSON array tur) — trwałość między uruchomieniami. */
    var chatHistoryJson: String
        get() = prefs.getString("chat_history", "[]").orEmpty()
        set(v) { prefs.edit().putString("chat_history", v).apply() }

    /** Motyw: "system" | "light" | "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system").orEmpty()
        set(v) { prefs.edit().putString("theme_mode", v).apply() }

    /** Czy proaktywne powiadomienia (wizyty kontrolne) są włączone. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(v) { prefs.edit().putBoolean("notifications_enabled", v).apply() }

    /** Zjedzone posiłki danego dnia (stary format, do migracji). */
    fun eatenMeals(dayKey: String): Set<String> = prefs.getStringSet("eaten_$dayKey", emptySet()) ?: emptySet()

    /**
     * Status posiłku danego dnia. Zapis: StringSet z wpisami "nameSTATUSlogId".
     * STATUS: EATEN | SKIPPED | REPLACED (brak wpisu = PLANNED). logId = id wpisu energy_logs (do cofania).
     */
    private fun mealEntries(dayKey: String): Set<String> = prefs.getStringSet("mealstatus_$dayKey", emptySet()) ?: emptySet()

    /** Zwraca (status, logId). Wsteczna zgodność: stary `eaten_` traktowany jako EATEN. */
    fun mealStatus(dayKey: String, name: String): Pair<String, Long> {
        mealEntries(dayKey).forEach { e ->
            val p = e.split("")
            if (p.getOrNull(0) == name) return (p.getOrElse(1) { "PLANNED" }) to (p.getOrElse(2) { "0" }.toLongOrNull() ?: 0L)
        }
        if (eatenMeals(dayKey).contains(name)) return "EATEN" to 0L
        return "PLANNED" to 0L
    }

    fun setMealStatus(dayKey: String, name: String, status: String, logId: Long = 0L) {
        val filtered = mealEntries(dayKey).filterNot { it.startsWith("$name") }.toMutableSet()
        if (status != "PLANNED") filtered.add("$name$status$logId")
        prefs.edit().putStringSet("mealstatus_$dayKey", filtered).apply()
    }

    private companion object { const val KEY_API = "claude_api_key" }
}
