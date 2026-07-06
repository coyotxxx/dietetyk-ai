package pl.filebit.dietetyk.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.dietetyk.DietetykApp
import java.time.Instant
import java.time.ZoneId

@Composable
fun NotificationsScreen(app: DietetykApp, onBack: () -> Unit) {
    val items by app.database.notificationDao().observe().collectAsState(initial = emptyList())
    // Otwarcie ekranu = przeczytane.
    LaunchedEffect(Unit) { app.database.notificationDao().markAllRead() }

    Column(Modifier.fillMaxSize().background(Palette.Bg).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("← Powiadomienia", color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.clickable { onBack() })
        }
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 16.dp)) {
            if (items.isEmpty()) {
                Text("Brak powiadomień. Dietetyk odezwie się przy wizycie kontrolnej.", color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items.forEach { n ->
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp).card(14.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(Modifier.size(38.dp).background(Palette.GreenTint, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                        Text(notifIcon(n.title, n.body), fontSize = 18.sp)
                    }
                    Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(n.title, color = Palette.Green, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                            Text(timeLabel(n.timeMs), color = Palette.Muted, fontSize = 11.sp)
                        }
                        Text(n.body, color = Palette.TextDark, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}

private fun notifIcon(title: String, body: String): String {
    val t = (title + " " + body).lowercase()
    return when {
        listOf("wizyt", "kontroln", "check").any { t.contains(it) } -> "🩺"
        listOf("zważ", "waga", "pomiar", "obwód").any { t.contains(it) } -> "⚖️"
        listOf("posił", "jedz", "loguj", "kalor").any { t.contains(it) } -> "🍽️"
        listOf("plan").any { t.contains(it) } -> "🥗"
        else -> "🔔"
    }
}

private fun timeLabel(ms: Long): String {
    val min = (System.currentTimeMillis() - ms) / 60000
    return when {
        min < 1 -> "przed chwilą"
        min < 60 -> "$min min temu"
        min < 24 * 60 -> "${min / 60} godz. temu"
        min < 48 * 60 -> "wczoraj"
        min < 7 * 24 * 60 -> "${min / (24 * 60)} dni temu"
        else -> Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).let { "%02d.%02d.%d".format(it.dayOfMonth, it.monthValue, it.year) }
    }
}
