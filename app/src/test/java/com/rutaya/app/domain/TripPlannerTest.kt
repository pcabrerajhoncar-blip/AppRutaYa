package com.rutaya.app.domain

import com.rutaya.app.model.CircuitRoute
import com.rutaya.app.model.CircuitStop
import com.rutaya.app.model.GeoPt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripPlannerTest {

    // Circuito cuadrado de ~1 km por lado con un paradero en cada esquina (orden 1..4).
    private val corners = listOf(
        GeoPt(0.0, 0.0), GeoPt(0.0, 0.009), GeoPt(-0.009, 0.009), GeoPt(-0.009, 0.0)
    )
    private val route = CircuitRoute(
        id = 1, name = "Circuito", transportType = "Combi", isCircular = true,
        lapMinutes = 20, frequencyMin = 10, price = 2.0,
        stops = corners.mapIndexed { i, p -> CircuitStop(i + 1, "P${i + 1}", "", p, i + 1, null) },
        path = corners + corners.first()
    )

    @Test
    fun picksNearestBoardingAndAlightingStops() {
        val plan = TripPlanner.plan(GeoPt(0.0001, 0.0001), GeoPt(-0.0091, 0.0089), listOf(route)).first()
        assertEquals("P1", plan.boarding.name)
        assertEquals("P3", plan.alighting.name)
        assertEquals(2, plan.stopsCount)
    }

    @Test
    fun circularRouteWrapsAroundTheLoop() {
        // De P4 a P2 hay que pasar por P1 (dar la vuelta): 2 paradas.
        val plan = TripPlanner.plan(GeoPt(-0.009, 0.0), GeoPt(0.0, 0.009), listOf(route)).first()
        assertEquals("P4", plan.boarding.name)
        assertEquals("P2", plan.alighting.name)
        assertEquals(2, plan.stopsCount)
        assertEquals(listOf("P4", "P1", "P2"), plan.rideStops.map { it.stop.name })
    }

    @Test
    fun linearRouteRejectsTheWrongDirection() {
        val linear = route.copy(isCircular = false)
        val plans = TripPlanner.plan(GeoPt(-0.009, 0.0), GeoPt(0.0, 0.0), listOf(linear))
        // P4 -> P1 no es posible en una ruta lineal; solo se aceptan paradas posteriores.
        plans.forEach { assertTrue(it.boarding.order < it.alighting.order) }
    }

    @Test
    fun farAwayPointsGiveNoPlan() {
        val plans = TripPlanner.plan(GeoPt(10.0, 10.0), GeoPt(0.0, 0.0), listOf(route))
        assertTrue(plans.isEmpty())
    }

    @Test
    fun pathWalkerInterpolatesAlongThePath() {
        val walker = PathWalker(listOf(GeoPt(0.0, 0.0), GeoPt(0.0, 0.009)))
        val mid = walker.pointAt(walker.totalM / 2)
        assertNotNull(mid)
        assertEquals(0.0045, mid.lng, 1e-6)
    }
}
