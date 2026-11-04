package com.rutaya.app.model

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.Serializable
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

class FavoriteRoute(
    val favoriteId: String,
    val origin: String,
    val destination: String,
    val routeOption: RouteOption?,
    val savedTimestamp: Long
) : Serializable {

    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        try {
            json.put("favoriteId", favoriteId)
            json.put("origin", origin)
            json.put("destination", destination)
            json.put("savedTimestamp", savedTimestamp)

            if (routeOption != null) {
                val routeJson = JSONObject()
                routeJson.put("id", routeOption.id)
                routeJson.put("routeName", routeOption.routeName)
                routeJson.put("transportType", routeOption.transportType)
                routeJson.put("timeMinutes", routeOption.timeMinutes)
                routeJson.put("cost", routeOption.cost)
                routeJson.put("walkingMeters", routeOption.walkingMeters)
                routeJson.put("badgeTag", routeOption.badgeTag)
                routeJson.put("stepsDescription", routeOption.stepsDescription)
                routeJson.put("departureInterval", routeOption.departureInterval)
                routeJson.put("vehicleTimeMinutes", routeOption.vehicleTimeMinutes)
                routeJson.put("totalTimeMinutes", routeOption.totalTimeMinutes)
                routeJson.put("boardingStopName", routeOption.boardingStopName)
                routeJson.put("boardingStopOrder", routeOption.boardingStopOrder)
                routeJson.put("alightingStopName", routeOption.alightingStopName)
                routeJson.put("alightingStopOrder", routeOption.alightingStopOrder)
                val warnings = JSONArray()
                for (warning in routeOption.warnings) warnings.put(warning)
                routeJson.put("warnings", warnings)

                json.put("routeOption", routeJson)
            }
        } catch (e: JSONException) {
            throw IllegalStateException("No se pudo serializar el favorito", e)
        }
        return json
    }

    companion object {
        fun generateFavoriteId(origin: String?, destination: String?, routeId: String?): String {
            val cleanOrigin = normalize(origin)
            val cleanDestination = normalize(destination)
            val cleanRouteId = normalize(routeId)
            val identity = cleanOrigin.length.toString() + ":" + cleanOrigin +
                cleanDestination.length + ":" + cleanDestination +
                cleanRouteId.length + ":" + cleanRouteId
            return try {
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.toByteArray(Charsets.UTF_8))
                val id = StringBuilder("route_")
                for (value in digest) {
                    id.append(String.format(Locale.ROOT, "%02x", value.toInt() and 0xff))
                }
                id.toString()
            } catch (e: NoSuchAlgorithmException) {
                // SHA-256 es obligatorio en Android; se conserva un respaldo determinista.
                "route_$identity"
            }
        }

        private fun normalize(value: String?): String =
            value?.trim()?.replace(Regex("\\s+"), " ")?.lowercase(Locale.ROOT) ?: ""

        /** Devuelve null si la clave no existe o su valor es JSON null. */
        private fun JSONObject.optStringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key)

        fun fromJSONObject(json: JSONObject?): FavoriteRoute? {
            if (json == null) return null

            val favId = json.optString("favoriteId", "")
            val origin = json.optString("origin", "")
            val destination = json.optString("destination", "")
            val timestamp = json.optLong("savedTimestamp", 0)

            var option: RouteOption? = null
            val routeJson = json.optJSONObject("routeOption")
            if (routeJson != null) {
                val id = routeJson.optString("id", "")
                val routeName = routeJson.optString("routeName", "")
                val transportType = routeJson.optString("transportType", "")
                val timeMinutes = routeJson.optInt("timeMinutes", 0)
                val cost = routeJson.optDouble("cost", 0.0)
                val walkingMeters = routeJson.optInt("walkingMeters", 0)
                val badgeTag = routeJson.optStringOrNull("badgeTag")
                val stepsDescription = routeJson.optString("stepsDescription", "")
                val departureInterval = routeJson.optString("departureInterval", "")

                option = if (routeJson.has("vehicleTimeMinutes") || routeJson.has("totalTimeMinutes")) {
                    val warnings = ArrayList<String>()
                    val warningArray = routeJson.optJSONArray("warnings")
                    if (warningArray != null) {
                        for (i in 0 until warningArray.length()) {
                            val warning = warningArray.optString(i, "")
                            if (warning.isNotEmpty()) warnings.add(warning)
                        }
                    }
                    RouteOption(
                        id, routeName, transportType, timeMinutes, cost, walkingMeters,
                        badgeTag, stepsDescription, departureInterval,
                        routeJson.optInt("vehicleTimeMinutes", -1),
                        routeJson.optInt("totalTimeMinutes", timeMinutes),
                        routeJson.optStringOrNull("boardingStopName"),
                        routeJson.optInt("boardingStopOrder", -1),
                        routeJson.optStringOrNull("alightingStopName"),
                        routeJson.optInt("alightingStopOrder", -1),
                        warnings
                    )
                } else {
                    // Favoritos guardados antes de existir las métricas por tramo.
                    RouteOption(
                        id, routeName, transportType, timeMinutes, cost,
                        walkingMeters, badgeTag, stepsDescription, departureInterval
                    )
                }
            }

            return FavoriteRoute(favId, origin, destination, option, timestamp)
        }
    }
}
