package com.rutaya.app.network

import com.rutaya.app.model.Account
import com.rutaya.app.model.FavoriteItem
import com.rutaya.app.model.GeoPt
import com.rutaya.app.model.SavedPlace
import com.rutaya.app.model.ShareLink
import com.rutaya.app.model.TripRecord
import com.rutaya.app.model.TripSummary
import com.rutaya.app.model.TrustedContact
import com.rutaya.app.storage.Session
import org.json.JSONArray
import org.json.JSONObject

/**
 * Todas las llamadas de la app a la API de RutaYa que tienen que ver con la cuenta.
 * Las que dicen "con sesión" usan el token; si el servidor lo rechaza (venció), se cierra la sesión sola.
 */
object Backend {

    // ------------------------------------------------------------ utilidades

    private fun JSONObject.str(key: String): String = if (isNull(key)) "" else optString(key, "")

    private fun jsonObject(data: Any?): JSONObject = data as? JSONObject ?: throw ApiException("Respuesta inesperada del servidor.")

    private fun jsonArray(data: Any?): JSONArray = data as? JSONArray ?: throw ApiException("Respuesta inesperada del servidor.")

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
        val list = ArrayList<T>()
        for (i in 0 until length()) optJSONObject(i)?.let { list.add(transform(it)) }
        return list
    }

    private fun parseAccount(o: JSONObject) = Account(o.str("id"), o.str("correo"), o.str("nombre"))

    /** Petición con sesión: si el token ya no sirve, se cierra la sesión. */
    private suspend fun <T> authed(
        method: String, path: String, body: JSONObject? = null, parse: (Any?) -> T
    ): ApiResult<T> {
        val token = Session.token ?: return ApiResult.Error("Inicia sesión para continuar.", unauthorized = true)
        val result = HttpApi.call(method, path, body, token, parse)
        if (result is ApiResult.Error && result.unauthorized) Session.logout()
        return result
    }

    // ------------------------------------------------------------ cuenta

    private fun saveSession(data: Any?): Account {
        val o = jsonObject(data)
        val account = parseAccount(o.getJSONObject("usuario"))
        Session.login(o.getString("token"), account)
        return account
    }

    suspend fun register(name: String, email: String, password: String): ApiResult<Account> {
        val body = JSONObject().put("nombre", name).put("correo", email).put("clave", password)
        return HttpApi.call("POST", "auth/registro", body) { saveSession(it) }
    }

    suspend fun login(email: String, password: String): ApiResult<Account> {
        val body = JSONObject().put("correo", email).put("clave", password)
        return HttpApi.call("POST", "auth/login", body) { saveSession(it) }
    }

    suspend fun updateName(name: String): ApiResult<Account> =
        authed("PUT", "me", JSONObject().put("nombre", name)) {
            parseAccount(jsonObject(it)).also { account -> Session.updateAccount(account) }
        }

    suspend fun changePassword(current: String, newPassword: String): ApiResult<Unit> =
        authed("POST", "me/clave", JSONObject().put("clave_actual", current).put("clave_nueva", newPassword)) { }

    /** Elimina la cuenta y todos sus datos en el servidor. */
    suspend fun deleteAccount(): ApiResult<Unit> =
        authed("DELETE", "me") { Session.logout() }

    // ------------------------------------------------------------ viajes (nube)

    private fun parseTrip(o: JSONObject): TripRecord = TripRecord(
        o.str("id"), o.str("ruta_nombre"), o.str("tipo_transporte"), o.str("origen"), o.str("destino"),
        o.optDouble("costo", 0.0), o.optInt("minutos", 0), o.str("inicio"),
        o.str("fin").ifBlank { null }, o.str("estado")
    )

    suspend fun trips(offset: Int, limit: Int): ApiResult<List<TripRecord>> =
        authed("GET", "viajes?offset=$offset&limit=$limit") { d -> jsonArray(d).mapObjects { parseTrip(it) } }

    suspend fun saveTrip(trip: TripRecord, routeId: Int?): ApiResult<Unit> {
        val body = JSONObject()
            .put("id", trip.id)
            .put("ruta_nombre", trip.routeName)
            .put("tipo_transporte", trip.transportType)
            .put("origen", trip.origin)
            .put("destino", trip.destination)
            .put("costo", trip.cost)
            .put("minutos", trip.timeMinutes)
            .put("inicio", trip.startTime)
            .put("fin", trip.finishTime ?: "")
            .put("estado", trip.status)
        if (routeId != null) body.put("ruta_id", routeId)
        return authed("POST", "viajes", body) { }
    }

    suspend fun deleteTrip(id: String): ApiResult<Unit> = authed("DELETE", "viajes/$id") { }

    suspend fun clearTrips(): ApiResult<Unit> = authed("DELETE", "viajes") { }

    suspend fun summary(): ApiResult<TripSummary> = authed("GET", "viajes/resumen") { d ->
        val o = jsonObject(d)
        TripSummary(o.optLong("viajes", 0), o.optLong("minutos", 0), o.optDouble("gasto", 0.0))
    }

    // ------------------------------------------------------------ favoritos

    private fun parseFavorite(o: JSONObject) = FavoriteItem(
        id = o.str("id"),
        routeId = o.optInt("ruta_id"),
        name = o.str("nombre"),
        originText = o.str("origen_texto"),
        destinationText = o.str("destino_texto"),
        origin = GeoPt(o.optDouble("origen_lat"), o.optDouble("origen_lng")),
        destination = GeoPt(o.optDouble("destino_lat"), o.optDouble("destino_lng"))
    )

    suspend fun favorites(): ApiResult<List<FavoriteItem>> =
        authed("GET", "favoritos") { d -> jsonArray(d).mapObjects { parseFavorite(it) } }

    suspend fun addFavorite(
        routeId: Int, name: String, originText: String, destinationText: String, origin: GeoPt, destination: GeoPt
    ): ApiResult<FavoriteItem> {
        val body = JSONObject()
            .put("ruta_id", routeId)
            .put("nombre", name)
            .put("origen_texto", originText)
            .put("destino_texto", destinationText)
            .put("origen_lat", origin.lat).put("origen_lng", origin.lng)
            .put("destino_lat", destination.lat).put("destino_lng", destination.lng)
        return authed("POST", "favoritos", body) { parseFavorite(jsonObject(it)) }
    }

    /** Guarda como favorito el viaje que se está viendo (ruta + origen + destino). */
    suspend fun favoritePlan(plan: com.rutaya.app.model.TripPlan, originText: String, destinationText: String): ApiResult<FavoriteItem> =
        addFavorite(
            plan.routeId,
            (plan.routeName + " → " + destinationText.ifBlank { "destino" }).take(120),
            originText.take(200), destinationText.take(200), plan.origin, plan.destination
        )

    suspend fun deleteFavorite(id: String): ApiResult<Unit> = authed("DELETE", "favoritos/$id") { }

    // ------------------------------------------------------------ lugares

    private fun parsePlace(o: JSONObject) = SavedPlace(
        o.str("id"), o.str("tipo"), o.str("nombre"), o.str("direccion"),
        GeoPt(o.optDouble("latitud"), o.optDouble("longitud"))
    )

    suspend fun places(): ApiResult<List<SavedPlace>> =
        authed("GET", "lugares") { d -> jsonArray(d).mapObjects { parsePlace(it) } }

    suspend fun savePlace(type: String, name: String, address: String, point: GeoPt): ApiResult<SavedPlace> {
        val body = JSONObject().put("tipo", type).put("nombre", name).put("direccion", address)
            .put("latitud", point.lat).put("longitud", point.lng)
        return authed("POST", "lugares", body) { parsePlace(jsonObject(it)) }
    }

    suspend fun deletePlace(id: String): ApiResult<Unit> = authed("DELETE", "lugares/$id") { }

    // ------------------------------------------------------------ personas de confianza

    private fun parseContact(o: JSONObject) = TrustedContact(o.str("id"), o.str("nombre"), o.str("telefono"))

    suspend fun contacts(): ApiResult<List<TrustedContact>> =
        authed("GET", "contactos") { d -> jsonArray(d).mapObjects { parseContact(it) } }

    suspend fun addContact(name: String, phone: String): ApiResult<TrustedContact> =
        authed("POST", "contactos", JSONObject().put("nombre", name).put("telefono", phone)) {
            parseContact(jsonObject(it))
        }

    suspend fun deleteContact(id: String): ApiResult<Unit> = authed("DELETE", "contactos/$id") { }

    // ------------------------------------------------------------ compartir viaje

    suspend fun startShare(routeName: String, destination: String, point: GeoPt): ApiResult<ShareLink> {
        val body = JSONObject().put("ruta_nombre", routeName).put("destino", destination)
            .put("lat", point.lat).put("lng", point.lng)
        return authed("POST", "compartir", body) { d ->
            val o = jsonObject(d)
            val token = o.str("token")
            ShareLink(token, ApiConfig.SERVER + o.str("enlace_ruta"))
        }
    }

    suspend fun sendLocation(token: String, point: GeoPt, sos: Boolean): ApiResult<Unit> =
        authed("PUT", "compartir/$token/ubicacion", JSONObject().put("lat", point.lat).put("lng", point.lng).put("sos", sos)) { }

    suspend fun stopShare(token: String): ApiResult<Unit> = authed("DELETE", "compartir/$token") { }
}
