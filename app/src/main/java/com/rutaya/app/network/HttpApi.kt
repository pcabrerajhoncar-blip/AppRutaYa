package com.rutaya.app.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import kotlin.coroutines.cancellation.CancellationException

/** Error de la API con el código HTTP (0 si no hubo respuesta). */
class ApiException(message: String, val status: Int = 0) : Exception(message)

/**
 * Cliente HTTP genérico para la API de RutaYa: envía JSON, agrega el token de sesión y entiende el formato
 * {"success":true,"data":...} / {"success":false,"error":"..."}.
 */
object HttpApi {

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    /** Ejecuta la petición y convierte el campo "data" con [parse]. Nunca lanza: devuelve [ApiResult]. */
    suspend fun <T> call(
        method: String,
        path: String,
        body: JSONObject? = null,
        token: String? = null,
        parse: (Any?) -> T
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            ApiResult.Success(parse(send(method, path, body, token)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            ApiResult.Error(e.message ?: "Ocurrió un error.", unauthorized = e.status == 401)
        } catch (e: Exception) {
            Log.e("HttpApi", "$method $path", e)
            ApiResult.Error("No se pudo leer la respuesta del servidor.")
        }
    }

    private fun send(method: String, path: String, body: JSONObject?, token: String?): Any? {
        var connection: HttpURLConnection? = null
        try {
            connection = URL(ApiConfig.API + path).openConnection(Proxy.NO_PROXY) as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val text = readBody(if (status in 200..299) connection.inputStream else connection.errorStream)
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw ApiException("El servidor devolvió una respuesta inválida (código $status).", status)
            }
            if (status !in 200..299 || !json.optBoolean("success", false)) {
                throw ApiException(json.optString("error", "").ifBlank { "El servidor respondió con el código $status." }, status)
            }
            return if (json.isNull("data")) null else json.opt("data")
        } catch (e: IOException) {
            Log.e("HttpApi", "$method $path", e)
            throw ApiException("No se pudo conectar con el servidor. Revisa tu conexión a internet.")
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
}
