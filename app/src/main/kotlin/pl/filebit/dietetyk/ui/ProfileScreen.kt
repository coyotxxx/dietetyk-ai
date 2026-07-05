package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
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
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.core.model.DietGoalType
import pl.filebit.dietetyk.core.model.Gender
import pl.filebit.dietetyk.core.model.NutritionProfile

@Composable
fun ProfileScreen(app: DietetykApp) {
    var profile by remember { mutableStateOf<NutritionProfile?>(null) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { profile = app.profileRepo.get(); loaded = true }

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
