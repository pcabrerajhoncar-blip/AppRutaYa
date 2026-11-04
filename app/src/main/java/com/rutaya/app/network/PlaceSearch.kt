package com.rutaya.app.network

import android.util.Log
import com.rutaya.app.model.GeoPt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException

/** Lugar encontrado por el buscador (nombre legible + coordenada). */
data class Place(val name: String, val point: GeoPt)

/** Búsqueda de lugares con Nominatim (OpenStreetMap). Necesita internet. */
object PlaceSearch {

    /** Busca [query] dando prioridad a lo cercano a [near] (unos 15 km a la redonda). */
    suspend fun search(query: String, near: GeoPt?): ApiResult<List<Place>> = withContext(Dispatchers.IO) {
        val text = query.trim()
        if (text.length < 3) return@withContext ApiResult.Error("Escribe al menos 3 letras para buscar.")
        try {
            val sb = StringBuilder("https://nominatim.openstreetmap.org/search?format=jsonv2&limit=6&countrycodes=pe")
            sb.append("&q=").append(URLEncoder.encode(text, "UTF-8"))
            if (near != null) {
                val d = 0.15
                sb.append(String.format(Locale.US, "&viewbox=%.5f,%.5f,%.5f,%.5f",
                    near.lng - d, near.lat + d, near.lng + d, near.lat - d))
            }
            val conn = URL(sb.toString()).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                conn.setRequestProperty("User-Agent", "RutaYa/1.0 (com.rutaya.app; proyecto academico)")
                conn.setRequestProperty("Accept", "application/json")
                if (conn.responseCode !in 200..299) {
                    return@withContext ApiResult.Error("El buscador de lugares no respondió (código ${conn.responseCode}).")
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val array = JSONArray(body)
                val places = ArrayList<Place>()
                for (i in 0 until array.length()) {
                    val o = array.optJSONObject(i) ?: continue
                    val lat = o.optString("lat").toDoubleOrNull() ?: continue
                    val lng = o.optString("lon").toDoubleOrNull() ?: continue
                    val name = o.optString("display_name", "Lugar sin nombre")
                    places.add(Place(name, GeoPt(lat, lng)))
                }
                ApiResult.Success(places)
            } finally {
                conn.disconnect()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("PlaceSearch", "search failed", e)
            ApiResult.Error("No se pudo buscar el lugar. Revisa tu conexión a internet o elígelo en el mapa.")
        }
    }
}
