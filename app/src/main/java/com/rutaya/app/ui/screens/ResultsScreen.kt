package com.rutaya.app.ui.screens

import android.content.Context
import com.rutaya.app.ui.theme.AccentText
import com.rutaya.app.ui.theme.ButtonBg
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.R
import com.rutaya.app.model.RouteOption
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.LocalNetworkAccess
import com.rutaya.app.network.RouteApiClient
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.IconBadge
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.formatCost
import com.rutaya.app.ui.theme.AccentCyan
import com.rutaya.app.ui.theme.AccentGreen
import com.rutaya.app.ui.theme.AccentOrange
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
import java.util.Locale

private sealed interface ResultsState {
    data object Loading : ResultsState
    data class Loaded(val routes: List<RouteOption>) : ResultsState
    data object Empty : ResultsState
    data class Error(val message: String) : ResultsState
}

@Composable
fun ResultsScreen(
    origin: String,
    destination: String,
    onBack: () -> Unit,
    onSelectRoute: (RouteOption) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ResultsState>(ResultsState.Loading) }
    var generation by remember { mutableIntStateOf(0) }

    fun load() {
        val current = ++generation
        state = ResultsState.Loading
        if (!LocalNetworkAccess.isGranted(context)) {
            state = ResultsState.Error(context.getString(R.string.local_network_permission_required))
            return
        }
        scope.launch {
            val result = RouteApiClient.searchRoutes(origin, destination)
            if (current != generation) return@launch
            state = when (result) {
                is ApiResult.Success ->
                    if (result.value.isEmpty()) ResultsState.Empty else ResultsState.Loaded(result.value)
                is ApiResult.Error -> ResultsState.Error(result.message)
            }
        }
    }

    val networkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) load()
        else state = ResultsState.Error(context.getString(R.string.local_network_permission_required))
    }

    LaunchedEffect(origin, destination) {
        if (!LocalNetworkAccess.isGranted(context)) {
            state = ResultsState.Loading
            networkLauncher.launch(LocalNetworkAccess.PERMISSION)
        } else {
            load()
        }
    }

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.results_title),
            subtitle = String.format(Locale.getDefault(), "%s ➔ %s", origin, destination),
            onBack = onBack
        )
        AppCard(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
            shape = RoundedCornerShape(12.dp), containerColor = NoticeBg, borderColor = NoticeStroke
        ) {
            Text(
                stringResource(R.string.results_disclaimer), color = NoticeText, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp)
            )
        }

        when (val s = state) {
            ResultsState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(48.dp), color = AccentText)
            }
            is ResultsState.Loaded -> LazyColumn(
                Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
            ) {
                items(s.routes) { route -> RouteOptionCard(route, onClick = { onSelectRoute(route) }) }
            }
            ResultsState.Empty -> EmptyState(
                stringResource(R.string.empty_routes_title),
                stringResource(R.string.empty_routes_api_message),
                showRetry = false, onRetry = { load() }, onReturn = onBack
            )
            is ResultsState.Error -> EmptyState(
                stringResource(R.string.routes_connection_error_title),
                s.message, showRetry = true, onRetry = { load() }, onReturn = onBack
            )
        }
    }
}

@Composable
private fun EmptyState(
    title: String, message: String, showRetry: Boolean, onRetry: () -> Unit, onReturn: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.ic_empty_routes), null, Modifier.size(80.dp))
        Text(
            title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )
        if (showRetry) {
            NavyButton(stringResource(R.string.btn_retry), onRetry, Modifier.padding(top = 16.dp))
        }
        NavyButton(stringResource(R.string.btn_return_search), onReturn, Modifier.padding(top = 24.dp))
    }
}

@Composable
private fun NavyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ButtonBg, contentColor = Color.White),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp)
    ) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
}

/** Tarjeta de una alternativa de ruta. Se reutiliza en Resultados y Favoritos. */
@Composable
fun RouteOptionCard(route: RouteOption, onClick: () -> Unit) {
    val context = LocalContext.current
    AppCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        elevation = 3.dp, onClick = onClick
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                IconBadge(R.drawable.ic_bus, BusBadgeBg, size = 42.dp, iconSize = 24.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        route.routeName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        route.transportType, color = TextSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                val badge = route.badgeTag
                if (!badge.isNullOrEmpty()) {
                    val color = when {
                        badge.contains("rápida") -> Color(0xFF00B4D8)
                        badge.contains("económica") -> Color(0xFFFF6B35)
                        badge.contains("caminata") -> Color(0xFF10B981)
                        else -> Color(0xFF0F2027)
                    }
                    Text(
                        badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.background(color).padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Box(Modifier.padding(top = 14.dp).fillMaxWidth().height(1.dp).background(DividerColor))
            Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Metric(
                    R.drawable.ic_clock,
                    if (route.vehicleTimeMinutes >= 0)
                        context.getString(R.string.route_vehicle_time_available, route.vehicleTimeMinutes)
                    else context.getString(R.string.route_vehicle_time_unavailable),
                    Modifier.weight(1f)
                )
                Metric(R.drawable.ic_money, formatCost(route.cost), Modifier.weight(1f))
                Metric(
                    R.drawable.ic_walk,
                    if (route.walkingMeters >= 0)
                        String.format(Locale.getDefault(), "%d m a pie", route.walkingMeters)
                    else "Sin dato",
                    Modifier.weight(1f)
                )
            }
            Text(
                if (route.totalTimeMinutes >= 0)
                    context.getString(R.string.route_total_time_available, route.totalTimeMinutes)
                else context.getString(R.string.route_total_time_unavailable),
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)
            )
            val steps = route.stepsDescription
            Text(
                if (steps.isNullOrEmpty()) "Paraderos no informados" else steps,
                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp)
            )
            val interval = route.departureInterval
            if (!interval.isNullOrEmpty()) {
                Text(interval, color = TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
            }
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentText),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentText)
            ) { Text(stringResource(R.string.btn_select_route), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Metric(icon: Int, text: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(icon), null, Modifier.size(18.dp))
        Text(
            text, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}
