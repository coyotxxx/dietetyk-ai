package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.BuildConfig
import pl.filebit.dietetyk.update.ApkInstaller
import pl.filebit.dietetyk.update.UpdateInfo

/** 6.2 — Aktualizacja aplikacji (bottom sheet): Nowa wersja + CO NOWEGO + Zainstaluj / Później. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(update: UpdateInfo, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Palette.Bg) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Palette.Green, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                    Text("⬇", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
                Column(Modifier.padding(start = 13.dp)) {
                    Text("Nowa wersja ${update.version}", color = Palette.TextDark, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Aktualna: ${BuildConfig.VERSION_NAME}", color = Palette.Muted, fontSize = 12.5.sp)
                }
            }

            val bullets = remember(update.notes) { parseNotes(update.notes) }
            if (bullets.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 18.dp).clip(RoundedCornerShape(18.dp)).background(Palette.Card, RoundedCornerShape(18.dp)).padding(15.dp)) {
                    Text("CO NOWEGO", color = Palette.Muted, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                    bullets.forEach { b ->
                        Row(Modifier.padding(top = 9.dp)) {
                            Text("•", color = Palette.Green, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(end = 9.dp))
                            Text(b, color = Palette.TextDark, fontSize = 14.sp)
                        }
                    }
                }
            }

            if (downloading) {
                Text("Pobieram…", color = Palette.GreenDark, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 7.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)), color = Palette.Green, trackColor = Palette.GreenTint)
            }

            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                Box(
                    Modifier.weight(1.5f).clip(RoundedCornerShape(18.dp)).background(Palette.Green, RoundedCornerShape(18.dp))
                        .clickable(enabled = !downloading) {
                            downloading = true
                            scope.launch { ApkInstaller.downloadAndInstall(ctx, update.apkUrl); downloading = false }
                        }.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Zainstaluj teraz", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(Palette.Card, RoundedCornerShape(18.dp))
                        .clickable { onDismiss() }.padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) { Text("Później", color = Palette.TextDark, fontSize = 15.5.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

/** Zamień treść release'u (GitHub body) na max 4 czyste punkty. */
private fun parseNotes(notes: String): List<String> =
    notes.lines().map { it.trim().removePrefix("-").removePrefix("*").removePrefix("•").trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("Co-Authored") && !it.startsWith("Claude-") && !it.startsWith("🤖") }
        .take(4)
