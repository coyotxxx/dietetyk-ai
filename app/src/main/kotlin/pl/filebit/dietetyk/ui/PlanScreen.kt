package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private data class PlanMeal(
    val name: String, val time: String, val kcal: Int, val ingredients: String,
    val ings: List<Pair<String, Int>>
)

@Composable
fun PlanScreen(app: DietetykApp) {
    var meals by remember { mutableStateOf<List<PlanMeal>?>(null) }
    var targetKcal by remember { mutableStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    var showShopping by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val entity = app.database.planDao().get()
        loaded = true
        if (entity != null) {
            targetKcal = entity.targetKcal
            meals = runCatching {
                Json.parseToJsonElement(entity.planJson).jsonObject["meals"]!!.jsonArray.map { e ->
                    val o = e.jsonObject
                    val ings = (o["ings"] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map { io ->
                        (io["name"]?.jsonPrimitive?.content ?: "") to (io["grams"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0)
                    } ?: emptyList()
                    PlanMeal(
                        name = o["name"]?.jsonPrimitive?.content ?: "Posiłek",
                        time = o["timeHint"]?.jsonPrimitive?.content ?: "",
                        kcal = o["kcal"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        ingredients = o["ingredients"]?.jsonPrimitive?.content ?: "",
                        ings = ings
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Plan", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
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

        m.forEachIndexed { i, meal ->
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).background(Palette.Card, RoundedCornerShape(16.dp)).padding(14.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text("POSIŁEK ${i + 1}" + (if (meal.time.isNotBlank()) " · ${meal.time}" else ""), color = Palette.Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(meal.name, color = Palette.TextDark, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
                    }
                    Text("${meal.kcal} kcal", color = Palette.Orange, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                }
                if (meal.ingredients.isNotBlank()) {
                    Text(meal.ingredients, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // === Lista zakupów (agregacja składników) ===
        val shopping = m.flatMap { it.ings }
            .groupBy({ it.first }, { it.second })
            .mapValues { it.value.sum() }
            .filterKeys { it.isNotBlank() }
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
                Column(Modifier.fillMaxWidth().padding(top = 8.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    shopping.entries.sortedBy { it.key }.forEach { (name, grams) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(name, color = Palette.TextDark, fontSize = 14.sp)
                            Text("${grams} g", color = Palette.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
