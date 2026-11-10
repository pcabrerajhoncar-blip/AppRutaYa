package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.domain.TripPlanner
import com.rutaya.app.model.CircuitRoute
import com.rutaya.app.model.GeoPt
import com.rutaya.app.model.TripPlan
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Place
import com.rutaya.app.network.RouteApiClient
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.AppSettings
import com.rutaya.app.storage.Session
import com.rutaya.app.ui.components.toast
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.MapLine
import com.rutaya.app.ui.components.MapPin
import com.rutaya.app.ui.components.OsmMap
import com.rutaya.app.ui.components.PinKind
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import java.util.Locale

private const val RED = 0xFFB91C1C.toInt()

/** Estado compartido del planificador (origen, destino, rutas del servidor y resultados). Vive en el ViewModel. */
@Stable
class PlanState {
    var routes by mutableStateOf<List<CircuitRoute>>(emptyList())
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    var origin by mutableStateOf<GeoPt?>(null)
    var originLabel by mutableStateOf("")
    var destination by mutableStateOf<GeoPt?>(null)
    var destinationLabel by mutableStateOf("")

    var query by mutableStateOf("")
    var places by mutableStateOf<List<Place>>(emptyList())
    var searchingPlaces by mutableStateOf(false)
    var placesMessage by mutableStateOf<String?>(null)

    var plans by mutableStateOf<List<TripPlan>>(emptyList())
    var message by mutableStateOf<String?>(null)

    /** true mientras se consultan al servidor las rutas cercanas al origen y al destino. */
    var searching by mutableStateOf(false)

    suspend fun loadRoutes() {
        loading = true
        error = null
        when (val result = RouteApiClient.getCircuitRoutes()) {
            is ApiResult.Success -> routes = result.value
            is ApiResult.Error -> error = result.message
        }
        loading = false
    }

    fun chooseOrigin(point: GeoPt, label: String) {
        origin = point
        originLabel = label
        plans = emptyList()
        message = null
    }

    fun chooseDestination(point: GeoPt, label: String) {
        destination = point
        destinationLabel = label
        places = emptyList()
        placesMessage = null
        plans = emptyList()
        message = null
    }

    /**
     * Pide al servidor SOLO las rutas con paraderos cerca del origen y del destino (hasta [AppSettings.maxWalkM] m)
     * y calcula los viajes posibles. Devuelve true si hay al menos uno.
     */
    suspend fun searchPlans(): Boolean {
        message = null
        val o = origin
        val d = destination
        if (o == null) { message = "Primero indica tu ubicación de origen."; return false }
        if (d == null) { message = "Indica tu destino: búscalo por nombre o elígelo en el mapa."; return false }
        val maxWalk = AppSettings.maxWalkM
        searching = true
        plans = emptyList()
        when (val result = RouteApiClient.getNearbyCircuitRoutes(o, d, (maxWalk / TripPlanner.WALK_DETOUR).toInt())) {
            is ApiResult.Success -> {
                routes = result.value
                plans = TripPlanner.plan(o, d, result.value, maxWalk.toDouble())
                if (plans.isEmpty()) {
                    message = if (result.value.isEmpty()) {
                        "Ninguna ruta tiene paraderos a menos de $maxWalk m de tu origen y de tu destino."
                    } else {
                        "Hay rutas con paraderos cerca, pero ninguna te sirve en ese sentido o es mejor ir caminando. " +
                            "Prueba otro origen/destino o aumenta la caminata en Configuración."
                    }
                }
            }
            is ApiResult.Error -> message = result.message
        }
        searching = false
        return plans.isNotEmpty()
    }
}

/**
 * Elegir origen o destino moviendo el mapa: el marcador queda fijo en el centro de la pantalla y el punto
 * elegido es el que queda bajo su base. Arriba del marcador se muestra la dirección de ese punto.
 */
@Composable
fun PickPointScreen(state: PlanState, forOrigin: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = remember {
        (if (forOrigin) state.origin else state.destination) ?: state.origin ?: GeoPt(-6.7714, -79.8409)
    }
    var center by remember { mutableStateOf(start) }
    var address by remember { mutableStateOf<String?>(null) }
    var target by remember { mutableStateOf(start) }
    var targetKey by remember { mutableStateOf(0) }

    // Cuando el mapa deja de moverse (0,6 s), se busca la dirección del punto central.
    LaunchedEffect(center) {
        address = null
        kotlinx.coroutines.delay(600)
        address = com.rutaya.app.network.PlaceSearch.reverse(center)
    }

    val pins = ArrayList<MapPin>()
    if (!forOrigin) state.origin?.let { pins.add(MapPin(it, PinKind.ORIGIN, "Tu ubicación")) }
    val pinColor = if (forOrigin) com.rutaya.app.ui.theme.AccentGreen else androidx.compose.ui.graphics.Color(0xFFDC2626)

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            if (forOrigin) "Elegir origen" else "Elegir destino",
            "Mueve el mapa para ubicar el punto",
            onBack = onBack
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lines = emptyList(), pins = pins,
                fitPoints = listOf(target), fitKey = targetKey,
                onCenterChanged = { center = it }
            )

            // Marcador fijo: su base queda exactamente en el centro del mapa.
            Box(Modifier.align(Alignment.Center).size(0.dp)) {
                Column(
                    Modifier.wrapContentSize(align = Alignment.BottomCenter, unbounded = true),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 260.dp)
                            .background(PrimaryNavy, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            if (forOrigin) "Punto de origen" else "Punto de destino",
                            color = androidx.compose.ui.graphics.Color(0xFFCBD5E1), fontSize = 11.sp
                        )
                        Text(
                            address ?: "Buscando dirección…",
                            color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, maxLines = 2
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier.size(44.dp)
                            .background(com.rutaya.app.ui.theme.CardSurface, RoundedCornerShape(10.dp))
                            .border(2.dp, pinColor, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.material3.Icon(
                            androidx.compose.ui.res.painterResource(com.rutaya.app.R.drawable.ic_location_pin),
                            null, Modifier.size(26.dp), tint = pinColor
                        )
                    }
                    Box(Modifier.width(3.dp).height(18.dp).background(pinColor))
                }
            }

            // Botón "mi ubicación": centra el mapa donde estás.
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    scope.launch {
                        val loc = com.rutaya.app.location.Gps.currentLocation(context)
                        if (loc == null) {
                            toast(context, "No pude obtener tu ubicación. Revisa el permiso y el GPS.")
                        } else {
                            target = GeoPt(loc.latitude, loc.longitude)
                            targetKey++
                        }
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                containerColor = com.rutaya.app.ui.theme.CardSurface
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(com.rutaya.app.R.drawable.ic_my_location),
                    "Mi ubicación", Modifier.size(24.dp), tint = AccentBlue
                )
            }
        }
        Column(Modifier.fillMaxWidth().background(com.rutaya.app.ui.theme.CardSurface).padding(12.dp)) {
            Text(
                address ?: "Buscando dirección…",
                color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2
            )
            PrimaryButton(
                if (forOrigin) "Confirmar origen" else "Confirmar destino",
                enabled = address != null, height = 48.dp,
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    val label = address ?: return@PrimaryButton
                    if (forOrigin) {
                        state.chooseOrigin(center, label)
                    } else {
                        state.chooseDestination(center, label)
                        state.query = label
                    }
                    onDone()
                }
            )
        }
    }
}

/** Lista de rutas posibles para ir del origen al destino. */
@Composable
fun PlanResultsScreen(state: PlanState, onBack: () -> Unit, onSelect: (TripPlan) -> Unit, onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader("Rutas disponibles", "Elige la que prefieras", onBack = onBack)
        if (state.plans.isEmpty()) {
            Text(
                state.message ?: "No hay rutas para mostrar.", color = AccentOrange, fontSize = 14.sp,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
            items(state.plans) { plan ->
                PlanCard(plan, onClick = { onSelect(plan) }, onFavorite = {
                    if (!Session.isLoggedIn) {
                        toast(context, "Inicia sesión para guardar rutas favoritas.")
                        onLogin()
                    } else {
                        scope.launch {
                            when (val r = Backend.favoritePlan(plan, state.originLabel, state.destinationLabel)) {
                                is ApiResult.Success -> toast(context, "Guardada en favoritos ⭐")
                                is ApiResult.Error -> toast(context, r.message, long = true)
                            }
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun PlanCard(plan: TripPlan, onClick: () -> Unit, onFavorite: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.routeName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("~${plan.totalMinutes} min", color = com.rutaya.app.ui.theme.AccentText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onFavorite) { Text("☆", fontSize = 20.sp) }
            }
            Text(
                plan.transportType + (plan.price?.let { String.format(Locale.getDefault(), " · S/. %.2f", it) } ?: "") +
                    " · pasa cada ~${plan.frequencyMin ?: 10} min",
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
            )
            Text("1. Camina ${plan.walkToStopM.toInt()} m hasta ${plan.boarding.name}.", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            Text("2. Viaja ${plan.stopsCount} paradas hasta ${plan.alighting.name} (~${plan.rideMinutes} min).", color = TextSecondary, fontSize = 13.sp)
            Text("3. Camina ${plan.walkFromStopM.toInt()} m hasta tu destino.", color = TextSecondary, fontSize = 13.sp)
            TextButton(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) {
                Text("Elegir esta ruta →", color = AccentBlue, fontWeight = FontWeight.Bold)
            }
        }
    }
}
