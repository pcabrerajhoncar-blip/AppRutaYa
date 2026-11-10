package com.rutaya.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.rutaya.app.network.LocalNetworkAccess
import com.rutaya.app.storage.AppSettings
import com.rutaya.app.ui.screens.AuthScreen
import com.rutaya.app.ui.screens.FavoritesTab
import com.rutaya.app.ui.screens.HistoryTab
import com.rutaya.app.ui.screens.InfoScreen
import com.rutaya.app.ui.screens.MainScaffold
import com.rutaya.app.ui.screens.MainTab
import com.rutaya.app.ui.screens.OnboardingScreen
import com.rutaya.app.ui.screens.PlacesScreen
import com.rutaya.app.ui.screens.ProfileTab
import com.rutaya.app.ui.screens.SecurityTab
import com.rutaya.app.ui.screens.SettingsScreen
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rutaya.app.model.RouteOption
import com.rutaya.app.model.TripPlan
import com.rutaya.app.ui.screens.ActiveTripScreen
import com.rutaya.app.ui.screens.FavoritesScreen
import com.rutaya.app.ui.screens.HomeScreen
import com.rutaya.app.ui.screens.LiveTripScreen
import com.rutaya.app.ui.screens.PickPointScreen
import com.rutaya.app.ui.screens.PlanResultsScreen
import com.rutaya.app.ui.screens.PlanState
import com.rutaya.app.ui.screens.MapScreen
import com.rutaya.app.ui.screens.ResultsScreen
import com.rutaya.app.ui.screens.RouteDetailScreen
import java.util.UUID

/** Destinos de la aplicación (equivalen a las 6 Activities del proyecto Java). */
sealed interface Screen {
    data object Home : Screen
    data class Results(val origin: String, val destination: String) : Screen
    data class RouteDetail(val origin: String, val destination: String, val route: RouteOption) : Screen
    data class ActiveTrip(
        val sessionId: String, val origin: String, val destination: String, val route: RouteOption
    ) : Screen
    data object Favorites : Screen
    data object Map : Screen
    data class PickPoint(val forOrigin: Boolean) : Screen
    data object Plans : Screen
    data class LiveTrip(val plan: TripPlan, val sessionId: String) : Screen
    data object Auth : Screen
    data object Settings : Screen
    data object Places : Screen
    data class Info(val kind: String) : Screen
}

class BackStackEntry(val screen: Screen, val id: String = UUID.randomUUID().toString())

/** Guarda la pila de pantallas para que sobreviva a rotaciones. */
class AppViewModel : ViewModel() {
    val stack = mutableStateListOf(BackStackEntry(Screen.Home))

    /** Estado de la pantalla de planificación (se conserva al ir y volver del viaje). */
    val planState = PlanState()

    /** Pestaña seleccionada en la barra inferior. */
    var tab by mutableStateOf(MainTab.INICIO)

    fun push(screen: Screen) {
        stack.add(BackStackEntry(screen))
    }

    fun pop() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    fun goHome() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

@Composable
fun RutaYaApp(vm: AppViewModel = viewModel()) {
    val context = LocalContext.current

    // Android 17 exige este permiso para hablar con el servidor en tu red local.
    val networkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (!LocalNetworkAccess.isGranted(context)) networkLauncher.launch(LocalNetworkAccess.PERMISSION)
    }

    if (!AppSettings.onboardingDone) {
        OnboardingScreen(onFinish = { AppSettings.finishOnboarding() })
        return
    }

    val holder = rememberSaveableStateHolder()
    val current = vm.stack.last()

    BackHandler(enabled = vm.stack.size > 1 || vm.tab != MainTab.INICIO) {
        if (vm.stack.size > 1) vm.pop() else vm.tab = MainTab.INICIO
    }

    holder.SaveableStateProvider(current.id) {
        when (val s = current.screen) {
            Screen.Home -> MainScaffold(tab = vm.tab, onTab = { vm.tab = it }) {
                when (vm.tab) {
                    MainTab.INICIO -> HomeScreen(
                        planState = vm.planState,
                        onOpenFavorites = { vm.tab = MainTab.FAVORITOS },
                        onOpenSecurity = { vm.tab = MainTab.SEGURIDAD },
                        onOpenHistory = { vm.tab = MainTab.VIAJES },
                        onPickOnMap = { forOrigin -> vm.push(Screen.PickPoint(forOrigin)) },
                        onPlansReady = { vm.push(Screen.Plans) }
                    )
                    MainTab.VIAJES -> HistoryTab(onLogin = { vm.push(Screen.Auth) })
                    MainTab.FAVORITOS -> FavoritesTab(
                        planState = vm.planState,
                        onLogin = { vm.push(Screen.Auth) },
                        onPlansReady = { vm.push(Screen.Plans) }
                    )
                    MainTab.SEGURIDAD -> SecurityTab(onLogin = { vm.push(Screen.Auth) })
                    MainTab.PERFIL -> ProfileTab(
                        onLogin = { vm.push(Screen.Auth) },
                        onOpenPlaces = { vm.push(Screen.Places) },
                        onOpenSettings = { vm.push(Screen.Settings) },
                        onOpenInfo = { kind -> vm.push(Screen.Info(kind)) }
                    )
                }
            }
            is Screen.Results -> ResultsScreen(
                origin = s.origin, destination = s.destination,
                onBack = { vm.pop() },
                onSelectRoute = { vm.push(Screen.RouteDetail(s.origin, s.destination, it)) }
            )
            is Screen.RouteDetail -> RouteDetailScreen(
                origin = s.origin, destination = s.destination, initialRoute = s.route,
                onBack = { vm.pop() },
                onStartTrip = { route, sessionId ->
                    vm.push(Screen.ActiveTrip(sessionId, s.origin, s.destination, route))
                }
            )
            is Screen.ActiveTrip -> ActiveTripScreen(
                sessionId = s.sessionId, origin = s.origin, destination = s.destination, route = s.route,
                onBack = { vm.pop() },
                onGoHome = { vm.goHome() }
            )
            Screen.Favorites -> FavoritesScreen(
                onBack = { vm.pop() },
                onOpenDetail = { fav ->
                    fav.routeOption?.let { vm.push(Screen.RouteDetail(fav.origin, fav.destination, it)) }
                },
                onSearchNew = { vm.goHome() }
            )
            Screen.Map -> MapScreen(onBack = { vm.pop() })
            is Screen.PickPoint -> PickPointScreen(
                state = vm.planState,
                forOrigin = s.forOrigin,
                onBack = { vm.pop() },
                onDone = { vm.pop() }
            )
            Screen.Plans -> PlanResultsScreen(
                state = vm.planState,
                onBack = { vm.pop() },
                onSelect = { plan -> vm.push(Screen.LiveTrip(plan, UUID.randomUUID().toString())) },
                onLogin = { vm.push(Screen.Auth) }
            )
            is Screen.LiveTrip -> LiveTripScreen(
                plan = s.plan, sessionId = s.sessionId,
                originLabel = vm.planState.originLabel,
                destinationLabel = vm.planState.destinationLabel,
                onBack = { vm.pop() },
                onGoHome = { vm.goHome(); vm.tab = MainTab.INICIO },
                onLogin = { vm.push(Screen.Auth) }
            )
            Screen.Auth -> AuthScreen(onBack = { vm.pop() }, onDone = { vm.pop() })
            Screen.Settings -> SettingsScreen(
                onBack = { vm.pop() },
                onAccountDeleted = { vm.goHome(); vm.tab = MainTab.INICIO }
            )
            Screen.Places -> PlacesScreen(
                center = vm.planState.origin,
                onBack = { vm.pop() },
                onLogin = { vm.push(Screen.Auth) }
            )
            is Screen.Info -> InfoScreen(kind = s.kind, onBack = { vm.pop() })
        }
    }
}
