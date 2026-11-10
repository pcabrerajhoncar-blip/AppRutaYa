package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.model.GeoPt
import com.rutaya.app.model.SavedPlace
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.network.Place
import com.rutaya.app.network.PlaceSearch
import com.rutaya.app.storage.Session
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.AppTextField
import com.rutaya.app.ui.components.GuestGate
import com.rutaya.app.ui.components.MapPin
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.OsmMap
import com.rutaya.app.ui.components.PinKind
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Mis lugares: casa, trabajo y otros sitios guardados. Requiere cuenta. */
@Composable
fun PlacesScreen(center: GeoPt?, onBack: () -> Unit, onLogin: () -> Unit) {
    val scope = rememberCoroutineScope()
    val account = Session.account

    var places by remember { mutableStateOf<List<SavedPlace>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var editingType by rememberSaveable { mutableStateOf<String?>(null) }

    fun reload() {
        if (!Session.isLoggedIn) return
        scope.launch {
            loading = true
            when (val r = Backend.places()) {
                is ApiResult.Success -> places = r.value
                is ApiResult.Error -> message = r.message
            }
            loading = false
        }
    }

    LaunchedEffect(account?.id) { reload() }

    val type = editingType
    if (type != null) {
        PlaceEditor(
            type = type, center = center,
            onCancel = { editingType = null },
            onSaved = { editingType = null; reload() }
        )
        return
    }

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader("Mis lugares", "Llega más rápido a donde siempre vas", onBack = onBack)
        if (account == null) {
            GuestGate("📍", "Guarda tus lugares", "Con una cuenta puedes guardar Casa, Trabajo y otros sitios para elegirlos con un toque como destino.", onLogin)
            return@Column
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            MessageText(message)
            if (loading) Text("Cargando…", color = TextMuted, fontSize = 13.sp)

            for ((t, label, emoji) in listOf(Triple("casa", "Casa", "🏠"), Triple("trabajo", "Trabajo", "💼"))) {
                val saved = places.firstOrNull { it.type == t }
                AppCard(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$emoji $label", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(saved?.address?.ifBlank { null } ?: if (saved != null) "Guardado" else "Sin definir", color = TextSecondary, fontSize = 12.sp)
                        }
                        TextButton(onClick = { editingType = t }) { Text(if (saved == null) "Definir" else "Cambiar", color = AccentBlue) }
                        if (saved != null) {
                            TextButton(onClick = {
                                scope.launch {
                                    when (val r = Backend.deletePlace(saved.id)) {
                                        is ApiResult.Success -> reload()
                                        is ApiResult.Error -> message = r.message
                                    }
                                }
                            }) { Text("Quitar", color = AccentOrange) }
                        }
                    }
                }
            }

            Text("Otros lugares", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
            val others = places.filter { it.type == "otro" }
            if (others.isEmpty() && !loading) Text("Aún no guardaste otros lugares.", color = TextMuted, fontSize = 13.sp)
            for (p in others) {
                AppCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("📌 ${p.name}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            if (p.address.isNotBlank()) Text(p.address, color = TextSecondary, fontSize = 12.sp, maxLines = 1)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                when (val r = Backend.deletePlace(p.id)) {
                                    is ApiResult.Success -> reload()
                                    is ApiResult.Error -> message = r.message
                                }
                            }
                        }) { Text("Quitar", color = AccentOrange) }
                    }
                }
            }
            SecondaryButton("＋ Agregar otro lugar", onClick = { editingType = "otro" }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
        }
    }
}

/** Editor: buscar el lugar por nombre o tocarlo en el mapa, y guardarlo. */
@Composable
private fun PlaceEditor(type: String, center: GeoPt?, onCancel: () -> Unit, onSaved: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by rememberSaveable { mutableStateOf(if (type == "casa") "Casa" else if (type == "trabajo") "Trabajo" else "") }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Place>>(emptyList()) }
    var picked by remember { mutableStateOf<GeoPt?>(null) }
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pins = ArrayList<MapPin>()
    picked?.let { pins.add(MapPin(it, PinKind.DESTINATION, name.ifBlank { "Lugar" })) }

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            when (type) { "casa" -> "Definir Casa"; "trabajo" -> "Definir Trabajo"; else -> "Nuevo lugar" },
            "Búscalo por nombre o toca el mapa", onBack = onCancel
        )
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (type == "otro") AppTextField(name, { name = it }, "Nombre del lugar", Modifier.padding(bottom = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { AppTextField(query, { query = it }, "Buscar dirección o lugar") }
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        when (val r = PlaceSearch.search(query, center)) {
                            is ApiResult.Success -> {
                                results = r.value.take(3)
                                if (r.value.isEmpty()) message = "No encontré ese lugar. Tócalo en el mapa."
                            }
                            is ApiResult.Error -> message = r.message
                        }
                        busy = false
                    }
                }) { Text(if (busy) "…" else "Buscar", color = AccentBlue) }
            }
            for (r in results) {
                Text(
                    r.name, color = TextPrimary, fontSize = 13.sp, maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                        .clickable { picked = r.point; address = r.name.substringBefore(",,"); results = emptyList() }
                        .padding(vertical = 8.dp)
                )
            }
            MessageText(message)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lines = emptyList(), pins = pins,
                fitPoints = picked?.let { listOf(it) } ?: center?.let { listOf(it) },
                fitKey = picked ?: "editor",
                onTap = { picked = it; if (address.isBlank()) address = "Punto elegido en el mapa" }
            )
        }
        Column(Modifier.fillMaxWidth().background(com.rutaya.app.ui.theme.CardSurface).padding(12.dp)) {
            Text(
                if (picked == null) "Aún no elegiste el lugar." else "Lugar elegido ✓ ${address.take(60)}",
                color = TextSecondary, fontSize = 13.sp
            )
            PrimaryButton(
                if (busy) "Guardando…" else "Guardar lugar", enabled = picked != null && name.isNotBlank() && !busy, height = 48.dp,
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    val point = picked ?: return@PrimaryButton
                    scope.launch {
                        busy = true
                        when (val r = Backend.savePlace(type, name.trim(), address, point)) {
                            is ApiResult.Success -> onSaved()
                            is ApiResult.Error -> message = r.message
                        }
                        busy = false
                    }
                }
            )
        }
    }
}
