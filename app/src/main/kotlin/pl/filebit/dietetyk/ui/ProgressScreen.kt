package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
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
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.core.calc.TrendAnalyzer
import pl.filebit.dietetyk.core.calc.TrendDirection
import pl.filebit.dietetyk.core.model.WeightSample

@Composable
fun ProgressScreen(app: DietetykApp) {
    var samples by remember { mutableStateOf<List<WeightSample>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        samples = app.weightRepo.since(System.currentTimeMillis() - 90L * 24 * 3600 * 1000)
        loaded = true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Postępy", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)

        val latest = samples.maxByOrNull { it.dateMs }?.weightKg
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(18.dp)) {
            Text("Waga", color = Palette.Muted, fontSize = 13.sp)
            Text(latest?.let { "$it kg" } ?: "—", color = Palette.TextDark, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            if (samples.size >= 3) {
                val t = TrendAnalyzer.analyze(samples)
                val dir = when (t.direction) {
                    TrendDirection.FALLING -> "spada"
                    TrendDirection.RISING -> "rośnie"
                    TrendDirection.FLAT -> "stabilna"
                    else -> "—"
                }
                Text(
                    "Trend: $dir" + (t.slopeKgPerWeek?.let { " (%.2f kg/tydz)".format(it) } ?: ""),
                    color = Palette.Green, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Text("Historia pomiarów", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (loaded && samples.isEmpty()) {
            Text("Brak pomiarów. Powiedz Dietetykowi ile ważysz, a zapisze pomiar.", color = Palette.Muted, fontSize = 14.sp)
        }
        samples.sortedByDescending { it.dateMs }.take(20).forEach { s ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).background(Palette.Card, RoundedCornerShape(10.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayLabel(s.dateMs), color = Palette.Muted, fontSize = 13.sp)
                Text("${s.weightKg} kg", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun dayLabel(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "${d.dayOfMonth}.${d.monthValue}.${d.year}"
}
