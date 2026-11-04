package com.rutaya.app.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.rutaya.app.R
import com.rutaya.app.model.NearbyStop
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.LocalNetworkAccess
import com.rutaya.app.network.RouteApiClient
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentCyan
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.util.Locale

private const val STOPS_SEARCH_RADIUS_METERS = 10_000
private const val LOCATION_TIMEOUT_MS = 15_000L
private const val MAX_CACHED_LOCATION_AGE_MS = 2 * 60_000L
private val CHICLAYO_CENTER = GeoPoint(-6.7714, -79.8409)

private fun Context.findActivity(): ComponentActivity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is ComponentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Estado y lógica del mapa (ubicación, paraderos del backend, selección manual). */
@Stable
class MapState(private val context: Context, private val scope: CoroutineScope) {

    var mapView: MapView? = null

    var status by mutableStateOf(context.getString(R.string.map_center_status))
    var stopsStatus by mutableStateOf(context.getString(R.string.map_stops_loading))
    var showRetryStops by mutableStateOf(false)
    var locationButtonText by mutableStateOf(context.getString(R.string.map_location_action))
    var locationButtonEnabled by mutableStateOf(true)
    var selectButtonText by mutableStateOf(context.getString(R.string.map_selection_start_action))
    var selectedCoordinatesText by mutableStateOf<String?>(null)

    private var locationCancellation: CancellationTokenSource? = null
    private var timeoutJob: Job? = null
    private var userMarker: Marker? = null
    private var selectedStopMarker: Marker? = null
    private var selectionOverlay: MapEventsOverlay? = null
    private val backendStopMarkers = ArrayList<Marker>()
    private var stopSelectionMode = false
    private var awaitingStopsNetworkPermission = false
    private var stopsGeneration = 0
    private var isLookingUpLastLocation = false
    private var locationDisabled = false
    private var permissionDeniedPermanently = false
    private var returningFromLocationSettings = false
    private var returningFromAppSettings = false

    var launchLocationPermission: () -> Unit = {}
    var launchNetworkPermission: () -> Unit = {}

    private fun str(id: Int) = context.getString(id)

    // ---- Paraderos del backend

    fun loadNearbyMapStops() {
        stopsStatus = str(R.string.map_stops_loading)
        showRetryStops = false
        if (!LocalNetworkAccess.isGranted(context)) {
            awaitingStopsNetworkPermission = true
            stopsStatus = str(R.string.map_stops_network_permission_requesting)
            launchNetworkPermission()
            return
        }
        awaitingStopsNetworkPermission = false
        val generation = ++stopsGeneration
        scope.launch {
            val result = RouteApiClient.getNearbyStops(
                CHICLAYO_CENTER.latitude, CHICLAYO_CENTER.longitude, STOPS_SEARCH_RADIUS_METERS
            )
            if (generation != stopsGeneration) return@launch
            when (result) {
                is ApiResult.Success -> {
                    clearBackendStopMarkers()
                    val stops = result.value
                    if (stops.isEmpty()) {
                        stopsStatus = str(R.string.map_stops_empty)
                        showRetryStops = false
                    } else {
                        stops.forEach { addBackendStopMarker(it) }
                        stopsStatus = context.getString(R.string.map_stops_loaded, stops.size)
                        showRetryStops = false
                        mapView?.invalidate()
                    }
                }
                is ApiResult.Error -> {
                    stopsStatus = str(R.string.map_stops_error)
                    showRetryStops = true
                }
            }
        }
    }

    fun onNetworkPermissionResult(granted: Boolean) {
        if (granted && awaitingStopsNetworkPermission) {
            loadNearbyMapStops()
        } else {
            awaitingStopsNetworkPermission = false
            stopsStatus = str(R.string.map_stops_network_denied)
            showRetryStops = true
        }
    }

    private fun tintedPin(colorRes: Int) =
        ContextCompat.getDrawable(context, R.drawable.ic_location_pin)?.let {
            DrawableCompat.wrap(it.mutate()).also { d ->
                DrawableCompat.setTint(d, ContextCompat.getColor(context, colorRes))
            }
        }

    private fun addBackendStopMarker(stop: NearbyStop) {
        val map = mapView ?: return
        val marker = Marker(map)
        marker.position = GeoPoint(stop.latitude, stop.longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = stop.name
        marker.snippet = if (stop.address.isEmpty()) str(R.string.map_stop_backend_marker)
        else stop.address + " · " + str(R.string.map_stop_backend_marker)
        tintedPin(R.color.accent_blue)?.let { marker.icon = it }
        map.overlays.add(marker)
        backendStopMarkers.add(marker)
    }

    private fun clearBackendStopMarkers() {
        val map = mapView ?: return
        backendStopMarkers.forEach { map.overlays.remove(it) }
        backendStopMarkers.clear()
    }

    // ---- Selección manual

    fun toggleStopSelectionMode() {
        if (stopSelectionMode) {
            cancelStopSelection()
            return
        }
        val map = mapView ?: return
        stopSelectionMode = true
        selectButtonText = str(R.string.map_selection_cancel_action)
        selectedCoordinatesText = null
        status = str(R.string.map_selection_prompt)
        val overlay = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                if (!stopSelectionMode) return false
                showSelectedStopPoint(p)
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        selectionOverlay = overlay
        map.overlays.add(overlay)
        map.invalidate()
    }

    private fun showSelectedStopPoint(point: GeoPoint) {
        val map = mapView ?: return
        if (selectedStopMarker == null) {
            val marker = Marker(map)
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            marker.title = str(R.string.map_selection_marker_title)
            marker.snippet = str(R.string.map_selection_marker_description)
            tintedPin(R.color.accent_cyan)?.let { marker.icon = it }
            map.overlays.add(marker)
            selectedStopMarker = marker
        }
        selectedStopMarker!!.position = point
        val lat = String.format(Locale.US, "%.7f", point.latitude)
        val lng = String.format(Locale.US, "%.7f", point.longitude)
        selectedCoordinatesText = context.getString(R.string.map_selection_coordinates_format, lat, lng)
        status = str(R.string.map_selection_updated)
        map.invalidate()
    }

    fun copySelectedCoordinates() {
        val marker = selectedStopMarker ?: return
        val p = marker.position
        val coordinates = String.format(Locale.US, "%.7f, %.7f", p.latitude, p.longitude)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            toast(context, str(R.string.map_selection_copy_error))
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(str(R.string.map_selection_clip_label), coordinates))
        toast(context, str(R.string.map_selection_copied))
    }

    private fun cancelStopSelection() {
        val map = mapView
        stopSelectionMode = false
        selectionOverlay?.let { map?.overlays?.remove(it) }
        selectionOverlay = null
        selectedStopMarker?.let { map?.overlays?.remove(it) }
        selectedStopMarker = null
        selectButtonText = str(R.string.map_selection_start_action)
        selectedCoordinatesText = null
        status = str(R.string.map_selection_cancelled)
        map?.invalidate()
    }

    // ---- Ubicación

    fun onLocationButtonPressed() {
        when {
            locationDisabled -> openLocationSettings()
            permissionDeniedPermanently -> openAppSettings()
            else -> showCurrentLocation()
        }
    }

    private fun hasLocationPermission() =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun showCurrentLocation() {
        if (!hasLocationPermission()) {
            status = str(R.string.map_location_permission_requesting)
            launchLocationPermission()
            return
        }
        requestCurrentLocation()
    }

    fun onLocationPermissionResult() {
        if (hasLocationPermission()) {
            permissionDeniedPermanently = false
            locationButtonText = str(R.string.map_location_action)
            requestCurrentLocation()
        } else {
            val activity = context.findActivity()
            val canAskAgain = activity != null && (
                activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            showPermissionDenied(!canAskAgain)
        }
    }

    private fun requestCurrentLocation() {
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || !manager.isLocationEnabled) {
            showLocationDisabled()
            return
        }
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) != ConnectionResult.SUCCESS) {
            locationDisabled = false
            status = str(R.string.map_location_services_unavailable)
            locationButtonEnabled = true
            return
        }

        locationDisabled = false
        permissionDeniedPermanently = false
        locationButtonEnabled = false
        status = str(R.string.map_location_searching)
        cancelPendingLocationRequest()
        val request = CancellationTokenSource()
        locationCancellation = request
        isLookingUpLastLocation = false
        timeoutJob = scope.launch {
            delay(LOCATION_TIMEOUT_MS)
            if (isCurrent(request)) tryLastKnownLocation(request)
        }

        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, request.token)
                .addOnSuccessListener { location ->
                    if (!isCurrent(request) || isLookingUpLastLocation) return@addOnSuccessListener
                    if (isValid(location)) showUserLocation(location!!) else tryLastKnownLocation(request)
                }
                .addOnFailureListener {
                    if (!isCurrent(request) || isLookingUpLastLocation) return@addOnFailureListener
                    tryLastKnownLocation(request)
                }
        } catch (e: SecurityException) {
            finishLocationRequest(request)
            showPermissionDenied(false)
        }
    }

    private fun tryLastKnownLocation(request: CancellationTokenSource) {
        if (!isCurrent(request) || isLookingUpLastLocation) return
        isLookingUpLastLocation = true
        timeoutJob?.cancel()
        // Se detiene la petición activa antes de consultar la caché para evitar carreras.
        request.cancel()
        try {
            LocationServices.getFusedLocationProviderClient(context).lastLocation
                .addOnSuccessListener { location ->
                    if (!isCurrent(request)) return@addOnSuccessListener
                    if (isRecentValid(location)) showUserLocation(location!!) else showLocationUnavailable(request)
                }
                .addOnFailureListener { showLocationUnavailable(request) }
        } catch (e: SecurityException) {
            finishLocationRequest(request)
            showPermissionDenied(false)
        }
    }

    private fun isValid(l: Location?) = l != null &&
        l.latitude.isFinite() && l.longitude.isFinite() &&
        l.latitude in -90.0..90.0 && l.longitude in -180.0..180.0

    private fun isRecentValid(l: Location?): Boolean {
        if (!isValid(l) || l!!.elapsedRealtimeNanos <= 0) return false
        val ageNanos = SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos
        return ageNanos >= 0 && ageNanos <= MAX_CACHED_LOCATION_AGE_MS * 1_000_000L
    }

    private fun isCurrent(request: CancellationTokenSource) = request === locationCancellation

    private fun showLocationUnavailable(request: CancellationTokenSource) {
        if (!isCurrent(request)) return
        finishLocationRequest(request)
        status = str(R.string.map_location_unavailable)
    }

    private fun showLocationDisabled() {
        finishLocationRequest(locationCancellation)
        locationDisabled = true
        permissionDeniedPermanently = false
        locationButtonText = str(R.string.map_location_open_settings)
        locationButtonEnabled = true
        status = str(R.string.map_location_disabled)
    }

    private fun openLocationSettings() {
        returningFromLocationSettings = true
        try {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: RuntimeException) {
            returningFromLocationSettings = false
            status = str(R.string.map_location_disabled)
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            returningFromAppSettings = true
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            returningFromAppSettings = false
            toast(context, str(R.string.map_location_app_settings_error))
        }
    }

    private fun finishLocationRequest(request: CancellationTokenSource?) {
        if (request != null && request === locationCancellation) {
            request.cancel()
            locationCancellation = null
        }
        timeoutJob?.cancel()
        locationButtonEnabled = true
    }

    private fun cancelPendingLocationRequest() {
        timeoutJob?.cancel()
        locationCancellation?.cancel()
        locationCancellation = null
    }

    private fun showUserLocation(location: Location) {
        finishLocationRequest(locationCancellation)
        locationDisabled = false
        permissionDeniedPermanently = false
        locationButtonText = str(R.string.map_location_action)
        val map = mapView ?: return
        val point = GeoPoint(location.latitude, location.longitude)
        userMarker?.let { map.overlays.remove(it) }
        val marker = Marker(map)
        marker.position = point
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = str(R.string.map_location_marker_title)
        map.overlays.add(marker)
        userMarker = marker
        map.controller.animateTo(point)
        map.invalidate()
        status = str(R.string.map_location_found)
    }

    private fun showPermissionDenied(permanently: Boolean) {
        locationDisabled = false
        permissionDeniedPermanently = permanently
        locationButtonEnabled = true
        locationButtonText = str(if (permanently) R.string.map_location_app_settings else R.string.map_location_action)
        val message = str(
            if (permanently) R.string.map_location_permission_settings else R.string.map_location_permission_denied
        )
        status = message
        toast(context, message)
    }

    fun onResume() {
        mapView?.onResume()
        if (returningFromAppSettings) {
            returningFromAppSettings = false
            if (hasLocationPermission()) {
                permissionDeniedPermanently = false
                locationButtonText = str(R.string.map_location_action)
                status = str(R.string.map_location_settings_enabled)
            } else {
                showPermissionDenied(true)
            }
        }
        if (returningFromLocationSettings) {
            returningFromLocationSettings = false
            val manager = context.getSystemService(LocationManager::class.java)
            if (manager != null && manager.isLocationEnabled) {
                locationDisabled = false
                locationButtonText = str(R.string.map_location_action)
                status = str(R.string.map_location_settings_enabled)
            } else {
                showLocationDisabled()
            }
        }
    }

    fun onPause() {
        mapView?.onPause()
    }

    fun dispose() {
        stopsGeneration++
        awaitingStopsNetworkPermission = false
        cancelPendingLocationRequest()
        backendStopMarkers.clear()
        userMarker = null
        mapView?.onDetach()
        mapView = null
    }
}

@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { MapState(context, scope) }

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
            when (event) {
                Lifecycle.Event.ON_RESUME -> state.onResume()
                Lifecycle.Event.ON_PAUSE -> state.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            state.dispose()
        }
    }

    Box(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                Configuration.getInstance().load(
                    ctx.applicationContext,
                    ctx.applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
                )
                Configuration.getInstance().userAgentValue = "RutaYa/1.0 (com.rutaya.app)"
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(14.0)
                    controller.setCenter(CHICLAYO_CENTER)
                    contentDescription = ctx.getString(R.string.map_accessibility)
                    state.mapView = this
                }
            }
        )
        LaunchedEffect(Unit) { state.loadNearbyMapStops() }

        Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
            AppCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), elevation = 6.dp) {
                Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, modifier = Modifier.height(52.dp)) {
                        Text(stringResource(R.string.map_back), color = AccentBlue, fontSize = 13.sp)
                    }
                    Text(
                        stringResource(R.string.map_title), color = TextPrimary, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            InfoCard(state.status, Modifier.padding(top = 8.dp))

            MapButton(
                state.locationButtonText, R.drawable.ic_my_location, state.locationButtonEnabled,
                Modifier.align(Alignment.End).padding(top = 8.dp)
            ) { state.onLocationButtonPressed() }
            MapButton(
                state.selectButtonText, R.drawable.ic_location_pin, true,
                Modifier.align(Alignment.End).padding(top = 6.dp)
            ) { state.toggleStopSelectionMode() }

            state.selectedCoordinatesText?.let { coords ->
                AppCard(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(12.dp), elevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            coords, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { state.copySelectedCoordinates() }, modifier = Modifier.height(44.dp)) {
                            Text(stringResource(R.string.map_selection_copy_action), color = AccentBlue, fontSize = 12.sp)
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth().padding(top = 6.dp), shape = RoundedCornerShape(12.dp), elevation = 3.dp) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(state.stopsStatus, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    if (state.showRetryStops) {
                        TextButton(onClick = { state.loadNearbyMapStops() }, modifier = Modifier.height(44.dp)) {
                            Text(stringResource(R.string.map_stops_retry), color = AccentBlue, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        Text(
            stringResource(R.string.map_osm_attribution),
            color = TextPrimary, fontSize = 11.sp,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 12.dp, bottom = 12.dp)
                .background(Color.White)
                .padding(horizontal = 8.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun InfoCard(text: String, modifier: Modifier = Modifier) {
    AppCard(modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), elevation = 3.dp) {
        Text(text, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun MapButton(text: String, icon: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DividerColor),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = PrimaryNavy)
    ) {
        androidx.compose.material3.Icon(
            painterResource(icon), null, Modifier.size(16.dp), tint = AccentCyan
        )
        androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
        Text(text, fontSize = 13.sp)
    }
}
