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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ekran powitalny (pierwsze uruchomienie) — prowadzi prosto do wywiadu z Dietetykiem. */
@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Palette.Bg).systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(88.dp).background(Palette.Green, CircleShape), contentAlignment = Alignment.Center) {
            Text("🥗", fontSize = 44.sp)
        }
        Text("Dietetyk AI", color = Palette.TextDark, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 24.dp))
        Text(
            "Cześć! Jestem Twoim osobistym dietetykiem. Poznam Cię, ułożę plan i poprowadzę Cię do celu — krok po kroku.",
            color = Palette.Muted, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp)
        )

        Column(Modifier.padding(top = 28.dp)) {
            Feature("💬", "Krótka rozmowa", "Zadam kilka pytań, żeby Cię poznać.")
            Feature("🎯", "Plan skrojony pod Ciebie", "Policzę kalorie i makro, ułożę posiłki z przepisami.")
            Feature("📈", "Prowadzę Cię dalej", "Śledzę postępy i co tydzień sprawdzam, jak idzie.")
        }

        Box(
            Modifier.padding(top = 36.dp).fillMaxWidth().background(Palette.Green, RoundedCornerShape(16.dp))
                .clickable { onStart() }.padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) { Text("Zaczynamy →", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun Feature(emoji: String, title: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Text(emoji, fontSize = 24.sp, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(title, color = Palette.TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = Palette.Muted, fontSize = 14.sp)
        }
    }
}
