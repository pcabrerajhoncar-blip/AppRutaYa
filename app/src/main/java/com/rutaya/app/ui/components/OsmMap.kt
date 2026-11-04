package com.rutaya.app.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rutaya.app.R
import com.rutaya.app.model.GeoPt
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

enum class PinKind { STOP, BOARDING, ALIGHTING, USER, ORIGIN, DESTINATION }

data class MapLine(
    val points: List<GeoPt>,
    val color: Int,
    val width: Float = 8f,
    val dashed: Boolean = false
)

data class MapPin(val point: GeoPt, val kind: PinKind, val title: String? = null)

private val CHICLAYO = GeoPoint(-6.7714, -79.8409)

/**
 * Mapa OpenStreetMap que se redibuja a partir de listas de líneas y marcadores.
 * [fitKey] cambia cuando se quiere reencuadrar el mapa sobre [fitPoints].
 * Si [followPoint] no es null, el mapa se centra en ese punto.
 */
@Composable
fun OsmMap(
    modifier: Modifier = Modifier,
    lines: List<MapLine>,
    pins: List<MapPin>,
    fitPoints: List<GeoPt>? = null,
    fitKey: Any? = null,
    followPoint: GeoPt? = null,
    onTap: ((GeoPt) -> Unit)? = null
) {
    val holder = remember { arrayOfNulls<MapView>(1) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> holder[0]?.onResume()
                Lifecycle.Event.ON_PAUSE -> holder[0]?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            holder[0]?.onDetach()
            holder[0] = null
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            Configuration.getInstance().load(
                ctx.applicationContext,
                ctx.applicationContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
            )
            Configuration.getInstance().userAgentValue = "RutaYa/1.0 (com.rutaya.app)"
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(14.0)
                controller.setCenter(CHICLAYO)
                holder[0] = this
            }
        },
        update = { map ->
            render(map, lines, pins, onTap)
            if (followPoint != null) {
                map.controller.animateTo(GeoPoint(followPoint.lat, followPoint.lng))
            } else if (fitKey != null && map.tag != fitKey) {
                map.tag = fitKey
                if (!fitPoints.isNullOrEmpty()) fit(map, fitPoints)
            }
        }
    )
}

private fun fit(map: MapView, points: List<GeoPt>) {
    val geo = points.map { GeoPoint(it.lat, it.lng) }
    if (geo.size == 1) {
        map.controller.setZoom(17.0)
        map.controller.setCenter(geo[0])
        return
    }
    val box = BoundingBox.fromGeoPoints(geo)
    if (map.width > 0 && map.height > 0) {
        map.zoomToBoundingBox(box, true, 120)
    } else {
        map.addOnFirstLayoutListener { _, _, _, _, _ -> map.zoomToBoundingBox(box, false, 120) }
    }
}

private fun render(map: MapView, lines: List<MapLine>, pins: List<MapPin>, onTap: ((GeoPt) -> Unit)?) {
    map.overlays.clear()

    if (onTap != null) {
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                onTap(GeoPt(p.latitude, p.longitude))
                return true
            }

            override fun longPressHelper(p: GeoPoint): Boolean = false
        }))
    }

    for (line in lines) {
        if (line.points.size < 2) continue
        val polyline = Polyline()
        polyline.setPoints(line.points.map { GeoPoint(it.lat, it.lng) })
        polyline.outlinePaint.color = line.color
        polyline.outlinePaint.strokeWidth = line.width
        polyline.outlinePaint.strokeCap = Paint.Cap.ROUND
        if (line.dashed) polyline.outlinePaint.pathEffect = DashPathEffect(floatArrayOf(22f, 16f), 0f)
        map.overlays.add(polyline)
    }

    val ctx = map.context
    for (pin in pins) {
        val marker = Marker(map)
        marker.position = GeoPoint(pin.point.lat, pin.point.lng)
        marker.icon = pinIcon(ctx, pin.kind)
        val centered = pin.kind != PinKind.DESTINATION
        marker.setAnchor(Marker.ANCHOR_CENTER, if (centered) Marker.ANCHOR_CENTER else Marker.ANCHOR_BOTTOM)
        if (pin.title != null) marker.title = pin.title
        map.overlays.add(marker)
    }
    map.invalidate()
}

private fun pinIcon(ctx: Context, kind: PinKind): Drawable = when (kind) {
    PinKind.STOP -> circleIcon(ctx, 0xFFF59E0B.toInt(), 26f, "P")
    PinKind.BOARDING -> circleIcon(ctx, 0xFF16A34A.toInt(), 34f, "P")
    PinKind.ALIGHTING -> circleIcon(ctx, 0xFFDC2626.toInt(), 34f, "P")
    PinKind.USER -> userIcon(ctx)
    PinKind.ORIGIN -> userIcon(ctx)
    PinKind.DESTINATION -> tintedPin(ctx, 0xFF16A34A.toInt(), 44f)
}

private fun circleIcon(ctx: Context, color: Int, sizeDp: Float, label: String): Drawable {
    val d = ctx.resources.displayMetrics.density
    val px = (sizeDp * d).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
    paint.color = color
    canvas.drawCircle(px / 2f, px / 2f, px / 2f - 2 * d, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = px * 0.55f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    canvas.drawText(label, px / 2f, px / 2f - (paint.descent() + paint.ascent()) / 2f, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

private fun userIcon(ctx: Context): Drawable {
    val d = ctx.resources.displayMetrics.density
    val px = (34 * d).toInt()
    val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = 0x332563EB
    canvas.drawCircle(px / 2f, px / 2f, px / 2f, paint)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(px / 2f, px / 2f, 10 * d, paint)
    paint.color = 0xFF2563EB.toInt()
    canvas.drawCircle(px / 2f, px / 2f, 7.5f * d, paint)
    return BitmapDrawable(ctx.resources, bmp)
}

private fun tintedPin(ctx: Context, color: Int, sizeDp: Float): Drawable {
    val px = (sizeDp * ctx.resources.displayMetrics.density).toInt()
    val base = ContextCompat.getDrawable(ctx, R.drawable.ic_location_pin)!!.mutate()
    val wrapped = DrawableCompat.wrap(base)
    DrawableCompat.setTint(wrapped, color)
    return BitmapDrawable(ctx.resources, wrapped.toBitmap(px, px))
}
