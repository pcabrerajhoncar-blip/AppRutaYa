package com.rutaya.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val RutaYaColors = lightColorScheme(
    primary = PrimaryNavy,
    onPrimary = TextWhite,
    secondary = AccentCyan,
    tertiary = AccentBlue,
    background = BgScreen,
    onBackground = TextPrimary,
    surface = CardSurface,
    onSurface = TextPrimary,
    surfaceVariant = InputSurface,
    onSurfaceVariant = TextSecondary,
    outline = DividerColor,
    error = AccentOrange
)

/** Tema fijo claro con los colores de marca de RutaYa (sin colores dinámicos). */
@Composable
fun RutaYaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RutaYaColors,
        typography = Typography,
        content = content
    )
}
