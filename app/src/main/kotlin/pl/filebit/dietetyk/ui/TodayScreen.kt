package pl.filebit.dietetyk.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
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
import androidx.compose.runtime.collectAsState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pl.filebit.dietetyk.BuildConfig
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.update.ApkInstaller
import pl.filebit.dietetyk.update.UpdateChecker
import pl.filebit.dietetyk.update.UpdateInfo
import pl.filebit.dietetyk.core.calc.DailyMacroGoal
import pl.filebit.dietetyk.core.calc.GoalPipeline
import pl.filebit.dietetyk.data.db.EnergyLogEntity
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min

private data class DayMeal(val name: String, val time: String, val kcal: Int, val p: Int, val c: Int, val f: Int, val emoji: String = "🍽️")

/** Zgadnij emoji dania z nazwy (fallback gdy plan nie niesie emoji). */
internal fun mealEmoji(name: String): String {
    val n = name.lowercase()
    return when {
        listOf("owsian", "płatki", "musli", "jogurt", "kanapk", "śniad", "twaróg", "twarog").any { n.contains(it) } -> "🥣"
        listOf("kurczak", "indyk", "wołow", "wolow", "kotlet", "stek", "schab", "mięso", "mieso").any { n.contains(it) } -> "🍗"
        listOf("sałat", "salat", "warzyw", "surówk", "surowk", "brokuł", "brokul").any { n.contains(it) } -> "🥗"
        listOf("ryba", "łoso", "loso", "tuńczyk", "tunczyk", "dorsz", "krewet").any { n.contains(it) } -> "🐟"
        listOf("ryż", "ryz", "makaron", "kasza", "ziemniak", "quinoa").any { n.contains(it) } -> "🍚"
        listOf("zupa", "krem", "rosół", "rosol").any { n.contains(it) } -> "🍲"
        listOf("jaj", "omlet", "jajecz").any { n.contains(it) } -> "🍳"
        listOf("owoc", "banan", "jabłk", "jablk", "malin", "truskaw", "borów").any { n.contains(it) } -> "🍎"
        listOf("koktajl", "smoothie", "shake", "napój", "napoj").any { n.contains(it) } -> "🥤"
        listOf("kanapka", "pieczyw", "chleb", "bułk", "bulk", "tost").any { n.contains(it) } -> "🥪"
        else -> "🍽️"
    }
}

@Composable
fun TodayScreen(app: DietetykApp, onBell: () -> Unit = {}, onGoToChat: () -> Unit = {}, onBrowseProducts: () -> Unit = {}) {
    var goal by remember { mutableStateOf<DailyMacroGoal?>(null) }
    var consumed by remember { mutableIntStateOf(0) }
    var consumedP by remember { mutableIntStateOf(0) }
    var consumedC by remember { mutableIntStateOf(0) }
    var consumedF by remember { mutableIntStateOf(0) }
    var hasProfile by remember { mutableStateOf(true) }
    var meals by remember { mutableStateOf<List<DayMeal>>(emptyList()) }
    val userName = remember { app.settings.userName }
    val dayKey = remember { LocalDate.now().let { "%04d%02d%02d".format(it.year, it.monthValue, it.dayOfMonth) } }
    var water by remember { mutableIntStateOf(app.settings.waterMl(dayKey)) }
    val waterTarget = 2500
    var reloadKey by remember { mutableIntStateOf(0) }
    val eaten = remember(reloadKey) { app.settings.eatenMeals(dayKey) }

    LaunchedEffect(reloadKey) {
        val p = app.profileRepo.get()
        hasProfile = p != null
        val w = app.weightRepo.latest()?.weightKg
        goal = p?.let { GoalPipeline.compute(it, latestMeasuredWeightKg = w) }
        val since = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val logs = app.database.energyLogDao().since(since)
        consumed = logs.sumOf { it.kcalConsumed }
        consumedP = logs.sumOf { it.proteinG }
        consumedC = logs.sumOf { it.carbsG }
        consumedF = logs.sumOf { it.fatG }
        app.database.planDao().get()?.let { plan ->
            meals = runCatching {
                Json.parseToJsonElement(plan.planJson).jsonObject["meals"]!!.jsonArray.map { e ->
                    val o = e.jsonObject
                    DayMeal(
                        o["name"]?.jsonPrimitive?.content ?: "Posiłek",
                        o["timeHint"]?.jsonPrimitive?.content ?: "",
                        o["kcal"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        o["proteinG"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        o["carbsG"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        o["fatG"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                        o["emoji"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: mealEmoji(o["name"]?.jsonPrimitive?.content ?: "")
                    )
                }
            }.getOrDefault(emptyList())
        }
    }

    val unread by app.database.notificationDao().unreadCount().collectAsState(initial = 0)
    var update by remember { mutableStateOf<UpdateInfo?>(null) }
    var updating by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { update = UpdateChecker.latest(BuildConfig.VERSION_NAME) }

    // Aparat: zdjęcie posiłku → wysyłka do czatu (AI rozpoznaje + liczy makro).
    var showAddSheet by remember { mutableStateOf(false) }
    var photoUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = photoUri
        if (success && uri != null) {
            app.pendingChatPhoto = pl.filebit.dietetyk.ImageUtil.persistChatImage(context, uri)
            onGoToChat()
        }
    }
    val launchCamera = {
        val uri = pl.filebit.dietetyk.ImageUtil.newPhotoUri(context)
        photoUri = uri
        cameraLauncher.launch(uri)
    }

    // Skaner kodów kreskowych → OpenFoodFacts → arkusz wyniku (zaloguj / dodaj do bazy).
    var scanState by remember { mutableStateOf<ScanState?>(null) }
    val launchScan = {
        launchBarcodeScan(context) { code ->
            if (code.isNullOrBlank()) return@launchBarcodeScan
            scanState = ScanState.Scanning
            scope.launch {
                val off = runCatching { app.offClient.lookup(query = null, barcode = code) }.getOrNull()
                scanState = if (off != null) ScanState.Found(code, off) else ScanState.NotFound(code)
            }
        }
    }
    scanState?.let { st ->
        BarcodeResultSheet(app, st, onDismiss = { scanState = null }, onAskDietitian = { msg ->
            app.pendingChatMessage = msg; onGoToChat()
        })
    }

    if (showAddSheet) {
        AddMealSheet(
            onDismiss = { showAddSheet = false },
            onPhoto = { showAddSheet = false; launchCamera() },
            onBrowseProducts = { showAddSheet = false; onBrowseProducts() },
            onScan = { showAddSheet = false; launchScan() }
        )
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column {
                Text("Cześć${if (userName.isNotBlank()) ", $userName" else ""}! 👋", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text(polishDate(), color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp, bottom = 16.dp))
            }
            Box(Modifier.clickable { onBell() }.padding(4.dp), contentAlignment = Alignment.TopEnd) {
                Text("🔔", fontSize = 24.sp)
                if (unread > 0) {
                    Box(Modifier.size(18.dp).background(Palette.Orange, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                        Text("$unread", color = androidx.compose.ui.graphics.Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        update?.let { u ->
            Row(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).background(Palette.Green, RoundedCornerShape(14.dp))
                    .clickable(enabled = !updating) {
                        updating = true
                        scope.launch { ApkInstaller.downloadAndInstall(context, u.apkUrl); updating = false }
                    }.padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⬆️ Nowa wersja ${u.version} dostępna", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(if (updating) "Pobieram…" else "Pobierz", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }

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
                    KcalRing(consumed = consumed, target = g.kcal, modifier = Modifier.size(128.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        MacroRing("Białko", consumedP, g.proteinG, Palette.Green)
                        MacroRing("Węgle", consumedC, g.carbsG, Palette.Orange)
                        MacroRing("Tłuszcz", consumedF, g.fatG, Palette.Blue)
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
                    Box(
                        Modifier.padding(top = 12.dp).background(Palette.Green, RoundedCornerShape(12.dp))
                            .clickable { onGoToChat() }.padding(horizontal = 18.dp, vertical = 9.dp)
                    ) { Text("Odpowiedz", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }

        WaterCard(water, waterTarget) { delta ->
            water = (water + delta).coerceAtLeast(0)
            app.settings.setWaterMl(dayKey, water)
        }

        if (meals.isEmpty() && hasProfile) {
            Column(
                Modifier.fillMaxWidth().padding(top = 20.dp).card(18.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("🌱", fontSize = 40.sp)
                Text("Zacznij od rozmowy", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text(
                    "Nie masz jeszcze planu na dziś. Napisz do dietetyka — ułoży plan i policzy kalorie.",
                    color = Palette.Muted, fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp)
                )
                Box(
                    Modifier.padding(top = 14.dp).background(Palette.Green, RoundedCornerShape(12.dp)).clickable { onGoToChat() }.padding(horizontal = 20.dp, vertical = 11.dp)
                ) { Text("Napisz do dietetyka", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }

        if (meals.isNotEmpty()) {
            val doneCount = meals.count { app.settings.mealStatus(dayKey, it.name).first == "EATEN" }
            Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Posiłki dnia", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$doneCount z ${meals.size}", color = Palette.Muted, fontSize = 13.sp)
            }
            meals.forEachIndexed { i, m ->
                val (status, logId) = app.settings.mealStatus(dayKey, m.name)
                MealRow(
                    index = i, meal = m, status = status,
                    onEat = {
                        scope.launch {
                            val id = app.database.energyLogDao().insert(
                                EnergyLogEntity(dateMs = System.currentTimeMillis(), kcalConsumed = m.kcal, isComplete = false, proteinG = m.p, carbsG = m.c, fatG = m.f)
                            )
                            app.settings.setMealStatus(dayKey, m.name, "EATEN", id)
                            reloadKey++
                        }
                    },
                    onSkip = { app.settings.setMealStatus(dayKey, m.name, "SKIPPED"); reloadKey++; onGoToChat() },
                    onReplace = { app.settings.setMealStatus(dayKey, m.name, "REPLACED"); reloadKey++; launchCamera() },
                    onUndo = {
                        scope.launch {
                            if (logId > 0) app.database.energyLogDao().deleteById(logId)
                            app.settings.setMealStatus(dayKey, m.name, "PLANNED")
                            reloadKey++
                        }
                    }
                )
            }
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(100.dp))
    }
        // FAB „Sfotografuj posiłek" (główna akcja logowania)
        Row(
            Modifier.align(Alignment.BottomEnd).padding(20.dp)
                .background(Palette.Green, RoundedCornerShape(28.dp))
                .clickable { showAddSheet = true }.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("📷", fontSize = 18.sp)
            Text("Sfotografuj posiłek", color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun AddMealSheet(onDismiss: () -> Unit, onPhoto: () -> Unit, onBrowseProducts: () -> Unit, onScan: () -> Unit) {
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Card) {
        Column(Modifier.fillMaxWidth().padding(20.dp).padding(bottom = 24.dp)) {
            Text("Co chcesz dodać?", color = Palette.TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            Row(
                Modifier.fillMaxWidth().background(Palette.GreenTint, RoundedCornerShape(14.dp)).clickable { onPhoto() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📷", fontSize = 26.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Zdjęcie posiłku", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("AI rozpozna danie i policzy kalorie", color = Palette.Muted, fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).background(Palette.GreenTint, RoundedCornerShape(14.dp)).clickable { onBrowseProducts() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🥫", fontSize = 26.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Przeglądaj produkty", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Baza z makro, ulubione, zaloguj porcję", color = Palette.Muted, fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 10.dp).background(Palette.GreenTint, RoundedCornerShape(14.dp)).clickable { onScan() }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🏷️", fontSize = 26.sp)
                Column(Modifier.padding(start = 12.dp)) {
                    Text("Skanuj kod kreskowy", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("Produkt z opakowania → makro z bazy", color = Palette.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WaterCard(water: Int, target: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp).card(18.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("💧 Woda", color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("${litersLabel(water)} / ${litersLabel(target)} l", color = Palette.Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        // kropki (1 kropka = 250 ml) — jak w Claude Design
        val dots = (target + 249) / 250
        val filled = water / 250
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(dots) { i ->
                Box(Modifier.size(14.dp).background(if (i < filled) Palette.Blue else Palette.Line, androidx.compose.foundation.shape.CircleShape))
            }
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
private fun MealRow(index: Int, meal: DayMeal, status: String, onEat: () -> Unit, onSkip: () -> Unit, onReplace: () -> Unit, onUndo: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val skipped = status == "SKIPPED"
    Row(
        Modifier.fillMaxWidth().padding(bottom = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).alpha(if (skipped) 0.5f else 1f).background(Palette.GreenTint, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { Text(meal.emoji, fontSize = 22.sp) }
        Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
            Text("Posiłek ${index + 1}" + (if (meal.time.isNotBlank()) " · ${meal.time}" else ""), color = Palette.Muted, fontSize = 11.sp)
            Text(
                meal.name, color = if (skipped) Palette.Muted else Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                textDecoration = if (skipped) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${meal.kcal} kcal", color = Palette.Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
            Box {
                val (label, color) = when (status) {
                    "EATEN" -> "✓ Zjadłem" to Palette.Green
                    "SKIPPED" -> "✕ Pominięty" to Palette.Muted
                    "REPLACED" -> "🔄 Coś innego" to Palette.Blue
                    else -> "⏳ Zjadłem?" to Palette.GreenDark
                }
                Box(Modifier.padding(top = 3.dp).background(Palette.GreenTint, RoundedCornerShape(8.dp)).clickable { menu = true }.padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("✓ Zjadłem") }, onClick = { menu = false; onEat() })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("✕ Pomiń — powiem dlaczego") }, onClick = { menu = false; onSkip() })
                    androidx.compose.material3.DropdownMenuItem(text = { Text("📷 Zjadłem coś innego") }, onClick = { menu = false; onReplace() })
                    if (status != "PLANNED") androidx.compose.material3.DropdownMenuItem(text = { Text("↩ Cofnij") }, onClick = { menu = false; onUndo() })
                }
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().card(18.dp).background(Palette.Card, RoundedCornerShape(18.dp)).padding(16.dp)) { content() }
}

@Composable
private fun MacroRing(name: String, consumed: Int, target: Int, color: Color) {
    val frac = if (target > 0) min(1f, consumed.toFloat() / target) else 0f
    val lineColor = Palette.Line
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 5.dp)) {
        Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 5f
                val d = size.minDimension - stroke
                val tl = androidx.compose.ui.geometry.Offset((size.width - d) / 2, (size.height - d) / 2)
                drawArc(lineColor, -90f, 360f, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, -90f, 360f * frac, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Column(Modifier.padding(start = 8.dp)) {
            Text(name, color = Palette.Muted, fontSize = 11.sp)
            Text("$consumed", color = Palette.TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Text("/${target}g", color = Palette.Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 3.dp, top = 6.dp))
    }
}

@Composable
private fun KcalRing(consumed: Int, target: Int, modifier: Modifier) {
    val frac = if (target > 0) min(1f, consumed.toFloat() / target) else 0f
    val lineColor = Palette.Line
    val greenColor = Palette.Green
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16f
            val d = min(size.width, size.height) - stroke
            val topLeft = androidx.compose.ui.geometry.Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(lineColor, -90f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(greenColor, -90f, 360f * frac, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${(target - consumed).coerceAtLeast(0)}", color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("kcal", color = Palette.Muted, fontSize = 11.sp)
        }
    }
}

private fun litersLabel(ml: Int): String = "%.1f".format(ml / 1000.0).replace('.', ',')

private fun polishDate(): String {
    val d = LocalDate.now()
    val dni = listOf("poniedziałek", "wtorek", "środa", "czwartek", "piątek", "sobota", "niedziela")
    val mies = listOf("stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca", "lipca", "sierpnia", "września", "października", "listopada", "grudnia")
    return "${dni[d.dayOfWeek.value - 1]}, ${d.dayOfMonth} ${mies[d.monthValue - 1]}"
}
