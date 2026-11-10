package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import com.rutaya.app.ui.theme.AccentText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.model.TripRecord
import com.rutaya.app.model.TripSummary
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.Session
import com.rutaya.app.storage.TripHistoryManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.TabHeader
import com.rutaya.app.ui.components.formatCost
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentGreen
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private fun localSummary(trips: List<TripRecord>): TripSummary {
    val done = trips.filter { it.status == "Finalizado" }
    return TripSummary(done.size.toLong(), done.sumOf { it.timeMinutes }.toLong(), done.sumOf { it.cost.coerceAtLeast(0.0) })
}

/** Pestaña "Viajes": historial y estadísticas. Con cuenta se guarda en la nube; sin cuenta, en el celular. */
@Composable
fun HistoryTab(onLogin: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = Session.account

    var trips by remember { mutableStateOf<List<TripRecord>>(emptyList()) }
    var summary by remember { mutableStateOf<TripSummary?>(null) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch {
            loading = true
            message = null
            val local = TripHistoryManager.getTripHistory(context)
            if (Session.isLoggedIn) {
                when (val r = Backend.trips(0, 50)) {
                    is ApiResult.Success -> trips = r.value
                    is ApiResult.Error -> {
                        trips = local
                        message = r.message + " Mostrando los viajes guardados en este celular."
                    }
                }
                when (val s = Backend.summary()) {
                    is ApiResult.Success -> summary = s.value
                    is ApiResult.Error -> summary = localSummary(trips)
                }
            } else {
                trips = local
                summary = localSummary(local)
            }
            loading = false
        }
    }

    LaunchedEffect(account?.id) { reload() }

    Column(Modifier.fillMaxSize().background(BgScreen)) {
        TabHeader("Mis viajes", if (account != null) "Guardados en tu cuenta" else "Guardados en este celular")
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { StatsCard(summary) }
            if (account == null) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp)) {
                            Text("Tu historial solo está en este celular.", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(
                                "Crea una cuenta para guardarlo en la nube y verlo desde otro celular.",
                                color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
                            )
                            TextButton(onClick = onLogin) { Text("Iniciar sesión", color = AccentBlue) }
                        }
                    }
                }
            }
            message?.let { item { MessageText(it) } }
            if (loading) item { Text("Cargando…", color = TextMuted, fontSize = 13.sp) }
            if (!loading && trips.isEmpty()) {
                item {
                    Text(
                        "Aún no hay viajes. Cuando termines uno aparecerá aquí.",
                        color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(trips) { trip ->
                TripCard(trip, onDelete = {
                    scope.launch {
                        if (Session.isLoggedIn) {
                            when (val r = Backend.deleteTrip(trip.id)) {
                                is ApiResult.Success -> {}
                                is ApiResult.Error -> message = r.message
                            }
                        }
                        TripHistoryManager.delete(context, trip.id)
                        reload()
                    }
                })
            }
            if (trips.isNotEmpty()) {
                item {
                    SecondaryButton("Borrar todo el historial", onClick = { confirmClear = true }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán tus viajes${if (account != null) " de la nube y de este celular" else " de este celular"}. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch {
                        if (Session.isLoggedIn) Backend.clearTrips()
                        TripHistoryManager.clear(context)
                        reload()
                    }
                }) { Text("Borrar", color = AccentOrange) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun StatsCard(summary: TripSummary?) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Stat((summary?.trips ?: 0).toString(), "viajes")
            Stat((summary?.minutes ?: 0).toString(), "minutos")
            Stat(formatCost(summary?.spent ?: 0.0), "gastado")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = AccentText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun TripCard(trip: TripRecord, onDelete: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(trip.routeName, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    trip.status, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (trip.status == "Finalizado") AccentGreen else AccentOrange
                )
            }
            Text("${trip.origin} → ${trip.destination}", color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Text(
                "${formatCost(trip.cost)} · ${trip.timeMinutes} min · ${trip.startTime}",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp)
            )
            TextButton(onClick = onDelete) { Text("Eliminar", color = AccentOrange, fontSize = 12.sp) }
        }
    }
}
