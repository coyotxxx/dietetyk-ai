package pl.filebit.dietetyk.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    val Line: Color,
    val Error: Color,
    val isDark: Boolean = false
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
    Line = Color(0xFFC8C9D3),
    Error = Color(0xFFDC2626)
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
    Line = Color(0xFF32403A),
    Error = Color(0xFFF85149),
    isDark = true
)

val LocalAppColors = staticCompositionLocalOf { LightColors }

/** Wygląd karty: delikatny cień w trybie jasnym, obwódka w ciemnym (cienie tam niewidoczne). */
@Composable
fun Modifier.card(radius: Dp = 18.dp): Modifier {
    val c = LocalAppColors.current
    val shape = RoundedCornerShape(radius)
    // clip NA KOŃCU (po shadow/border) — przycina tło i ripple do kształtu, cień przeżywa (rysowany przed clip).
    return if (c.isDark)
        this.border(1.dp, c.Line, shape).clip(shape)
    else
        this.shadow(5.dp, shape, spotColor = Color(0x33232620), ambientColor = Color(0x22232620)).clip(shape)
}

/** Bieżąca paleta (jasna/ciemna). Używaj w @Composable jak dotąd: `Palette.Green`. */
val Palette: AppColors
    @Composable
    get() = LocalAppColors.current

/** Buduje Material3 colorScheme z naszej palety — żeby komponenty Material (pola, przyciski,
 *  kursor, checkbox, menu) miały zielony akcent zamiast domyślnego fioletu, w obu motywach. */
private fun AppColors.toM3Scheme(dark: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (dark) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme()
    return base.copy(
        primary = Green, onPrimary = Color.White,
        primaryContainer = GreenTint, onPrimaryContainer = GreenDark,
        secondary = GreenDark, onSecondary = Color.White,
        secondaryContainer = GreenTint, onSecondaryContainer = GreenDark,
        tertiary = Blue, onTertiary = Color.White,
        background = Bg, onBackground = TextDark,
        surface = Card, onSurface = TextDark,
        surfaceVariant = Bg, onSurfaceVariant = Muted,
        surfaceContainer = Card, surfaceContainerHigh = Card, surfaceContainerHighest = Card,
        surfaceContainerLow = Card, surfaceContainerLowest = Bg,
        outline = Line, outlineVariant = Line,
        error = Error, onError = Color.White
    )
}

/** Motyw aplikacji — "system" (wg telefonu), "light" lub "dark". */
@Composable
fun DietetykTheme(themeMode: String = "system", content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalAppColors provides colors) {
        androidx.compose.material3.MaterialTheme(colorScheme = colors.toM3Scheme(dark)) { content() }
    }
}
