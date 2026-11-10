package com.rutaya.app.model

import java.io.Serializable

/** Cuenta del usuario (opcional). */
data class Account(val id: String, val email: String, val name: String) : Serializable

/** Ruta favorita: la ruta más el origen y destino con los que se planificó. */
data class FavoriteItem(
    val id: String,
    val routeId: Int,
    val name: String,
    val originText: String,
    val destinationText: String,
    val origin: GeoPt,
    val destination: GeoPt
) : Serializable

/** Lugar guardado. [type] es "casa", "trabajo" u "otro". */
data class SavedPlace(
    val id: String,
    val type: String,
    val name: String,
    val address: String,
    val point: GeoPt
) : Serializable

/** Persona de confianza: solo nombre y teléfono (solo dígitos). */
data class TrustedContact(val id: String, val name: String, val phone: String) : Serializable

/** Viaje compartido: el token identifica el enlace de seguimiento. */
data class ShareLink(val token: String, val url: String) : Serializable

/** Estadísticas personales. */
data class TripSummary(val trips: Long, val minutes: Long, val spent: Double)
