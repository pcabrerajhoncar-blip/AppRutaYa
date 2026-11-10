package com.rutaya.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.rutaya.app.storage.AppSettings

/** Estado del tema: [systemDark] lo actualiza RutaYaTheme según el modo oscuro del celular. */
object Pal {
    var systemDark by mutableStateOf(false)
}

/** true si la app debe verse en modo oscuro (según Configuración > Apariencia). */
val isDark: Boolean
    get() = when (AppSettings.themeMode) {
        1 -> false
        2 -> true
        else -> Pal.systemDark
    }

private fun pick(light: Long, dark: Long) = Color(if (isDark) dark else light)

// ---- Colores de marca (iguales en claro y oscuro)
val PrimaryNavy = Color(0xFF0F2A4A)
val PrimaryNavyDark = Color(0xFF0A1625)
val PrimaryNavyLight = Color(0xFF173A63)
val HeaderEnd = Color(0xFF1E4A78)
val BrandOrange = Color(0xFFF47B20)
val BrandOrangeLight = Color(0xFFFF9A4D)
val AccentCyan = Color(0xFF00B4D8)
val AccentBlue = Color(0xFF1E88E5)
val AccentOrange = Color(0xFFF26A1B)
val AccentGreen = Color(0xFF10B981)
val TextWhite = Color(0xFFFFFFFF)
val TextWhiteSecondary = Color(0xFFE2E8F0)
val CircleButton = Color(0xFF2A3F54)

/** Degradado naranja de los botones principales. */
val BrandBrush: Brush = Brush.horizontalGradient(listOf(BrandOrangeLight, BrandOrange))

// ---- Colores que cambian con el modo oscuro
val AccentGreenLight: Color get() = pick(0xFFE3F6EE, 0xFF11382D)
val BgScreen: Color get() = pick(0xFFF3F7FC, 0xFF0A1625)
val CardSurface: Color get() = pick(0xFFFFFFFF, 0xFF112136)
val InputSurface: Color get() = pick(0xFFF6F9FD, 0xFF0D1B2E)
val DividerColor: Color get() = pick(0xFFDCE5F0, 0xFF2A4058)
val TextPrimary: Color get() = pick(0xFF0F2A4A, 0xFFF1F6FC)
val TextSecondary: Color get() = pick(0xFF3B5472, 0xFFC3D2E4)
val TextMuted: Color get() = pick(0xFF6B7F99, 0xFF8FA3BC)
val NoticeBg: Color get() = pick(0xFFFDEBDD, 0xFF3A2616)
val NoticeStroke: Color get() = pick(0xFFF9C9A3, 0xFF8A5222)
val NoticeText: Color get() = pick(0xFF9A4A10, 0xFFFFC08A)
val BusBadgeBg: Color get() = pick(0xFFE3EFFC, 0xFF12304F)
val HistoryBg: Color get() = pick(0xFFE8EEF5, 0xFF1D2E43)
val DangerBg: Color get() = pick(0xFFFEE2E2, 0xFF4A1D1D)

/** Tintes suaves de las tarjetas (naranja, azul, gris). */
val OrangeTint: Color get() = pick(0xFFFDEBDD, 0xFF2E2015)
val BlueTint: Color get() = pick(0xFFE3EFFC, 0xFF12304F)
val GrayTint: Color get() = pick(0xFFEAEFF5, 0xFF1A2B40)

/** Color para textos, bordes y marcas azules (en oscuro, celeste legible). */
val AccentText: Color get() = pick(0xFF1E6FC0, 0xFF5CB8F8)

/** Fondo de botones principales sólidos: naranja de la marca. */
val ButtonBg: Color get() = BrandOrange
