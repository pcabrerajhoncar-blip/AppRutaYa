package com.rutaya.app.ui.screens

import android.Manifest
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.rutaya.app.R
import com.rutaya.app.domain.Geo
import com.rutaya.app.domain.PathWalker
import com.rutaya.app.location.Gps
import com.rutaya.app.model.GeoPt
import com.rutaya.app.model.RouteOption
import com.rutaya.app.model.TripPlan
import com.rutaya.app.model.TripRecord
import com.rutaya.app.storage.TripHistoryManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.MapLine
import com.rutaya.app.ui.components.MapPin
import com.rutaya.app.ui.components.OsmMap
import com.rutaya.app.ui.components.PinKind
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentGreen
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fases del viaje, en orden. Se guardan por posición para sobrevivir a rotaciones. */
private enum class Phase { WALK_TO_STOP, WAITING, RIDING, WALK_TO_DEST, ARRIVED }

private const val RED = 0xFFB91C1C.toInt()
private const val BLUE = 0xFF2563EB.toInt()

private const val BOARDING_RADIUS_M = 30.0
private const val ALIGHTING_RADIUS_M = 60.0
private const val DESTINATION_RADIUS_M = 40.0
private const val WALK_SPEED_MS = 1.3
private const val RIDE_SPEED_MS = 6.0
private const val TICK_MS = 200L

private val SPEEDS = listOf(1, 5, 10, 20)

private fun now(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

private fun fmtM(m: Double): String =
    if (m >= 1000) String.format(Locale.getDefault(), "%.1f km", m / 1000) else "${m.toInt()} m"

@Composable
fun LiveTripScreen(
    plan: TripPlan,
    sessionId: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current

    var simulate by rememberSaveable { mutableStateOf(false) }
    var phaseIndex by rememberSaveable { mutableIntStateOf(0) }
    val phase = Phase.entries[phaseIndex]

    // En simulación: metros recorridos dentro de la fase actual. En modo real: última posición del GPS.
    var legDistance by rememberSaveable { mutableDoubleStateOf(0.0) }
    fun goTo(p: Phase) { phaseIndex = p.ordinal; legDistance = 0.0 }
    var realPosition by rememberSaveable { mutableStateOf<GeoPt?>(null) }
    var speedIndex by rememberSaveable { mutableIntStateOf(1) }
    var follow by rememberSaveable { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    val startTime = rememberSaveable { now() }

    // ---- Recorridos de cada tramo
    val walkToStop = remember(plan) { PathWalker(listOf(plan.origin, plan.boarding.point)) }
    val ridePath = remember(plan) { PathWalker(plan.ridePath) }
    val walkToDest = remember(plan) { PathWalker(listOf(plan.alighting.point, plan.destination)) }
    val rideCumulative = remember(plan) { Geo.cumulative(plan.ridePath) }

    // ---- Posición actual
    val position: GeoPt = if (simulate) {
        when (phase) {
            Phase.WALK_TO_STOP -> walkToStop.pointAt(legDistance)
            Phase.WAITING -> plan.boarding.point
            Phase.RIDING -> ridePath.pointAt(legDistance)
            Phase.WALK_TO_DEST -> walkToDest.pointAt(legDistance)
            Phase.ARRIVED -> plan.destination
        }
    } else {
        realPosition ?: plan.origin
    }

    // ---- Simulación: avanza el punto
    LaunchedEffect(simulate, phase, speedIndex) {
        if (!simulate) return@LaunchedEffect
        val factor = SPEEDS[speedIndex]
        while (true) {
            delay(TICK_MS)
            val dt = TICK_MS / 1000.0
            when (phase) {
                Phase.WALK_TO_STOP -> {
                    legDistance += WALK_SPEED_MS * factor * dt
                    if (legDistance >= walkToStop.totalM) { goTo(Phase.WAITING); return@LaunchedEffect }
                }
                Phase.RIDING -> {
                    legDistance += RIDE_SPEED_MS * factor * dt
                    if (legDistance >= ridePath.totalM) { goTo(Phase.WALK_TO_DEST); return@LaunchedEffect }
                }
                Phase.WALK_TO_DEST -> {
                    legDistance += WALK_SPEED_MS * factor * dt
                    if (legDistance >= walkToDest.totalM) { goTo(Phase.ARRIVED); return@LaunchedEffect }
                }
                else -> Unit
            }
            if (phase == Phase.WAITING || phase == Phase.ARRIVED) return@LaunchedEffect
        }
    }

    // ---- Modo real: permisos y actualizaciones del GPS
    var hasPermission by remember { mutableStateOf(Gps.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasPermission = Gps.hasPermission(context) }

    LaunchedEffect(simulate) {
        if (!simulate && !hasPermission) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
    }

    DisposableEffect(simulate, hasPermission) {
        if (simulate || !hasPermission) {
            onDispose { }
        } else {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateDistanceMeters(2f)
                .build()
            val callback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let { realPosition = GeoPt(it.latitude, it.longitude) }
                }
            }
            try {
                client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            } catch (_: SecurityException) {
            }
            onDispose { client.removeLocationUpdates(callback) }
        }
    }

    // ---- Modo real: cambio automático de fase según la distancia
    LaunchedEffect(realPosition, phase) {
        if (simulate) return@LaunchedEffect
        val p = realPosition ?: return@LaunchedEffect
        when (phase) {
            Phase.WALK_TO_STOP -> if (Geo.distance(p, plan.boarding.point) <= BOARDING_RADIUS_M) goTo(Phase.WAITING)
            Phase.RIDING -> if (Geo.distance(p, plan.alighting.point) <= ALIGHTING_RADIUS_M) goTo(Phase.WALK_TO_DEST)
            Phase.WALK_TO_DEST -> if (Geo.distance(p, plan.destination) <= DESTINATION_RADIUS_M) goTo(Phase.ARRIVED)
            else -> Unit
        }
    }

    // ---- Avance sobre la ruta (para "próximo paradero")
    val rideProgressM: Double = when {
        phase.ordinal < Phase.RIDING.ordinal -> 0.0
        phase.ordinal > Phase.RIDING.ordinal -> ridePath.totalM
        simulate -> legDistance
        else -> rideCumulative.getOrElse(Geo.nearestIndex(plan.ridePath, position)) { 0.0 }
    }
    val nextStop = plan.rideStops.firstOrNull { it.distanceAlongM > rideProgressM + 5.0 }
    val stopsLeft = plan.rideStops.count { it.distanceAlongM > rideProgressM + 5.0 }

    // ---- Texto de la fase
    val title: String
    val detail: String
    when (phase) {
        Phase.WALK_TO_STOP -> {
            title = "Camina hacia ${plan.boarding.name}"
            detail = "Te faltan ${fmtM(Geo.distance(position, plan.boarding.point))}."
        }
        Phase.WAITING -> {
            title = "Espera tu combi en ${plan.boarding.name}"
            detail = "Pasa cada ~${plan.frequencyMin ?: 10} min. Cuando subas, toca «Iniciar viaje»."
        }
        Phase.RIDING -> {
            title = "Vas en la combi"
            detail = (nextStop?.let { "Próximo paradero: ${it.stop.name} (en ${fmtM(it.distanceAlongM - rideProgressM)}). " } ?: "") +
                "Bájate en ${plan.alighting.name} · faltan $stopsLeft paradas."
        }
        Phase.WALK_TO_DEST -> {
            title = "Camina hacia tu destino"
            detail = "Te faltan ${fmtM(Geo.distance(position, plan.destination))}."
        }
        Phase.ARRIVED -> {
            title = "¡Llegaste a tu destino!"
            detail = "Toca «Finalizar viaje» para guardarlo en tu historial."
        }
    }

    fun finishTrip() {
        val steps = "Aborda en ${plan.boarding.name} → desciende en ${plan.alighting.name}"
        val option = RouteOption(
            plan.routeId.toString(), plan.routeName, plan.transportType,
            plan.totalMinutes, plan.price ?: -1.0,
            (plan.walkToStopM + plan.walkFromStopM).toInt(), null, steps, null,
            plan.rideMinutes, plan.totalMinutes,
            plan.boarding.name, plan.boarding.order, plan.alighting.name, plan.alighting.order
        )
        val originText = String.format(Locale.US, "Mi origen (%.5f, %.5f)", plan.origin.lat, plan.origin.lng)
        val destText = String.format(Locale.US, "Mi destino (%.5f, %.5f)", plan.destination.lat, plan.destination.lng)
        val record = TripRecord.fromRouteOption(sessionId, option, originText, destText, startTime, now(), "Finalizado")
        TripHistoryManager.saveCompletedTrip(context, record)
        toast(context, "Viaje finalizado y guardado en tu historial.")
        onGoHome()
    }

    // ---- Qué se dibuja
    val lines = listOf(
        MapLine(plan.ridePath, RED, 11f),
        MapLine(listOf(plan.origin, plan.boarding.point), BLUE, 7f, dashed = true),
        MapLine(listOf(plan.alighting.point, plan.destination), BLUE, 7f, dashed = true)
    )
    val pins = ArrayList<MapPin>()
    for (rs in plan.rideStops) {
        val kind = when (rs.stop.id) {
            plan.boarding.id -> PinKind.BOARDING
            plan.alighting.id -> PinKind.ALIGHTING
            else -> PinKind.STOP
        }
        pins.add(MapPin(rs.stop.point, kind, rs.stop.name))
    }
    pins.add(MapPin(plan.destination, PinKind.DESTINATION, "Tu destino"))
    pins.add(MapPin(position, PinKind.USER, "Tú"))

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        // ---- Mapa (la mitad superior)
        Box(Modifier.fillMaxWidth().weight(1f)) {
            OsmMap(
                modifier = Modifier.fillMaxSize(),
                lines = lines,
                pins = pins,
                fitPoints = plan.ridePath + plan.origin + plan.destination,
                fitKey = "live",
                followPoint = if (follow) position else null
            )
            AppCard(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(8.dp),
                shape = RoundedCornerShape(14.dp), elevation = 6.dp
            ) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack) { Text("Volver", color = AccentBlue, fontSize = 13.sp) }
                    Column(Modifier.weight(1f)) {
                        Text(plan.routeName, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(
                            if (simulate) "Modo simulación" else "GPS en tiempo real",
                            color = if (simulate) AccentOrange else AccentGreen, fontSize = 11.sp
                        )
                    }
                    FilterChip(
                        selected = simulate,
                        onClick = { simulate = !simulate; phaseIndex = 0; legDistance = 0.0 },
                        label = { Text("Simular", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = follow, onClick = { follow = !follow },
                        modifier = Modifier.padding(start = 4.dp),
                        label = { Text("Seguirme", fontSize = 12.sp) }
                    )
                }
            }
        }

        // ---- Panel de instrucciones (la mitad inferior)
        AppCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), elevation = 10.dp
        ) {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
                Text(title, color = PrimaryNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(detail, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                if (!simulate && !hasPermission) {
                    Text(
                        "Sin permiso de ubicación: no se puede seguir tu posición. Usa los botones manuales o el modo simulación.",
                        color = AccentOrange, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (!simulate && realPosition == null) {
                    Text("Esperando señal de GPS…", color = AccentOrange, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }

                if (simulate && (phase == Phase.WALK_TO_STOP || phase == Phase.RIDING || phase == Phase.WALK_TO_DEST)) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            "Velocidad x${SPEEDS[speedIndex]}", fontSize = 12,
                            onClick = { speedIndex = (speedIndex + 1) % SPEEDS.size }
                        )
                    }
                }

                when (phase) {
                    Phase.WALK_TO_STOP -> if (!simulate) {
                        SecondaryButton(
                            "Ya llegué al paradero", fontSize = 12,
                            onClick = { goTo(Phase.WAITING) }, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    Phase.WAITING -> PrimaryButton(
                        "Iniciar viaje", onClick = { goTo(Phase.RIDING) }, height = 46.dp,
                        icon = R.drawable.ic_bus, modifier = Modifier.padding(top = 8.dp)
                    )
                    Phase.RIDING -> SecondaryButton(
                        "Ya me bajé", fontSize = 12,
                        onClick = { goTo(Phase.WALK_TO_DEST) }, modifier = Modifier.padding(top = 8.dp)
                    )
                    else -> Unit
                }

                PrimaryButton(
                    "Finalizar viaje",
                    onClick = { if (phase == Phase.ARRIVED) finishTrip() else showConfirm = true },
                    height = 46.dp, icon = R.drawable.ic_check_circle,
                    containerColor = if (phase == Phase.ARRIVED) AccentGreen else PrimaryNavy,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text("¿Finalizar el viaje?") },
            text = { Text("Aún no llegas a tu destino. Si finalizas ahora, el viaje se guardará igualmente en tu historial.") },
            confirmButton = { TextButton(onClick = { showConfirm = false; finishTrip() }) { Text("Finalizar") } },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Seguir viajando") } }
        )
    }
}
