package com.rutaya.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.model.FavoriteItem
import com.rutaya.app.network.ApiResult
import com.rutaya.app.network.Backend
import com.rutaya.app.storage.Session
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.GuestGate
import com.rutaya.app.ui.components.MessageText
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.TabHeader
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

/** Pestaña "Favoritos": viajes que repites seguido. Requiere cuenta. */
@Composable
fun FavoritesTab(planState: PlanState, onLogin: () -> Unit, onPlansReady: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val account = Session.account

    var items by remember { mutableStateOf<List<FavoriteItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    fun reload() {
        if (!Session.isLoggedIn) return
        scope.launch {
            loading = true
            message = null
            when (val r = Backend.favorites()) {
                is ApiResult.Success -> items = r.value
                is ApiResult.Error -> message = r.message
            }
            loading = false
        }
    }

    LaunchedEffect(account?.id) {
        items = emptyList()
        reload()
    }

    Column(Modifier.fillMaxSize().background(BgScreen)) {
        TabHeader("Favoritos", "Tus viajes de siempre, a un toque")
        if (account == null) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                GuestGate(
                    "⭐", "Guarda tus rutas favoritas",
                    "Con una cuenta puedes marcar con una estrella los viajes que repites (casa → trabajo, por ejemplo) y volver a planificarlos al instante.",
                    onLogin
                )
            }
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            message?.let { item { MessageText(it) } }
            if (loading) item { Text("Cargando…", color = TextMuted, fontSize = 13.sp) }
            if (!loading && items.isEmpty() && message == null) {
                item {
                    Text(
                        "Aún no tienes favoritos. Al elegir una ruta o durante un viaje toca la estrella ☆ para guardarla.",
                        color = TextMuted, fontSize = 14.sp, modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            }
            items(items) { fav ->
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("⭐ ${fav.name}", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "${fav.originText.ifBlank { "Origen" }} → ${fav.destinationText.ifBlank { "Destino" }}",
                            color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp)
                        )
                        Row(Modifier.padding(top = 8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            PrimaryButton(
                                "Ir ahora", height = 42.dp, modifier = Modifier.weight(1f),
                                onClick = {
                                    scope.launch {
                                        planState.chooseDestination(fav.destination, fav.destinationText.ifBlank { "Destino favorito" })
                                        planState.query = fav.destinationText
                                        if (planState.origin == null) {
                                            planState.chooseOrigin(fav.origin, fav.originText.ifBlank { "Origen favorito" })
                                        }
                                        if (planState.searchPlans()) onPlansReady()
                                        else toast(context, planState.message ?: "No hay rutas para ese viaje.", long = true)
                                    }
                                }
                            )
                            TextButton(onClick = {
                                scope.launch {
                                    when (val r = Backend.deleteFavorite(fav.id)) {
                                        is ApiResult.Success -> reload()
                                        is ApiResult.Error -> message = r.message
                                    }
                                }
                            }) { Text("Quitar", color = AccentOrange) }
                        }
                    }
                }
            }
        }
    }
}
