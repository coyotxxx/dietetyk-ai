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
        Box(
            Modifier.padding(top = 12.dp).background(Palette.Green, RoundedCornerShape(12.dp))
                .clickable { app.triggerCheckInNow() }.padding(horizontal = 16.dp, vertical = 10.dp)
        ) { Text("Sprawdź teraz (wizyta kontrolna)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(top = 12.dp)) {
            if (items.isEmpty()) {
                Text("Brak powiadomień. Dietetyk odezwie się przy wizycie kontrolnej.", color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items.forEach { n ->
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Palette.Card, RoundedCornerShape(14.dp)).padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(n.title, color = Palette.Green, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text(timeLabel(n.timeMs), color = Palette.Muted, fontSize = 11.sp)
                    }
                    Text(n.body, color = Palette.TextDark, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

private fun timeLabel(ms: Long): String {
    val d = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault())
    return "%02d.%02d %02d:%02d".format(d.dayOfMonth, d.monthValue, d.hour, d.minute)
}
