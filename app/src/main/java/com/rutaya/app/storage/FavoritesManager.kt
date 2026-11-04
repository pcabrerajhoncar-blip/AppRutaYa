package com.rutaya.app.storage

import android.content.Context
import com.rutaya.app.model.FavoriteRoute
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Guarda y lee las rutas favoritas (JSON dentro de SharedPreferences). */
object FavoritesManager {

    private const val PREF_NAME = "rutaya_favorites"
    private const val KEY_FAVORITES = "key_favorites_array"

    class FavoriteReadResult internal constructor(
        favorites: List<FavoriteRoute>,
        val hasMalformedJson: Boolean,
        val invalidRecordCount: Int
    ) {
        val favorites: List<FavoriteRoute> = favorites.toList()
        val hasReadProblems: Boolean get() = hasMalformedJson || invalidRecordCount > 0
    }

    private fun identity(fav: FavoriteRoute): String =
        FavoriteRoute.generateFavoriteId(fav.origin, fav.destination, fav.routeOption?.id)

    fun isFavorite(context: Context?, origin: String?, destination: String?, routeId: String?): Boolean {
        if (context == null || routeId == null) return false
        val favoriteId = FavoriteRoute.generateFavoriteId(origin, destination, routeId)
        return getFavoriteList(context).any { identity(it) == favoriteId }
    }

    fun addFavorite(context: Context?, favRoute: FavoriteRoute?): Boolean {
        if (context == null || favRoute == null || favRoute.routeOption == null) return false

        val favoriteId = identity(favRoute)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"

        return try {
            val jsonArray = JSONArray(rawJson)
            for (existing in parseFavoritesJson(rawJson).favorites) {
                if (identity(existing) == favoriteId) return true
            }
            val favoriteJson = favRoute.toJSONObject()
            favoriteJson.put("favoriteId", favoriteId)
            jsonArray.put(favoriteJson)
            prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
            true
        } catch (e: JSONException) {
            false
        } catch (e: IllegalStateException) {
            false
        }
    }

    fun removeFavorite(context: Context?, favoriteId: String?): Boolean {
        if (context == null || favoriteId.isNullOrEmpty()) return false

        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val rawJson = prefs.getString(KEY_FAVORITES, "[]") ?: "[]"

        return try {
            val jsonArray = JSONArray(rawJson)
            var matchingIndex = -1
            // Solo se elimina el elemento elegido; los demás duplicados se conservan.
            for (i in jsonArray.length() - 1 downTo 0) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                if (favoriteId == obj.optString("favoriteId", "") ||
                    favoriteId == canonicalFavoriteId(obj)
                ) {
                    matchingIndex = i
                    break
                }
            }
            if (matchingIndex < 0) return false
            jsonArray.remove(matchingIndex)
            prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
            true
        } catch (e: JSONException) {
            false
        }
    }

    fun getFavoriteList(context: Context?): List<FavoriteRoute> = readFavorites(context).favorites

    fun readFavorites(context: Context?): FavoriteReadResult {
        if (context == null) return FavoriteReadResult(emptyList(), true, 0)
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return parseFavoritesJson(prefs.getString(KEY_FAVORITES, "[]") ?: "[]")
    }

    /** Lee los datos guardados sin normalizar, deduplicar ni reescribir nada. */
    fun parseFavoritesJson(rawJson: String): FavoriteReadResult {
        val list = ArrayList<FavoriteRoute>()
        var invalidRecordCount = 0
        return try {
            val jsonArray = JSONArray(rawJson)
            for (i in jsonArray.length() - 1 downTo 0) {
                val obj = jsonArray.optJSONObject(i)
                if (obj != null && isRecognizedFavorite(obj)) {
                    var fav = FavoriteRoute.fromJSONObject(obj)
                    if (fav == null || fav.routeOption == null) {
                        invalidRecordCount++
                        continue
                    }
                    if (fav.favoriteId.isEmpty()) {
                        fav = FavoriteRoute(
                            canonicalFavoriteId(obj), fav.origin, fav.destination,
                            fav.routeOption, fav.savedTimestamp
                        )
                    }
                    list.add(fav)
                } else {
                    invalidRecordCount++
                }
            }
            FavoriteReadResult(list, false, invalidRecordCount)
        } catch (e: JSONException) {
            FavoriteReadResult(emptyList(), true, 0)
        }
    }

    private fun isRecognizedFavorite(obj: JSONObject): Boolean {
        if (!hasString(obj, "origin") || !hasString(obj, "destination")) return false
        val route = obj.optJSONObject("routeOption") ?: return false
        return hasString(route, "id") &&
            hasString(route, "routeName") &&
            hasString(route, "transportType") &&
            hasNumber(route, "timeMinutes") &&
            hasNumber(route, "cost") &&
            hasNumber(route, "walkingMeters")
    }

    private fun hasString(obj: JSONObject, key: String): Boolean {
        val value = obj.opt(key)
        return value is String && value.trim().isNotEmpty()
    }

    private fun hasNumber(obj: JSONObject, key: String): Boolean = obj.opt(key) is Number

    private fun canonicalFavoriteId(obj: JSONObject): String {
        val route = obj.optJSONObject("routeOption")
        if (route == null || !hasString(obj, "origin") || !hasString(obj, "destination") ||
            !hasString(route, "id")
        ) return ""
        return FavoriteRoute.generateFavoriteId(
            obj.optString("origin", ""), obj.optString("destination", ""), route.optString("id", "")
        )
    }
}
