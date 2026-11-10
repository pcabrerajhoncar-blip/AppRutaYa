package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.TripHistoryManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.AppTextField
import com.rutaya.app.ui.components.ChoiceChip
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

/** Crear cuenta o iniciar sesión (solo correo y contraseña). La cuenta es opcional. */
@Composable
fun AuthScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var registering by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit() {
        val mail = email.trim()
        error = when {
            registering && name.isBlank() -> "Escribe tu nombre."
            !EMAIL_REGEX.matches(mail) -> "Escribe un correo válido."
            registering && password.length < 6 -> "La contraseña debe tener al menos 6 caracteres."
            password.isEmpty() -> "Escribe tu contraseña."
            else -> null
        }
        if (error != null) return
        loading = true
        scope.launch {
            val result = if (registering) Backend.register(name.trim(), mail, password) else Backend.login(mail, password)
            loading = false
            when (result) {
                is ApiResult.Success -> {
                    // Los viajes hechos como invitado se suben a tu cuenta (si falla alguno, se ignora).
                    for (trip in TripHistoryManager.getTripHistory(context).take(50)) Backend.saveTrip(trip, null)
                    toast(context, "¡Hola, ${result.value.name}!")
                    onDone()
                }
                is ApiResult.Error -> error = result.message
            }
        }
    }

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            if (registering) "Crear cuenta" else "Iniciar sesión",
            "Es opcional: sirve para favoritos, personas de confianza y guardar tu historial.",
            onBack = onBack
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row {
                ChoiceChip("Ya tengo cuenta", selected = !registering, onClick = { registering = false; error = null })
                Row(Modifier.padding(start = 8.dp)) {
                    ChoiceChip("Crear cuenta", selected = registering, onClick = { registering = true; error = null })
                }
            }
            AppCard(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                Column(Modifier.padding(16.dp)) {
                    if (registering) {
                        AppTextField(name, { name = it }, "Nombre", Modifier.padding(bottom = 10.dp))
                    }
                    AppTextField(email, { email = it }, "Correo electrónico", Modifier.padding(bottom = 10.dp), keyboardType = KeyboardType.Email)
                    AppTextField(password, { password = it }, "Contraseña", password = true)
                    MessageText(error)
                    PrimaryButton(
                        if (loading) "Un momento…" else if (registering) "Crear mi cuenta" else "Entrar",
                        enabled = !loading, onClick = { submit() }, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            Text(
                "Solo pedimos tu correo y una contraseña. Tu contraseña se guarda cifrada y nunca podemos verla. " +
                    "Puedes eliminar tu cuenta y todos tus datos cuando quieras desde Perfil → Configuración.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp)
            )
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Seguir sin cuenta", color = AccentBlue, fontSize = 14.sp)
            }
            Text(
                "Recuperar contraseña por correo estará disponible próximamente.",
                color = TextSecondary, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
