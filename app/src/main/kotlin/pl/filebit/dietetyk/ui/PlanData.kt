package pl.filebit.dietetyk.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Odczyt/zapis planu WIELODNIOWEGO trzymanego w jednym polu `planJson`.
 * Format nowy: {"days":{"1":{"meals":[...],"targetKcal":N},...,"7":{...}}} — klucz = dzień
 * tygodnia 1–7 (Pn=1…Nd=7, `DayOfWeek.value`). Szablon powtarzalny, NIE przypięty do dat.
 * Format stary (jednodniowy): {"meals":[...]} — traktowany jako plan DZISIEJSZEGO dnia.
 * Dzięki temu zmiana formatu NIE wymaga migracji bazy (planJson to zwykły String).
 */
object PlanData {

    fun todayDow(): Int = java.time.LocalDate.now().dayOfWeek.value

    private fun root(planJson: String) =
        runCatching { Json.parseToJsonElement(planJson).jsonObject }.getOrNull()

    /** Posiłki (JsonArray) dla dnia tygodnia (1–7). null gdy brak planu na ten dzień. */
    fun mealsForDay(planJson: String, dow: Int): JsonArray? {
        val r = root(planJson) ?: return null
        val days = r["days"]?.jsonObject
        if (days != null) return days[dow.toString()]?.jsonObject?.get("meals") as? JsonArray
        // stary format {meals} → tylko dzisiejszy dzień
        if (dow == todayDow()) return r["meals"] as? JsonArray
        return null
    }

    /** Zbiór dni tygodnia (1–7), które mają zapisany niepusty plan. */
    fun daysWithPlan(planJson: String): Set<Int> {
        val r = root(planJson) ?: return emptySet()
        val days = r["days"]?.jsonObject
        if (days != null) return days.keys.mapNotNull { it.toIntOrNull() }
            .filter { (days[it.toString()]?.jsonObject?.get("meals") as? JsonArray)?.isNotEmpty() == true }
            .toSet()
        val oldMeals = r["meals"] as? JsonArray
        return if (oldMeals?.isNotEmpty() == true) setOf(todayDow()) else emptySet()
    }

    /** Ustawia posiłki dla dnia, zachowując pozostałe dni (migruje stary format przy okazji). */
    fun setDayMeals(planJson: String, dow: Int, meals: JsonArray, targetKcal: Int): String {
        val r = root(planJson)
        val existingDays = r?.get("days")?.jsonObject
        return buildJsonObject {
            put("days", buildJsonObject {
                if (existingDays != null) {
                    existingDays.forEach { (k, v) -> if (k != dow.toString()) put(k, v) }
                } else {
                    // migracja: stary {meals} → dzisiejszy dzień (jeśli to nie ten, który nadpisujemy)
                    val oldMeals = r?.get("meals") as? JsonArray
                    if (oldMeals != null && todayDow() != dow) put(todayDow().toString(), buildJsonObject { put("meals", oldMeals) })
                }
                put(dow.toString(), buildJsonObject { put("meals", meals); put("targetKcal", targetKcal) })
            })
        }.toString()
    }

    /** Kopiuje posiłki z dnia [src] do [dst]. Zwraca nowy planJson lub null gdy [src] pusty. */
    fun copyDay(planJson: String, src: Int, dst: Int, targetKcal: Int): String? {
        val meals = mealsForDay(planJson, src) ?: return null
        return setDayMeals(planJson, dst, meals, targetKcal)
    }
}

/** Krótkie etykiety dni tygodnia (Pn=1…Nd=7). */
val DOW_SHORT = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")
val DOW_LONG = listOf("Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota", "Niedziela")
