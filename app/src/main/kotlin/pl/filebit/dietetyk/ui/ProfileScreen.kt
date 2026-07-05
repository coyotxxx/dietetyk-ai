package pl.filebit.dietetyk.ui

import android.content.Intent
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
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.Backup
import pl.filebit.dietetyk.BuildConfig
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.update.ApkInstaller
import pl.filebit.dietetyk.update.UpdateChecker
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile

@Composable
fun ProfileScreen(app: DietetykApp) {
    var profile by remember { mutableStateOf<NutritionProfile?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { profile = app.profileRepo.get(); loaded = true }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Profil", color = Palette.TextDark, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        val p = profile
        if (p == null) {
            Text(
                if (loaded) "Brak profilu — porozmawiaj z Dietetykiem, zrobi wywiad." else "Wczytuję…",
                color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)
            )
            return
        }

        // Hero cel
        Column(Modifier.fillMaxWidth().padding(top = 12.dp).background(Palette.Green, RoundedCornerShape(18.dp)).padding(18.dp)) {
            Text("MÓJ CEL · ${goalLabel(p.goal).uppercase()}", color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                (p.weightKg?.let { "${it} kg" } ?: "—"),
                color = androidx.compose.ui.graphics.Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 4.dp)
            )
        }

        InfoRow("Płeć", if (p.gender == Gender.MALE) "mężczyzna" else "kobieta")
        InfoRow("Wiek", "${p.ageYears} lat")
        InfoRow("Wzrost", "${p.heightCm} cm")
        InfoRow("Waga", p.weightKg?.let { "$it kg" } ?: "—")
        InfoRow("Aktywność", activityLabel(p))
        InfoRow("Treningi/tydzień", "${p.daysPerWeek}")
        InfoRow("Cel", goalLabel(p.goal))
        InfoRow("Tempo", "${p.paceKgPerWeek} kg/tydz")

        Text("Ustawienia", color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))

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
