package com.rutaya.app.domain

import com.rutaya.app.model.GeoPt
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_000.0

    /** Distancia en metros entre dos coordenadas (fórmula de Haversine). */
    fun distance(a: GeoPt, b: GeoPt): Double {
        val lat1 = Math.toRadians(a.lat)
        val lat2 = Math.toRadians(b.lat)
        val dLat = lat2 - lat1
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(h)))
    }

    /** Distancia acumulada en cada vértice del trazado (el primero vale 0). */
    fun cumulative(path: List<GeoPt>): DoubleArray {
        val cum = DoubleArray(path.size)
        for (i in 1 until path.size) cum[i] = cum[i - 1] + distance(path[i - 1], path[i])
        return cum
    }

    /** Índice del vértice del trazado más cercano al punto. */
    fun nearestIndex(path: List<GeoPt>, p: GeoPt, from: Int = 0, to: Int = path.lastIndex): Int {
        var best = from
        var bestDist = Double.MAX_VALUE
        for (i in from..to) {
            val d = distance(path[i], p)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }
}

/** Recorre una polilínea: devuelve el punto que está a cierta distancia desde el inicio. */
class PathWalker(val points: List<GeoPt>) {
    private val cum = Geo.cumulative(points)
    val totalM: Double = if (cum.isEmpty()) 0.0 else cum.last()

    fun pointAt(distanceM: Double): GeoPt {
        if (points.isEmpty()) return GeoPt(0.0, 0.0)
        if (distanceM <= 0.0) return points.first()
        if (distanceM >= totalM) return points.last()
        var i = 1
        while (i < cum.size && cum[i] < distanceM) i++
        val segment = cum[i] - cum[i - 1]
        val t = if (segment <= 0.0) 0.0 else (distanceM - cum[i - 1]) / segment
        val a = points[i - 1]
        val b = points[i]
        return GeoPt(a.lat + (b.lat - a.lat) * t, a.lng + (b.lng - a.lng) * t)
    }
}
