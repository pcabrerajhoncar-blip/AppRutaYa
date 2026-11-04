package com.rutaya.app.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Pequeño ayudante para obtener la ubicación actual del dispositivo. */
object Gps {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Devuelve la ubicación actual o null si no se pudo obtener (sin permiso, GPS apagado, etc.). */
    suspend fun currentLocation(context: Context): Location? = suspendCancellableCoroutine { cont ->
        if (!hasPermission(context)) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val token = CancellationTokenSource()
        cont.invokeOnCancellation { token.cancel() }
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, token.token)
                .addOnSuccessListener { if (cont.isActive) cont.resume(it) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } catch (e: SecurityException) {
            if (cont.isActive) cont.resume(null)
        }
    }
}
