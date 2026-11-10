package com.rutaya.app.ui.screens

import android.Manifest
import com.rutaya.app.ui.theme.NoticeText
import com.rutaya.app.ui.theme.NoticeBg
import com.rutaya.app.ui.theme.ButtonBg
import com.rutaya.app.ui.theme.AccentText
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.rutaya.app.model.TrustedContact
import com.rutaya.app.network.ApiConfig
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.notify.Notifier
import com.rutaya.app.storage.AppSettings
import com.rutaya.app.storage.Session
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
    originLabel: String,
    destinationLabel: String,
    onBack: () -> Unit,
    onGoHome: () -> Unit,
    onLogin: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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

    // ---- Compartir viaje, favorito y aviso de bajada
    var shareToken by rememberSaveable { mutableStateOf<String?>(null) }
    var shareUrl by rememberSaveable { mutableStateOf<String?>(null) }
    var sosSent by rememberSaveable { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showConsent by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<TrustedContact>>(emptyList()) }
    var shareMessage by remember { mutableStateOf<String?>(null) }
    var sharing by remember { mutableStateOf(false) }
    var alertShown by rememberSaveable { mutableStateOf(false) }
    var favoriteSaved by rememberSaveable { mutableStateOf(false) }

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

    // ---- Aviso "bájate ya"
    val getOffNow = phase == Phase.RIDING && AppSettings.alertEnabled &&
        stopsLeft in 1..AppSettings.alertStopsBefore
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (AppSettings.alertEnabled && !Notifier.canNotify(context)) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(getOffNow) {
        if (getOffNow && !alertShown) {
            alertShown = true
            if (AppSettings.vibrate) Notifier.vibrate(context)
            Notifier.notifyGetOff(context, "¡Prepárate para bajar!", "Bájate en ${plan.alighting.name}.")
        }
    }

    // ---- Mientras se comparte, se envía la ubicación cada ~8 segundos
    val latestPosition = rememberUpdatedState(position)
    val latestSos = rememberUpdatedState(sosSent)
    LaunchedEffect(shareToken) {
        val token = shareToken ?: return@LaunchedEffect
        while (true) {
            delay(8_000L)
            Backend.sendLocation(token, latestPosition.value, latestSos.value)
        }
    }
    // Si sales de la pantalla sin finalizar, se deja de compartir (se borra la ubicación).
    val latestToken = rememberUpdatedState(shareToken)
    DisposableEffect(Unit) {
        onDispose {
            val token = latestToken.value
            if (token != null) CoroutineScope(Dispatchers.IO).launch { Backend.stopShare(token) }
            Notifier.cancel(context)
        }
    }

    val destText = destinationLabel.ifBlank { String.format(Locale.US, "Mi destino (%.5f, %.5f)", plan.destination.lat, plan.destination.lng) }

    fun startSharing() {
        if (sharing) return
        sharing = true
        shareMessage = null
        scope.launch {
            if (shareToken == null) {
                when (val r = Backend.startShare(plan.routeName, destText, position)) {
                    is ApiResult.Success -> { shareToken = r.value.token; shareUrl = r.value.url }
                    is ApiResult.Error -> shareMessage = r.message
                }
            }
            if (shareToken != null) {
                when (val c = Backend.contacts()) {
                    is ApiResult.Success -> contacts = c.value
                    is ApiResult.Error -> shareMessage = c.message
                }
                showShare = true
            } else {
                toast(context, shareMessage ?: "No se pudo compartir el viaje.", long = true)
            }
            sharing = false
        }
    }

    fun onShareClick() {
        when {
            !Session.isLoggedIn -> { toast(context, "Inicia sesión para compartir tu viaje con personas de confianza."); onLogin() }
            !AppSettings.shareConsent -> showConsent = true
            else -> startSharing()
        }
    }

    fun sendSos() {
        sosSent = true
        val token = shareToken
        if (token == null) { toast(context, "Primero comparte tu viaje para poder avisar a tus contactos."); return }
        scope.launch { Backend.sendLocation(token, position, true) }
        toast(context, "Aviso de ayuda enviado: tus contactos lo verán en el enlace.", long = true)
    }

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
        val originText = originLabel.ifBlank { String.format(Locale.US, "Mi origen (%.5f, %.5f)", plan.origin.lat, plan.origin.lng) }
        val record = TripRecord.fromRouteOption(sessionId, option, originText, destText, startTime, now(), "Finalizado")
        TripHistoryManager.saveCompletedTrip(context, record)
        Notifier.cancel(context)
        val token = shareToken
        scope.launch {
            if (token != null) { Backend.stopShare(token); shareToken = null }
            if (record != null && Session.isLoggedIn) Backend.saveTrip(record, plan.routeId)
            toast(context, "Viaje finalizado y guardado en tu historial.")
            onGoHome()
        }
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
                    TextButton(onClick = {
                        if (!Session.isLoggedIn) {
                            toast(context, "Inicia sesión para guardar rutas favoritas.")
                            onLogin()
                        } else if (!favoriteSaved) {
                            scope.launch {
                                when (val r = Backend.favoritePlan(plan, originLabel, destinationLabel)) {
                                    is ApiResult.Success -> { favoriteSaved = true; toast(context, "Guardada en favoritos ⭐") }
                                    is ApiResult.Error -> toast(context, r.message, long = true)
                                }
                            }
                        }
                    }) { Text(if (favoriteSaved) "⭐" else "☆", fontSize = 20.sp) }
                }
            }
        }

        // ---- Panel de instrucciones (la mitad inferior)
        AppCard(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), elevation = 10.dp
        ) {
            Column(Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState()).padding(14.dp)) {
                Text(title, color = AccentText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
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

                if (getOffNow) {
                    AppCard(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        containerColor = NoticeBg
                    ) {
                        Text(
                            "🔔 ¡Prepárate para bajar! Tu paradero es ${plan.alighting.name} " +
                                "(faltan $stopsLeft ${if (stopsLeft == 1) "parada" else "paradas"}).",
                            color = NoticeText, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                if (phase != Phase.ARRIVED) {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton(
                            if (sharing) "Un momento…" else if (shareToken != null) "Compartiendo ✓" else "Compartir mi viaje",
                            fontSize = 12, enabled = !sharing,
                            onClick = { if (shareToken != null) startSharing() else onShareClick() },
                            modifier = Modifier.weight(1f)
                        )
                        if (shareToken != null) {
                            SecondaryButton(
                                "Dejar de compartir", fontSize = 12,
                                onClick = {
                                    val token = shareToken
                                    shareToken = null
                                    if (token != null) scope.launch { Backend.stopShare(token) }
                                    toast(context, "Dejaste de compartir tu ubicación.")
                                }
                            )
                        }
                        SecondaryButton("🚨 SOS", fontSize = 12, onClick = { sendSos() })
                    }
                }

                PrimaryButton(
                    "Finalizar viaje",
                    onClick = { if (phase == Phase.ARRIVED) finishTrip() else showConfirm = true },
                    height = 46.dp, icon = R.drawable.ic_check_circle,
                    containerColor = if (phase == Phase.ARRIVED) AccentGreen else ButtonBg,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Compartir tu ubicación") },
            text = {
                Text(
                    "Si continúas, tu ubicación en tiempo real podrá verse en un enlace que tú envías a quien elijas, " +
                        "solo mientras dure este viaje. Al terminar, se borra. Puedes dejar de compartir cuando quieras."
                )
            },
            confirmButton = {
                TextButton(onClick = { showConsent = false; AppSettings.acceptShareConsent(); startSharing() }) { Text("Acepto") }
            },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancelar") } }
        )
    }

    if (showShare) {
        val link = shareUrl ?: ""
        val text = "Hola, estoy viajando en ${plan.routeName} con RutaYa. Sigue mi viaje en tiempo real aquí: $link"
        AlertDialog(
            onDismissRequest = { showShare = false },
            title = { Text("Enviar mi viaje por WhatsApp") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    if (contacts.isEmpty()) {
                        Text("Aún no tienes personas de confianza. Agrégalas en la pestaña Seguridad, o envía el enlace a cualquiera:", fontSize = 13.sp)
                    }
                    for (c in contacts) {
                        TextButton(onClick = {
                            val number = if (c.phone.length == 9) "51${c.phone}" else c.phone
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(text)))
                                )
                            } catch (e: ActivityNotFoundException) {
                                toast(context, "No se pudo abrir WhatsApp.")
                            }
                        }) { Text("📲 ${c.name}", fontSize = 15.sp) }
                    }
                    TextButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(send, "Compartir enlace"))
                    }) { Text("🔗 Enviar el enlace a otra persona…", fontSize = 14.sp) }
                    if (ApiConfig.SERVER.startsWith("http://192.168") || ApiConfig.SERVER.startsWith("http://10.")) {
                        Text(
                            "Nota: mientras pruebas en tu red local, el enlace solo abre dentro de tu mismo Wi‑Fi. " +
                                "Funcionará para todos cuando la API esté en la nube.",
                            fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showShare = false }) { Text("Listo") } }
        )
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
