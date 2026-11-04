package com.rutaya.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedAssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.rutaya.app.R
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.LocalNetworkAccess
import com.rutaya.app.network.RouteApiClient
import com.rutaya.app.storage.TripHistoryManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.HeaderBrush
import com.rutaya.app.ui.components.IconBadge
import com.rutaya.app.ui.components.LinkButton
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.SectionTitle
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentCyan
import com.rutaya.app.ui.theme.AccentGreenLight
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.BusBadgeBg
import com.rutaya.app.ui.theme.CircleButton
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.HistoryBg
import com.rutaya.app.ui.theme.InputSurface
import com.rutaya.app.ui.theme.NoticeBg
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import com.rutaya.app.ui.theme.TextWhite
import com.rutaya.app.ui.theme.TextWhiteSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private const val NEARBY_RADIUS_METERS = 1000
private val COORDINATES_REGEX = Regex("[-+]?\\d{1,2}\\.\\d{6},\\s*[-+]?\\d{1,3}\\.\\d{6}")

/** Estado y lógica de la pantalla principal (ubicación, paraderos cercanos, validación). */
@Stable
class HomeState(private val context: Context, private val scope: CoroutineScope) {

    var origin by mutableStateOf("")
    var destination by mutableStateOf("")
    var selectedGps by mutableStateOf<String?>(null)

    var originError by mutableStateOf<String?>(null)
    var destinationError by mutableStateOf<String?>(null)

    var statusText by mutableStateOf<String?>(null)
    var statusIsError by mutableStateOf(false)
    var showRetry by mutableStateOf(false)
    var locationDisabled by mutableStateOf(false)
    var locating by mutableStateOf(false)

    var nearbyVisible by mutableStateOf(false)
    var nearbyLoading by mutableStateOf(false)
    var nearbyText by mutableStateOf("")
    var nearbyRetry by mutableStateOf(false)
    var findEnabled by mutableStateOf(true)

    private var pendingNearbySearch = false
    private var awaitingNearbyNetworkPermission = false
    private var pendingLat = 0.0
    private var pendingLng = 0.0
    private var generation = 0
    private var cancellation: CancellationTokenSource? = null
    private var returningFromSettings = false

    var launchLocationPermission: () -> Unit = {}
    var launchNetworkPermission: () -> Unit = {}

    private fun str(id: Int) = context.getString(id)

    // ---- Acciones simples

    fun swap() {
        val o = origin
        origin = destination
        destination = o
        selectedGps = null
        originError = null
        destinationError = null
        toast(context, str(R.string.toast_swapped))
    }

    fun selectChip(text: String) {
        destination = text
        destinationError = null
        toast(context, "Destino seleccionado: $text")
    }

    fun showHistoryToast() {
        val history = TripHistoryManager.getTripHistory(context)
        if (history.isEmpty()) {
            toast(context, "Historial de viajes vacío. Inicia y finaliza una simulación de viaje.")
        } else {
            val last = history[0]
            toast(
                context,
                "Historial: ${history.size} viaje(s) grabado(s). Último: ${last.routeName} (${last.destination})",
                long = true
            )
        }
    }

    fun showFeatureToast(name: String) =
        toast(context, name + " • " + str(R.string.toast_feature_coming_soon))

    fun selectPopularRoute(o: String, d: String, onSearch: (String, String) -> Unit) {
        origin = o
        destination = d
        originError = null
        destinationError = null
        search(onSearch)
    }

    /** Valida y, si todo está bien, pide abrir los resultados. */
    fun search(onSearch: (String, String) -> Unit) {
        val o = origin.trim()
        val d = destination.trim()

        if (o.isEmpty()) {
            originError = "Por favor, ingresa un punto de partida"
            return
        }
        if ((selectedGps != null && selectedGps == o) || COORDINATES_REGEX.matches(o)) {
            originError = str(R.string.location_search_requires_stop)
            return
        }
        selectedGps = null
        if (d.isEmpty()) {
            destinationError = "Por favor, ingresa un destino"
            return
        }
        originError = null
        destinationError = null
        onSearch(o, d)
    }

    // ---- Ubicación

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun setStatus(message: String, retry: Boolean, error: Boolean) {
        statusText = message
        statusIsError = error
        showRetry = retry
    }

    fun requestCurrentLocation() {
        if (!hasLocationPermission()) {
            setStatus(str(R.string.location_status_requesting_permission), false, false)
            launchLocationPermission()
            return
        }

        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !manager.isLocationEnabled) {
            showLocationDisabled()
            return
        }

        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            locationDisabled = false
            setStatus(str(R.string.location_status_services_unavailable), true, false)
            failPendingNearbySearch(str(R.string.location_status_services_unavailable))
            return
        }

        setStatus(str(R.string.location_status_searching), false, false)
        locating = true
        cancellation?.cancel()
        val request = CancellationTokenSource()
        cancellation = request

        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, request.token)
                .addOnSuccessListener { location ->
                    if (request !== cancellation) return@addOnSuccessListener
                    locating = false
                    cancellation = null
                    if (location == null) {
                        setStatus(str(R.string.location_status_error), true, false)
                        failPendingNearbySearch(str(R.string.location_status_error))
                    } else {
                        showObtainedLocation(location)
                    }
                }
                .addOnFailureListener {
                    if (request !== cancellation) return@addOnFailureListener
                    locating = false
                    cancellation = null
                    setStatus(str(R.string.location_status_error), true, false)
                    failPendingNearbySearch(str(R.string.location_status_error))
                }
        } catch (e: SecurityException) {
            locating = false
            cancellation = null
            showPermissionDenied()
        }
    }

    private fun showObtainedLocation(location: Location) {
        val lat = String.format(Locale.US, "%.6f", location.latitude)
        val lng = String.format(Locale.US, "%.6f", location.longitude)
        val coordinates = "$lat, $lng"
        selectedGps = coordinates
        origin = coordinates
        originError = null
        locationDisabled = false
        setStatus(context.getString(R.string.location_status_success, lat, lng), false, false)
        if (pendingNearbySearch) {
            pendingNearbySearch = false
            fetchNearbyStops(location.latitude, location.longitude)
        }
    }

    fun retryLocation() {
        if (locationDisabled) {
            returningFromSettings = true
            try {
                context.startActivity(
                    Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e: RuntimeException) {
                returningFromSettings = false
                setStatus(str(R.string.location_status_disabled), true, true)
            }
        } else {
            requestCurrentLocation()
        }
    }

    private fun showLocationDisabled() {
        locationDisabled = true
        setStatus(str(R.string.location_status_disabled), true, true)
        failPendingNearbySearch(str(R.string.location_status_disabled))
    }

    private fun showPermissionDenied() {
        locationDisabled = false
        setStatus(str(R.string.location_status_permission_denied), true, false)
        failPendingNearbySearch(str(R.string.location_status_permission_denied))
    }

    fun onLocationPermissionResult() {
        if (hasLocationPermission()) requestCurrentLocation() else showPermissionDenied()
    }

    fun onResume() {
        if (!returningFromSettings) return
        returningFromSettings = false
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager != null && manager.isLocationEnabled) {
            locationDisabled = false
            setStatus(str(R.string.location_status_settings_enabled), true, false)
        } else {
            showLocationDisabled()
        }
    }

    fun dispose() {
        generation++
        cancellation?.cancel()
        cancellation = null
    }

    // ---- Paraderos cercanos

    fun searchNearbyStops() {
        pendingNearbySearch = true
        awaitingNearbyNetworkPermission = false
        generation++
        findEnabled = false
        nearbyVisible = true
        nearbyLoading = true
        nearbyText = str(R.string.nearby_stops_searching_location)
        nearbyRetry = false
        requestCurrentLocation()
    }

    private fun fetchNearbyStops(latitude: Double, longitude: Double) {
        nearbyText = str(R.string.nearby_stops_searching_api)
        nearbyLoading = true
        nearbyRetry = false

        if (!LocalNetworkAccess.isGranted(context)) {
            pendingLat = latitude
            pendingLng = longitude
            awaitingNearbyNetworkPermission = true
            launchNetworkPermission()
            return
        }

        awaitingNearbyNetworkPermission = false
        val current = ++generation
        scope.launch {
            val result = RouteApiClient.getNearbyStops(latitude, longitude, NEARBY_RADIUS_METERS)
            if (current != generation) return@launch
            when (result) {
                is ApiResult.Success -> {
                    nearbyLoading = false
                    findEnabled = true
                    val stops = result.value
                    if (stops.isEmpty()) {
                        nearbyText = str(R.string.nearby_stops_empty)
                        nearbyRetry = true
                    } else {
                        nearbyText = stops.joinToString("\n") {
                            context.getString(R.string.nearby_stops_distance, it.name, Math.round(it.distanceMeters))
                        }
                        nearbyRetry = false
                    }
                }
                is ApiResult.Error -> showNearbyError(result.message)
            }
        }
    }

    fun onNetworkPermissionResult(granted: Boolean) {
        if (granted && awaitingNearbyNetworkPermission) {
            fetchNearbyStops(pendingLat, pendingLng)
        } else {
            showNearbyError(str(R.string.local_network_permission_required))
        }
    }

    private fun showNearbyError(message: String) {
        pendingNearbySearch = false
        awaitingNearbyNetworkPermission = false
        findEnabled = true
        nearbyLoading = false
        nearbyVisible = true
        nearbyText = message
        nearbyRetry = true
    }

    private fun failPendingNearbySearch(message: String) {
        if (pendingNearbySearch) showNearbyError(message)
    }

    companion object {
        fun saver(context: Context, scope: CoroutineScope): Saver<HomeState, Any> = listSaver(
            save = { listOf(it.origin, it.destination, it.selectedGps ?: "") },
            restore = {
                HomeState(context, scope).apply {
                    origin = it[0] as String
                    destination = it[1] as String
                    selectedGps = (it[2] as String).ifEmpty { null }
                }
            }
        )
    }
}

@Composable
fun HomeScreen(
    planState: PlanState,
    onOpenFavorites: () -> Unit,
    onPickOnMap: (forOrigin: Boolean) -> Unit,
    onPlansReady: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = rememberSaveable(saver = HomeState.saver(context, scope)) { HomeState(context, scope) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { state.onLocationPermissionResult() }
    val networkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> state.onNetworkPermissionResult(granted) }

    SideEffect {
        state.launchLocationPermission = {
            locationLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
        state.launchNetworkPermission = { networkLauncher.launch(LocalNetworkAccess.PERMISSION) }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.dispose()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BgScreen)
            .systemBarsPadding()
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(250.dp).background(HeaderBrush))
                Column(Modifier.fillMaxWidth()) {
                    TopBar(onNotification = {
                        toast(context, "Notificaciones • " + context.getString(R.string.toast_feature_coming_soon))
                    })
                    Text(
                        stringResource(R.string.welcome_title),
                        color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp)
                    )
                    Text(
                        stringResource(R.string.welcome_subtitle),
                        color = TextWhiteSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp)
                    )
                    PlannerCard(planState, onPickOnMap, onPlansReady)
                    QuickAccess(state, onOpenFavorites)
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TopBar(onNotification: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.ic_rutaya_logo), stringResource(R.string.app_name), Modifier.size(36.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(stringResource(R.string.app_name), color = TextWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.app_tagline), color = TextWhiteSecondary, fontSize = 11.sp)
        }
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(CircleButton).clickable(onClick = onNotification),
            contentAlignment = Alignment.Center
        ) {
            Image(painterResource(R.drawable.ic_notifications), stringResource(R.string.app_name), Modifier.size(22.dp))
        }
    }
}

@Composable
private fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    error: String?,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(hint, color = TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            singleLine = true,
            isError = error != null,
            textStyle = androidx.compose.ui.text.TextStyle(
                color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold
            ),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                cursorColor = AccentCyan
            ),
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(error, color = AccentOrange, fontSize = 12.sp, modifier = Modifier.padding(start = 16.dp, end = 8.dp))
        }
    }
}

@Composable
private fun TripPlannerCard(
    state: HomeState,
    onSearch: (String, String) -> Unit,
    onOpenMap: () -> Unit,
    onOpenPlan: () -> Unit
) {
    val chips = listOf(
        stringResource(R.string.chip_uss), stringResource(R.string.chip_real_plaza),
        stringResource(R.string.chip_parque), stringResource(R.string.chip_pimentel),
        stringResource(R.string.chip_usat)
    )

    AppCard(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 18.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = 8.dp
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    // ---- Origen
                    Row(verticalAlignment = Alignment.Top) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(
                                painterResource(R.drawable.ic_my_location),
                                stringResource(R.string.origin_label),
                                Modifier.padding(top = 4.dp).size(22.dp)
                            )
                            Box(Modifier.padding(vertical = 2.dp).width(2.dp).height(24.dp).background(DividerColor))
                        }
                        Column(Modifier.weight(1f).padding(start = 4.dp)) {
                            Text(
                                stringResource(R.string.origin_label), color = TextMuted, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)
                            )
                            PlainField(
                                state.origin, { state.origin = it; state.originError = null },
                                stringResource(R.string.origin_hint), state.originError
                            )
                        }
                    }
                    Column(Modifier.padding(start = 34.dp)) {
                        OutlinedActionButton(
                            text = stringResource(R.string.location_action_current),
                            enabled = !state.locating,
                            onClick = { state.requestCurrentLocation() }
                        )
                        val status = state.statusText
                        if (status != null) {
                            Text(
                                status,
                                color = if (state.statusIsError) AccentOrange else TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp, end = 8.dp)
                            )
                        }
                        if (state.showRetry) {
                            LinkButton(
                                if (state.locationDisabled) stringResource(R.string.location_open_settings)
                                else stringResource(R.string.location_retry),
                                onClick = { state.retryLocation() }
                            )
                        }
                        OutlinedActionButton(
                            text = stringResource(R.string.nearby_stops_action),
                            enabled = state.findEnabled,
                            onClick = { state.searchNearbyStops() }
                        )
                        if (state.nearbyVisible) {
                            AppCard(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 8.dp),
                                shape = RoundedCornerShape(12.dp), elevation = 0.dp
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(
                                        stringResource(R.string.nearby_stops_title), color = TextPrimary,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold
                                    )
                                    if (state.nearbyLoading) {
                                        CircularProgressIndicator(
                                            Modifier.padding(top = 8.dp).size(24.dp),
                                            strokeWidth = 2.dp, color = AccentCyan
                                        )
                                    }
                                    Text(
                                        state.nearbyText, color = TextSecondary, fontSize = 13.sp,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                    if (state.nearbyRetry) {
                                        LinkButton(
                                            stringResource(R.string.nearby_stops_retry),
                                            onClick = { state.searchNearbyStops() }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(Modifier.padding(start = 34.dp, top = 8.dp, end = 8.dp).fillMaxWidth().height(1.dp).background(DividerColor))

                    // ---- Destino
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                        Image(
                            painterResource(R.drawable.ic_location_pin),
                            stringResource(R.string.destination_label),
                            Modifier.size(22.dp)
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.destination_label), color = TextMuted, fontSize = 11.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp)
                            )
                            PlainField(
                                state.destination, { state.destination = it; state.destinationError = null },
                                stringResource(R.string.destination_hint), state.destinationError
                            )
                        }
                    }
                }

                // ---- Botón intercambiar
                Box(
                    Modifier
                        .padding(start = 4.dp, top = 4.dp)
                        .size(48.dp)
                        .shadow(2.dp, CircleShape)
                        .clip(CircleShape)
                        .background(InputSurface)
                        .clickable { state.swap() },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painterResource(R.drawable.ic_swap_vert),
                        stringResource(R.string.swap_locations_desc), Modifier.size(22.dp)
                    )
                }
            }

            // ---- Chips
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (chip in chips) {
                    ElevatedAssistChip(
                        onClick = { state.selectChip(chip) },
                        label = { Text(chip, color = PrimaryNavy, fontSize = 12.sp) },
                        leadingIcon = {
                            Icon(painterResource(R.drawable.ic_place), null, Modifier.size(16.dp), tint = AccentCyan)
                        },
                        colors = AssistChipDefaults.elevatedAssistChipColors(containerColor = Color.White)
                    )
                }
            }

            PrimaryButton(
                text = stringResource(R.string.btn_search_routes),
                onClick = { state.search(onSearch) },
                icon = R.drawable.ic_search,
                modifier = Modifier.padding(top = 16.dp)
            )
            PrimaryButton(
                text = "Planificar viaje con mapa",
                onClick = onOpenPlan,
                icon = R.drawable.ic_bus,
                height = 48.dp,
                containerColor = com.rutaya.app.ui.theme.AccentBlue,
                modifier = Modifier.padding(top = 8.dp)
            )
            SecondaryButton(
                text = stringResource(R.string.map_open_action),
                onClick = onOpenMap,
                icon = R.drawable.ic_location_pin,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(44.dp)
            )
        }
    }
}

@Composable
private fun OutlinedActionButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    SecondaryButton(
        text = text,
        onClick = onClick,
        icon = R.drawable.ic_my_location,
        enabled = enabled,
        fontSize = 12,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.padding(top = 2.dp).height(40.dp)
    )
}

@Composable
private fun QuickAccess(state: HomeState, onOpenFavorites: () -> Unit) {
    SectionTitle(
        stringResource(R.string.quick_access_title),
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)
    )
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        QuickCard(
            Modifier.weight(1f), R.drawable.ic_star, Color(0xFFFEF3C7),
            stringResource(R.string.feature_favorites_title), stringResource(R.string.feature_favorites_desc),
            onOpenFavorites
        )
        val safeTitle = stringResource(R.string.feature_safe_trip_title)
        QuickCard(
            Modifier.weight(1f), R.drawable.ic_shield_check, AccentGreenLight,
            safeTitle, stringResource(R.string.feature_safe_trip_desc)
        ) { state.showFeatureToast(safeTitle) }
        QuickCard(
            Modifier.weight(1f), R.drawable.ic_history, HistoryBg,
            stringResource(R.string.feature_history_title), stringResource(R.string.feature_history_desc)
        ) { state.showHistoryToast() }
    }
}

@Composable
private fun QuickCard(
    modifier: Modifier,
    icon: Int,
    iconBackground: Color,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    AppCard(modifier = modifier, onClick = onClick) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconBadge(icon, iconBackground, contentDescription = title)
            Text(
                title, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                description, color = TextMuted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun PopularRoutes(state: HomeState, onSearch: (String, String) -> Unit) {
    SectionTitle(
        stringResource(R.string.popular_routes_title),
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 24.dp)
    )
    val originHint = stringResource(R.string.origin_hint)
    val uss = stringResource(R.string.chip_uss)
    val realPlaza = stringResource(R.string.chip_real_plaza)
    val pimentel = stringResource(R.string.chip_pimentel)

    PopularRouteCard(
        stringResource(R.string.route_1_title), stringResource(R.string.route_1_subtitle),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) { state.selectPopularRoute(originHint, uss, onSearch) }
    PopularRouteCard(
        stringResource(R.string.route_2_title), stringResource(R.string.route_2_subtitle),
        Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp)
    ) { state.selectPopularRoute(realPlaza, pimentel, onSearch) }
}

@Composable
private fun PopularRouteCard(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconBadge(R.drawable.ic_bus, BusBadgeBg, size = 40.dp, shape = RoundedCornerShape(12.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle, color = TextSecondary, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
                )
            }
            Image(painterResource(R.drawable.ic_arrow_forward), null, Modifier.size(20.dp))
        }
    }
}
