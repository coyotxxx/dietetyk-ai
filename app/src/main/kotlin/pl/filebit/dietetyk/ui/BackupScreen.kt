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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.dietetyk.Backup
import pl.filebit.dietetyk.DietetykApp

/** 6.3 — Kopia zapasowa: jeden czysty ekran (eksport + przywróć + auto-kopia razem). */
@Composable
fun BackupScreen(app: DietetykApp, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var lastBackup by remember { mutableStateOf(Backup.lastBackupAt(ctx)) }
    var autoOn by remember { mutableStateOf(app.settings.autoBackupEnabled) }
    var pendingRestore by remember { mutableStateOf<android.net.Uri?>(null) }
    var restoreError by remember { mutableStateOf<String?>(null) }
    val restoreLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) pendingRestore = uri }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        // Nagłówek
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Palette.Card, RoundedCornerShape(13.dp)).clickable { onBack() },
                contentAlignment = Alignment.Center
            ) { Text("‹", color = Palette.TextDark, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
            Text("Kopia zapasowa", color = Palette.TextDark, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(start = 12.dp))
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            // Hero — ostatnia kopia
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.GreenTint, RoundedCornerShape(24.dp)).padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Palette.Card, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Text("☁️", fontSize = 30.sp)
                }
                Text(
                    if (lastBackup > 0) "Ostatnia kopia: ${formatBackupTime(lastBackup)}" else "Jeszcze bez kopii",
                    color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 14.dp)
                )
                Text(
                    if (autoOn) "Automatyczna · przechowywana lokalnie" else "Ręczna · przechowywana lokalnie",
                    color = Palette.GreenDark, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 3.dp)
                )
            }

            Text(
                "Wszystkie dane, plany i historia rozmów z dietetykiem w jednym pliku — bez chmury i kont.",
                color = Palette.Muted, fontSize = 13.5.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 6.dp, end = 6.dp)
            )

            // Eksportuj (główny)
            Box(
                Modifier.fillMaxWidth().padding(top = 20.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Green, RoundedCornerShape(18.dp))
                    .clickable {
                        Backup.exportShareIntent(ctx, app, includeApiKey = true)?.let { ctx.startActivity(Intent.createChooser(it, "Kopia zapasowa")) }
                        lastBackup = Backup.lastBackupAt(ctx)
                    }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("↑  Eksportuj dane do pliku", color = Color.White, fontSize = 15.5.sp, fontWeight = FontWeight.Bold) }

            // Przywróć
            Box(
                Modifier.fillMaxWidth().padding(top = 11.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Card, RoundedCornerShape(18.dp))
                    .clickable { restoreLauncher.launch(arrayOf("*/*")) }.padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) { Text("↓  Przywróć z pliku", color = Palette.TextDark, fontSize = 15.5.sp, fontWeight = FontWeight.Bold) }

            // Kopia automatyczna
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).card(16.dp).background(Palette.Card, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Kopia automatyczna", color = Palette.TextDark, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Codziennie, w tle", color = Palette.Muted, fontSize = 12.sp)
                }
                Switch(
                    checked = autoOn,
                    onCheckedChange = { autoOn = it; app.settings.autoBackupEnabled = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Palette.Green, checkedThumbColor = Color.White)
                )
            }
        }
    }

    pendingRestore?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Przywrócić kopię?") },
            text = { Text("Obecne dane zostaną zastąpione danymi z kopii. Zrobimy kopię bezpieczeństwa, a aplikacja uruchomi się ponownie.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    val r = Backup.restoreFromUri(ctx, uri)
                    pendingRestore = null
                    when (r) {
                        is Backup.RestoreResult.Success -> Backup.restartApp(ctx)
                        is Backup.RestoreResult.Error -> restoreError = r.message
                    }
                }) { Text("Przywróć") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) { Text("Anuluj") } }
        )
    }
    restoreError?.let { msg ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { restoreError = null },
            title = { Text("Nie przywrócono") }, text = { Text(msg) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { restoreError = null }) { Text("OK") } }
        )
    }
}

private fun formatBackupTime(ms: Long): String {
    val z = java.time.ZoneId.systemDefault()
    val dt = java.time.Instant.ofEpochMilli(ms).atZone(z)
    val today = java.time.LocalDate.now(z)
    val hm = "%02d:%02d".format(dt.hour, dt.minute)
    return when (dt.toLocalDate()) {
        today -> "dziś $hm"
        today.minusDays(1) -> "wczoraj $hm"
        else -> "%02d.%02d.%d %s".format(dt.dayOfMonth, dt.monthValue, dt.year, hm)
    }
}
