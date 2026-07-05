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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    DZIS("Dziś", Icons.Filled.Today),
    PLAN("Plan", Icons.Filled.RestaurantMenu),
    DIETETYK("Dietetyk", Icons.Filled.Spa),
    POSTEPY("Postępy", Icons.Filled.ShowChart),
    PROFIL("Profil", Icons.Filled.Person)
}

@Composable
fun AppScaffold(app: DietetykApp) {
    var tab by remember { mutableStateOf(Tab.DIETETYK) }
    Scaffold(
        containerColor = Palette.Bg,
        bottomBar = { BottomBar(tab) { tab = it } }
    ) { inner ->
        Box(Modifier.padding(inner).fillMaxSize().background(Palette.Bg)) {
            when (tab) {
                Tab.DZIS -> TodayScreen(app)
                Tab.PLAN -> PlanScreen(app)
                Tab.DIETETYK -> ChatScreen(app, Modifier.fillMaxSize())
                Tab.POSTEPY -> ProgressScreen(app)
                Tab.PROFIL -> ProfileScreen(app)
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
            ) {
                if (center) {
                    Box(
                        Modifier.size(44.dp).background(Palette.Green, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(t.icon, t.label, tint = Color.White, modifier = Modifier.size(24.dp)) }
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
