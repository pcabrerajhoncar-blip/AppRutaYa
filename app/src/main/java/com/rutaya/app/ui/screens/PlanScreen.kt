package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
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

    suspend fun loadRoutes() {
        loading = true
        error = null
        when (val result = RouteApiClient.getCircuitRoutes()) {
            is ApiResult.Success -> {
                routes = result.value
                if (result.value.isEmpty()) error = "El servidor no tiene rutas con trazado. Importa seed_ruta_circuito.sql."
            }
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

    /** Calcula los viajes posibles. Devuelve true si hay al menos uno. */
    fun searchPlans(): Boolean {
        message = null
        val o = origin
        val d = destination
        when {
            routes.isEmpty() -> message = error ?: "Aún no se cargaron las rutas del servidor."
            o == null -> message = "Primero indica tu ubicación de origen."
            d == null -> message = "Indica tu destino: búscalo por nombre o elígelo en el mapa."
            else -> {
                plans = TripPlanner.plan(o, d, routes)
                if (plans.isEmpty()) {
                    message = "Ninguna ruta tiene paraderos a menos de ${TripPlanner.MAX_WALK_M.toInt()} m de tu origen y de tu destino."
                }
            }
        }
        return plans.isNotEmpty()
    }
}

/** Mapa para elegir el destino tocando; se abre centrado en tu ubicación. */
@Composable
fun PickPointScreen(state: PlanState, forOrigin: Boolean, onBack: () -> Unit, onDone: () -> Unit) {
    var picked by remember { mutableStateOf(if (forOrigin) state.origin else state.destination) }

    val lines = emptyList<MapLine>()
    val pins = ArrayList<MapPin>()
    if (!forOrigin) state.origin?.let { pins.add(MapPin(it, PinKind.ORIGIN, "Tu ubicación")) }
    picked?.let { pins.add(MapPin(it, if (forOrigin) PinKind.ORIGIN else PinKind.DESTINATION, if (forOrigin) "Origen" else "Destino")) }
    val center = state.origin?.let { listOf(it) }

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(if (forOrigin) "Elegir origen" else "Elegir destino", if (forOrigin) "Toca el mapa donde estás" else "Toca el mapa donde quieres llegar", onBack = onBack)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lines = lines, pins = pins,
                fitPoints = center, fitKey = "pick",
                onTap = { picked = it }
            )
        }
        Column(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color.White).padding(12.dp)) {
            Text(
                if (picked == null) "Aún no elegiste un punto." else if (forOrigin) "Origen elegido en el mapa ✓" else "Destino elegido en el mapa ✓",
                color = TextSecondary, fontSize = 13.sp
            )
            PrimaryButton(
                if (forOrigin) "Confirmar origen" else "Confirmar destino", enabled = picked != null, height = 48.dp,
                modifier = Modifier.padding(top = 8.dp),
                onClick = {
                    picked?.let {
                        if (forOrigin) {
                            state.chooseOrigin(it, "Origen elegido en el mapa")
                        } else {
                            state.chooseDestination(it, String.format(Locale.US, "Punto en el mapa (%.5f, %.5f)", it.lat, it.lng))
                            state.query = ""
                        }
                        onDone()
                    }
                }
            )
        }
    }
}

/** Lista de rutas posibles para ir del origen al destino. */
@Composable
fun PlanResultsScreen(state: PlanState, onBack: () -> Unit, onSelect: (TripPlan) -> Unit) {
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
            items(state.plans) { plan -> PlanCard(plan, onClick = { onSelect(plan) }) }
        }
    }
}

@Composable
private fun PlanCard(plan: TripPlan, onClick: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), onClick = onClick) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(plan.routeName, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("~${plan.totalMinutes} min", color = PrimaryNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
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
