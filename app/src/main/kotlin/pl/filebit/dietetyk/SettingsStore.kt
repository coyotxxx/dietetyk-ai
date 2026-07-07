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

    /** Czy przeniesiono cel/posiłki/preferencje z prefs do Room (jednorazowy backfill). */
    var prefsMigratedToProfile: Boolean
        get() = prefs.getBoolean("prefs_migrated_profile", false)
        set(v) { prefs.edit().putBoolean("prefs_migrated_profile", v).apply() }

    /** Waga docelowa w kg (0 = nieustawiona). LEGACY — źródło dla backfillu, potem profil. */
    var goalWeightKg: Double
        get() = prefs.getFloat("goal_weight", 0f).toDouble()
        set(v) { prefs.edit().putFloat("goal_weight", v.toFloat()).apply() }

    /** Liczba posiłków dziennie (2–8). */
    var mealsPerDay: Int
        get() = prefs.getInt("meals_per_day", 4)
        set(v) { prefs.edit().putInt("meals_per_day", v.coerceIn(2, 8)).apply() }

    /** Preferencje/alergie (krótki tekst zebrany przez AI). */
    var dietaryPrefs: String
        get() = prefs.getString("dietary_prefs", "").orEmpty()
        set(v) { prefs.edit().putString("dietary_prefs", v).apply() }

    /** Historia rozmowy dla API Claude (JSON array tur) — trwałość między uruchomieniami. */
    var chatHistoryJson: String
        get() = prefs.getString("chat_history", "[]").orEmpty()
        set(v) { prefs.edit().putString("chat_history", v).apply() }

    /** Domyślny wariant przepisu (0=Tradycyjnie, 1=Air Fryer, 2=Thermomix). */
    var recipeVariant: Int
        get() = prefs.getInt("recipe_variant", 0)
        set(v) { prefs.edit().putInt("recipe_variant", v).apply() }

    /** Data ostatniego pokazania proaktywnej podpowiedzi danego typu (cooldown, per InsightType.name). */
    fun insightShownDate(type: String): java.time.LocalDate? =
        prefs.getString("insight_shown_$type", null)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
    fun markInsightShown(type: String, date: java.time.LocalDate) {
        prefs.edit().putString("insight_shown_$type", date.toString()).apply()
    }

    /** Czy pokazano już jednorazową kartę „jak korzystać" (po wywiadzie, na Dziś). */
    var howToShown: Boolean
        get() = prefs.getBoolean("how_to_shown", false)
        set(v) { prefs.edit().putBoolean("how_to_shown", v).apply() }

    /** Sprzęt kuchenny użytkownika (CSV: "airfryer,thermomix") — filtruje warianty przepisów.
     *  Kuchenka/piekarnik („tradycyjnie") zawsze dostępne. Puste = tylko tradycyjnie. */
    var kitchenEquipment: String
        get() = prefs.getString("kitchen_equipment", "").orEmpty()
        set(v) { prefs.edit().putString("kitchen_equipment", v).apply() }

    /** Ton rozmowy dietetyka: "gentle" | "balanced" | "tough". */
    var aiTone: String
        get() = prefs.getString("ai_tone", "balanced").orEmpty()
        set(v) { prefs.edit().putString("ai_tone", v).apply() }

    /** Motyw: "system" | "light" | "dark". */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system").orEmpty()
        set(v) { prefs.edit().putString("theme_mode", v).apply() }

    /** Czy proaktywne powiadomienia (wizyty kontrolne) są włączone. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean("notifications_enabled", true)
        set(v) { prefs.edit().putBoolean("notifications_enabled", v).apply() }

    /** Czy codzienna automatyczna kopia zapasowa (lokalna) jest włączona. */
    var autoBackupEnabled: Boolean
        get() = prefs.getBoolean("auto_backup_enabled", true)
        set(v) { prefs.edit().putBoolean("auto_backup_enabled", v).apply() }

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

    /** Liczba posiłków ze statusem EATEN danego dnia (do metryki adherencji). */
    fun eatenCountForDay(dayKey: String): Int {
        val fromStatus = mealEntries(dayKey).count { it.split('\u0001').getOrNull(1) == "EATEN" }
        return if (fromStatus > 0) fromStatus else eatenMeals(dayKey).size
    }

    fun setMealStatus(dayKey: String, name: String, status: String, logId: Long = 0L) {
        val filtered = mealEntries(dayKey).filterNot { it.startsWith("$name") }.toMutableSet()
        if (status != "PLANNED") filtered.add("$name$status$logId")
        prefs.edit().putStringSet("mealstatus_$dayKey", filtered).apply()
    }

    private companion object { const val KEY_API = "claude_api_key" }
}
