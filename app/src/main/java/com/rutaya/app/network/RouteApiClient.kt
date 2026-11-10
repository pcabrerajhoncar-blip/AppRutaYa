package com.rutaya.app.network

import android.util.Log
import com.rutaya.app.model.CircuitRoute
import com.rutaya.app.model.CircuitStop
import com.rutaya.app.model.GeoPt
import com.rutaya.app.model.NearbyStop
import com.rutaya.app.model.RouteOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Resultado de una llamada a la API: éxito con datos, o error con mensaje listo para el usuario. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Error(val message: String, val unauthorized: Boolean = false) : ApiResult<Nothing>
}

/** Cliente HTTP de la API de solo lectura de RutaYa. */
object RouteApiClient {

    const val BASE_URL = ApiConfig.API

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 10_000

    private class ApiException(message: String) : Exception(message)

    // ---------------------------------------------------------------- API pública

    suspend fun searchRoutes(
        origin: String?,
        destination: String?,
        criterion: String? = "tiempo"
    ): ApiResult<List<RouteOption>> = call { searchRoutesBlocking(origin, destination, criterion) }

    suspend fun getRouteDetails(routeId: String?): ApiResult<RouteDetails> =
        call { getRouteDetailsBlocking(routeId) }

    suspend fun getNearbyStops(
        latitude: Double,
        longitude: Double,
        radiusMeters: Int
    ): ApiResult<List<NearbyStop>> = call { getNearbyStopsBlocking(latitude, longitude, radiusMeters) }

    /** Rutas con paraderos y trazado (la línea de la ruta) para planificar y seguir viajes. */
    suspend fun getCircuitRoutes(): ApiResult<List<CircuitRoute>> =
        call { parseCircuitRoutes(requestJson("rutas/trazados")) }

    /** Solo las rutas con un paradero a menos de [radiusM] metros del origen y otro del destino (filtra el servidor). */
    suspend fun getNearbyCircuitRoutes(
        origin: GeoPt,
        destination: GeoPt,
        radiusM: Int
    ): ApiResult<List<CircuitRoute>> = call {
        val path = "rutas/cercanas?origen_lat=" + String.format(Locale.US, "%.6f", origin.lat) +
            "&origen_lng=" + String.format(Locale.US, "%.6f", origin.lng) +
            "&destino_lat=" + String.format(Locale.US, "%.6f", destination.lat) +
            "&destino_lng=" + String.format(Locale.US, "%.6f", destination.lng) +
            "&radio=" + radiusM
        parseCircuitRoutes(requestJson(path))
    }

    private suspend fun <T> call(block: () -> T): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            ApiResult.Success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ApiResult.Error(toUserMessage(e))
        }
    }

    private fun toUserMessage(exception: Exception): String =
        if (exception is ApiException) {
            exception.message ?: ""
        } else {
            "No se pudo leer la respuesta de rutas. Vuelve a intentarlo."
        }

    // ---------------------------------------------------------------- Paraderos cercanos

    private fun getNearbyStopsBlocking(latitude: Double, longitude: Double, radiusMeters: Int): List<NearbyStop> {
        if (!latitude.isFinite() || latitude < -90 || latitude > 90 ||
            !longitude.isFinite() || longitude < -180 || longitude > 180 ||
            radiusMeters <= 0 || radiusMeters > 10000
        ) {
            throw ApiException("La ubicación o el radio de búsqueda no son válidos.")
        }

        val path = "paraderos/cercanos?latitud=" +
            encodeQueryParameter(String.format(Locale.US, "%.7f", latitude)) +
            "&longitud=" +
            encodeQueryParameter(String.format(Locale.US, "%.7f", longitude)) +
            "&radio_m=" + encodeQueryParameter(radiusMeters.toString())
        return parseNearbyStops(requestJson(path))
    }

    private fun parseNearbyStops(response: JSONObject): List<NearbyStop> {
        val data = response.optJSONArray("data")
            ?: throw ApiException("El servidor devolvió una lista de paraderos inválida.")

        val stops = ArrayList<NearbyStop>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index)
                ?: throw ApiException("El servidor devolvió un paradero inválido.")

            val rawId = item.opt("id")
            val rawName = item.opt("nombre")
            val rawAddress = item.opt("direccion")
            if (rawId !is Number || rawId.toInt() <= 0 ||
                rawName !is String || rawName.trim().isEmpty() ||
                (rawAddress !is String && rawAddress !== JSONObject.NULL)
            ) {
                throw ApiException("El servidor devolvió datos incompletos de un paradero.")
            }

            val latitude = requiredFiniteNumber(item, "latitud")
            val longitude = requiredFiniteNumber(item, "longitud")
            val distanceMeters = requiredFiniteNumber(item, "distancia_m")
            if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180 ||
                distanceMeters < 0
            ) {
                throw ApiException("El servidor devolvió coordenadas o distancia inválidas.")
            }

            val address = if (rawAddress === JSONObject.NULL) "" else rawAddress as String
            stops.add(NearbyStop(rawId.toInt(), rawName.trim(), address, latitude, longitude, distanceMeters))
        }
        stops.sortBy { it.distanceMeters }
        return stops
    }

    private fun requiredFiniteNumber(obj: JSONObject, key: String): Double {
        val value = obj.opt(key)
        if (value !is Number) {
            throw ApiException("El servidor devolvió datos incompletos de un paradero.")
        }
        val number = value.toDouble()
        if (!number.isFinite()) {
            throw ApiException("El servidor devolvió un valor numérico inválido.")
        }
        return number
    }

    // ---------------------------------------------------------------- Búsqueda de rutas

    private fun searchRoutesBlocking(origin: String?, destination: String?, criterion: String?): List<RouteOption> {
        val safeOrigin = origin?.trim() ?: ""
        val safeDestination = destination?.trim() ?: ""
        val safeCriterion = criterion?.trim()?.lowercase(Locale.ROOT) ?: ""
        if (safeOrigin.isEmpty() || safeDestination.isEmpty()) {
            throw ApiException("Ingresa un origen y un destino para buscar rutas.")
        }
        if (safeCriterion != "tiempo" && safeCriterion != "costo" && safeCriterion != "caminata") {
            throw ApiException("El criterio de búsqueda no es válido.")
        }

        val path = "rutas/buscar?origen=" + encodeQueryParameter(safeOrigin) +
            "&destino=" + encodeQueryParameter(safeDestination) +
            "&criterio=" + encodeQueryParameter(safeCriterion)
        return parseSearchResults(requestJson(path))
    }

    private fun encodeQueryParameter(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun parseSearchResults(response: JSONObject): List<RouteOption> {
        val data = requireDataArray(response)
        val routes = ArrayList<RouteOption>()
        for (index in 0 until data.length()) {
            val item = data.optJSONObject(index)
                ?: throw ApiException("El servidor devolvió una alternativa de ruta inválida.")

            val route = item.optJSONObject("ruta")
            val originStop = item.optJSONObject("origen")
            val destinationStop = item.optJSONObject("destino")
            val metrics = item.optJSONObject("metricas")
            if (route == null || originStop == null || destinationStop == null || metrics == null) {
                throw ApiException("El servidor devolvió datos incompletos para una ruta.")
            }

            val vehicleMinutes = optionalInt(metrics, "tiempo_en_vehiculo_min")
            val totalMinutes = optionalInt(metrics, "tiempo_total_min")
            val walkingMeters = optionalInt(metrics, "distancia_caminando_m")
            val cost = optionalDouble(metrics, "costo_soles")

            val boardingName = originStop.optString("nombre", "")
            val alightingName = destinationStop.optString("nombre", "")
            val boardingOrder = optionalInt(originStop, "orden")
            val alightingOrder = optionalInt(destinationStop, "orden")
            val steps = buildSelectedSegmentDescription(boardingName, boardingOrder, alightingName, alightingOrder)

            val warningArray = item.optJSONArray("advertencias")
            val warnings = ArrayList<String>()
            if (warningArray != null) {
                for (warningIndex in 0 until warningArray.length()) {
                    val warning = warningArray.optString(warningIndex, "")
                    if (warning.isNotEmpty()) warnings.add(warning)
                }
            }

            routes.add(
                RouteOption(
                    route.optString("id", ""),
                    route.optString("nombre", "Ruta sin nombre"),
                    route.optString("tipo_transporte", "Tipo de transporte no informado"),
                    totalMinutes,
                    cost,
                    walkingMeters,
                    null,
                    steps,
                    null,
                    vehicleMinutes,
                    totalMinutes,
                    boardingName,
                    boardingOrder,
                    alightingName,
                    alightingOrder,
                    warnings
                )
            )
        }
        return routes
    }

    private fun optionalInt(obj: JSONObject?, key: String): Int =
        if (obj == null || !obj.has(key) || obj.isNull(key)) -1 else obj.optInt(key, -1)

    private fun optionalDouble(obj: JSONObject?, key: String): Double =
        if (obj == null || !obj.has(key) || obj.isNull(key)) -1.0 else obj.optDouble(key, -1.0)

    private fun buildSelectedSegmentDescription(
        boardingName: String, boardingOrder: Int, alightingName: String, alightingOrder: Int
    ): String {
        val boarding = boardingName.ifEmpty { "Paradero de abordaje no informado" }
        val alighting = alightingName.ifEmpty { "Paradero de descenso no informado" }
        val boardingNumber = if (boardingOrder > 0) " (paradero $boardingOrder)" else ""
        val alightingNumber = if (alightingOrder > 0) " (paradero $alightingOrder)" else ""
        return "Aborda en $boarding$boardingNumber → desciende en $alighting$alightingNumber"
    }

    // ---------------------------------------------------------------- Detalle de ruta

    private fun getRouteDetailsBlocking(routeId: String?): RouteDetails {
        if (routeId == null || !routeId.matches(Regex("[0-9]+"))) {
            throw ApiException("El identificador de la ruta no es válido.")
        }

        val response = requestJson("rutas/$routeId")
        val route = response.optJSONObject("data")
            ?: throw ApiException("El servidor devolvió un detalle de ruta inválido.")

        val stops = parseStops(route.optJSONArray("paraderos"))
        val fares = parseFares(route.optJSONArray("tarifas_vigentes"))

        var timeMinutes = -1
        for (stop in stops) {
            if (stop.minutesFromStart >= 0) timeMinutes = maxOf(timeMinutes, stop.minutesFromStart)
        }

        val cost = if (fares.isEmpty()) -1.0 else fares[0]
        val steps = buildStopsDescription(stops)
        val routeOption = RouteOption(
            route.optString("id", routeId),
            route.optString("nombre", "Ruta sin nombre"),
            route.optString("tipo_transporte", "Tipo de transporte no informado"),
            timeMinutes,
            cost,
            -1,
            null,
            steps,
            null
        )
        return RouteDetails(routeOption, stops, fares)
    }

    private fun parseStops(array: JSONArray?): List<RouteStop> {
        val stops = ArrayList<RouteStop>()
        if (array == null) return stops
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            stops.add(
                RouteStop(
                    item.optString("nombre", "Paradero sin nombre"),
                    item.optString("direccion", ""),
                    item.optInt("orden", index + 1),
                    item.optInt("minutos_desde_inicio", -1)
                )
            )
        }
        return stops
    }

    private fun parseFares(array: JSONArray?): List<Double> {
        val fares = ArrayList<Double>()
        if (array == null) return fares
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item != null && !item.isNull("precio")) {
                val fare = item.optDouble("precio", -1.0)
                if (fare >= 0) fares.add(fare)
            }
        }
        return fares
    }

    private fun buildStopsDescription(stops: List<RouteStop>): String {
        if (stops.isEmpty()) return "El servidor no informó paraderos para esta ruta."
        return "Paraderos en orden: " + stops.joinToString(" → ") { it.name }
    }

    // ---------------------------------------------------------------- Rutas con trazado

    private fun parseCircuitRoutes(response: JSONObject): List<CircuitRoute> {
        val data = requireDataArray(response)
        val routes = ArrayList<CircuitRoute>()
        for (i in 0 until data.length()) {
            val item = data.optJSONObject(i) ?: continue

            val stops = ArrayList<CircuitStop>()
            val stopArray = item.optJSONArray("paraderos")
            if (stopArray != null) {
                for (j in 0 until stopArray.length()) {
                    val s = stopArray.optJSONObject(j) ?: continue
                    val lat = s.optDouble("latitud", Double.NaN)
                    val lng = s.optDouble("longitud", Double.NaN)
                    if (!lat.isFinite() || !lng.isFinite()) continue
                    stops.add(
                        CircuitStop(
                            id = s.optInt("id", j),
                            name = s.optString("nombre", "Paradero ${j + 1}"),
                            address = if (s.isNull("direccion")) "" else s.optString("direccion", ""),
                            point = GeoPt(lat, lng),
                            order = s.optInt("orden", j + 1),
                            minutesFromStart = if (s.isNull("minutos_desde_inicio")) null
                            else s.optInt("minutos_desde_inicio")
                        )
                    )
                }
            }
            stops.sortBy { it.order }

            val path = ArrayList<GeoPt>()
            val pathArray = item.optJSONArray("trazado")
            if (pathArray != null) {
                for (j in 0 until pathArray.length()) {
                    val pair = pathArray.optJSONArray(j) ?: continue
                    val lat = pair.optDouble(0, Double.NaN)
                    val lng = pair.optDouble(1, Double.NaN)
                    if (lat.isFinite() && lng.isFinite()) path.add(GeoPt(lat, lng))
                }
            }
            if (stops.size < 2 || path.size < 2) continue

            routes.add(
                CircuitRoute(
                    id = item.optInt("id"),
                    name = item.optString("nombre", "Ruta sin nombre"),
                    transportType = item.optString("tipo_transporte", "Transporte"),
                    isCircular = item.optBoolean("es_circular", false),
                    lapMinutes = if (item.isNull("minutos_vuelta")) null else item.optInt("minutos_vuelta"),
                    frequencyMin = if (item.isNull("frecuencia_min")) null else item.optInt("frecuencia_min"),
                    price = if (item.isNull("precio")) null else item.optDouble("precio"),
                    stops = stops,
                    path = path
                )
            )
        }
        return routes
    }

    // ---------------------------------------------------------------- HTTP

    private fun requestJson(path: String): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(BASE_URL + path)
            connection = url.openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = readBody(stream)
            if (status < 200 || status >= 300) {
                throw ApiException(
                    if (status == 404) "La ruta ya no está disponible en el servidor."
                    else "El servidor respondió con el código $status."
                )
            }
            try {
                val json = JSONObject(body)
                if (!json.optBoolean("success", false)) {
                    throw ApiException(json.optString("error", "").ifBlank { "El servidor no pudo completar la consulta." })
                }
                return json
            } catch (e: JSONException) {
                throw ApiException("El servidor devolvió una respuesta JSON inválida.")
            }
        } catch (e: IOException) {
            Log.e("RouteApiClient", "GET failed for $path", e)
            throw ApiException("No se pudo conectar con RutaYa. Comprueba que la API de RutaYa esté encendida y que tu celular esté en la misma red.")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readBody(stream: InputStream?): String {
        if (stream == null) return ""
        val body = StringBuilder()
        BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
            var line = reader.readLine()
            while (line != null) {
                body.append(line)
                line = reader.readLine()
            }
        }
        return body.toString()
    }

    private fun requireDataArray(response: JSONObject): JSONArray =
        response.optJSONArray("data")
            ?: throw ApiException("El servidor devolvió una lista de rutas inválida.")

    // ---------------------------------------------------------------- Tipos de apoyo

    private fun normalize(value: String?): String {
        if (value == null) return ""
        val normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return normalized.replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
    }

    private fun matchesStop(stop: RouteStop, searchValue: String): Boolean =
        normalize(stop.name) == searchValue || normalize(stop.address) == searchValue

    class RouteDetails internal constructor(
        val routeOption: RouteOption,
        val stops: List<RouteStop>,
        val fares: List<Double>
    ) {
        fun getFaresLabel(): String {
            if (fares.isEmpty()) return "Sin dato"
            return fares.joinToString(" / ") { String.format(Locale.getDefault(), "S/. %.2f", it) }
        }

        fun getStopsDescription(): String {
            val value = StringBuilder()
            for (stop in stops) {
                if (value.isNotEmpty()) value.append("\n")
                value.append(stop.order).append(". ").append(stop.name)
                if (stop.minutesFromStart >= 0) {
                    value.append(" — ").append(stop.minutesFromStart).append(" min")
                }
            }
            return if (value.isEmpty()) "El servidor no informó paraderos." else value.toString()
        }

        fun findStop(value: String?): RouteStop? {
            val normalized = normalize(value)
            return stops.firstOrNull { matchesStop(it, normalized) }
        }
    }

    class RouteStop internal constructor(
        val name: String,
        val address: String,
        val order: Int,
        val minutesFromStart: Int
    )
}
