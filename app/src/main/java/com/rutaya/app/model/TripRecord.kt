package com.rutaya.app.model

import android.util.Log
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable

class TripRecord(
    val id: String,
    val routeName: String,
    val transportType: String,
    val origin: String,
    val destination: String,
    val cost: Double,
    /** Se guarda bajo la clave JSON "timeMinutes" por compatibilidad con registros antiguos. */
    val timeMinutes: Int,
    /** Duración del tramo en vehículo; -1 en registros anteriores a este campo. */
    val vehicleTimeMinutes: Int,
    val startTime: String,
    var finishTime: String?,
    var status: String
) : Serializable {

    constructor(
        id: String, routeName: String, transportType: String, origin: String,
        destination: String, cost: Double, timeMinutes: Int,
        startTime: String, finishTime: String?, status: String
    ) : this(
        id, routeName, transportType, origin, destination, cost, timeMinutes, -1,
        startTime, finishTime, status
    )

    /** Duración total registrada (mismo valor histórico de [timeMinutes]). */
    val totalTimeMinutes: Int get() = timeMinutes

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put("id", id)
            json.put("routeName", routeName)
            json.put("transportType", transportType)
            json.put("origin", origin)
            json.put("destination", destination)
            json.put("cost", cost)
            json.put("timeMinutes", timeMinutes)
            json.put("vehicleTimeMinutes", vehicleTimeMinutes)
            json.put("startTime", startTime)
            json.put("finishTime", finishTime)
            json.put("status", status)
        } catch (e: JSONException) {
            Log.e("TripRecord", "Error JSON", e)
        }
        return json
    }

    companion object {
        fun fromRouteOption(
            id: String, route: RouteOption?, origin: String, destination: String,
            startTime: String, finishTime: String?, status: String
        ): TripRecord? {
            if (route == null) return null
            return TripRecord(
                id, route.routeName, route.transportType, origin, destination, route.cost,
                route.totalTimeMinutes, route.vehicleTimeMinutes, startTime, finishTime, status
            )
        }

        fun fromJSONObject(json: JSONObject?): TripRecord? {
            if (json == null) return null
            return TripRecord(
                json.optString("id", ""),
                json.optString("routeName", ""),
                json.optString("transportType", ""),
                json.optString("origin", ""),
                json.optString("destination", ""),
                json.optDouble("cost", 0.0),
                json.optInt("timeMinutes", 0),
                json.optInt("vehicleTimeMinutes", -1),
                json.optString("startTime", ""),
                json.optString("finishTime", ""),
                json.optString("status", "")
            )
        }
    }
}
