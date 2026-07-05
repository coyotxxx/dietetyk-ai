package pl.filebit.dietetyk.ui

import android.content.Intent
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
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.Backup
import pl.filebit.dietetyk.BuildConfig
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.update.ApkInstaller
import pl.filebit.dietetyk.update.UpdateChecker
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile

/** Formatuje wagę: bez zbędnego „.0", przecinek dziesiętny (84.0→„84", 81.6→„81,6"). */
private fun kg(d: Double?): String = d?.let { (if (it % 1.0 == 0.0) "%.0f" else "%.1f").format(it).replace('.', ',') } ?: "—"

@Composable
fun ProfileScreen(app: DietetykApp) {
    var profile by remember { mutableStateOf<NutritionProfile?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var currentW by remember { mutableStateOf<Double?>(null) }
    var startW by remember { mutableStateOf<Double?>(null) }
    var meals by remember { androidx.compose.runtime.mutableIntStateOf(4) }
    LaunchedEffect(Unit) {
        val prof = app.profileRepo.get()
        profile = prof
        meals = prof?.mealsPerDay ?: 4
        val samples = app.weightRepo.since(0)
        currentW = samples.maxByOrNull { it.dateMs }?.weightKg ?: prof?.weightKg
        startW = samples.minByOrNull { it.dateMs }?.weightKg ?: prof?.weightKg
        loaded = true
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Zapis pojedynczego pola profilu do Room (cel/posiłki) + odświeżenie lokalnego stanu.
    fun saveProfilePatch(patch: (NutritionProfile) -> NutritionProfile) {
        val cur = profile ?: return
        val np = patch(cur)
        profile = np
        scope.launch { app.profileRepo.save(np, System.currentTimeMillis()) }
    }
    var notifOn by remember { mutableStateOf(app.settings.notificationsEnabled) }
    var updateStatus by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf(false) }

    if (showKeyDialog) {
        var keyText by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            title = { Text("Klucz Claude API") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = keyText, onValueChange = { keyText = it },
                    placeholder = { Text("sk-ant-…") }, modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    if (keyText.isNotBlank()) app.settings.apiKey = keyText.trim()
                    showKeyDialog = false
                }) { Text("Zapisz") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { showKeyDialog = false }) { Text("Anuluj") } }
        )
    }

    val white = androidx.compose.ui.graphics.Color.White
    val userName = remember { app.settings.userName }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(Palette.Green, androidx.compose.foundation.shape.CircleShape), contentAlignment = Alignment.Center) {
                Text(userName.take(1).uppercase().ifBlank { "🙂" }, color = white, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text(userName.ifBlank { "Profil" }, color = Palette.TextDark, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                Text("Twój profil zdrowotny", color = Palette.Muted, fontSize = 13.sp)
            }
        }
        val p = profile
        if (p == null) {
            Text(
                if (loaded) "Brak profilu — porozmawiaj z Dietetykiem, zrobi wywiad." else "Wczytuję…",
                color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)
            )
            return
        }

        // Karta celu: start → teraz → cel + pasek postępu (tap = edycja celu)
        var showGoalDialog by remember { mutableStateOf(false) }
        val goalW = p.goalWeightKg?.takeIf { it > 0 }
        val cur = currentW ?: p.weightKg
        val start = startW ?: p.weightKg
        val frac = if (goalW != null && cur != null && start != null && kotlin.math.abs(goalW - start) > 0.1)
            (kotlin.math.abs(cur - start) / kotlin.math.abs(goalW - start)).coerceIn(0.0, 1.0).toFloat() else 0f
        Column(Modifier.fillMaxWidth().padding(top = 14.dp).background(Palette.Green, RoundedCornerShape(18.dp)).clickable { showGoalDialog = true }.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MÓJ CEL · ${goalLabel(p.goal).uppercase()}", color = white.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (frac > 0f) Text("${(frac * 100).toInt()}%", color = white, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
            if (goalW == null) {
                Text(kg(cur) + " kg", color = white, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))
                Text("Ustal wagę docelową →", color = white.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
            } else {
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(kg(start), color = white.copy(alpha = 0.7f), fontSize = 16.sp)
                    Text("  →  ", color = white.copy(alpha = 0.7f), fontSize = 16.sp)
                    Text(kg(cur) + " kg", color = white, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("  →  " + kg(goalW), color = white.copy(alpha = 0.7f), fontSize = 16.sp)
                }
                Box(Modifier.fillMaxWidth().padding(top = 12.dp).height(7.dp).background(white.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) {
                    Box(Modifier.fillMaxWidth(frac).height(7.dp).background(Palette.Orange, RoundedCornerShape(4.dp)))
                }
            }
        }
        if (showGoalDialog) {
            var goalText by remember { mutableStateOf(goalW?.let { kg(it) } ?: "") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text("Waga docelowa") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = goalText, onValueChange = { goalText = it },
                        label = { Text("kg") }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        goalText.replace(',', '.').toDoubleOrNull()?.let { x -> saveProfilePatch { it.copy(goalWeightKg = x) } }
                        showGoalDialog = false
                    }) { Text("Zapisz") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { showGoalDialog = false }) { Text("Anuluj") } }
            )
        }

        InfoRow("Płeć", if (p.gender == Gender.MALE) "mężczyzna" else "kobieta")
        InfoRow("Wiek", "${p.ageYears} lat")
        InfoRow("Wzrost", "${p.heightCm} cm")
        InfoRow("Waga", (currentW ?: p.weightKg)?.let { kg(it) + " kg" } ?: "—")
        InfoRow("Aktywność", activityLabel(p))
        InfoRow("Treningi/tydzień", "${p.daysPerWeek}")
        InfoRow("Cel", goalLabel(p.goal))
        InfoRow("Tempo", "${p.paceKgPerWeek} kg/tydz")
        InfoRow("Preferencje", (p.dietaryPrefs ?: "").ifBlank { "—" })

        // Stepper liczby posiłków
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp).background(Palette.Card, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🍽️ Liczba posiłków", color = Palette.TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(32.dp).background(Palette.GreenTint, androidx.compose.foundation.shape.CircleShape).clickable { if (meals > 2) { meals--; saveProfilePatch { it.copy(mealsPerDay = meals) } } }, contentAlignment = Alignment.Center) { Text("−", color = Palette.GreenDark, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Text("$meals", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 14.dp))
                Box(Modifier.size(32.dp).background(Palette.GreenTint, androidx.compose.foundation.shape.CircleShape).clickable { if (meals < 8) { meals++; saveProfilePatch { it.copy(mealsPerDay = meals) } } }, contentAlignment = Alignment.Center) { Text("+", color = Palette.GreenDark, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }

        Text("Ustawienia", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))

        Text("Motyw", color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("system" to "System", "light" to "Jasny", "dark" to "Ciemny").forEach { (mode, label) ->
                val active = app.themeMode.value == mode
                Box(
                    Modifier.weight(1f).background(if (active) Palette.Green else Palette.Card, RoundedCornerShape(12.dp))
                        .clickable { app.themeMode.value = mode; app.settings.themeMode = mode }.padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) { Text(label, color = if (active) androidx.compose.ui.graphics.Color.White else Palette.TextDark, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }

        SettingRow("🔔 Powiadomienia (wizyty)", if (notifOn) "Włączone" else "Wyłączone") {
            notifOn = !notifOn; app.settings.notificationsEnabled = notifOn
        }
        SettingRow("⬆️ Sprawdź aktualizacje", updateStatus.ifBlank { "wersja ${BuildConfig.VERSION_NAME}" }) {
            updateStatus = "Sprawdzam…"
            scope.launch {
                val u = UpdateChecker.latest(BuildConfig.VERSION_NAME)
                if (u == null) updateStatus = "Masz najnowszą (${BuildConfig.VERSION_NAME})"
                else {
                    updateStatus = "Pobieram ${u.version}…"
                    ApkInstaller.downloadAndInstall(ctx, u.apkUrl)
                    updateStatus = "Dostępna ${u.version}"
                }
            }
        }
        SettingRow("🔑 Klucz Claude API", "Zmień ›") { showKeyDialog = true }
        SettingRow("💾 Kopia zapasowa", "Udostępnij ›") {
            Backup.exportShareIntent(ctx, app)?.let { ctx.startActivity(Intent.createChooser(it, "Kopia zapasowa")) }
        }

        var showClearChat by remember { mutableStateOf(false) }
        SettingRow("🗑️ Wyczyść rozmowę", "Wyczyść ›") { showClearChat = true }
        if (showClearChat) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showClearChat = false },
                title = { Text("Wyczyścić rozmowę?") },
                text = { Text("Cała historia czatu z dietetykiem zostanie usunięta. Profil, plan i pomiary zostają.") },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        scope.launch { app.database.chatMessageDao().clear(); app.settings.chatHistoryJson = "[]" }
                        app.pendingChatClear = true
                        showClearChat = false
                    }) { Text("Wyczyść") }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { showClearChat = false }) { Text("Anuluj") } }
            )
        }

        Text(
            "🛡️ Dietetyk AI nie zastępuje lekarza ani konsultacji medycznej.",
            color = Palette.Muted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 8.dp)
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(Palette.Card, RoundedCornerShape(12.dp))
            .clickable { onClick() }.padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(value, color = Palette.Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp).background(Palette.Card, RoundedCornerShape(12.dp)).padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Palette.Muted, fontSize = 14.sp)
        Text(value, color = Palette.TextDark, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

private fun goalLabel(g: DietGoalType): String = when (g) {
    DietGoalType.FAT_LOSS -> "Redukcja"
    DietGoalType.MUSCLE_GAIN -> "Masa"
    DietGoalType.RECOMP -> "Rekompozycja"
    DietGoalType.MAINTAIN -> "Utrzymanie"
    DietGoalType.STRENGTH -> "Siła"
    DietGoalType.ENDURANCE -> "Wydolność"
    DietGoalType.HEALTH -> "Zdrowie"
    DietGoalType.EVENT_PREP -> "Na termin"
}

private fun activityLabel(p: NutritionProfile): String = when (p.activityLevel.name) {
    "SEDENTARY" -> "siedzący"
    "LIGHT" -> "lekko aktywny"
    "MODERATE" -> "umiarkowany"
    "VERY_ACTIVE" -> "bardzo aktywny"
    else -> "ekstremalny"
}
