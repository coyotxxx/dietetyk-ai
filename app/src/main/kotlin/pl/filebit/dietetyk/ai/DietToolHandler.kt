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

    /** Ile razy z rzędu walidator odrzucił plan — po 2 próbach zapisujemy mimo drobnej rozbieżności
     *  (żeby AI nie pętliło save_diet_plan bez końca i nie wyczerpało limitu tur). */
    private var planRetries = 0

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
            "set_food_preference" -> setFoodPreference(input)
            "remember_context" -> rememberContext(input, now)
            "generate_shopping_list" -> generateShoppingList()
            "propose_adjustment" -> proposeAdjustment(input, now)
            "schedule_checkin" -> scheduleCheckin(input, now)
            "defer_goal" -> deferGoal(input, now)
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
        val existing = app.profileRepo.get()   // zachowaj wcześniej ustawione pola, gdy AI ich nie poda
        p.string("name")?.let { app.settings.userName = it }
        p.string("equipment")?.let { app.settings.kitchenEquipment = it.lowercase() }   // sprzęt → filtr wariantów przepisów
        val profile = NutritionProfile(
            gender = enumOr(p.string("gender"), Gender.MALE),
            ageYears = p.int("ageYears") ?: p.int("wiek") ?: 30,
            heightCm = p.int("heightCm") ?: p.int("wzrost") ?: 175,
            weightKg = p.double("weightKg") ?: p.double("waga"),
            activityLevel = enumOr(p.string("activityLevel"), ActivityLevel.MODERATE),
            daysPerWeek = p.int("daysPerWeek") ?: 0,
            goal = enumOr(p.string("goal"), DietGoalType.MAINTAIN),
            paceKgPerWeek = p.double("paceKgPerWeek") ?: 0.5,
            goalWeightKg = p.double("goalWeightKg") ?: existing?.goalWeightKg,
            mealsPerDay = p.int("mealsPerDay") ?: existing?.mealsPerDay,
            dietaryPrefs = p.string("preferences") ?: existing?.dietaryPrefs
        )
        app.profileRepo.save(profile, now)
        // Pierwsza realna kopia zapasowa od razu po utworzeniu profilu (auto-worker startuje dopiero za dobę).
        runCatching { pl.filebit.dietetyk.Backup.writeLocalBackup(app, app) }
        return ToolResult("Zapisałem profil.")
    }

    private suspend fun history(now: Long): ToolResult {
        val ctx = app.contextBuilder.build(now) ?: return ToolResult("Brak profilu — jesteśmy na etapie wywiadu.")
        val trend = if (ctx.weightTrend.hasEnoughData) "trend ${ctx.weightTrend.direction}" else "za mało pomiarów wagi"
        return ToolResult("Waga: ${ctx.latestWeightKg ?: "?"} kg, $trend. Trzymanie planu 14d: kcal ${ctx.adherence14d.avgKcalPct}%. Dni pełnych logów: ${ctx.completeLogDays14d}.")
    }

    /**
     * Zapis SMAKU (PREFER/AVOID/NEUTRAL) na produkt — strukturalne, jedno źródło prawdy. Wołane przez AI,
     * gdy wychwyci preferencję w rozmowie („nie znoszę twarogu"). Match: exact norm → pierwszy trafny w bazie.
     */
    /** Pamięć miękka: zapis faktu o życiu usera (stres/sen/nastrój), gdy sam o nim wspomni. */
    private suspend fun rememberContext(input: JsonObject, now: Long): ToolResult {
        val note = input.string("note") ?: return ToolResult("Podaj notatkę (note).", isError = true)
        app.database.aiMemoryDao().insert(
            pl.filebit.dietetyk.data.db.AiMemoryEntity(note = note, createdAt = now, updatedAt = now, dirty = true)
        )
        return ToolResult("Zapamiętane.")
    }

    private suspend fun setFoodPreference(input: JsonObject): ToolResult {
        val productName = input.string("product") ?: return ToolResult("Podaj produkt (product).", isError = true)
        val pref = when (input.string("preference")?.uppercase()) {
            "AVOID" -> pl.filebit.dietetyk.data.db.Pref.AVOID
            "NEUTRAL" -> pl.filebit.dietetyk.data.db.Pref.NEUTRAL
            else -> pl.filebit.dietetyk.data.db.Pref.PREFER
        }
        val norm = FoodProductSeed.normalize(productName)
        val dao = app.database.foodProductDao()
        val hits = dao.search(norm)
        val target = hits.firstOrNull { it.nameNorm == norm } ?: hits.firstOrNull()
        val label = when (pref) {
            pl.filebit.dietetyk.data.db.Pref.PREFER -> "lubiany (❤️ preferuję w planach)"
            pl.filebit.dietetyk.data.db.Pref.AVOID -> "nie jadany (🚫 nie zaplanuję)"
            else -> "obojętny"
        }
        return if (target != null) {
            dao.setPreference(target.id, pref)
            ToolResult("Zapisane: ${target.name} = $label.")
        } else {
            ToolResult("Zanotowałem: $productName = $label. Nie mam go jeszcze w bazie — jeśli chcesz go w planach, dodaj przez add_missing_product.")
        }
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
        // Tryb tygodniowy: dayOfWeek 1-7. Pominięty = dzisiejszy dzień (zgodność wsteczna).
        val dow = (input.int("dayOfWeek")?.coerceIn(1, 7)) ?: pl.filebit.dietetyk.ui.PlanData.todayDow()
        val weeklyMode = input.int("dayOfWeek") != null

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
        // Nielubiane (🚫) → twardy guardrail walidatora: plan NIGDY nie użyje tych produktów.
        val avoidedNorms = entities.filter { it.preference == pl.filebit.dietetyk.data.db.Pref.AVOID }
            .map { FoodProductSeed.normalize(it.name) }.toSet()

        val ctx = ValidationContext(
            expectedMealsCount = plan.meals.size,
            targetKcal = goal.kcal, targetProteinG = goal.proteinG,
            targetCarbsG = goal.carbsG, targetFatG = goal.fatG,
            perMealProteinMinG = 0, maxCookingMinutesPerMeal = 240,
            productsByName = byName, avoidedNorms = avoidedNorms
        )
        val result = PlanValidator.validate(plan, ctx)
        // W trybie TYGODNIOWYM nie retry'ujemy (7 dni × 2 próby > limit tur → korupcja) — fallback-zapis
        // z ostrzeżeniem. W trybie jednodniowym: do 2 prób poprawy, potem fallback.
        if (!result.isValid && !weeklyMode && planRetries < 2) {
            planRetries++
            return ToolResult(PlanValidator.buildRetryFeedback(result), isError = true)
        }
        planRetries = 0
        // GRACEFUL FALLBACK smaku: NIGDY nie zapisuj planu z produktem AVOID (obietnica: nie planuję czego nie jesz).
        // Po wyczerpaniu prób → rozmowa, nie zapis kompromisu. Rozróżnij przyczynę (smak) od matematyki celu.
        val avoidHits = result.errors.filter { it.code == "avoided_product" }
            .mapNotNull { Regex("używa '([^']+)'").find(it.message)?.groupValues?.get(1) }.distinct()
        if (avoidHits.isNotEmpty()) {
            return ToolResult(
                "NIE ZAPISANO planu — zawiera produkty, których użytkownik nie je (🚫): ${avoidHits.joinToString(", ")}. " +
                    "Za dużo wykluczeń, żeby ułożyć sensowny plan pod cel. Powiedz użytkownikowi KONKRETNIE, czego nie możesz obejść " +
                    "(te produkty), i zapytaj, czy dopuści któryś z nich — dopiero po jego zgodzie ułóż plan ponownie. Nie układaj w kółko.",
                isError = true
            )
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
        // Zapis do dnia w mapie tygodnia (zachowuje pozostałe dni). Stary format {meals} migruje się sam.
        val existing = app.database.planDao().get()?.planJson ?: "{}"
        val newJson = pl.filebit.dietetyk.ui.PlanData.setDayMeals(existing, dow, mealsJson, goal.kcal)
        app.database.planDao().upsert(PlanEntity(planJson = newJson, targetKcal = goal.kcal, updatedAt = now, dirty = true))

        val ct = result.correctedTotal
        val warn = if (result.warnings.isNotEmpty()) " Uwagi: " + result.warnings.take(3).joinToString("; ") { it.message } else ""
        val dayName = pl.filebit.dietetyk.ui.DOW_LONG[dow - 1]
        return ToolResult("Zapisałem plan na $dayName (przeliczony z bazy): ${plan.meals.size} posiłków, ${ct.totalKcal} kcal — B ${ct.totalProteinG}g / W ${ct.totalCarbsG}g / T ${ct.totalFatG}g (cel ${goal.kcal} kcal).$warn")
    }

    /** Dopasowanie nazwy do produktu bazy: exact → substring (spójne z PlanValidator). */
    private fun resolve(name: String, byName: Map<String, FoodProductModel>): FoodProductModel? {
        val key = name.trim().lowercase()
        return byName[key] ?: byName.entries.firstOrNull { (k, _) -> k.contains(key) || key.contains(k) }?.value
    }

    /** Lista zakupów z aktualnego planu tygodnia — sumuje gramaturę per produkt, grupuje po kategorii. */
    private suspend fun generateShoppingList(): ToolResult {
        val planJson = app.database.planDao().get()?.planJson
            ?: return ToolResult("Brak zapisanego planu — najpierw ułóż plan (save_diet_plan).", isError = true)
        // produkt (nazwa) -> (kategoria, suma gramów)
        val agg = LinkedHashMap<String, Pair<String, Int>>()
        for (dow in 1..7) {
            val meals = pl.filebit.dietetyk.ui.PlanData.mealsForDay(planJson, dow) ?: continue
            meals.mapNotNull { it as? JsonObject }.forEach { m ->
                (m["ings"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.forEach { ing ->
                    val name = ing.string("name") ?: return@forEach
                    val grams = ing.int("grams") ?: 0
                    val cat = ing.string("cat") ?: "Inne"
                    val prev = agg[name]
                    agg[name] = cat to ((prev?.second ?: 0) + grams)
                }
            }
        }
        if (agg.isEmpty()) return ToolResult("Plan nie ma jeszcze składników — ułóż dni tygodnia (save_diet_plan).", isError = true)
        val byCat = agg.entries.groupBy({ it.value.first }, { it.key to it.value.second })
        val sb = StringBuilder("Lista zakupów na tydzień (produkty surowe, zsumowane):\n")
        byCat.toSortedMap().forEach { (cat, items) ->
            sb.append("\n$cat:\n")
            items.sortedByDescending { it.second }.forEach { (name, grams) ->
                val amount = if (grams >= 1000) "%.1f kg".format(grams / 1000.0).replace('.', ',') else "$grams g"
                sb.append("• $name — $amount\n")
            }
        }
        return ToolResult(sb.toString().trimEnd())
    }

    /**
     * Korekta celu KIERUNKIEM + SIŁĄ. Kod reguluje `paceKgPerWeek` (tempo zmiany masy), a GoalPipeline
     * przelicza i CLAMPUJE kcal przez SafetyGuard — AI nigdy nie podaje surowej liczby kcal.
     * increase = więcej kcal (mniejsze tempo), decrease = mniej kcal (większe tempo).
     */
    private suspend fun proposeAdjustment(input: JsonObject, now: Long): ToolResult {
        val profile = app.profileRepo.get() ?: return ToolResult("Brak profilu — najpierw wywiad.", isError = true)
        val weight = app.weightRepo.latest()?.weightKg
        val before = GoalPipeline.compute(profile, latestMeasuredWeightKg = weight)
        val dir = input.string("direction")?.lowercase()
        if (dir != "increase" && dir != "decrease") return ToolResult("Podaj direction: increase lub decrease.", isError = true)
        val step = when (input.string("magnitude")?.lowercase()) { "medium" -> 0.2; else -> 0.1 }
        // increase kcal → wolniejsze tempo; decrease kcal → szybsze tempo. Clamp 0..MAX (SafetyGuard).
        val maxPace = pl.filebit.dietetyk.core.safety.SafetyGuard.MAX_LOSS_KG_PER_WEEK
        val delta = if (dir == "increase") -step else step
        val newPace = (profile.paceKgPerWeek + delta).coerceIn(0.0, maxPace)
        if (newPace == profile.paceKgPerWeek) {
            return ToolResult("Tempo jest już na granicy bezpieczeństwa (${"%.1f".format(profile.paceKgPerWeek)} kg/tydz) — nie mogę skorygować dalej w tym kierunku. Zaproponuj inne rozwiązanie (np. diet break) albo zostaw jak jest.")
        }
        app.profileRepo.save(profile.copy(paceKgPerWeek = newPace), now)
        val after = GoalPipeline.compute(profile.copy(paceKgPerWeek = newPace), latestMeasuredWeightKg = weight)
        val diff = after.kcal - before.kcal
        val sign = if (diff > 0) "+" else ""
        val warn = if (after.safetyWarnings.isNotEmpty()) " (${after.safetyWarnings.first()})" else ""
        return ToolResult("Skorygowałem cel: ${before.kcal} → ${after.kcal} kcal ($sign$diff), tempo ${"%.1f".format(newPace)} kg/tydz.$warn")
    }

    /** Umawia wizytę kontrolną na wskazany termin (za ile dni). Zapisuje termin — worker/UI go użyje. */
    private suspend fun scheduleCheckin(input: JsonObject, now: Long): ToolResult {
        val days = input.int("whenDays") ?: return ToolResult("Podaj za ile dni (whenDays).", isError = true)
        val d = days.coerceIn(1, 90)
        app.settings.nextCheckinAt = now + d * 86_400_000L
        return ToolResult("Umówiłem wizytę kontrolną za $d dni.")
    }

    /** Świadome zawieszenie celu na jakiś czas — kod nie karze w tym okresie. Domyślnie 7 dni. */
    private suspend fun deferGoal(input: JsonObject, now: Long): ToolResult {
        val reason = input.string("reason") ?: "wsparcie/rozmowa"
        app.settings.goalDeferredUntil = now + 7 * 86_400_000L
        app.database.aiMemoryDao().insert(AiMemoryEntity(note = "Cel zawieszony: $reason", createdAt = now, updatedAt = now, dirty = true))
        return ToolResult("Odłożyłem agendę celu na tydzień — bez presji. Skupiamy się na tym, czego teraz potrzebujesz.")
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
