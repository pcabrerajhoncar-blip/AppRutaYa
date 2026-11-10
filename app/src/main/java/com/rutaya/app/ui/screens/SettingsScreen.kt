package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.network.ApiConfig
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.AppSettings
import com.rutaya.app.storage.Session
import com.rutaya.app.storage.TripHistoryManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.ChipRow
import com.rutaya.app.ui.components.ChoiceChip
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.SwitchRow
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 6.dp)
    )
}

/** Configuración: caminata, avisos de viaje, privacidad y datos. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onAccountDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var confirmClearLocal by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader("Configuración", "Ajusta RutaYa a tu gusto", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {

            SectionLabel("Apariencia")
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Tema de la aplicación", color = TextPrimary, fontSize = 14.sp)
                    Text(
                        "«Sistema» sigue el modo oscuro de tu celular.",
                        color = TextMuted, fontSize = 12.sp
                    )
                    ChipRow {
                        for ((mode, name) in listOf(0 to "Sistema", 1 to "Claro", 2 to "Oscuro")) {
                            ChoiceChip(
                                name,
                                selected = AppSettings.themeMode == mode,
                                onClick = { AppSettings.changeThemeMode(mode) }
                            )
                        }
                    }
                }
            }

            SectionLabel("Caminata")
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("Distancia máxima a pie hasta un paradero", color = TextPrimary, fontSize = 14.sp)
                    Text(
                        "Solo verás rutas con paraderos a esta distancia a pie (con vueltas de calles) de tu origen y de tu destino.",
                        color = TextMuted, fontSize = 12.sp
                    )
                    ChipRow {
                        for (m in listOf(300, 500, 800, 1000)) {
                            ChoiceChip(
                                if (m >= 1000) "${m / 1000.0} km".replace(".0", "") else "$m m",
                                selected = AppSettings.maxWalkM == m,
                                onClick = { AppSettings.changeMaxWalk(m) }
                            )
                        }
                    }
                }
            }

            SectionLabel("Avisos durante el viaje")
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    SwitchRow(
                        "Avisarme cuando deba bajar", "Muestra un aviso y una notificación cerca de tu paradero de bajada.",
                        AppSettings.alertEnabled
                    ) { AppSettings.changeAlert(it) }
                    if (AppSettings.alertEnabled) {
                        Text("¿Cuántas paradas antes?", color = TextPrimary, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
                        ChipRow {
                            for (n in 1..3) {
                                ChoiceChip(
                                    if (n == 1) "1 parada" else "$n paradas",
                                    selected = AppSettings.alertStopsBefore == n,
                                    onClick = { AppSettings.changeAlertStops(n) }
                                )
                            }
                        }
                        SwitchRow("Vibrar", "El celular vibra con el aviso.", AppSettings.vibrate) { AppSettings.changeVibrate(it) }
                    }
                }
            }

            SectionLabel("Privacidad y datos")
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        "Tu ubicación solo se usa para planificar y seguir tu viaje. Solo se comparte con otras personas si tú tocas «Compartir mi viaje», y se borra al terminar.",
                        color = TextMuted, fontSize = 12.sp
                    )
                    SecondaryButton(
                        "Borrar historial de este celular", onClick = { confirmClearLocal = true },
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                    )
                    if (Session.isLoggedIn) {
                        SecondaryButton(
                            "Eliminar mi cuenta y todos mis datos", onClick = { confirmDelete = true },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                    MessageText(error)
                }
            }

            SectionLabel("Acerca de esta versión")
            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text("RutaYa 1.0 · Proyecto del Taller de Desarrollo de Aplicaciones Móviles", color = TextPrimary, fontSize = 13.sp)
                    Text("Servidor: ${ApiConfig.SERVER}", color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    if (confirmClearLocal) {
        AlertDialog(
            onDismissRequest = { confirmClearLocal = false },
            title = { Text("¿Borrar el historial de este celular?") },
            text = { Text("Se eliminarán los viajes guardados aquí. Los de tu cuenta en la nube no cambian.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearLocal = false
                    TripHistoryManager.clear(context)
                    toast(context, "Historial local borrado")
                }) { Text("Borrar", color = AccentOrange) }
            },
            dismissButton = { TextButton(onClick = { confirmClearLocal = false }) { Text("Cancelar") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { if (!deleting) confirmDelete = false },
            title = { Text("¿Eliminar tu cuenta?") },
            text = {
                Text("Se borrarán para siempre tu cuenta, tu historial en la nube, favoritos, lugares y personas de confianza. No se puede deshacer.")
            },
            confirmButton = {
                TextButton(enabled = !deleting, onClick = {
                    deleting = true
                    scope.launch {
                        when (val r = Backend.deleteAccount()) {
                            is ApiResult.Success -> {
                                confirmDelete = false
                                toast(context, "Tu cuenta fue eliminada.")
                                onAccountDeleted()
                            }
                            is ApiResult.Error -> { error = r.message; confirmDelete = false }
                        }
                        deleting = false
                    }
                }) { Text(if (deleting) "Eliminando…" else "Eliminar todo", color = AccentOrange) }
            },
            dismissButton = { TextButton(enabled = !deleting, onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}
