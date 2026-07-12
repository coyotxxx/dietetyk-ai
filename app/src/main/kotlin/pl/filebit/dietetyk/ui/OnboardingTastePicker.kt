package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import pl.filebit.dietetyk.DietetykApp
import pl.filebit.dietetyk.data.db.FoodProductEntity
import pl.filebit.dietetyk.data.db.Pref

/**
 * Onboardingowy PICKER SMAKU (werdykt ja+Fable): szybka siatka popularnych produktów, dotknij co lubisz
 * (❤️) / czego nie jesz (🚫). Rozwiązuje „recall vs recognition" — user rozpoznaje z listy zamiast wyliczać
 * z głowy. Reużywa istniejącej bazy (preference), zero pól tekstowych/skanera — jeden ekran, „Gotowe".
 *
 * Wejście z czatu (akcja `[[Otwórz i zaznacz produkty]]`), wyjście → pendingChatMessage → AI kontynuuje wywiad.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingTastePicker(app: DietetykApp, onDone: () -> Unit) {
    val all by app.database.foodProductDao().observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    // Kurowana lista popularnych produktów (podzbiór seed) w kolejności kategorii — szybka do przejrzenia.
    val byName = all.associateBy { it.name }
    val groups = FEATURED.mapNotNull { (cat, names) ->
        val items = names.mapNotNull { byName[it] }
        if (items.isEmpty()) null else cat to items
    }

    val cycle: (FoodProductEntity) -> Unit = { p ->
        val next = when (p.preference) { Pref.NEUTRAL -> Pref.PREFER; Pref.PREFER -> Pref.AVOID; else -> Pref.NEUTRAL }
        scope.launch { app.database.foodProductDao().setPreference(p.id, next) }
    }
    val likedCount = all.count { it.preference == Pref.PREFER }
    val avoidCount = all.count { it.preference == Pref.AVOID }

    Column(Modifier.fillMaxSize().background(Palette.Bg)) {
        Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)) {
            Text("Co lubisz, czego nie jesz?", color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("Dotknij: raz = lubię ❤️, drugi = nie jem 🚫. Na tej podstawie ułożę plan z Twoich produktów.",
                color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }

        LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 16.dp)) {
            groups.forEach { (cat, items) ->
                item(key = "h_$cat") {
                    Text(cat, color = Palette.Green, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
                }
                item(key = "g_$cat") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items.forEach { p -> TasteChip(p) { cycle(p) } }
                    }
                }
            }
        }

        // Podsumowanie + Gotowe.
        Column(Modifier.fillMaxWidth().background(Palette.Card).padding(16.dp).navBar()) {
            Text(
                if (likedCount == 0 && avoidCount == 0) "Zaznacz choć kilka — im więcej, tym lepszy plan."
                else "Lubię: $likedCount  ·  Nie jem: $avoidCount",
                color = Palette.Muted, fontSize = 13.sp, modifier = Modifier.padding(bottom = 8.dp)
            )
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.Green, RoundedCornerShape(14.dp))
                    .clickable {
                        app.settings.tastePickerSeen = true
                        scope.launch {
                            val liked = app.database.foodProductDao().preferred().map { it.name }
                            val avoided = app.database.foodProductDao().avoided().map { it.name }
                            app.pendingChatMessage = buildString {
                                append("Oznaczyłem na liście produkty. ")
                                append(if (liked.isEmpty()) "Nie wskazałem ulubionych. " else "Lubię: ${liked.joinToString(", ")}. ")
                                if (avoided.isNotEmpty()) append("Nie jem: ${avoided.joinToString(", ")}. ")
                                append("Kontynuujmy wywiad.")
                            }
                            onDone()
                        }
                    }.padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) { Text("Gotowe", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun TasteChip(p: FoodProductEntity, onTap: () -> Unit) {
    val dark = Palette.isDark
    val (bg, border, icon) = when (p.preference) {
        Pref.PREFER -> Triple(Palette.Green.copy(alpha = if (dark) 0.30f else 0.16f), Palette.Green, "❤️ ")
        Pref.AVOID -> Triple(Palette.Error.copy(alpha = if (dark) 0.30f else 0.14f), Palette.Error, "🚫 ")
        else -> Triple(Palette.Card, Palette.Line, "")
    }
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(bg, RoundedCornerShape(20.dp))
            .border(1.dp, border, RoundedCornerShape(20.dp)).clickable { onTap() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$icon${p.name}", color = Palette.TextDark, fontSize = 13.sp,
            fontWeight = if (p.preference == Pref.NEUTRAL) FontWeight.Medium else FontWeight.Bold, maxLines = 1)
    }
}

/** Padding pod pasek nawigacji systemu (żeby „Gotowe" nie chował się pod gestami). */
private fun Modifier.navBar(): Modifier = this.navigationBarsPadding()

/** Kurowana lista: kategoria → nazwy produktów z seed (dokładne). ~58 popularnych, kolejność jak jemy. */
private val FEATURED: List<Pair<String, List<String>>> = listOf(
    "Mięso" to listOf("Pierś z kurczaka", "Pierś z indyka", "Schab wieprzowy", "Szynka wieprzowa gotowana", "Mięso mielone wołowe", "Kabanosy"),
    "Ryby" to listOf("Łosoś", "Dorsz", "Tuńczyk w sosie własnym", "Makrela", "Śledź"),
    "Nabiał" to listOf("Mleko 2%", "Jogurt naturalny 2%", "Jogurt grecki", "Skyr naturalny", "Twaróg chudy", "Serek wiejski", "Ser żółty gouda", "Mozzarella"),
    "Jaja" to listOf("Jajko kurze całe"),
    "Zboża" to listOf("Płatki owsiane", "Ryż biały", "Ryż brązowy", "Kasza gryczana", "Makaron pszenny", "Chleb żytni razowy", "Chleb pszenny", "Bułka pszenna"),
    "Warzywa" to listOf("Ziemniaki", "Pomidor", "Ogórek", "Papryka czerwona", "Brokuły", "Marchew", "Szpinak", "Sałata", "Cukinia", "Pieczarki", "Awokado", "Kukurydza konserwowa"),
    "Owoce" to listOf("Banan", "Jabłko", "Truskawki", "Maliny", "Borówki", "Winogrona", "Pomarańcza", "Arbuz"),
    "Strączki" to listOf("Soczewica czerwona", "Ciecierzyca", "Fasola czerwona", "Tofu"),
    "Orzechy" to listOf("Orzechy włoskie", "Migdały", "Masło orzechowe"),
    "Inne" to listOf("Miód", "Czekolada gorzka 70%", "Odżywka białkowa WPC")
)
