package com.rutaya.app.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.R
import com.rutaya.app.location.Gps
import com.rutaya.app.model.GeoPt
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.LocalNetworkAccess
import com.rutaya.app.network.PlaceSearch
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentGreen
import com.rutaya.app.ui.theme.InputSurface
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Tarjeta de la pantalla de inicio: origen (tu ubicación), destino y botón de buscar rutas. */
@Composable
fun PlannerCard(state: PlanState, onPickOnMap: (forOrigin: Boolean) -> Unit, onPlansReady: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    fun detectLocation() {
        state.message = "Buscando tu ubicación…"
        scope.launch {
            val location = Gps.currentLocation(context)
            if (location == null) {
                state.message = "No se pudo obtener tu ubicación. Activa el GPS e inténtalo de nuevo."
            } else {
                state.message = null
                state.chooseOrigin(GeoPt(location.latitude, location.longitude), "Mi ubicación actual")
            }
        }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (Gps.hasPermission(context)) detectLocation()
        else state.message = context.getString(R.string.location_status_permission_denied)
    }
    val networkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) state.error = context.getString(R.string.local_network_permission_required)
    }

    fun requestLocation() {
        if (Gps.hasPermission(context)) detectLocation()
        else locationLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
        )
    }

    LaunchedEffect(Unit) {
        if (!LocalNetworkAccess.isGranted(context)) networkLauncher.launch(LocalNetworkAccess.PERMISSION)
        if (state.origin == null && Gps.hasPermission(context)) detectLocation()
    }

    fun searchPlaces() {
        scope.launch {
            state.searchingPlaces = true
            state.placesMessage = null
            when (val r = PlaceSearch.search(state.query, state.origin)) {
                is ApiResult.Success -> {
                    state.places = r.value
                    if (r.value.isEmpty()) state.placesMessage = "No encontré ese lugar. Prueba otro nombre o elígelo en el mapa."
                }
                is ApiResult.Error -> state.placesMessage = r.message
            }
            state.searchingPlaces = false
        }
    }

    AppCard(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
        shape = RoundedCornerShape(28.dp), elevation = 6.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                // ---- Indicador vertical: punto verde, línea punteada, pin rojo
                Column(Modifier.width(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.size(14.dp).background(com.rutaya.app.ui.theme.BrandOrange, CircleShape))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(2.dp).height(42.dp).background(DividerColor))
                    Spacer(Modifier.height(4.dp))
                    Icon(painterResource(R.drawable.ic_location_pin), null, Modifier.size(20.dp), tint = AccentBlue)
                }

                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    // ---- Origen
                    Row(
                        Modifier.fillMaxWidth().height(56.dp)
                            .background(InputSurface, RoundedCornerShape(20.dp))
                            .border(1.dp, DividerColor, RoundedCornerShape(20.dp))
                            .padding(start = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (state.origin == null) "Detectando tu ubicación…" else state.originLabel,
                            color = if (state.origin == null) TextMuted else TextPrimary,
                            fontSize = 14.sp, maxLines = 1, modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { requestLocation() }) {
                            Icon(painterResource(R.drawable.ic_my_location), "Usar mi ubicación", Modifier.size(22.dp), tint = AccentBlue)
                        }
                        IconButton(onClick = { onPickOnMap(true) }) {
                            Icon(painterResource(R.drawable.ic_place), "Ajustar origen en el mapa", Modifier.size(22.dp), tint = AccentBlue)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ---- Destino
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = { state.query = it },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_search), null, Modifier.size(20.dp), tint = TextMuted)
                        },
                        trailingIcon = {
                            IconButton(onClick = { onPickOnMap(false) }) {
                                Icon(painterResource(R.drawable.ic_place), "Elegir destino en el mapa", Modifier.size(22.dp), tint = AccentBlue)
                            }
                        },
                        placeholder = { Text("¿A dónde vas?", fontSize = 14.sp) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { searchPlaces() })
                    )
                }
            }

            // ---- Estado del destino y sugerencias
            if (state.searchingPlaces) {
                Text("Buscando lugares…", color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            state.placesMessage?.let {
                Text(it, color = AccentOrange, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }
            if (state.places.isNotEmpty()) {
                Column(Modifier.padding(top = 6.dp)) {
                    HorizontalDivider(color = DividerColor)
                    for (place in state.places) {
                        Text(
                            place.name, color = TextPrimary, fontSize = 13.sp, maxLines = 2,
                            modifier = Modifier.fillMaxWidth()
                                .clickable {
                                    val short = place.name.substringBefore(",")
                                    state.chooseDestination(place.point, short)
                                    state.query = short
                                }
                                .padding(vertical = 10.dp, horizontal = 4.dp)
                        )
                        HorizontalDivider(color = DividerColor)
                    }
                }
            } else if (state.destination != null) {
                Text(
                    "Destino: ${state.destinationLabel} ✓", color = com.rutaya.app.ui.theme.AccentText, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---- Mensajes (una sola línea)
            val note = when {
                state.searching -> "Buscando rutas cercanas…"
                state.message != null -> state.message
                state.error != null -> state.error
                else -> null
            }
            if (note != null) {
                Text(
                    note, color = if (state.searching) TextMuted else AccentOrange, fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }

            PrimaryButton(
                if (state.searching) "Buscando…" else "Buscar rutas", height = 58.dp, icon = R.drawable.ic_search,
                enabled = !state.searching,
                modifier = Modifier.padding(top = 14.dp),
                onClick = { scope.launch { if (state.searchPlans()) onPlansReady() } }
            )

            TextButton(
                onClick = {
                    state.chooseOrigin(GeoPt(-6.744477, -79.841786), "Origen de prueba")
                    state.chooseDestination(GeoPt(-6.753506, -79.850855), "Destino de prueba")
                    state.query = ""
                },
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) { Text("Usar datos de prueba", color = TextMuted, fontSize = 11.sp) }
        }
    }
}
