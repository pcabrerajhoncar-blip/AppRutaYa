package com.rutaya.app.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Preferencias de la app (se guardan en el celular y la interfaz las observa). */
object AppSettings {

    private const val PREF_NAME = "rutaya_settings"
    private var prefs: SharedPreferences? = null

    /** Ya se mostró la bienvenida. */
    var onboardingDone by mutableStateOf(false)
        private set

    /** Avisar cuando falte poco para bajarte. */
    var alertEnabled by mutableStateOf(true)
        private set

    /** Cuántas paradas antes del destino avisar (1 a 3). */
    var alertStopsBefore by mutableStateOf(1)
        private set

    var vibrate by mutableStateOf(true)
        private set

    /** Distancia máxima a pie (real, con vueltas de calles) hasta un paradero: 300 a 1000 m. */
    var maxWalkM by mutableStateOf(500)
        private set

    /** El usuario ya aceptó compartir su ubicación con personas de confianza. */
    var shareConsent by mutableStateOf(false)
        private set

    /** Apariencia: 0 = como el sistema, 1 = claro, 2 = oscuro. */
    var themeMode by mutableStateOf(0)
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs = p
        onboardingDone = p.getBoolean("onboarding", false)
        alertEnabled = p.getBoolean("alert", true)
        alertStopsBefore = p.getInt("alertStops", 1)
        vibrate = p.getBoolean("vibrate", true)
        maxWalkM = p.getInt("maxWalk", 500)
        shareConsent = p.getBoolean("shareConsent", false)
        themeMode = p.getInt("themeMode", 0)
    }

    fun finishOnboarding() { onboardingDone = true; prefs?.edit()?.putBoolean("onboarding", true)?.apply() }
    fun changeAlert(value: Boolean) { alertEnabled = value; prefs?.edit()?.putBoolean("alert", value)?.apply() }
    fun changeAlertStops(value: Int) {
        alertStopsBefore = value.coerceIn(1, 3); prefs?.edit()?.putInt("alertStops", alertStopsBefore)?.apply()
    }
    fun changeVibrate(value: Boolean) { vibrate = value; prefs?.edit()?.putBoolean("vibrate", value)?.apply() }
    fun changeMaxWalk(value: Int) { maxWalkM = value; prefs?.edit()?.putInt("maxWalk", value)?.apply() }
    fun acceptShareConsent() { shareConsent = true; prefs?.edit()?.putBoolean("shareConsent", true)?.apply() }
    fun changeThemeMode(value: Int) { themeMode = value.coerceIn(0, 2); prefs?.edit()?.putInt("themeMode", themeMode)?.apply() }
}
