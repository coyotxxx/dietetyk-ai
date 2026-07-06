package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.dietetyk.DietetykApp

private data class ShopItem(val name: String, val grams: Int, val cat: String)
private data class PlanMeal(
    val name: String, val time: String, val kcal: Int, val prep: Int, val ingredients: String,
    val ings: List<ShopItem>, val emoji: String = "🍽️"
)

private val RECIPE_VARIANTS = listOf("Tradycyjnie", "Air Fryer", "Thermomix")
private val RECIPE_KEYS = listOf("tradycyjnie", "airfryer", "thermomix")

@Composable
private fun MealCard(app: DietetykApp, index: Int, meal: PlanMeal) {
    var expanded by remember { mutableStateOf(false) }
    var recipe by remember { mutableStateOf<List<String>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var variant by remember { mutableStateOf(app.settings.recipeVariant) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).card(16.dp).background(Palette.Card, RoundedCornerShape(16.dp))
        .clickable {
            expanded = !expanded
            if (expanded && recipe == null && !loading) {
                loading = true; error = null
                scope.launch {
                    try {
                        val json = app.recipeFor(meal.name, meal.ingredients)
                        val obj = Json.parseToJsonElement(json).jsonObject
                        recipe = RECIPE_KEYS.map { obj[it]?.jsonPrimitive?.content ?: "—" }
                    } catch (e: Exception) {
                        error = "Nie udało się wygenerować przepisu. Spróbuj ponownie."
                    } finally { loading = false }
                }
            }
        }
        .padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(52.dp).background(Palette.GreenTint, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text(meal.emoji, fontSize = 26.sp) }
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
                Text("POSIŁEK ${index + 1}" + (if (meal.time.isNotBlank()) " · ${meal.time}" else ""), color = Palette.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(meal.name, color = Palette.TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${meal.kcal} kcal", color = Palette.Orange, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                if (meal.prep > 0) Text("⏱ ${meal.prep} min", color = Palette.Muted, fontSize = 11.sp, maxLines = 1, softWrap = false)
            }
        }
        if (meal.ingredients.isNotBlank()) {
            Text(meal.ingredients, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        }
        Text(if (expanded) "▲ przepis" else "▼ przepis (3 warianty)", color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))

        if (expanded) {
            when {
                loading -> Text("Generuję przepis…", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                error != null -> Text(error!!, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                recipe != null -> {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        RECIPE_VARIANTS.forEachIndexed { vi, label ->
                            Box(
                                Modifier.weight(1f).background(if (vi == variant) Palette.Green else Palette.GreenTint, RoundedCornerShape(10.dp))
                                    .clickable { variant = vi; app.settings.recipeVariant = vi }.padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) { Text(label, color = if (vi == variant) androidx.compose.ui.graphics.Color.White else Palette.GreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    Text(recipe!!.getOrElse(variant) { "—" }, color = Palette.TextDark, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
    }
}

@Composable
fun PlanScreen(app: DietetykApp) {
    var meals by remember { mutableStateOf<List<PlanMeal>?>(null) }
    var targetKcal by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var showShopping by remember { mutableStateOf(false) }
    val checked = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(Unit) {
        val entity = app.database.planDao().get()
        loaded = true
        if (entity != null) {
            targetKcal = entity.targetKcal
            meals = runCatching {
                Json.parseToJsonElement(entity.planJson).jsonObject["meals"]!!.jsonArray.map { e ->
                    val o = e.jsonObject
                    val ings = (o["ings"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map { io ->
                        ShopItem(
                            io["name"]?.jsonPrimitive?.content ?: "",
                            io["grams"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                            io["cat"]?.jsonPrimitive?.content ?: "Inne"
                        )
                    } ?: emptyList()
                    PlanMeal(
                        name = o["name"]?.jsonPrimitive?.content ?: "Posiłek",
                        time = o["timeHint"]?.jsonPrimitive?.content ?: "",
                        kcal = o["kcal"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        prep = o["prepMinutes"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        ingredients = o["ingredients"]?.jsonPrimitive?.content ?: "",
                        ings = ings,
                        emoji = o["emoji"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: mealEmoji(o["name"]?.jsonPrimitive?.content ?: "")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Plan na dziś", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Plan na kolejne dni ułoży Dietetyk w rozmowie", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        val m = meals
        if (m == null || m.isEmpty()) {
            Text(
                if (loaded) "Nie masz jeszcze planu. Poproś Dietetyka, żeby ułożył plan na dziś." else "Wczytuję…",
                color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)
            )
            return
        }
        val sum = m.sumOf { it.kcal }
        Text("${m.size} posiłki • $sum kcal" + (if (targetKcal > 0) " (cel $targetKcal)" else ""),
            color = Palette.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))

        m.forEachIndexed { i, meal -> MealCard(app, i, meal) }

        // === Lista zakupów (agregacja + kategorie + odhaczanie) ===
        val shopping = m.flatMap { it.ings }.filter { it.name.isNotBlank() }
            .groupBy { it.name }
            .map { (name, items) -> Triple(name, items.sumOf { it.grams }, items.first().cat) }
        val byCat = shopping.groupBy { it.third }.toSortedMap()
        if (shopping.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp)
                    .background(Palette.Green, RoundedCornerShape(14.dp))
                    .clickable { showShopping = !showShopping }.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🛒 Lista zakupów (${shopping.size})", color = androidx.compose.ui.graphics.Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (showShopping) "▲" else "▼", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
            }
            if (showShopping) {
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    byCat.forEach { (cat, items) ->
                        Text(cat.uppercase(), color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                        items.sortedBy { it.first }.forEach { (name, grams, _) ->
                            val isOn = checked[name] == true
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { checked[name] = !isOn },
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier.size(20.dp).background(if (isOn) Palette.Green else Palette.Line, RoundedCornerShape(6.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isOn) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        name, color = if (isOn) Palette.Muted else Palette.TextDark, fontSize = 14.sp,
                                        textDecoration = if (isOn) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                                        modifier = Modifier.padding(start = 10.dp)
                                    )
                                }
                                Text("${grams} g", color = Palette.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

