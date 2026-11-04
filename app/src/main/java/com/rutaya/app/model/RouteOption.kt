package com.rutaya.app.model

import java.io.Serializable

/**
 * Opción de ruta devuelta por la API (o guardada en favoritos).
 *
 * [timeMinutes] es el campo "legado" de duración: las rutas nuevas guardan aquí el tiempo total
 * estimado; el tiempo en vehículo va aparte en [vehicleTimeMinutes]. -1 significa "sin dato".
 */
data class RouteOption(
    val id: String,
    val routeName: String,
    val transportType: String,
    val timeMinutes: Int,
    val cost: Double,
    val walkingMeters: Int,
    val badgeTag: String?,
    val stepsDescription: String?,
    val departureInterval: String?,
    val vehicleTimeMinutes: Int = -1,
    val totalTimeMinutes: Int = timeMinutes,
    val boardingStopName: String? = null,
    val boardingStopOrder: Int = -1,
    val alightingStopName: String? = null,
    val alightingStopOrder: Int = -1,
    val warnings: List<String> = emptyList()
) : Serializable
