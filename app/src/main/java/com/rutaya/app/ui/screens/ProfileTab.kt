package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import com.rutaya.app.ui.theme.AccentText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.model.TripSummary
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.Session
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.AppTextField
import com.rutaya.app.ui.components.MenuRow
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.TabHeader
import com.rutaya.app.ui.components.formatCost
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Pestaña "Perfil": cuenta (o invitado), estadísticas, lugares, configuración y ayuda. */
@Composable
fun ProfileTab(
    onLogin: () -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenInfo: (kind: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = Session.account

    var summary by remember { mutableStateOf<TripSummary?>(null) }
    var editName by remember { mutableStateOf(false) }
    var editPassword by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(account?.id) {
        summary = null
        if (Session.isLoggedIn) {
            val r = Backend.summary()
            if (r is ApiResult.Success) summary = r.value
        }
    }

    Column(Modifier.fillMaxSize().background(BgScreen)) {
        TabHeader("Perfil")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            AppCard(Modifier.fillMaxWidth().padding(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(PrimaryNavy),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                account?.name?.take(1)?.uppercase() ?: "👤",
                                color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(account?.name ?: "Invitado", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(account?.email ?: "Usando RutaYa sin cuenta", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                    if (account == null) {
                        Text(
                            "Crea una cuenta gratis para tener favoritos, personas de confianza, lugares guardados e historial en la nube.",
                            color = TextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)
                        )
                        PrimaryButton("Iniciar sesión o crear cuenta", onClick = onLogin, height = 48.dp, modifier = Modifier.padding(top = 12.dp))
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(top = 14.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStat((summary?.trips ?: 0).toString(), "viajes")
                            ProfileStat((summary?.minutes ?: 0).toString(), "minutos")
                            ProfileStat(formatCost(summary?.spent ?: 0.0), "gastado")
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Column {
                    if (account != null) {
                        MenuRow("📍", "Mis lugares", "Casa, trabajo y otros", onOpenPlaces)
                        HorizontalDivider(color = DividerColor)
                        MenuRow("✏️", "Cambiar mi nombre", null) { editName = true }
                        HorizontalDivider(color = DividerColor)
                        MenuRow("🔑", "Cambiar mi contraseña", null) { editPassword = true }
                        HorizontalDivider(color = DividerColor)
                    }
                    MenuRow("⚙️", "Configuración", "Avisos, distancias y privacidad", onOpenSettings)
                    HorizontalDivider(color = DividerColor)
                    MenuRow("❓", "Ayuda", "Preguntas frecuentes") { onOpenInfo("ayuda") }
                    HorizontalDivider(color = DividerColor)
                    MenuRow("🔒", "Privacidad", "Cómo cuidamos tus datos") { onOpenInfo("privacidad") }
                    HorizontalDivider(color = DividerColor)
                    MenuRow("ℹ️", "Acerca de RutaYa", null) { onOpenInfo("acerca") }
                }
            }

            if (account != null) {
                SecondaryButton(
                    "Cerrar sesión", onClick = { confirmLogout = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
        }
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("¿Cerrar sesión?") },
            text = { Text("Tus datos siguen guardados en tu cuenta. Podrás volver a entrar cuando quieras.") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; Session.logout() }) { Text("Cerrar sesión", color = AccentOrange) }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Cancelar") } }
        )
    }

    if (editName) {
        var newName by remember { mutableStateOf(account?.name ?: "") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editName = false },
            title = { Text("Cambiar mi nombre") },
            text = {
                Column {
                    AppTextField(newName, { newName = it }, "Nombre")
                    MessageText(error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isBlank()) {
                        error = "Escribe un nombre."
                    } else {
                        scope.launch {
                            when (val r = Backend.updateName(newName.trim())) {
                                is ApiResult.Success -> { editName = false; toast(context, "Nombre actualizado") }
                                is ApiResult.Error -> error = r.message
                            }
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editName = false }) { Text("Cancelar") } }
        )
    }

    if (editPassword) {
        var current by remember { mutableStateOf("") }
        var newPass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { editPassword = false },
            title = { Text("Cambiar contraseña") },
            text = {
                Column {
                    AppTextField(current, { current = it }, "Contraseña actual", password = true)
                    AppTextField(newPass, { newPass = it }, "Nueva contraseña (mín. 6)", Modifier.padding(top = 8.dp), password = true)
                    MessageText(error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPass.length < 6) {
                        error = "La nueva contraseña debe tener al menos 6 caracteres."
                    } else {
                        scope.launch {
                            when (val r = Backend.changePassword(current, newPass)) {
                                is ApiResult.Success -> { editPassword = false; toast(context, "Contraseña actualizada") }
                                is ApiResult.Error -> error = r.message
                            }
                        }
                    }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editPassword = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun ProfileStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AccentText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}
