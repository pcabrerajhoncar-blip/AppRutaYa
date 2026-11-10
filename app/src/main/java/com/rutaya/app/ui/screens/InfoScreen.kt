package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary

private class Section(val title: String, val body: String)

private fun sectionsFor(kind: String): Pair<String, List<Section>> = when (kind) {
    "ayuda" -> "Ayuda" to listOf(
        Section("¿Cómo busco una ruta?", "En Inicio, tu ubicación se detecta sola (puedes ajustarla en el mapa). Escribe tu destino o elígelo en el mapa y toca «Buscar rutas»."),
        Section("¿Qué significan las fases del viaje?", "Primero caminas al paradero, esperas la combi, tocas «Iniciar viaje» cuando subes, y la app te muestra el avance hasta que debes bajar y caminar a tu destino."),
        Section("¿Necesito una cuenta?", "No. Puedes buscar rutas y viajar sin cuenta. La cuenta solo es necesaria para favoritos, lugares guardados, personas de confianza e historial en la nube."),
        Section("¿Cómo comparto mi viaje?", "Crea una cuenta, agrega a tus personas de confianza en la pestaña Seguridad y, durante un viaje, toca «Compartir mi viaje». Se abrirá WhatsApp con un enlace donde ven tu ubicación en un mapa."),
        Section("¿Qué hace el botón SOS?", "Marca tu viaje compartido con una alerta de ayuda que tus contactos ven en el enlace. No reemplaza a la Policía (105) ni al Serenazgo."),
        Section("No me aparece ninguna ruta", "Puede que no haya rutas con paraderos cerca de tu origen y destino. Prueba aumentar la distancia a pie en Configuración, o elige otro punto."),
        Section("Modo simulación", "El interruptor «Simular» durante el viaje mueve un punto por la ruta para que pruebes la app sin salir de casa.")
    )
    "privacidad" -> "Privacidad" to listOf(
        Section("Qué datos usamos", "Tu ubicación mientras usas la app para planificar y seguir tu viaje. Si creas una cuenta: tu nombre, correo, historial de viajes, favoritos, lugares y los nombres y teléfonos de tus personas de confianza."),
        Section("Para qué los usamos", "Solo para que la app funcione: mostrarte rutas, guardar tu historial y permitir que compartas tu viaje. No vendemos ni cedemos tus datos."),
        Section("Ubicación compartida", "Se comparte únicamente si tú lo decides, mediante un enlace privado que envías a quien quieras. Cuando el viaje termina o dejas de compartir, la ubicación guardada se borra."),
        Section("Tu contraseña", "Se guarda cifrada (con un algoritmo de un solo sentido): ni el equipo puede verla."),
        Section("Tus derechos", "Puedes ver, corregir y eliminar tus datos cuando quieras. En Perfil → Configuración puedes eliminar tu cuenta y todos tus datos. Aplicamos los principios de la Ley N.º 29733 (Protección de Datos Personales del Perú): consentimiento, finalidad y minimización."),
        Section("Contacto", "Proyecto académico del Taller de Desarrollo de Aplicaciones Móviles. Para consultas sobre tus datos, escribe a tu docente o al equipo del proyecto.")
    )
    else -> "Acerca de RutaYa" to listOf(
        Section("RutaYa", "Aplicación de transporte público para Chiclayo: te guía desde donde estás hasta tu destino usando las rutas circulares de combis y sus paraderos."),
        Section("Cómo funciona", "Calcula en tu celular dónde subir y dónde bajar a partir de las rutas del servidor, te acompaña en el mapa y te avisa cuándo bajar."),
        Section("Datos de prueba", "Las rutas y paraderos son datos de ejemplo creados para este proyecto académico; no son información oficial."),
        Section("Versión", "1.0 · Hecho con Kotlin, Jetpack Compose, Spring Boot y MySQL/PostgreSQL.")
    )
}

/** Pantalla de texto: ayuda, privacidad o acerca de. */
@Composable
fun InfoScreen(kind: String, onBack: () -> Unit) {
    val (title, sections) = sectionsFor(kind)
    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(title, onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            for (s in sections) {
                AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(s.title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(s.body, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
}
