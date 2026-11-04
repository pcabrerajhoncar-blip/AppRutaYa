package com.rutaya.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.rutaya.app.R
import com.rutaya.app.model.FavoriteRoute
import com.rutaya.app.model.RouteOption
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.LocalNetworkAccess
import com.rutaya.app.network.RouteApiClient
import com.rutaya.app.storage.FavoritesManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.IconBadge
import com.rutaya.app.ui.components.LinkButton
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.formatCost
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.BusBadgeBg
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.NoticeBg
import com.rutaya.app.ui.theme.NoticeStroke
import com.rutaya.app.ui.theme.NoticeText
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch
import java.util.Collections
import java.util.Locale
import java.util.UUID

private sealed interface DetailApiState {
    data object Hidden : DetailApiState
    data object Loading : DetailApiState
    data class Error(val message: String) : DetailApiState
}

internal fun stopOrderSuffix(order: Int): String = if (order > 0) " (paradero $order)" else ""

private fun isNumericRouteId(id: String?): Boolean = id != null && id.matches(Regex("[0-9]+"))

private fun enrichLegacySegment(
    selected: RouteOption?, details: RouteApiClient.RouteDetails, origin: String, destination: String
): RouteOption? {
    if (selected == null || selected.boardingStopName != null || selected.alightingStopName != null) {
        return selected
    }
    val boarding = details.findStop(origin)
    val alighting = details.findStop(destination)
    if (boarding == null || alighting == null || alighting.order <= boarding.order ||
        boarding.minutesFromStart < 0 || alighting.minutesFromStart < 0
    ) return selected

    val vehicleMinutes = alighting.minutesFromStart - boarding.minutesFromStart
    if (vehicleMinutes <= 0) return selected

    val singleFare = if (details.fares.size == 1) details.fares[0] else -1.0
    val steps = "Aborda en ${boarding.name}${stopOrderSuffix(boarding.order)}" +
        " → desciende en ${alighting.name}${stopOrderSuffix(alighting.order)}"
    return RouteOption(
        selected.id, selected.routeName, selected.transportType,
        -1, singleFare, -1, null, steps, null,
        vehicleMinutes, -1,
        boarding.name, boarding.order, alighting.name, alighting.order,
        Collections.singletonList("LEGACY_FAVORITE_ENRICHED_FROM_API")
    )
}

@Composable
fun RouteDetailScreen(
    origin: String,
    destination: String,
    initialRoute: RouteOption,
    onBack: () -> Unit,
    onStartTrip: (route: RouteOption, sessionId: String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val safeOrigin = if (origin.isBlank()) stringResource(R.string.origin_hint) else origin

    var route by rememberSaveable { mutableStateOf(initialRoute) }
    var serverDetails by remember { mutableStateOf<RouteApiClient.RouteDetails?>(null) }
    var apiState by remember { mutableStateOf<DetailApiState>(DetailApiState.Hidden) }
    var generation by remember { mutableIntStateOf(0) }
    var favoriteTick by remember { mutableIntStateOf(0) }
    var launchingTrip by remember { mutableStateOf(false) }

    fun loadDetails() {
        val current = ++generation
        apiState = DetailApiState.Loading
        if (!LocalNetworkAccess.isGranted(context)) {
            apiState = DetailApiState.Error(context.getString(R.string.local_network_permission_required))
            return
        }
        scope.launch {
            when (val result = RouteApiClient.getRouteDetails(route.id)) {
                is ApiResult.Success -> {
                    if (current != generation) return@launch
                    apiState = DetailApiState.Hidden
                    route = enrichLegacySegment(route, result.value, safeOrigin, destination) ?: route
                    serverDetails = result.value
                    favoriteTick++
                }
                is ApiResult.Error -> {
                    if (current != generation) return@launch
                    apiState = DetailApiState.Error(result.message)
                }
            }
        }
    }

    val networkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) loadDetails()
        else apiState = DetailApiState.Error(context.getString(R.string.local_network_permission_required))
    }

    LaunchedEffect(Unit) {
        if (isNumericRouteId(route.id)) {
            if (!LocalNetworkAccess.isGranted(context)) {
                apiState = DetailApiState.Loading
                networkLauncher.launch(LocalNetworkAccess.PERMISSION)
            } else {
                loadDetails()
            }
        }
    }

    val isFavorite = remember(favoriteTick, route) {
        FavoritesManager.isFavorite(context, safeOrigin, destination, route.id)
    }

    fun toggleFavorite() {
        val favId = FavoriteRoute.generateFavoriteId(safeOrigin, destination, route.id)
        if (isFavorite) {
            if (FavoritesManager.removeFavorite(context, favId)) toast(context, context.getString(R.string.toast_fav_removed))
            else toast(context, context.getString(R.string.favorite_storage_error), long = true)
        } else {
            val fav = FavoriteRoute(favId, safeOrigin, destination, route, System.currentTimeMillis())
            if (FavoritesManager.addFavorite(context, fav)) toast(context, context.getString(R.string.toast_fav_added))
            else toast(context, context.getString(R.string.favorite_storage_error), long = true)
        }
        favoriteTick++
    }

    val details = serverDetails

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.detail_title),
            subtitle = route.routeName,
            onBack = onBack
        )
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)
        ) {
            val state = apiState
            if (state != DetailApiState.Hidden) {
                AppCard(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    shape = RoundedCornerShape(12.dp), elevation = 0.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        if (state == DetailApiState.Loading) {
                            CircularProgressIndicator(Modifier.size(32.dp), strokeWidth = 3.dp, color = PrimaryNavy)
                        }
                        Text(
                            when (state) {
                                DetailApiState.Loading -> stringResource(R.string.route_detail_loading)
                                is DetailApiState.Error -> state.message
                                else -> ""
                            },
                            color = TextSecondary, fontSize = 13.sp,
                            modifier = Modifier.padding(top = if (state == DetailApiState.Loading) 8.dp else 0.dp)
                        )
                        if (state is DetailApiState.Error) {
                            LinkButton(stringResource(R.string.btn_retry), onClick = { loadDetails() })
                        }
                    }
                }
            }

            // ---- Información principal
            AppCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                shape = RoundedCornerShape(20.dp), elevation = 4.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(R.drawable.ic_bus, BusBadgeBg, size = 48.dp, iconSize = 26.dp, shape = RoundedCornerShape(14.dp))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(route.routeName, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(route.transportType, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }

            // ---- Métricas
            AppCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                elevation = 2.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp)) {
                    MetricColumn(
                        R.drawable.ic_clock,
                        if (route.vehicleTimeMinutes >= 0)
                            String.format(Locale.getDefault(), "%d min", route.vehicleTimeMinutes) else "Sin dato",
                        stringResource(R.string.detail_time_vehicle_label),
                        if (route.totalTimeMinutes >= 0)
                            stringResource(R.string.detail_total_time_available, route.totalTimeMinutes)
                        else stringResource(R.string.detail_total_time_unavailable),
                        Modifier.weight(1f)
                    )
                    Box(Modifier.width(1.dp).height(60.dp).background(DividerColor))
                    MetricColumn(
                        R.drawable.ic_money, formatCost(route.cost),
                        stringResource(R.string.detail_cost_label), null, Modifier.weight(1f)
                    )
                    Box(Modifier.width(1.dp).height(60.dp).background(DividerColor))
                    MetricColumn(
                        R.drawable.ic_walk,
                        if (details != null) "Sin dato"
                        else if (route.walkingMeters >= 0) String.format(Locale.getDefault(), "%d m", route.walkingMeters)
                        else "Sin dato",
                        stringResource(R.string.detail_walk_label), null, Modifier.weight(1f)
                    )
                }
            }

            // ---- Recorrido y paraderos
            AppCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                elevation = 2.dp
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.detail_steps_title), color = TextPrimary,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold
                    )

                    val boarding = route.boardingStopName
                    val step1 = if (boarding.isNullOrEmpty()) {
                        if (details != null) "El backend no informó el primer paradero ni la distancia a pie."
                        else "El backend no proporciona la distancia a pie ni el paradero de abordaje."
                    } else "Aborda en $boarding${stopOrderSuffix(route.boardingStopOrder)}. Distancia a pie: Sin dato."
                    Step(R.drawable.ic_my_location, stringResource(R.string.step_1_title), step1, null)

                    val steps = route.stepsDescription
                    val step2 = details?.getStopsDescription()
                        ?: if (!steps.isNullOrEmpty()) steps else "El backend no informó los paraderos de esta ruta."
                    val frequency = if (details == null) route.departureInterval else null
                    Step(R.drawable.ic_bus, stringResource(R.string.step_2_title), step2, frequency)

                    val alighting = route.alightingStopName
                    val step3 = if (alighting.isNullOrEmpty()) {
                        if (details != null) "El backend no informó un paradero de destino."
                        else "El backend no informó el paradero de destino."
                    } else "Desciende en $alighting${stopOrderSuffix(route.alightingStopOrder)}"
                    Step(R.drawable.ic_location_pin, stringResource(R.string.step_3_title), step3, null)
                }
            }

            // ---- Aviso DEMO
            AppCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                shape = RoundedCornerShape(12.dp), containerColor = NoticeBg, borderColor = NoticeStroke
            ) {
                Text(stringResource(R.string.detail_demo_notice), color = NoticeText, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
            }

            PrimaryButton(
                text = stringResource(R.string.detail_btn_start_trip),
                onClick = {
                    if (!launchingTrip) {
                        launchingTrip = true
                        onStartTrip(route, UUID.randomUUID().toString())
                    }
                },
                icon = R.drawable.ic_play_circle,
                height = 52.dp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )

            androidx.compose.material3.OutlinedButton(
                onClick = { toggleFavorite() },
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PrimaryNavy),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = PrimaryNavy)
            ) {
                Icon(
                    painterResource(if (isFavorite) R.drawable.ic_star else R.drawable.ic_star_outline),
                    null, Modifier.size(20.dp), tint = Color(0xFFF59E0B)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(if (isFavorite) R.string.detail_btn_remove_favorite else R.string.detail_btn_favorite),
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(icon: Int, value: String, label: String, extra: String?, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Image(painterResource(icon), null, Modifier.size(22.dp))
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
        Text(label, color = TextMuted, fontSize = 11.sp)
        if (extra != null) Text(extra, color = TextSecondary, fontSize = 10.sp)
    }
}

@Composable
internal fun Step(icon: Int, title: String, description: String, extra: String?) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.Top) {
        Image(painterResource(icon), null, Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(description, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            if (!extra.isNullOrEmpty()) {
                Text(extra, color = AccentBlue, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
