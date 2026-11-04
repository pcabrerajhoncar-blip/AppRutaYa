package com.rutaya.app.model

/** Paradero geolocalizado devuelto por la API de paraderos cercanos. */
data class NearbyStop(
    val id: Int,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val distanceMeters: Double
)
