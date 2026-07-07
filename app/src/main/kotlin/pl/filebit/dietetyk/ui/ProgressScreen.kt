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
import androidx.compose.ui.draw.clip
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
fun ProgressScreen(app: DietetykApp, onGoToChat: () -> Unit = {}) {
    var samples by remember { mutableStateOf<List<WeightSample>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf(30) }
    var profile by remember { mutableStateOf<pl.filebit.dietetyk.core.model.NutritionProfile?>(null) }
    var visits by remember { mutableStateOf<List<pl.filebit.dietetyk.data.db.VisitEntity>>(emptyList()) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(reloadKey) {
        samples = app.weightRepo.since(System.currentTimeMillis() - 365L * 24 * 3600 * 1000)
        profile = app.profileRepo.get()
        visits = app.database.visitDao().all()
        loaded = true
    }

    if (showSheet) {
        NewMeasurementSheet(
            onDismiss = { showSheet = false },
            onSave = { kg, waistCm, bodyFatPct ->
                scope.launch {
                    app.weightRepo.add(
                        pl.filebit.dietetyk.core.model.WeightSample(dateMs = System.currentTimeMillis(), weightKg = kg, waistCm = waistCm, bodyFatPct = bodyFatPct),
                        System.currentTimeMillis()
                    )
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
                Modifier.clip(RoundedCornerShape(12.dp)).background(Palette.Green, RoundedCornerShape(12.dp)).clickable { showSheet = true }.padding(horizontal = 14.dp, vertical = 8.dp)
            ) { Text("+ Waga", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }

        val allSorted = samples.sortedBy { it.dateMs }
        val latest = allSorted.lastOrNull()?.weightKg
        val goalW = profile?.goalWeightKg?.takeIf { it > 0 }
        val now = System.currentTimeMillis()
        val ranged = allSorted.filter { it.dateMs >= now - range.toLong() * 24 * 3600 * 1000 }
        // delta liczona w oknie (spójna z wykresem)
        val startRanged = ranged.firstOrNull()?.weightKg
        val delta = if (latest != null && startRanged != null) latest - startRanged else null

        // DROGA DO CELU — start → teraz → cel: motywująca wizualizacja (gdy jest cel i pomiary).
        val startW = allSorted.firstOrNull()?.weightKg
        val canJourney = latest != null && goalW != null && startW != null && kotlin.math.abs(startW - goalW) >= 0.1
        if (canJourney) {
            val prog = ((latest!! - startW!!) / (goalW!! - startW)).coerceIn(0.0, 1.0).toFloat()
            val toGo = kotlin.math.abs(latest - goalW)
            val reached = toGo < 0.3
            val subtitle = when {
                reached -> "Cel osiągnięty — gratulacje! 🎯"
                prog >= 0.5 -> "Większość drogi za Tobą — trzymaj tempo 🌱"
                prog > 0.0 -> "Dobry początek — krok po kroku 🌱"
                else -> "Startujemy — pierwszy krok za Tobą"
            }
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(20.dp).background(Palette.Card, RoundedCornerShape(20.dp)).padding(20.dp)) {
                Text("Droga do celu", color = Palette.TextDark, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Bottom) {
                    Text("Teraz ", color = Palette.Muted, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("${kgP(latest)} kg", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    if (delta != null && kotlin.math.abs(delta) >= 0.1) {
                        val down = delta < 0
                        Text("  ${if (down) "↓" else "↑"} ${kgP(kotlin.math.abs(delta))} kg", color = if (down) Palette.Green else Palette.Orange, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                // Pasek postępu start→cel (wypełnienie = dotychczasowa droga).
                Box(Modifier.fillMaxWidth().padding(top = 14.dp).height(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Palette.GreenTint)) {
                    Box(Modifier.fillMaxWidth(prog).height(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Palette.Green))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Start ${kgP(startW)} kg", color = Palette.Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                    Text("🏁 Cel ${kgP(goalW)} kg", color = Palette.GreenDark, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.Center) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.GreenTint, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 9.dp)) {
                        Text(
                            if (reached) "Cel osiągnięty! 🎯" else "Jeszcze ${kgP(toGo)} kg — ${if (prog >= 0.7) "jesteś blisko!" else "dasz radę!"}",
                            color = Palette.GreenDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            // Fallback bez celu/pomiarów: sama aktualna waga + delta.
            Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(18.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(18.dp)) {
                Text("Aktualna waga", color = Palette.Muted, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(latest?.let { kgP(it) + " kg" } ?: "—", color = Palette.TextDark, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    if (delta != null && kotlin.math.abs(delta) >= 0.1) {
                        val down = delta < 0
                        Box(Modifier.padding(start = 10.dp, bottom = 4.dp).background(Palette.GreenTint, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                            Text((if (down) "↓ " else "↑ ") + kgP(kotlin.math.abs(delta)) + " kg", color = if (down) Palette.Green else Palette.Orange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                }
            }
        }

        // Metryki 2×2
        val reachedGoal = goalW != null && latest != null && ((profile?.goal == pl.filebit.dietetyk.core.model.DietGoalType.FAT_LOSS && latest <= goalW) || (latest != null && kotlin.math.abs(latest - goalW) < 0.3))
        val toGoal = if (goalW != null && latest != null) kotlin.math.abs(latest - goalW) else null
        val waistVal = allSorted.lastOrNull { it.waistCm != null }?.waistCm
        val bfVal = allSorted.lastOrNull { it.bodyFatPct != null }?.bodyFatPct
            ?: profile?.let { p -> pl.filebit.dietetyk.core.calc.BodyFatEstimator.estimate(latest, p.heightCm, p.ageYears, p.gender, waistCm = waistVal)?.percent }
        val adherence = run {
            val today = java.time.LocalDate.now()
            val meals = profile?.mealsPerDay ?: 4
            var eaten = 0; var activeDays = 0
            for (i in 0 until 14) {
                val d = today.minusDays(i.toLong())
                val c = app.settings.eatenCountForDay("%04d%02d%02d".format(d.year, d.monthValue, d.dayOfMonth))
                if (c > 0) { eaten += c; activeDays++ }
            }
            if (activeDays >= 2 && meals > 0) (eaten * 100 / (activeDays * meals)).coerceIn(0, 100) else null
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTileP("Do celu", if (reachedGoal) "Cel! 🎯" else toGoal?.let { kgP(it) + " kg" } ?: "—", Palette.Orange, Modifier.weight(1f))
            MetricTileP("Tkanka tłuszczowa", bfVal?.let { kgP(it) + "%" } ?: "—", Palette.Blue, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricTileP("Obwód pasa", waistVal?.let { kgP(it) + " cm" } ?: "—", Palette.TextDark, Modifier.weight(1f))
            MetricTileGradient("Trzymanie planu", adherence?.let { "$it%" } ?: "—", Modifier.weight(1f))
        }

        // Segmented 7/30/90
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7 to "7 dni", 30 to "30 dni", 90 to "90 dni").forEach { (d, label) ->
                val active = range == d
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (active) Palette.Green else Palette.Card, RoundedCornerShape(10.dp)).clickable { range = d }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (active) androidx.compose.ui.graphics.Color.White else Palette.Muted, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }

        if (ranged.size >= 2) {
            WeightChart(
                ranged, goalW,
                Modifier.fillMaxWidth().padding(top = 10.dp).height(170.dp)
                    .card(18.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(16.dp)
            )
            // Stopka: cel + ETA
            val trend = if (ranged.size >= 3) TrendAnalyzer.analyze(ranged) else null
            val slope = trend?.slopeKgPerWeek
            val eta = if (goalW != null && latest != null && slope != null && kotlin.math.abs(slope) > 0.02 &&
                ((goalW < latest && slope < 0) || (goalW > latest && slope > 0)))
                (kotlin.math.abs(latest - goalW) / kotlin.math.abs(slope)).let { kotlin.math.ceil(it).toInt() } else null
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(goalW?.let { "Cel ${kgP(it)} kg" } ?: "Cel: nie ustawiony", color = Palette.Muted, fontSize = 12.sp)
                Text(eta?.let { "ok. $it tyg. do celu" } ?: "", color = Palette.Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Karta najbliższej wizyty kontrolnej (ciemna)
        val white = androidx.compose.ui.graphics.Color.White
        val nextVisitMs = (visits.firstOrNull()?.dateMs ?: now) + 7L * 24 * 3600 * 1000
        Column(Modifier.fillMaxWidth().padding(top = 20.dp).background(androidx.compose.ui.graphics.Color(0xFF23261F), RoundedCornerShape(18.dp)).padding(18.dp)) {
            Text("NAJBLIŻSZA WIZYTA KONTROLNA", color = white.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                if (nextVisitMs <= now) "Termin już minął — czas na wizytę" else "za ${((nextVisitMs - now) / (24 * 3600 * 1000)).toInt() + 1} dni · ${dayLabel(nextVisitMs)}",
                color = white, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)
            )
            Box(
                Modifier.padding(top = 12.dp).background(Palette.Green, RoundedCornerShape(12.dp))
                    .clickable {
                        app.pendingChatMessage = "Przeprowadź teraz wizytę kontrolną: podsumuj miniony tydzień (raport tygodnia) i — jeśli trzeba — zaproponuj korektę planu. Pokaż to jako karty."
                        onGoToChat()
                    }.padding(horizontal = 18.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) { Text("Zacznij wizytę", color = white, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
        }

        // Historia wizyt kontrolnych
        if (visits.isNotEmpty()) {
            Text("Wizyty kontrolne", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
            visits.forEachIndexed { i, v ->
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Wizyta kontrolna #${visits.size - i}", color = Palette.TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(dayLabel(v.dateMs), color = Palette.Muted, fontSize = 12.sp)
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        v.deltaKg?.let { Text((if (it < 0) "↓ " else "↑ ") + kgP(kotlin.math.abs(it)) + " kg", color = if (it < 0) Palette.Green else Palette.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        v.adherencePct?.let { Text("Trzymanie $it%", color = Palette.Muted, fontSize = 12.sp) }
                    }
                    Text(v.decisionText, color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        Text("Historia pomiarów", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        if (loaded && samples.isEmpty()) {
            Text("Brak pomiarów. Powiedz Dietetykowi ile ważysz, a zapisze pomiar.", color = Palette.Muted, fontSize = 14.sp)
        }
        samples.sortedByDescending { it.dateMs }.take(20).forEach { s ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).card(10.dp).background(Palette.Card, RoundedCornerShape(10.dp)).padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayLabel(s.dateMs), color = Palette.Muted, fontSize = 13.sp)
                Text(kgP(s.weightKg) + " kg", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun dayLabel(ms: Long): String {
    val d = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
    return "${d.dayOfMonth}.${d.monthValue}.${d.year}"
}

private fun kgP(d: Double): String = (if (d % 1.0 == 0.0) "%.0f" else "%.1f").format(d).replace('.', ',')

@Composable
private fun MetricTileP(label: String, value: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier.card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp)) {
        Text(value, color = tint, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = Palette.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun MetricTileGradient(label: String, value: String, modifier: Modifier) {
    val white = androidx.compose.ui.graphics.Color.White
    Column(
        modifier.background(
            androidx.compose.ui.graphics.Brush.linearGradient(listOf(Palette.Green, Palette.GreenDark)),
            RoundedCornerShape(14.dp)
        ).padding(14.dp)
    ) {
        Text(value, color = white, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
        Text(label, color = white.copy(alpha = 0.85f), fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NewMeasurementSheet(onDismiss: () -> Unit, onSave: (Double, Double?, Double?) -> Unit) {
    var text by remember { mutableStateOf("") }
    var waist by remember { mutableStateOf("") }
    var bodyfat by remember { mutableStateOf("") }
    val dec = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.fillMaxWidth().imePadding().padding(20.dp).padding(bottom = 20.dp)) {
            Text("Nowy pomiar", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            androidx.compose.material3.OutlinedTextField(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), 
                value = text, onValueChange = { text = it.replace(',', '.') },
                label = { Text("Waga w kg") }, keyboardOptions = dec,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            )
            androidx.compose.material3.OutlinedTextField(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), 
                value = waist, onValueChange = { waist = it.replace(',', '.') },
                label = { Text("Obwód pasa w cm (opcjonalnie)") }, keyboardOptions = dec,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            androidx.compose.material3.OutlinedTextField(shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp), 
                value = bodyfat, onValueChange = { bodyfat = it.replace(',', '.') },
                label = { Text("Tkanka tłuszczowa % (opcjonalnie)") }, keyboardOptions = dec,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            val kg = text.toDoubleOrNull()
            Box(
                Modifier.fillMaxWidth().padding(top = 16.dp)
                    .background(if (kg != null && kg in 30.0..350.0) Palette.Green else Palette.Line, RoundedCornerShape(14.dp))
                    .clickable(enabled = kg != null && kg in 30.0..350.0) { onSave(kg!!, waist.toDoubleOrNull(), bodyfat.toDoubleOrNull()) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Zapisz", color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** Wykres liniowy wagi z przerywaną linią celu i gradientowym wypełnieniem. */
@Composable
private fun WeightChart(sorted: List<WeightSample>, goalW: Double?, modifier: Modifier) {
    val vals = sorted.map { it.weightKg } + listOfNotNull(goalW)
    val minW = vals.min()
    val maxW = vals.max()
    val span = (maxW - minW).takeIf { it > 0.01 } ?: 1.0
    val greenColor = Palette.Green
    val fillColor = Palette.Green.copy(alpha = 0.15f)
    val lineColor = Palette.Line
    Canvas(modifier) {
        val n = sorted.size
        val dx = if (n > 1) size.width / (n - 1) else 0f
        fun yFor(w: Double) = (size.height * (1f - ((w - minW) / span).toFloat())).toFloat()
        // linia celu (przerywana)
        if (goalW != null) {
            val gy = yFor(goalW)
            drawLine(lineColor, androidx.compose.ui.geometry.Offset(0f, gy), androidx.compose.ui.geometry.Offset(size.width, gy),
                strokeWidth = 3f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
        // fill pod krzywą
        val fill = androidx.compose.ui.graphics.Path()
        sorted.forEachIndexed { i, s -> val x = i * dx; val y = yFor(s.weightKg); if (i == 0) fill.moveTo(x, y) else fill.lineTo(x, y) }
        fill.lineTo((n - 1) * dx, size.height); fill.lineTo(0f, size.height); fill.close()
        drawPath(fill, fillColor)
        // krzywa
        val path = androidx.compose.ui.graphics.Path()
        sorted.forEachIndexed { i, s -> val x = i * dx; val y = yFor(s.weightKg); if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        drawPath(path, greenColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        sorted.forEachIndexed { i, s -> drawCircle(greenColor, radius = 7f, center = androidx.compose.ui.geometry.Offset(i * dx, yFor(s.weightKg))) }
    }
}
