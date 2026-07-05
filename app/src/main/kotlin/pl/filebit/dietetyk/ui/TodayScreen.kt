package pl.filebit.dietetyk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.core.calc.DailyMacroGoal
import pl.filebit.dietetyk.core.calc.GoalPipeline
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

@Composable
fun TodayScreen(app: DietetykApp) {
    var goal by remember { mutableStateOf<DailyMacroGoal?>(null) }
    var consumed by remember { mutableIntStateOf(0) }
    var hasProfile by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val p = app.profileRepo.get()
        hasProfile = p != null
        val w = app.weightRepo.latest()?.weightKg
        goal = p?.let { GoalPipeline.compute(it, latestMeasuredWeightKg = w) }
        val since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        consumed = app.database.energyLogDao().since(since).sumOf { it.kcalConsumed }
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
