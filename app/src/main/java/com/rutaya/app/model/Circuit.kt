package com.rutaya.app.model

import java.io.Serializable

/** Coordenada geográfica simple (sin depender de osmdroid). */
data class GeoPt(val lat: Double, val lng: Double) : Serializable

data class CircuitStop(
    val id: Int,
    val name: String,
    val address: String,
    val point: GeoPt,
    val order: Int,
    val minutesFromStart: Int?
) : Serializable

/** Ruta con trazado (la "línea roja") y sus paraderos, tal como la entrega /api/rutas/trazados. */
data class CircuitRoute(
    val id: Int,
    val name: String,
    val transportType: String,
    val isCircular: Boolean,
    val lapMinutes: Int?,
    val frequencyMin: Int?,
    val price: Double?,
    val stops: List<CircuitStop>,
    val path: List<GeoPt>
) : Serializable

/** Paraderos que se recorren durante el viaje, con la distancia acumulada sobre [TripPlan.ridePath]. */
data class RideStop(val stop: CircuitStop, val distanceAlongM: Double) : Serializable

/** Plan de viaje: caminar al paradero, subir a la combi, bajar y caminar al destino. */
data class TripPlan(
    val routeId: Int,
    val routeName: String,
    val transportType: String,
    val price: Double?,
    val frequencyMin: Int?,
    val origin: GeoPt,
    val destination: GeoPt,
    val boarding: CircuitStop,
    val alighting: CircuitStop,
    val walkToStopM: Double,
    val walkFromStopM: Double,
    val ridePath: List<GeoPt>,
    val rideStops: List<RideStop>,
    val rideDistanceM: Double,
    val stopsCount: Int,
    val walkMinutes: Int,
    val waitMinutes: Int,
    val rideMinutes: Int
) : Serializable {
    val totalMinutes: Int get() = walkMinutes + waitMinutes + rideMinutes
}
