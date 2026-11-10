package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.ui.components.HeaderBrush
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.theme.AccentCyan

private class Page(val emoji: String, val title: String, val text: String)

private val PAGES = listOf(
    Page("🚌", "Encuentra tu combi", "Dinos a dónde vas y RutaYa te muestra qué ruta tomar, dónde subir y dónde bajar."),
    Page("📍", "Te guiamos paso a paso", "Caminas al paradero, subes, y ves en el mapa cómo avanzas. Te avisamos cuándo bajar."),
    Page("🛡️", "Viaja con tranquilidad", "Si quieres, crea una cuenta gratis para guardar favoritos y compartir tu viaje por WhatsApp con tus personas de confianza. Es opcional.")
)

/** Bienvenida que se muestra solo la primera vez. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val page = PAGES[index]
    val last = index == PAGES.lastIndex

    Column(Modifier.fillMaxSize().background(HeaderBrush).systemBarsPadding().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onFinish) { Text("Omitir", color = Color.White) }
        }
        Column(
            Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(page.emoji, fontSize = 88.sp)
            Text(
                page.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp)
            )
            Text(
                page.text, color = Color(0xFFE2E8F0), fontSize = 16.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
            for (i in PAGES.indices) {
                Box(
                    Modifier.padding(4.dp).size(if (i == index) 12.dp else 8.dp)
                        .background(if (i == index) AccentCyan else Color(0x66FFFFFF), CircleShape)
                )
            }
        }
        PrimaryButton(
            if (last) "Empezar" else "Siguiente",
            onClick = { if (last) onFinish() else index += 1 },
            containerColor = AccentCyan
        )
    }
}
