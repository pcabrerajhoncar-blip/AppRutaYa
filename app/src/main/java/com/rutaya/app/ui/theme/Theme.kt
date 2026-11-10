package com.rutaya.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect

/** Tema de RutaYa: claro u oscuro según Configuración > Apariencia (o el celular). */
@Composable
fun RutaYaTheme(content: @Composable () -> Unit) {
    val system = isSystemInDarkTheme()
    SideEffect { Pal.systemDark = system }
    val dark = isDark

    val scheme = if (dark) {
        darkColorScheme(
            primary = AccentBlue,
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
    } else {
        lightColorScheme(
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
    }
    MaterialTheme(colorScheme = scheme, typography = Typography, content = content)
}
