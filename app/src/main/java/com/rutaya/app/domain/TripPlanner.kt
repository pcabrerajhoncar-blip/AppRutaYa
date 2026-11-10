package com.rutaya.app.domain

import com.rutaya.app.model.CircuitRoute
import com.rutaya.app.model.CircuitStop
import com.rutaya.app.model.GeoPt
import com.rutaya.app.model.RideStop
import com.rutaya.app.model.TripPlan
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Calcula qué ruta tomar, dónde subir y dónde bajar. Todo se hace en el teléfono con los trazados del servidor. */
object TripPlanner {

    /** Caminata máxima por defecto (m). */
    const val MAX_WALK_M = 500.0

    /** Las calles no son línea recta: la caminata real es ~30 % mayor que la distancia en línea recta. */
    const val WALK_DETOUR = 1.3

    /** Caminar cansa más que ir sentado: se pesa 1,5 veces al elegir en qué paraderos subir y bajar. */
    private const val WALK_PENALTY = 1.5
    const val WALK_M_PER_MIN = 80.0
    private const val DEFAULT_RIDE_M_PER_MIN = 250.0
    private const val DEFAULT_FREQUENCY_MIN = 10

    /** Paradero más cercano a un punto (o null si no hay ninguno dentro de [maxM]). */
    fun nearestStop(point: GeoPt, routes: List<CircuitRoute>, maxM: Double = Double.MAX_VALUE): Pair<CircuitRoute, CircuitStop>? {
        var best: Pair<CircuitRoute, CircuitStop>? = null
        var bestDist = maxM
        for (route in routes) for (stop in route.stops) {
            val d = Geo.distance(point, stop.point)
            if (d <= bestDist) {
                bestDist = d
                best = route to stop
            }
        }
        return best
    }

    fun plan(
        origin: GeoPt,
        destination: GeoPt,
        routes: List<CircuitRoute>,
        maxWalkM: Double = MAX_WALK_M
    ): List<TripPlan> =
        routes.mapNotNull { planForRoute(origin, destination, it, maxWalkM) }.sortedBy { it.totalMinutes }

    private fun planForRoute(origin: GeoPt, destination: GeoPt, route: CircuitRoute, maxWalkM: Double): TripPlan? {
        val stops = route.stops.sortedBy { it.order }
        val path = route.path
        if (stops.size < 2 || path.size < 2) return null

        val n = stops.size
        val cum = Geo.cumulative(path)
        val loopLength = cum.last() + if (route.isCircular) Geo.distance(path.last(), path.first()) else 0.0
        if (loopLength <= 0.0) return null

        val stopVertex = IntArray(n) { Geo.nearestIndex(path, stops[it].point) }
        val walkTo = DoubleArray(n) { Geo.distance(origin, stops[it].point) }
        val walkFrom = DoubleArray(n) { Geo.distance(destination, stops[it].point) }

        var bestB = -1
        var bestA = -1
        var bestCost = Double.MAX_VALUE
        var bestRideM = 0.0
        for (b in 0 until n) {
            // la distancia a pie real (con las vueltas de las calles) no debe pasar del máximo elegido
            if (walkTo[b] * WALK_DETOUR > maxWalkM) continue
            for (a in 0 until n) {
                if (a == b || walkFrom[a] * WALK_DETOUR > maxWalkM) continue
                val rideM = rideDistance(cum, stopVertex, b, a, loopLength, route.isCircular) ?: continue
                // no vale la pena una ruta donde caminas más de lo que viajas en la combi
                if ((walkTo[b] + walkFrom[a]) * WALK_DETOUR > rideM) continue
                val walkMin = (walkTo[b] + walkFrom[a]) * WALK_DETOUR / WALK_M_PER_MIN
                val cost = walkMin * WALK_PENALTY + rideMinutes(route, rideM, loopLength, stops, b, a)
                if (cost < bestCost) {
                    bestCost = cost
                    bestB = b
                    bestA = a
                    bestRideM = rideM
                }
            }
        }
        if (bestB < 0) return null

        val ridePath = buildRidePath(path, stopVertex[bestB], stopVertex[bestA], stops[bestB], stops[bestA], route.isCircular)
        val rideCum = Geo.cumulative(ridePath)
        val count = if (route.isCircular) (bestA - bestB + n) % n else bestA - bestB
        val rideStops = ArrayList<RideStop>()
        for (k in 0..count) {
            val stop = stops[(bestB + k) % n]
            val along = when (k) {
                0 -> 0.0
                count -> rideCum.last()
                else -> rideCum[Geo.nearestIndex(ridePath, stop.point)]
            }
            rideStops.add(RideStop(stop, along))
        }

        val rideMin = rideMinutes(route, bestRideM, loopLength, stops, bestB, bestA).roundToInt().coerceAtLeast(1)
        // si llegar caminando directo es igual de rápido (o más), no tiene sentido proponer la combi
        val directWalkMin = Geo.distance(origin, destination) * WALK_DETOUR / WALK_M_PER_MIN
        val planMin = ceil((walkTo[bestB] + walkFrom[bestA]) * WALK_DETOUR / WALK_M_PER_MIN) +
            (route.frequencyMin ?: DEFAULT_FREQUENCY_MIN) / 2 + rideMin
        // (solo en trayectos cortos: de ~12 min caminando o menos)
        if (directWalkMin <= 12.0 && planMin >= directWalkMin) return null
        return TripPlan(
            routeId = route.id,
            routeName = route.name,
            transportType = route.transportType,
            price = route.price,
            frequencyMin = route.frequencyMin,
            origin = origin,
            destination = destination,
            boarding = stops[bestB],
            alighting = stops[bestA],
            walkToStopM = walkTo[bestB],
            walkFromStopM = walkFrom[bestA],
            ridePath = ridePath,
            rideStops = rideStops,
            rideDistanceM = rideCum.last(),
            stopsCount = count,
            walkMinutes = ceil((walkTo[bestB] + walkFrom[bestA]) * WALK_DETOUR / WALK_M_PER_MIN).toInt(),
            waitMinutes = (route.frequencyMin ?: DEFAULT_FREQUENCY_MIN) / 2,
            rideMinutes = rideMin
        )
    }

    /** Metros recorridos en la combi de la parada [b] a la parada [a]; null si la ruta no permite ese sentido. */
    private fun rideDistance(
        cum: DoubleArray, stopVertex: IntArray, b: Int, a: Int, loopLength: Double, circular: Boolean
    ): Double? {
        val direct = cum[stopVertex[a]] - cum[stopVertex[b]]
        return when {
            a > b -> max(direct, 0.0)
            circular -> direct + loopLength
            else -> null
        }
    }

    private fun rideMinutes(
        route: CircuitRoute, rideM: Double, loopLength: Double, stops: List<CircuitStop>, b: Int, a: Int
    ): Double {
        val lap = route.lapMinutes
        if (lap != null && lap > 0) return rideM / loopLength * lap
        val mb = stops[b].minutesFromStart
        val ma = stops[a].minutesFromStart
        if (mb != null && ma != null && ma > mb) return (ma - mb).toDouble()
        return rideM / DEFAULT_RIDE_M_PER_MIN
    }

    private fun buildRidePath(
        path: List<GeoPt>, ib: Int, ia: Int, boarding: CircuitStop, alighting: CircuitStop, circular: Boolean
    ): List<GeoPt> {
        val result = ArrayList<GeoPt>()
        result.add(boarding.point)
        if (ia > ib) {
            for (i in ib..ia) result.add(path[i])
        } else if (circular) {
            for (i in ib until path.size) result.add(path[i])
            for (i in 0..ia) result.add(path[i])
        }
        result.add(alighting.point)
        return result
    }
}
