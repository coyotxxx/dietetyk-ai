package pl.filebit.dietetyk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.core.calc.DailyMacroGoal
import pl.filebit.dietetyk.core.calc.GoalPipeline
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

private data class DayMeal(val name: String, val time: String, val kcal: Int)

@Composable
fun TodayScreen(app: DietetykApp) {
    var goal by remember { mutableStateOf<DailyMacroGoal?>(null) }
    var consumed by remember { mutableIntStateOf(0) }
    var hasProfile by remember { mutableStateOf(true) }
    var meals by remember { mutableStateOf<List<DayMeal>>(emptyList()) }
    val dayKey = remember { LocalDate.now().let { "%04d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth) } }
    var water by remember { mutableIntStateOf(app.settings.waterMl(dayKey)) }
    val waterTarget = 2500

    LaunchedEffect(Unit) {
        val p = app.profileRepo.get()
        hasProfile = p != null
        val w = app.weightRepo.latest()?.weightKg
        goal = p?.let { GoalPipeline.compute(it, latestMeasuredWeightKg = w) }
        val since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        consumed = app.database.energyLogDao().since(since).sumOf { it.kcalConsumed }
        app.database.planDao().get()?.let { plan ->
            meals = runCatching {
                Json.parseToJsonElement(plan.planJson).jsonObject["meals"]!!.jsonArray.map { e ->
                    val o = e.jsonObject
                    DayMeal(
                        o["name"]?.jsonPrimitive?.content ?: "Posiłek",
                        o["timeHint"]?.jsonPrimitive?.content ?: "",
                        o["kcal"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Cześć! 👋", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(polishDate(), color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))

        val g = goal
        if (g == null) {
            Card {
                Text(
                    if (hasProfile) "Liczę Twój cel…" else "Zacznij od rozmowy z Dietetykiem — zrobi wywiad i ułoży cel.",
                    color = Palette.TextDark, fontSize = 15.sp
                )
            }
        } else {
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KcalRing(consumed = consumed, target = g.kcal, modifier = Modifier.size(120.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        MacroLine("Białko", g.proteinG, Palette.Green)
                        MacroLine("Węgle", g.carbsG, Palette.Orange)
                        MacroLine("Tłuszcz", g.fatG, Palette.Blue)
                    }
                }
                val left = (g.kcal - consumed).coerceAtLeast(0)
                Text(
                    "Zjedzone $consumed / ${g.kcal} kcal • zostało $left",
                    color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
                )
            }

            Box(Modifier.padding(top = 12.dp)) {
                Column(
                    Modifier.fillMaxWidth().background(Palette.GreenTint, RoundedCornerShape(16.dp)).padding(16.dp)
                ) {
                    Text("DIETETYK MÓWI", color = Palette.GreenDark, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        g.breakdown.deficitLabel + " Loguj posiłki, a dopasuję cel do Twojego realnego metabolizmu.",
                        color = Palette.TextDark, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        WaterCard(water, waterTarget) { delta ->
            water = (water + delta).coerceAtLeast(0)
            app.settings.setWaterMl(dayKey, water)
        }

        if (meals.isNotEmpty()) {
            Text("Posiłki dnia", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            meals.forEachIndexed { i, m -> MealRow(i, m) }
        }
    }
}

@Composable
private fun WaterCard(water: Int, target: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("💧 Woda", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("$water / $target ml", color = Palette.Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        // pasek postępu
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(8.dp).background(Palette.Line, RoundedCornerShape(4.dp))) {
            val frac = if (target > 0) min(1f, water.toFloat() / target) else 0f
            Box(Modifier.fillMaxWidth(frac).height(8.dp).background(Palette.Blue, RoundedCornerShape(4.dp)))
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WaterBtn("+250 ml", Modifier.weight(1f)) { onChange(250) }
            WaterBtn("+500 ml", Modifier.weight(1f)) { onChange(500) }
            WaterBtn("−", Modifier.width(56.dp)) { onChange(-250) }
        }
    }
}

@Composable
private fun WaterBtn(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.background(Palette.Blue, RoundedCornerShape(12.dp)).clickable { onClick() }.padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) { Text(label, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun MealRow(index: Int, meal: DayMeal) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text("Posiłek ${index + 1}" + (if (meal.time.isNotBlank()) " · ${meal.time}" else ""), color = Palette.Muted, fontSize = 11.sp)
            Text(meal.name, color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${meal.kcal} kcal", color = Palette.Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Text("⏳ Zaplanowany", color = Palette.Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(18.dp)).padding(16.dp)) { content() }
}

@Composable
private fun MacroLine(name: String, grams: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(3.dp)))
        Text("  $name  ", color = Palette.Muted, fontSize = 13.sp)
        Text("${grams}g", color = Palette.TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun KcalRing(consumed: Int, target: Int, modifier: Modifier) {
    val frac = if (target > 0) min(1f, consumed.toFloat() / target) else 0f
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16f
            val d = min(size.width, size.height) - stroke
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(Palette.Line, -90f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(Palette.Green, -90f, 360f * frac, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(target - consumed).coerceAtLeast(0)}", color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("kcal", color = Palette.Muted, fontSize = 11.sp)
        }
    }
}

private fun polishDate(): String {
    val d = LocalDate.now()
    val dni = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")
    val mies = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    return "${dni[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${mies[d.monthValue - 1]}"
}
