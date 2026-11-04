package com.rutaya.app.storage

import android.content.Context
import com.rutaya.app.model.TripRecord
import org.json.JSONArray
import org.json.JSONException

/** Historial de viajes finalizados (JSON dentro de SharedPreferences). */
object TripHistoryManager {

    private const val PREF_NAME = "rutaya_trip_history"
    private const val KEY_TRIPS = "key_trips_array"

    private fun raw(context: Context): String =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_TRIPS, "[]") ?: "[]"

    fun saveCompletedTrip(context: Context?, trip: TripRecord?) {
        if (context == null || trip == null) return
        try {
            val jsonArray = JSONArray(raw(context))
            var updated = false
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                if (trip.id == obj.optString("id")) {
                    jsonArray.put(index, trip.toJSONObject())
                    updated = true
                    break
                }
            }
            if (!updated) jsonArray.put(trip.toJSONObject())
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_TRIPS, jsonArray.toString()).apply()
        } catch (e: JSONException) {
            // Se ignora igual que en la versión original.
        }
    }

    fun getTripRecordById(context: Context?, tripId: String?): TripRecord? {
        if (context == null || tripId.isNullOrEmpty()) return null
        try {
            val jsonArray = JSONArray(raw(context))
            for (index in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(index)
                if (tripId == obj.optString("id")) return TripRecord.fromJSONObject(obj)
            }
        } catch (e: JSONException) {
            // Se ignora igual que en la versión original.
        }
        return null
    }

    /** Del más reciente al más antiguo. */
    fun getTripHistory(context: Context?): List<TripRecord> {
        val list = ArrayList<TripRecord>()
        if (context == null) return list
        try {
            val jsonArray = JSONArray(raw(context))
            for (idx in jsonArray.length() - 1 downTo 0) {
                TripRecord.fromJSONObject(jsonArray.getJSONObject(idx))?.let { list.add(it) }
            }
        } catch (e: JSONException) {
            // Se ignora igual que en la versión original.
        }
        return list
    }
}
