package pl.filebit.dietetyk.core.plan

import pl.filebit.dietetyk.core.model.AiDayPlan
import pl.filebit.dietetyk.core.model.FoodProductModel
import kotlin.math.abs
import kotlin.math.min

enum class ValidationSeverity { ERROR, WARNING }

data class ValidationIssue(
    val severity: ValidationSeverity,
    val mealIndex: Int?,   // null = global (cały plan)
    val code: String,
    val message: String
)

data class CorrectedMacros(val totalKcal: Int, val totalProteinG: Int, val totalCarbsG: Int, val totalFatG: Int)

data class ValidationResult(
    val isValid: Boolean,                  // false = HARD errors
    val errors: List<ValidationIssue>,
    val warnings: List<ValidationIssue>,
    val correctedTotal: CorrectedMacros,
    /** Mapping AI productName → dopasowany produkt (null jeśli nie znaleziono). */
    val productMatches: Map<String, FoodProductModel?>
)

data class ValidationContext(
    val expectedMealsCount: Int,
    val targetKcal: Int,
    val targetProteinG: Int,
    val targetCarbsG: Int = 0,
    val targetFatG: Int = 0,
    val perMealProteinMinG: Int,
    val maxCookingMinutesPerMeal: Int,
    val ketoMaxCarbsG: Int? = null,
    val lowCarbDinnerMaxG: Int = 30,
    val productsByName: Map<String, FoodProductModel>,  // klucz = name.lowercase()
    /** Szew pod diety kliniczne: pusta lista na start (ARCHITECTURE.md §3). */
    val constraints: List<DietConstraint> = emptyList(),
    /** Znormalizowane nazwy produktów NIELUBIANYCH (🚫 AVOID) — walidator hard-odrzuca plan, który ich używa.
     *  Dopasowanie PRECYZYJNE (całe słowo), bias na przepust: fałszywy reject gorszy niż przepust. */
    val avoidedNorms: Set<String> = emptySet(),
    /** Znormalizowane nazwy STUBÓW (produkty-zaślepki bez makr, source="stub" — utworzone tylko po to,
     *  by AVOID nie zginął). Backstop bezpieczeństwa (Fable, warstwa 2): stub NIGDY nie może być składnikiem
     *  planu (0 kcal rozjechałoby makra) — niezależnie od tego, jak trafił. Dopasowanie jak przy [avoidedNorms]. */
    val stubNorms: Set<String> = emptySet(),
    /** Minimalna liczba UNIKALNYCH produktów w dniu (0 = wyłączone). Miękki próg (WARNING) przeciw monotonii —
     *  szczególnie w trybie SAME_DAILY, gdzie jeden dzień powtarza się przez cały tydzień. */
    val minUniqueProductsPerDay: Int = 0,
    /** Znormalizowane nazwy produktów LUBIANYCH (❤️). Reguła kotwicowa planu #1: każdy posiłek ≥1 lubiany. */
    val likedNorms: Set<String> = emptySet(),
    /** Czy to PIERWSZY plan (brak istniejącego). Włącza regułę kotwicową (WARNING, nie blokada). */
    val firstPlan: Boolean = false,
    val perSlotKcalTargets: List<Int> = emptyList(),
    val perSlotKcalTolerance: Double = 0.25,
    val enforceDailyKcal: Boolean = true,
    val enforceDailyMacros: Boolean = true,
    val dailyMacroTolerance: Double = 0.20
)

/**
 * Walidator planu dnia od AI — guardrail liczbowy ZANIM plan trafi do usera.
 * Polityka: kcal/makro deklarowane przez AI IGNORUJEMY — liczymy z bazy produktów. HARD constraints
 * → ERROR (retry/odrzuć), SOFT → WARNING (wstaw + wyjaśnij).
 *
 * Przeniesione z GymTrackera (`AiMealJsonValidator`) jako czysty `object` (usunięty `javax.inject`;
 * `FoodProduct`(Room) → [FoodProductModel]). Logika 1:1.
 */
object PlanValidator {

    fun validate(plan: AiDayPlan, ctx: ValidationContext): ValidationResult {
        val errors = mutableListOf<ValidationIssue>()
        val warnings = mutableListOf<ValidationIssue>()
        val productMatches = HashMap<String, FoodProductModel?>()

        var totalKcalReal = 0
        var totalProteinReal = 0
        var totalCarbsReal = 0
        var totalFatReal = 0

        if (plan.meals.size != ctx.expectedMealsCount) {
            errors += ValidationIssue(ValidationSeverity.ERROR, null, "meals_count_mismatch",
                "AI zwróciło ${plan.meals.size} posiłków, oczekiwano DOKŁADNIE ${ctx.expectedMealsCount}.")
        }

        plan.meals.forEachIndexed { mIdx, meal ->
            if (meal.ingredients.isEmpty()) {
                errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "empty_ingredients",
                    "Posiłek '${meal.name}' nie ma składników.")
                return@forEachIndexed
            }
            if (meal.prepMinutes > ctx.maxCookingMinutesPerMeal) {
                warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "cooking_time_exceeded",
                    "Posiłek '${meal.name}' wymaga ${meal.prepMinutes} min, max ${ctx.maxCookingMinutesPerMeal} min.")
            }

            val matchedProducts = mutableListOf<FoodProductModel>()
            var mealHasLiked = false
            var mealKcalReal = 0.0
            var mealProteinReal = 0.0
            var mealCarbsReal = 0.0
            var mealFatReal = 0.0

            meal.ingredients.forEach { ing ->
                // AVOID (🚫): produkt nielubiany → HARD reject. Match PRECYZYJNY (całe słowo), NIE substring:
                // „deser" nie łapie „ser", „porzeczka" nie łapie „por". Bias na przepust — odrzucaj tylko pewne.
                if (ctx.avoidedNorms.isNotEmpty()) {
                    val ingNorm = " ${normalizeName(ing.productName)} "
                    val hit = ctx.avoidedNorms.firstOrNull { av -> av.isNotBlank() && ingNorm.contains(" $av ") }
                    if (hit != null) errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "avoided_product",
                        "Posiłek '${meal.name}' używa '${ing.productName}', którego użytkownik NIE JE (🚫). Zastąp innym produktem.")
                }
                // STUB (zaślepka bez makr) NIGDY nie może być składnikiem — backstop niezależny od avoidedNorms.
                if (ctx.stubNorms.isNotEmpty()) {
                    val ingNorm = " ${normalizeName(ing.productName)} "
                    val hit = ctx.stubNorms.firstOrNull { s -> s.isNotBlank() && ingNorm.contains(" $s ") }
                    if (hit != null) errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "stub_ingredient",
                        "Posiłek '${meal.name}' używa '${ing.productName}' — to produkt bez wartości odżywczych (zaślepka). Użyj realnego produktu z bazy.")
                }
                // Reguła kotwicowa (plan #1): czy składnik jest LUBIANY (całe słowo, jak avoided).
                if (ctx.likedNorms.isNotEmpty()) {
                    val ingNorm = " ${normalizeName(ing.productName)} "
                    if (ctx.likedNorms.any { lk -> lk.isNotBlank() && ingNorm.contains(" $lk ") }) mealHasLiked = true
                }
                if (ing.grams < 5 || ing.grams > 1000) {
                    warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "grams_out_of_range",
                        "${ing.productName}: ${ing.grams}g poza rozsądnym zakresem [5-1000g].")
                }
                val match = matchProduct(ing.productName, ctx.productsByName)
                productMatches[ing.productName] = match
                if (match == null) {
                    warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "unknown_product",
                        "Produkt '${ing.productName}' nie znaleziony w bazie — pominięty.")
                    return@forEach
                }
                matchedProducts += match
                val factor = ing.grams / 100.0
                mealKcalReal += match.kcalPer100g * factor
                mealProteinReal += match.proteinPer100g * factor
                mealCarbsReal += match.carbsPer100g * factor
                mealFatReal += match.fatPer100g * factor
            }

            // Reguła kotwicowa planu #1: każdy posiłek ma zawierać ≥1 lubiany produkt (WARNING, nie blokada —
            // bias na przepust; egzekwuje głównie prompt, walidator tylko sygnalizuje AI do poprawy).
            if (ctx.firstPlan && ctx.likedNorms.isNotEmpty() && !mealHasLiked && meal.ingredients.isNotEmpty()) {
                warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "meal_no_liked_product",
                    "Posiłek '${meal.name}' nie zawiera żadnego produktu LUBIANEGO (❤️). W pierwszym planie każdy posiłek powinien mieć co najmniej jeden — dołóż lubiany produkt.")
            }
            if (meal.kcal > 0 && abs(meal.kcal - mealKcalReal) > meal.kcal * 0.10) {
                warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "ai_kcal_mismatch",
                    "AI deklaruje ${meal.kcal} kcal, real (z bazy): ${mealKcalReal.toInt()} kcal. Używam realnej.")
            }
            if (mealProteinReal < ctx.perMealProteinMinG) {
                warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "low_protein",
                    "Posiłek '${meal.name}' ma tylko ${mealProteinReal.toInt()}g białka, cel ≥${ctx.perMealProteinMinG}g.")
            }

            // HARD / SOFT constraints
            ctx.constraints.filter { it.priority == ConstraintPriority.HARD }.forEach { c ->
                val violators = matchedProducts.filter { p -> c.isViolated(listOf(p)) }
                if (violators.isNotEmpty()) errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "hard_constraint_violation",
                    "Posiłek '${meal.name}' łamie HARD: ${c.description}. Produkty: ${violators.joinToString(", ") { it.name }}")
            }
            ctx.constraints.filter { it.priority == ConstraintPriority.SOFT }.forEach { c ->
                val violators = matchedProducts.filter { p -> c.isViolated(listOf(p)) }
                if (violators.isNotEmpty()) warnings += ValidationIssue(ValidationSeverity.WARNING, mIdx, "soft_constraint_violation",
                    "Posiłek '${meal.name}' narusza SOFT: ${c.description}. Produkty: ${violators.joinToString(", ") { it.name }}")
            }

            if (matchedProducts.any { it.name.equals("Białko jaja", ignoreCase = true) }) {
                errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "egg_white_split_not_allowed",
                    "Posiłek '${meal.name}' używa 'Białko jaja' — zastąp produktem 'Jajko całe' i przelicz gramaturę.")
            }

            ctx.perSlotKcalTargets.getOrNull(mIdx)?.let { target ->
                val minK = target * (1.0 - ctx.perSlotKcalTolerance)
                val maxK = target * (1.0 + ctx.perSlotKcalTolerance)
                if (mealKcalReal < minK || mealKcalReal > maxK) {
                    errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "slot_kcal_off_target",
                        "Slot $mIdx ('${meal.name}'): ${mealKcalReal.toInt()} kcal, target $target kcal (zakres ${minK.toInt()}–${maxK.toInt()}).")
                }
            }

            val mealFatKcal = mealFatReal * 9.0
            if (mealKcalReal > 200 && mealFatKcal > mealKcalReal * 0.55) {
                val fatPct = (mealFatKcal * 100 / mealKcalReal).toInt()
                errors += ValidationIssue(ValidationSeverity.ERROR, mIdx, "slot_fat_too_high",
                    "Slot $mIdx ('${meal.name}'): ${mealFatReal.toInt()}g tłuszczu = $fatPct% kcal posiłku (limit 55%). Posiłek za tłusty.")
            }

            totalKcalReal += mealKcalReal.toInt()
            totalProteinReal += mealProteinReal.toInt()
            totalCarbsReal += mealCarbsReal.toInt()
            totalFatReal += mealFatReal.toInt()
        }

        // Suma dnia — ±7% albo retry
        if (totalKcalReal > 0 && ctx.enforceDailyKcal) {
            val kcalDeviation = abs(totalKcalReal - ctx.targetKcal).toDouble() / ctx.targetKcal
            val minDaily = (ctx.targetKcal * 0.93).toInt()
            val maxDaily = (ctx.targetKcal * 1.07).toInt()
            if (kcalDeviation > 0.07) {
                errors += ValidationIssue(ValidationSeverity.ERROR, null, "daily_kcal_off_target",
                    "Suma dnia: $totalKcalReal kcal, cel ${ctx.targetKcal} kcal (odchylenie ${(kcalDeviation * 100).toInt()}%, zakres $minDaily–$maxDaily). Zwiększ gramatury.")
            } else if (kcalDeviation > 0.03) {
                warnings += ValidationIssue(ValidationSeverity.WARNING, null, "daily_kcal_minor_off",
                    "Suma dnia: $totalKcalReal kcal vs cel ${ctx.targetKcal} kcal (odchylenie ${(kcalDeviation * 100).toInt()}%).")
            }
        }

        // Makro dnia — asymetryczne progi
        if (ctx.enforceDailyMacros) {
            if (ctx.targetProteinG > 0) {
                val minP = (ctx.targetProteinG * 0.85).toInt()
                if (totalProteinReal < minP) errors += ValidationIssue(ValidationSeverity.ERROR, null, "daily_protein_too_low",
                    "Białko dnia: ${totalProteinReal}g, cel ≥${ctx.targetProteinG}g (min ${minP}g). DODAJ produkty białkowe.")
                else if (totalProteinReal > ctx.targetProteinG * 1.5) warnings += ValidationIssue(ValidationSeverity.WARNING, null, "daily_protein_high",
                    "Białko dnia: ${totalProteinReal}g vs cel ${ctx.targetProteinG}g — nadmiar nie szkodzi.")
            }
            if (ctx.targetFatG > 0) {
                val minF = (ctx.targetFatG * 0.75).toInt()
                if (totalFatReal < minF) errors += ValidationIssue(ValidationSeverity.ERROR, null, "daily_fat_too_low",
                    "Tłuszcz dnia: ${totalFatReal}g, cel ≥${ctx.targetFatG}g (min ${minF}g). DODAJ zdrowe tłuszcze.")
                else if (totalFatReal > ctx.targetFatG * 1.3) warnings += ValidationIssue(ValidationSeverity.WARNING, null, "daily_fat_high",
                    "Tłuszcz dnia: ${totalFatReal}g vs cel ${ctx.targetFatG}g.")
            }
            if (ctx.targetCarbsG > 0) {
                val dev = abs(totalCarbsReal - ctx.targetCarbsG).toDouble() / ctx.targetCarbsG
                if (dev > 0.30) warnings += ValidationIssue(ValidationSeverity.WARNING, null, "daily_carbs_off",
                    "Węgle dnia: ${totalCarbsReal}g vs cel ${ctx.targetCarbsG}g (odchylenie ${(dev * 100).toInt()}%).")
            }
        }

        ctx.ketoMaxCarbsG?.let { maxCarbs ->
            if (totalCarbsReal > maxCarbs) errors += ValidationIssue(ValidationSeverity.ERROR, null, "keto_carbs_exceeded",
                "Keto: węgli ${totalCarbsReal}g > limit ${maxCarbs}g.")
        }

        ctx.constraints.filterIsInstance<DietConstraint.SafetyKcalMin>().firstOrNull()?.let { safety ->
            if (totalKcalReal in 1 until safety.minKcal) errors += ValidationIssue(ValidationSeverity.ERROR, null, "safety_kcal_too_low",
                "Plan ma $totalKcalReal kcal — poniżej bezpiecznego min ${safety.minKcal} kcal.")
        }

        plan.meals.lastOrNull()?.let { dinner ->
            val dinnerCarbs = dinner.ingredients.sumOf { ing ->
                val p = matchProduct(ing.productName, ctx.productsByName)
                if (p != null) p.carbsPer100g * ing.grams / 100.0 else 0.0
            }
            if (dinnerCarbs > ctx.lowCarbDinnerMaxG) warnings += ValidationIssue(ValidationSeverity.WARNING, plan.meals.lastIndex, "dinner_high_carb",
                "Kolacja '${dinner.name}': ${dinnerCarbs.toInt()}g węgli (zalecane <${ctx.lowCarbDinnerMaxG}g).")
        }

        // Różnorodność dnia (miękko) — przeciw monotonii, ważne zwłaszcza w SAME_DAILY (dzień × 7).
        if (ctx.minUniqueProductsPerDay > 0) {
            val uniqueCount = productMatches.values.filterNotNull().map { it.name.lowercase() }.distinct().size
            if (uniqueCount in 1 until ctx.minUniqueProductsPerDay) {
                warnings += ValidationIssue(ValidationSeverity.WARNING, null, "low_variety_day",
                    "Dzień ma tylko $uniqueCount różnych produktów (zalecane ≥${ctx.minUniqueProductsPerDay}) — dołóż urozmaicenia dla lepszej mikroskładnikowej pokrycia.")
            }
        }

        return ValidationResult(
            isValid = errors.isEmpty(), errors = errors, warnings = warnings,
            correctedTotal = CorrectedMacros(totalKcalReal, totalProteinReal, totalCarbsReal, totalFatReal),
            productMatches = productMatches
        )
    }

    /** Feedback do AI przy retry — lista błędów do uniknięcia. */
    fun buildRetryFeedback(result: ValidationResult): String = buildString {
        if (result.errors.isEmpty()) return@buildString
        appendLine("Twoja poprzednia odpowiedź zawierała poważne błędy. Wygeneruj plan ponownie unikając:")
        result.errors.forEach { appendLine("- [${it.code}] ${it.message}") }
    }

    /** Match produktu: exact → substring → Levenshtein (≤2, ≤3 dla długich nazw). */
    private fun matchProduct(query: String, productsByName: Map<String, FoodProductModel>): FoodProductModel? {
        val key = query.trim().lowercase()
        productsByName[key]?.let { return it }
        productsByName.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.let { return it.value }
        val maxDist = if (key.length > 12) 3 else 2
        var bestMatch: FoodProductModel? = null
        var bestDist = Int.MAX_VALUE
        for ((k, product) in productsByName) {
            val dist = levenshtein(key, k)
            if (dist < bestDist && dist <= maxDist) { bestDist = dist; bestMatch = product }
        }
        return bestMatch
    }

    /** Normalizacja nazwy (lowercase + fold polskich znaków) — spójna z FoodProductSeed.normalize. */
    private fun normalizeName(s: String): String = buildString {
        for (c in s.lowercase().trim()) append(
            when (c) { 'ą' -> 'a'; 'ć' -> 'c'; 'ę' -> 'e'; 'ł' -> 'l'; 'ń' -> 'n'; 'ó' -> 'o'; 'ś' -> 's'; 'ź' -> 'z'; 'ż' -> 'z'; else -> c }
        )
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val prev = IntArray(b.length + 1) { it }
        val curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = min(min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            for (j in 0..b.length) prev[j] = curr[j]
        }
        return curr[b.length]
    }
}
