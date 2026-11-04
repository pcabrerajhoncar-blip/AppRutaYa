package com.rutaya.app.network

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Control del permiso de "red local" que Android 17 exige para llegar al backend en tu red. */
object LocalNetworkAccess {

    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

    /** true si no hace falta pedirlo (Android anterior a la API 37) o ya fue concedido. */
    fun isGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < 37 ||
            context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
}
