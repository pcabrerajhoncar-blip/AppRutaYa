package com.rutaya.app.network

/**
 * Dirección del servidor RutaYa.
 * - Probando en tu red (celular y PC en el mismo Wi-Fi): la IP de tu PC con el puerto 8080.
 * - Cuando la API esté en la nube: cambia SOLO esta línea por "https://tu-servicio.onrender.com".
 */
object ApiConfig {
    const val SERVER = "http://192.168.1.13:8080"
    const val API = "$SERVER/api/"
}
