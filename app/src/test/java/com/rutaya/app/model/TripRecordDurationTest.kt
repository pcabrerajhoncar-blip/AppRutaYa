package com.rutaya.app.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TripRecordDurationTest {

    @Test
    fun newTripRecordKeepsVehicleAndUnknownTotalSeparate() {
        val route = RouteOption(
            "1", "Ruta DEMO", "Demo urbano",
            -1, 2.50, -1, null, "Paradero A → Paradero B", null,
            9, -1, "Paradero A", 1, "Paradero B", 2, emptyList()
        )
        val record = TripRecord.fromRouteOption(
            "trip-1", route, "Paradero A", "Paradero B", "inicio", "fin", "Finalizado"
        )!!

        assertEquals(9, record.vehicleTimeMinutes)
        assertEquals(-1, record.totalTimeMinutes)
        assertEquals(-1, record.timeMinutes)
    }

    @Test
    fun legacyTripRecordKeepsItsStoredDurationAndMarksVehicleUnknown() {
        val record = TripRecord(
            "legacy-trip", "Ruta antigua", "Combi", "Origen", "Destino",
            2.00, 18, "inicio", "fin", "Finalizado"
        )

        assertEquals(18, record.timeMinutes)
        assertEquals(18, record.totalTimeMinutes)
        assertEquals(-1, record.vehicleTimeMinutes)
    }
}
