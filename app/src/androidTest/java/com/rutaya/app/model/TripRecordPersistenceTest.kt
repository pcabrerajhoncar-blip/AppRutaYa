package com.rutaya.app.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripRecordPersistenceTest {

    @Test
    fun roundTripPreservesVehicleTimeAndUnknownTotal() {
        val original = TripRecord(
            "trip-1", "Ruta DEMO", "Demo urbano", "Parque", "Campus",
            3.00, -1, 9, "inicio", "fin", "Finalizado"
        )

        val stored = original.toJSONObject()
        val restored = TripRecord.fromJSONObject(JSONObject(stored.toString()))!!

        assertEquals(-1, stored.getInt("timeMinutes"))
        assertEquals(9, stored.getInt("vehicleTimeMinutes"))
        assertEquals(-1, restored.totalTimeMinutes)
        assertEquals(9, restored.vehicleTimeMinutes)
    }

    @Test
    fun readsLegacyHistoryWithoutVehicleTimeField() {
        val legacy = JSONObject()
            .put("id", "legacy-trip")
            .put("routeName", "Ruta antigua")
            .put("transportType", "Combi")
            .put("origin", "Origen")
            .put("destination", "Destino")
            .put("cost", 2.00)
            .put("timeMinutes", 18)
            .put("startTime", "inicio")
            .put("finishTime", "fin")
            .put("status", "Finalizado")

        val restored = TripRecord.fromJSONObject(legacy)!!

        assertEquals(18, restored.timeMinutes)
        assertEquals(18, restored.totalTimeMinutes)
        assertEquals(-1, restored.vehicleTimeMinutes)
    }
}
