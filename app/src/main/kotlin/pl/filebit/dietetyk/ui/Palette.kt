package pl.filebit.dietetyk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Kolory aplikacji — zestaw dla trybu jasnego i ciemnego (z Claude Design). */
data class AppColors(
    val Bg: Color,
    val Card: Color,
    val GreenTint: Color,
    val Green: Color,
    val GreenDark: Color,
    val Orange: Color,
    val Blue: Color,
    val TextDark: Color,
    val Muted: Color,
    val Line: Color
)

val LightColors = AppColors(
    Bg = Color(0xFFFAF7F2),
    Card = Color(0xFFFFFFFF),
    GreenTint = Color(0xFFEAF3EC),
    Green = Color(0xFF3E7C5B),
    GreenDark = Color(0xFF2F5F46),
    Orange = Color(0xFFE08A54),
    Blue = Color(0xFF5B93C4),
    TextDark = Color(0xFF23261F),
    Muted = Color(0xFF7A857D),
    Line = Color(0xFFC8C9D3)
)

val DarkColors = AppColors(
    Bg = Color(0xFF12150F),
    Card = Color(0xFF1B211A),
    GreenTint = Color(0xFF16301F),
    Green = Color(0xFF5FA57D),
    GreenDark = Color(0xFF8FC0A3),
    Orange = Color(0xFFE0975A),
    Blue = Color(0xFF6BA3D4),
    TextDark = Color(0xFFEDEDE6),
    Muted = Color(0xFF8B958C),
    Line = Color(0xFF32403A)
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Bieżąca paleta (jasna/ciemna). Używaj w @Composable jak dotąd: `Palette.Green`. */
val Palette: AppColors
    @Composable
    get() = LocalAppColors.current

/** Motyw aplikacji — "system" (wg telefonu), "light" lub "dark". */
@Composable
fun DietetykTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    CompositionLocalProvider(LocalAppColors provides if (dark) DarkColors else LightColors) { content() }
}
