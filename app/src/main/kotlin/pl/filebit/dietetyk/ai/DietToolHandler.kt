package pl.filebit.dietetyk.ai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.data.db.FoodProductEntity
import pl.filebit.dietetyk.data.db.FoodProductSeed
import pl.filebit.dietetyk.data.db.PlanEntity
import pl.filebit.dietetyk.core.adapt.CheckInEngine
import pl.filebit.dietetyk.core.calc.GoalPipeline
import pl.filebit.dietetyk.core.model.AiDayPlan
import pl.filebit.dietetyk.core.model.AiMealRecipe
import pl.filebit.dietetyk.core.model.AiRecipeIngredient
import pl.filebit.dietetyk.core.model.FoodProductModel
import pl.filebit.dietetyk.core.plan.PlanValidator
import pl.filebit.dietetyk.core.plan.ValidationContext
import pl.filebit.dietetyk.core.model.ActivityLevel
import pl.filebit.dietetyk.core.model.ClinicalContext
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile
import pl.filebit.dietetyk.core.model.WeightSample
import pl.filebit.dietetyk.data.db.AiMemoryEntity
import pl.filebit.dietetyk.data.db.EnergyLogEntity

/**
 * Wykonawca narzędzi AI podpięty do realnych danych i silnika. To TU „AI działa": model woła
 * narzędzie z [pl.filebit.dietetyk.core.aicontract.AiToolCatalog], a my wykonujemy akcję na
 * `:data` (repozytoria) + `:core-domain` (silnik). Liczby ZAWSZE z kodu — nigdy z AI.
 *
 * Część narzędzi (plan/produkty/zakupy) na razie zwraca „wkrótce" — dojdą z encjami planu i OFF.
 */
class DietToolHandler(
    private val app: DietetykApp,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) : ToolHandler {

    override suspend fun handle(name: String, input: JsonObject): ToolResult {
        val now = nowProvider()
        return when (name) {
            "calculate_targets" -> calculateTargets(now)
            "log_measurement" -> logMeasurement(input, now)
            "log_meal" -> logMeal(input, now)
            "save_visit_note" -> saveVisitNote(input, now)
            "save_profile" -> saveProfile(input, now)
            "get_history" -> history(now)
            "run_checkin" -> runCheckin(now)
            "save_diet_plan" -> saveDietPlan(input, now)
            "search_products" -> searchProducts(input)
            "add_missing_product" -> addMissingProduct(input)
            else -> ToolResult("Narzędzie '$name' będzie dostępne wkrótce.")
        }
    }

    private suspend fun calculateTargets(now: Long): ToolResult {
        val profile = app.profileRepo.get() ?: return ToolResult("Brak profilu — najpierw zrób wywiad (save_profile).", isError = true)
        val weight = app.weightRepo.latest()?.weightKg
        val g = GoalPipeline.compute(profile, latestMeasuredWeightKg = weight)
        return ToolResult("Cel: ${g.kcal} kcal (białko ${g.proteinG} g, węgle ${g.carbsG} g, tłuszcz ${g.fatG} g). ${g.breakdown.deficitLabel}. ${g.breakdown.tdeeFormulaText}.")
    }

    private suspend fun logMeasurement(input: JsonObject, now: Long): ToolResult {
        val weight = input.double("weightKg") ?: input.double("waga")
            ?: return ToolResult("Podaj wagę (weightKg).", isError = true)
        app.weightRepo.add(WeightSample(dateMs = now, weightKg = weight), now)
        return ToolResult("Zapisałem wagę: $weight kg.")
    }

    private suspend fun logMeal(input: JsonObject, now: Long): ToolResult {
        val kcal = input.int("kcal") ?: return ToolResult("Podaj kcal posiłku.", isError = true)
        app.database.energyLogDao().insert(
            EnergyLogEntity(
                dateMs = now, kcalConsumed = kcal, isComplete = false,
                proteinG = input.int("proteinG") ?: 0,
                carbsG = input.int("carbsG") ?: 0,
                fatG = input.int("fatG") ?: 0
            )
        )
        val name = input.string("name")?.let { " ($it)" } ?: ""
        return ToolResult("Zapisałem posiłek$name: $kcal kcal.")
    }

    private suspend fun saveVisitNote(input: JsonObject, now: Long): ToolResult {
        val note = input.string("note") ?: return ToolResult("Brak treści notatki.", isError = true)
        app.database.aiMemoryDao().insert(AiMemoryEntity(note = note, createdAt = now))
        return ToolResult("Zanotowałem.")
    }

    private suspend fun saveProfile(input: JsonObject, now: Long): ToolResult {
        val p = input["profile"]?.jsonObject ?: input   // wspiera płaskie pola i zagnieżdżone
        p.string("name")?.let { app.settings.userName = it }
        p.double("goalWeightKg")?.let { app.settings.goalWeightKg = it }
        p.int("mealsPerDay")?.let { app.settings.mealsPerDay = it }
        p.string("preferences")?.let { app.settings.dietaryPrefs = it }
        val profile = NutritionProfile(
            gender = enumOr(p.string("gender"), Gender.MALE),
            ageYears = p.int("ageYears") ?: p.int("wiek") ?: 30,
            heightCm = p.int("heightCm") ?: p.int("wzrost") ?: 175,
            weightKg = p.double("weightKg") ?: p.double("waga"),
            activityLevel = enumOr(p.string("activityLevel"), ActivityLevel.MODERATE),
            daysPerWeek = p.int("daysPerWeek") ?: 0,
            goal = enumOr(p.string("goal"), DietGoalType.MAINTAIN),
            paceKgPerWeek = p.double("paceKgPerWeek") ?: 0.5
        )
        app.profileRepo.save(profile, now)
        return ToolResult("Zapisałem profil.")
    }

    private suspend fun history(now: Long): ToolResult {
        val ctx = app.contextBuilder.build(now) ?: return ToolResult("Brak profilu — jesteśmy na etapie wywiadu.")
        val trend = if (ctx.weightTrend.hasEnoughData) "trend ${ctx.weightTrend.direction}" else "za mało pomiarów wagi"
        return ToolResult("Waga: ${ctx.latestWeightKg ?: "?"} kg, $trend. Trzymanie planu 14d: kcal ${ctx.adherence14d.avgKcalPct}%. Dni pełnych logów: ${ctx.completeLogDays14d}.")
    }

    private suspend fun searchProducts(input: JsonObject): ToolResult {
        val query = input.string("query")?.takeIf { it.isNotBlank() }
            ?: return ToolResult("Podaj nazwę produktu (query).", isError = true)
        val hits = app.database.foodProductDao().search(FoodProductSeed.normalize(query))
        if (hits.isEmpty()) return ToolResult("Brak w bazie produktu: $query. Wartości oszacuj ostrożnie lub dodaj przez add_missing_product.")
        val lines = hits.joinToString("\n") { p ->
            "• ${p.name} (na 100g surowego): ${p.kcal} kcal, B ${p.proteinG}g, W ${p.carbsG}g, T ${p.fatG}g"
        }
        return ToolResult("Produkty w bazie (surowe, na 100g):\n$lines")
    }

    private suspend fun addMissingProduct(input: JsonObject): ToolResult {
        val query = input.string("query")
        val barcode = input.string("barcode")
        if (query.isNullOrBlank() && barcode.isNullOrBlank()) {
            return ToolResult("Podaj nazwę (query) lub kod kreskowy (barcode).", isError = true)
        }
        val off = app.offClient.lookup(query, barcode)
            ?: return ToolResult("Nie znalazłem w OpenFoodFacts. Oszacuj wartości ostrożnie lub dopytaj użytkownika.")
        app.database.foodProductDao().insertAll(
            listOf(
                FoodProductEntity(
                    name = off.name, nameNorm = FoodProductSeed.normalize(off.name),
                    kcal = off.kcal, proteinG = off.proteinG, carbsG = off.carbsG, fatG = off.fatG,
                    category = "OpenFoodFacts", source = "off"
                )
            )
        )
        return ToolResult("Dodałem do bazy: ${off.name} (na 100g): ${off.kcal} kcal, B ${off.proteinG}g, W ${off.carbsG}g, T ${off.fatG}g.")
    }

    private suspend fun saveDietPlan(input: JsonObject, now: Long): ToolResult {
        val mealsArr = input["meals"]?.let { it as? JsonArray }
            ?: return ToolResult("Brak posiłków (meals).", isError = true)
        if (mealsArr.isEmpty()) return ToolResult("Plan jest pusty.", isError = true)

        val goal = app.profileRepo.get()?.let {
            GoalPipeline.compute(it, latestMeasuredWeightKg = app.weightRepo.latest()?.weightKg)
        } ?: return ToolResult("Brak profilu/celu — najpierw calculate_targets.", isError = true)

        // Parsuj posiłki ze strukturalnymi składnikami
        data class ParsedMeal(val name: String, val time: String, val recipe: AiMealRecipe)
        val parsed = mealsArr.mapNotNull { it as? JsonObject }.map { mo ->
            val name = mo.string("name") ?: "Posiłek"
            val ings = (mo["ingredients"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.map { io ->
                AiRecipeIngredient(io.string("productName") ?: "", io.int("grams") ?: 0)
            } ?: emptyList()
            ParsedMeal(name, mo.string("timeHint") ?: "", AiMealRecipe(name, 0, mo.int("prepMinutes") ?: 0, ings))
        }
        val plan = AiDayPlan(parsed.map { it.recipe })

        // Baza produktów → model do walidatora + mapa kategorii (do listy zakupów)
        val entities = app.database.foodProductDao().all()
        val byName = entities.associate { e ->
            e.name.lowercase() to FoodProductModel(
                id = e.id, name = e.name, kcalPer100g = e.kcal,
                proteinPer100g = e.proteinG, carbsPer100g = e.carbsG, fatPer100g = e.fatG
            )
        }
        val catByName = entities.associate { it.name.lowercase() to it.category }

        val ctx = ValidationContext(
            expectedMealsCount = plan.meals.size,
            targetKcal = goal.kcal, targetProteinG = goal.proteinG,
            targetCarbsG = goal.carbsG, targetFatG = goal.fatG,
            perMealProteinMinG = 0, maxCookingMinutesPerMeal = 240,
            productsByName = byName
        )
        val result = PlanValidator.validate(plan, ctx)
        if (!result.isValid) {
            // Guardrail: silnik odrzuca plan → AI dostaje feedback i poprawia gramatury.
            return ToolResult(PlanValidator.buildRetryFeedback(result), isError = true)
        }

        // Per-posiłek przeliczone z bazy (do wyświetlenia na Dziś/Plan)
        val mealsJson = buildJsonArray {
            parsed.forEach { pm ->
                var k = 0.0; var pr = 0.0; var c = 0.0; var f = 0.0
                pm.recipe.ingredients.forEach { ing ->
                    resolve(ing.productName, byName)?.let { p ->
                        val fac = ing.grams / 100.0
                        k += p.kcalPer100g * fac; pr += p.proteinPer100g * fac; c += p.carbsPer100g * fac; f += p.fatPer100g * fac
                    }
                }
                add(buildJsonObject {
                    put("name", pm.name); put("timeHint", pm.time); put("prepMinutes", pm.recipe.prepMinutes)
                    put("kcal", k.toInt()); put("proteinG", pr.toInt()); put("carbsG", c.toInt()); put("fatG", f.toInt())
                    put("ingredients", pm.recipe.ingredients.joinToString(", ") { "${it.productName} ${it.grams}g" })
                    put("ings", buildJsonArray {
                        pm.recipe.ingredients.forEach { ing ->
                            val cat = catByName[ing.productName.trim().lowercase()]
                                ?: catByName.entries.firstOrNull { (kk, _) -> kk.contains(ing.productName.trim().lowercase()) }?.value
                                ?: "Inne"
                            add(buildJsonObject { put("name", ing.productName); put("grams", ing.grams); put("cat", cat) })
                        }
                    })
                })
            }
        }
        val planJson = buildJsonObject { put("meals", mealsJson) }.toString()
        app.database.planDao().upsert(PlanEntity(planJson = planJson, targetKcal = goal.kcal, updatedAt = now, dirty = true))

        val ct = result.correctedTotal
        val warn = if (result.warnings.isNotEmpty()) " Uwagi: " + result.warnings.take(3).joinToString("; ") { it.message } else ""
        return ToolResult("Zapisałem plan (przeliczony z bazy): ${plan.meals.size} posiłków, ${ct.totalKcal} kcal — B ${ct.totalProteinG}g / W ${ct.totalCarbsG}g / T ${ct.totalFatG}g (cel ${goal.kcal} kcal).$warn")
    }

    /** Dopasowanie nazwy do produktu bazy: exact → substring (spójne z PlanValidator). */
    private fun resolve(name: String, byName: Map<String, FoodProductModel>): FoodProductModel? {
        val key = name.trim().lowercase()
        return byName[key] ?: byName.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.value
    }

    private suspend fun runCheckin(now: Long): ToolResult {
        val ctx = app.contextBuilder.build(now) ?: return ToolResult("Brak profilu — najpierw wywiad.", isError = true)
        val currentKcal = ctx.currentGoal?.kcal ?: return ToolResult("Brak celu kcal.", isError = true)
        val report = CheckInEngine.run(
            profile = ctx.profile, clinical = ctx.clinical, currentKcal = currentKcal,
            weightTrend = ctx.weightTrend, adherence14d = ctx.adherence14d,
            currentWeightKg = ctx.latestWeightKg
        )
        val delta = if (report.kcalDelta != 0) " (${if (report.kcalDelta > 0) "+" else ""}${report.kcalDelta} kcal → ${report.newKcal})" else ""
        return ToolResult("Wizyta: ${report.verdict}$delta — ${report.headline} ${report.detail}")
    }

    // --- pomocnicze odczyty z JSON ---
    private fun JsonObject.double(k: String): Double? = this[k]?.jsonPrimitive?.content?.toDoubleOrNull()
    private fun JsonObject.int(k: String): Int? = this[k]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
    private fun JsonObject.string(k: String): String? = this[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
    private inline fun <reified T : Enum<T>> enumOr(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it.uppercase()) }.getOrNull() } ?: default
}
