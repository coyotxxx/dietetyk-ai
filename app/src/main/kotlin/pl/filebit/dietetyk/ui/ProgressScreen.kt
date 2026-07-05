package pl.filebit.dietetyk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import kotlinx.coroutines.launch

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(app: DietetykApp) {
    var samples by remember { mutableStateOf<List<WeightSample>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        samples = app.weightRepo.since(System.currentTimeMillis() - 90L * 24 * 3600 * 1000)
        loaded = true
    }

    if (showSheet) {
        NewMeasurementSheet(
            onDismiss = { showSheet = false },
            onSave = { kg ->
                scope.launch {
                    app.weightRepo.add(pl.filebit.dietetyk.core.model.WeightSample(dateMs = System.currentTimeMillis(), weightKg = kg), System.currentTimeMillis())
                    showSheet = false
                    reloadKey++
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Postępy", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Box(
                Modifier.background(Palette.Green, RoundedCornerShape(12.dp)).clickable { showSheet = true }.padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("+ Waga", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }

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

        if (samples.size >= 2) {
            WeightChart(
                samples.sortedBy { it.dateMs },
                Modifier.fillMaxWidth().padding(top = 12.dp).height(160.dp)
                    .background(Palette.Card, RoundedCornerShape(18.dp)).padding(16.dp)
            )
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

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewMeasurementSheet(onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp).padding(bottom = 20.dp)) {
            Text("Nowy pomiar wagi", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.OutlinedTextField(
                value = text, onValueChange = { text = it.replace(',', '.') },
                label = { Text("Waga w kg") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            val kg = text.toDoubleOrNull()
            Box(
                Modifier.fillMaxWidth().padding(top = 16.dp)
                    .background(if (kg != null && kg in 30.0..350.0) Palette.Green else Palette.Line, RoundedCornerShape(14.dp))
                    .clickable(enabled = kg != null && kg in 30.0..350.0) { onSave(kg!!) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Zapisz", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Prosty wykres liniowy wagi (posortowane rosnąco po dacie). */
@Composable
private fun WeightChart(sorted: List<WeightSample>, modifier: Modifier) {
    val minW = sorted.minOf { it.weightKg }
    val maxW = sorted.maxOf { it.weightKg }
    val range = (maxW - minW).takeIf { it > 0.01 } ?: 1.0
    val greenColor = Palette.Green
    Canvas(modifier) {
        val n = sorted.size
        val dx = if (n > 1) size.width / (n - 1) else 0f
        fun yFor(w: Double) = (size.height * (1f - ((w - minW) / range).toFloat())).toFloat()
        val path = androidx.compose.ui.graphics.Path()
        sorted.forEachIndexed { i, s ->
            val x = i * dx
            val y = yFor(s.weightKg)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, greenColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        sorted.forEachIndexed { i, s ->
            drawCircle(greenColor, radius = 7f, center = androidx.compose.ui.geometry.Offset(i * dx, yFor(s.weightKg)))
        }
    }
}
