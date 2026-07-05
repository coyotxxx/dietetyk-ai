package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.min

/** Strukturalna karta akcji od AI — surowy JSON renderowany leniwie. */
data class CardData(val type: String, val json: JsonObject)

private fun JsonObject.str(k: String): String? = this[k]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.num(k: String): Int? = this[k]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt()
private fun JsonObject.arr(k: String): List<JsonObject> = (this[k] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

/** Dispatcher: wybiera renderer wg typu karty (fallback = karta ogólna). */
@Composable
fun ActionCard(card: CardData, onAction: (String) -> Unit) {
    val j = card.json
    when (card.type) {
        "meal_plan" -> MealPlanCard(j, onAction)
        "food_recognition" -> FoodRecognitionCard(j, onAction)
        "day_summary" -> DaySummaryCard(j)
        "plan_adjustment" -> PlanAdjustmentCard(j, onAction)
        "week_report" -> WeekReportCard(j, onAction)
        "blood_results" -> BloodResultsCard(j, onAction)
        "interview_summary" -> InterviewSummaryCard(j, onAction)
        else -> CardShell(j.str("badge"), j.str("title"), j.str("subtitle")) {
            j.str("text")?.let { Text(it, color = Palette.TextDark, fontSize = 14.sp) }
            CardActions(j.arr("actions"), onAction)
        }
    }
}

// === PRYMITYWY ===

@Composable
private fun CardShell(badge: String?, title: String?, subtitle: String?, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 4.dp, start = 32.dp).card(20.dp).background(Palette.Card, RoundedCornerShape(20.dp)).padding(16.dp)) {
        if (badge != null) Text(badge, color = Palette.Green, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        if (title != null) Text(title, color = Palette.TextDark, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = if (badge != null) 4.dp else 0.dp))
        if (subtitle != null) Text(subtitle, color = Palette.Muted, fontSize = 12.sp)
        content()
    }
}

@Composable
private fun MetricTile(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(Palette.GreenTint, RoundedCornerShape(12.dp)).padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = Palette.Muted, fontSize = 10.sp)
    }
}

@Composable
private fun MacroBar(label: String, value: Int, target: Int, color: Color) {
    val frac = if (target > 0) min(1f, value.toFloat() / target) else 0f
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Palette.Muted, fontSize = 12.sp)
            Text("$value / ${target}g", color = Palette.TextDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.fillMaxWidth().padding(top = 3.dp).height(6.dp).background(Palette.Line, RoundedCornerShape(3.dp))) {
            Box(Modifier.fillMaxWidth(frac).height(6.dp).background(color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun ListRow(emoji: String?, name: String, meta: String?, trailing: String?, trailingColor: Color = Palette.Orange) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        if (emoji != null) Box(Modifier.size(38.dp).background(Palette.GreenTint, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 18.sp) }
        Column(Modifier.weight(1f).padding(start = if (emoji != null) 10.dp else 0.dp)) {
            Text(name, color = Palette.TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (meta != null) Text(meta, color = Palette.Muted, fontSize = 11.sp)
        }
        if (trailing != null) Text(trailing, color = trailingColor, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun CardActions(actions: List<JsonObject>, onAction: (String) -> Unit) {
    if (actions.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        actions.take(3).forEachIndexed { i, a ->
            val label = a.str("label") ?: "OK"
            val send = a.str("send") ?: label
            val bg = when (i) { 0 -> Palette.Green; 1 -> Palette.GreenTint; else -> Color.Transparent }
            val fg = when (i) { 0 -> Color.White; 1 -> Palette.GreenDark; else -> Palette.Muted }
            Box(
                Modifier.weight(1f)
                    .then(if (i >= 2) Modifier.border(1.dp, Palette.Line, RoundedCornerShape(12.dp)) else Modifier)
                    .background(bg, RoundedCornerShape(12.dp))
                    .clickable { onAction(send) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text(label, color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
        }
    }
}

// === KARTY ===

@Composable
private fun MealPlanCard(j: JsonObject, onAction: (String) -> Unit) = CardShell("🌱 ${j.str("title") ?: "Propozycja planu"}", null, j.str("subtitle")) {
    Column(Modifier.padding(top = 8.dp)) {
        j.arr("meals").forEach { m -> ListRow(m.str("emoji"), m.str("name") ?: "Posiłek", m.str("meta"), m.num("kcal")?.let { "$it kcal" }) }
    }
    j["totals"]?.let { (it as? JsonObject) }?.let { t ->
        Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(Palette.Line))
        Text("Razem ${t.num("kcal") ?: 0} kcal   ·   B ${t.num("p") ?: 0} · W ${t.num("c") ?: 0} · T ${t.num("f") ?: 0}",
            color = Palette.TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
    }
    CardActions(j.arr("actions"), onAction)
}

@Composable
private fun FoodRecognitionCard(j: JsonObject, onAction: (String) -> Unit) = CardShell("ROZPOZNANE", j.str("name") ?: "Posiłek", j.str("note") ?: "szacunek na podstawie zdjęcia") {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricTile("${j.num("kcal") ?: 0}", "kcal", Palette.Orange, Modifier.weight(1f))
        MetricTile("${j.num("p") ?: 0} g", "białko", Palette.Green, Modifier.weight(1f))
        MetricTile("${j.num("c") ?: 0} g", "węgle", Palette.Orange, Modifier.weight(1f))
        MetricTile("${j.num("f") ?: 0} g", "tłuszcz", Palette.Blue, Modifier.weight(1f))
    }
    CardActions(j.arr("actions"), onAction)
}

@Composable
private fun DaySummaryCard(j: JsonObject) = CardShell(j.str("badge") ?: "Podsumowanie dnia", j.str("title"), j.str("subtitle")) {
    j.str("score")?.let { Text(it, color = Palette.Green, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp)) }
    j.str("comment")?.let { Text(it, color = Palette.TextDark, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)) }
    j.arr("macros").forEach { m ->
        val label = m.str("label") ?: ""
        val color = when {
            label.startsWith("Bia", true) -> Palette.Green
            label.startsWith("Węg", true) || label.startsWith("Weg", true) -> Palette.Orange
            else -> Palette.Blue
        }
        MacroBar(label, m.num("value") ?: 0, m.num("target") ?: 0, color)
    }
}

@Composable
private fun PlanAdjustmentCard(j: JsonObject, onAction: (String) -> Unit) = CardShell("⚙️ Korekta planu", j.str("title") ?: "Propozycja korekty", null) {
    Column(Modifier.padding(top = 8.dp)) {
        j.arr("changes").forEach { c -> Text("• ${c.str("text") ?: ""}", color = Palette.TextDark, fontSize = 14.sp, modifier = Modifier.padding(vertical = 3.dp)) }
    }
    CardActions(j.arr("actions"), onAction)
}

@Composable
private fun WeekReportCard(j: JsonObject, onAction: (String) -> Unit) = CardShell("RAPORT TYGODNIA", j.str("title"), j.str("subtitle")) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        j.arr("tiles").forEach { t -> MetricTile(t.str("value") ?: "—", t.str("label") ?: "", Palette.Green, Modifier.weight(1f)) }
    }
    CardActions(j.arr("actions"), onAction)
}

@Composable
private fun BloodResultsCard(j: JsonObject, onAction: (String) -> Unit) = CardShell("WYNIKI BADAŃ", j.str("title") ?: "Odczytane parametry", null) {
    Column(Modifier.padding(top = 8.dp)) {
        j.arr("params").forEach { p ->
            val ok = (p.str("status") ?: "ok") == "ok"
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).background(if (ok) Palette.Green else Palette.Orange, RoundedCornerShape(5.dp)))
                Text(p.str("name") ?: "", color = Palette.TextDark, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 10.dp))
                Text("${p.str("value") ?: ""} ${p.str("unit") ?: ""}", color = Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    CardActions(j.arr("actions"), onAction)
}

@Composable
private fun InterviewSummaryCard(j: JsonObject, onAction: (String) -> Unit) = CardShell(null, j.str("title") ?: "Podsumowanie wywiadu", null) {
    Column(Modifier.padding(top = 8.dp)) {
        j.arr("rows").forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(r.str("label") ?: "", color = Palette.Muted, fontSize = 13.sp)
                Text(r.str("value") ?: "", color = Palette.TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
    CardActions(j.arr("actions"), onAction)
}
