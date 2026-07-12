package pl.filebit.dietetyk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pl.filebit.dietetyk.ChatScreen
import pl.filebit.dietetyk.DietetykApp

private enum class Tab(val label: String, val icon: ImageVector) {
    DZIS("Dziś", Icons.Filled.WbSunny),
    PLAN("Plan", Icons.Filled.RestaurantMenu),
    DIETETYK("Dietetyk", Icons.Filled.Spa),
    POSTEPY("Postępy", Icons.Filled.ShowChart),
    PROFIL("Profil", Icons.Filled.Person)
}

@Composable
fun AppScaffold(app: DietetykApp) {
    var onboarded by remember { mutableStateOf(app.settings.onboardingDone) }
    if (!onboarded) {
        OnboardingScreen { app.settings.onboardingDone = true; onboarded = true }
        return
    }
    // Start: onboardowany (ma profil) → Dziś (konkret); pierwszy raz (brak profilu) → Dietetyk (wywiad).
    var tab by remember { mutableStateOf<Tab?>(null) }
    LaunchedEffect(Unit) { if (tab == null) tab = if (app.profileRepo.get() != null) Tab.DZIS else Tab.DIETETYK }
    var showNotifs by remember { mutableStateOf(false) }
    var showProducts by remember { mutableStateOf(false) }
    var showBackup by remember { mutableStateOf(false) }
    var showTastePicker by remember { mutableStateOf(false) }
    val overlay = showNotifs || showProducts || showBackup || showTastePicker

    // Aktualizacja: sprawdź raz na starcie → bottom sheet (6.2), jedno miejsce zamiast banera na Dziś.
    var updateInfo by remember { mutableStateOf<pl.filebit.dietetyk.update.UpdateInfo?>(null) }
    var updateDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { updateInfo = pl.filebit.dietetyk.update.UpdateChecker.latest(pl.filebit.dietetyk.BuildConfig.VERSION_NAME) }
    updateInfo?.let { u -> if (!updateDismissed) UpdateSheet(u) { updateDismissed = true } }
    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = { if (!overlay && tab != null) BottomBar(tab!!) { tab = it } }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize().background(Palette.Bg)) {
            when {
                showNotifs -> NotificationsScreen(app) { showNotifs = false }
                showProducts -> ProductsScreen(app) { showProducts = false }
                showBackup -> BackupScreen(app) { showBackup = false }
                showTastePicker -> OnboardingTastePicker(app) { showTastePicker = false }
                else -> when (tab) {
                    Tab.DZIS -> TodayScreen(app, onBell = { showNotifs = true }, onGoToChat = { tab = Tab.DIETETYK }, onBrowseProducts = { showProducts = true })
                    Tab.PLAN -> PlanScreen(app, onGoToChat = { tab = Tab.DIETETYK })
                    Tab.DIETETYK -> ChatScreen(app, Modifier.fillMaxSize(), onBrowseProducts = { showProducts = true }, onOpenTastePicker = { showTastePicker = true })
                    Tab.POSTEPY -> ProgressScreen(app, onGoToChat = { tab = Tab.DIETETYK })
                    Tab.PROFIL -> ProfileScreen(app, onBrowseProducts = { showProducts = true }, onOpenBackup = { showBackup = true })
                    null -> {}
                }
            }
        }
    }
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Palette.Card).navigationBarsPadding().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Tab.entries.forEach { t ->
            val selected = t == current
            val center = t == Tab.DIETETYK
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onSelect(t) }.padding(horizontal = 4.dp)
                    .then(if (center) Modifier.offset(y = (-20).dp) else Modifier)
            ) {
                if (center) {
                    Box(
                        Modifier.size(58.dp)
                            .background(Palette.Bg, CircleShape).padding(4.dp)
                            .shadow(10.dp, CircleShape)
                            .background(Palette.Green, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(t.icon, t.label, tint = Color.White, modifier = Modifier.size(28.dp)) }
                } else {
                    Icon(t.icon, t.label, tint = if (selected) Palette.Green else Palette.Muted, modifier = Modifier.size(24.dp))
                }
                Text(
                    t.label, fontSize = 11.sp,
                    color = if (selected) Palette.Green else Palette.Muted,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Palette.TextDark, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Palette.Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
