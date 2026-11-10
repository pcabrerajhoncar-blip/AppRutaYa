package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.model.TrustedContact
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.Session
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.AppTextField
import com.rutaya.app.ui.components.GuestGate
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.TabHeader
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.NoticeBg
import com.rutaya.app.ui.theme.NoticeText
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Pestaña "Seguridad": personas de confianza con quienes compartes tu viaje por WhatsApp. Requiere cuenta. */
@Composable
fun SecurityTab(onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val account = Session.account

    var contacts by remember { mutableStateOf<List<TrustedContact>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun reload() {
        if (!Session.isLoggedIn) return
        scope.launch {
            loading = true
            when (val r = Backend.contacts()) {
                is ApiResult.Success -> contacts = r.value
                is ApiResult.Error -> message = r.message
            }
            loading = false
        }
    }

    LaunchedEffect(account?.id) {
        contacts = emptyList()
        reload()
    }

    Column(Modifier.fillMaxSize().background(BgScreen)) {
        TabHeader("Viaje seguro", "Que alguien de confianza te acompañe a distancia")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (account == null) {
                GuestGate(
                    "🛡️", "Comparte tu viaje con quien quieras",
                    "Con una cuenta puedes guardar personas de confianza y enviarles por WhatsApp un enlace para que vean por dónde vas en tu viaje, en tiempo real.",
                    onLogin
                )
                return@Column
            }
            Column(Modifier.padding(16.dp)) {
                AppCard(Modifier.fillMaxWidth(), containerColor = NoticeBg) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Cómo funciona", color = NoticeText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "1. Agrega a tus personas de confianza aquí.\n" +
                                "2. Durante un viaje toca «Compartir mi viaje» y elige a quién enviar el enlace por WhatsApp.\n" +
                                "3. Esa persona abre el enlace y ve tu ubicación en un mapa, sin instalar nada.\n" +
                                "4. Cuando termina el viaje, el enlace deja de mostrar tu ubicación y se borra.",
                            color = NoticeText, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                Text(
                    "Personas de confianza", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
                )
                if (loading) Text("Cargando…", color = TextMuted, fontSize = 13.sp)
                if (!loading && contacts.isEmpty()) {
                    Text("Aún no agregaste a nadie.", color = TextMuted, fontSize = 13.sp)
                }
                for (c in contacts) {
                    AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(c.name, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                                Text("+51 ${c.phone.takeLast(9)}", color = TextSecondary, fontSize = 12.sp)
                            }
                            TextButton(onClick = {
                                scope.launch {
                                    when (val r = Backend.deleteContact(c.id)) {
                                        is ApiResult.Success -> reload()
                                        is ApiResult.Error -> message = r.message
                                    }
                                }
                            }) { Text("Quitar", color = AccentOrange) }
                        }
                    }
                }

                Text(
                    "Agregar persona", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 14.dp, bottom = 8.dp)
                )
                AppTextField(name, { name = it }, "Nombre (ej.: Mamá)", Modifier.padding(bottom = 8.dp))
                AppTextField(phone, { phone = it }, "Celular de WhatsApp (9 dígitos)", keyboardType = KeyboardType.Phone)
                MessageText(message)
                PrimaryButton(
                    if (saving) "Guardando…" else "Guardar persona", enabled = !saving, height = 48.dp,
                    modifier = Modifier.padding(top = 8.dp),
                    onClick = {
                        val digits = phone.filter { it.isDigit() }
                        when {
                            name.isBlank() -> message = "Escribe el nombre de tu contacto."
                            digits.length != 9 -> message = "El celular debe tener 9 dígitos."
                            else -> {
                                message = null
                                saving = true
                                scope.launch {
                                    when (val r = Backend.addContact(name.trim(), digits)) {
                                        is ApiResult.Success -> {
                                            name = ""
                                            phone = ""
                                            reload()
                                        }
                                        is ApiResult.Error -> message = r.message
                                    }
                                    saving = false
                                }
                            }
                        }
                    }
                )
                Text(
                    "Solo guardamos el nombre y el número para armar el mensaje de WhatsApp. Tú decides cuándo compartir y con quién; " +
                        "tu ubicación solo se comparte mientras dura el viaje.",
                    color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
                )
            }
        }
    }
}
