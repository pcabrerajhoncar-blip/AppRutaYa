package com.rutaya.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.R
import com.rutaya.app.model.RouteOption
import com.rutaya.app.model.TripRecord
import com.rutaya.app.storage.TripHistoryManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.PrimaryButton
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.SecondaryButton
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.NoticeBg
import com.rutaya.app.ui.theme.NoticeStroke
import com.rutaya.app.ui.theme.NoticeText
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun now(): String = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())

@Composable
fun ActiveTripScreen(
    sessionId: String,
    origin: String,
    destination: String,
    route: RouteOption,
    onBack: () -> Unit,
    onGoHome: () -> Unit
) {
    val context = LocalContext.current
    val safeOrigin = if (origin.isBlank()) stringResource(R.string.origin_hint) else origin

    // Si este viaje ya fue finalizado antes, se muestra directamente el estado final.
    val existing = remember(sessionId) {
        if (sessionId.isNotEmpty()) TripHistoryManager.getTripRecordById(context, sessionId) else null
    }
    val alreadyFinished = existing != null && "Finalizado".equals(existing.status, ignoreCase = true)

    var isFinished by rememberSaveable { mutableStateOf(alreadyFinished) }
    var startTime by rememberSaveable {
        mutableStateOf(if (alreadyFinished && !existing?.startTime.isNullOrEmpty()) existing!!.startTime else now())
    }
    var finishTime by rememberSaveable { mutableStateOf<String?>(if (alreadyFinished) existing?.finishTime else null) }
    var showDialog by rememberSaveable { mutableStateOf(false) }

    fun finishTrip() {
        if (isFinished) return
        isFinished = true
        finishTime = now()
        val record = TripRecord.fromRouteOption(
            sessionId, route, safeOrigin, destination, startTime, finishTime, "Finalizado"
        )
        TripHistoryManager.saveCompletedTrip(context, record)
        toast(context, context.getString(R.string.toast_trip_saved), long = true)
    }

    val statusBg = if (isFinished) Color(0xFFDBEAFE) else Color(0xFFE6F4EA)
    val statusStroke = if (isFinished) Color(0xFF93C5FD) else Color(0xFFA7F3D0)
    val statusTitleColor = if (isFinished) Color(0xFF1E3A8A) else Color(0xFF065F46)
    val statusSubColor = if (isFinished) Color(0xFF1E3A8A) else Color(0xFF047857)
    val iconColor = if (isFinished) Color(0xFF1E3A8A) else Color(0xFF10B981)

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.active_trip_title),
            subtitle = route.routeName,
            onBack = onBack
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            // ---- Estado
            AppCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                containerColor = statusBg, borderColor = statusStroke
            ) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painterResource(R.drawable.ic_check_circle), null, Modifier.size(28.dp),
                        colorFilter = ColorFilter.tint(iconColor)
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            stringResource(
                                if (isFinished) R.string.active_trip_status_finished
                                else R.string.active_trip_status_in_progress
                            ),
                            color = statusTitleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            if (isFinished) {
                                finishTime?.let { String.format(Locale.getDefault(), "Finalizado a las %s", it) }
                                    ?: stringResource(R.string.active_trip_status_finished)
                            } else String.format(Locale.getDefault(), "Iniciado hoy a las %s", startTime),
                            color = statusSubColor, fontSize = 12.sp
                        )
                    }
                }
            }

            // ---- Información de la ruta
            AppCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(route.routeName, color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(route.transportType, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                    Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(DividerColor))
                    Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_my_location), null, Modifier.size(22.dp))
                        Text(
                            safeOrigin, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Box(Modifier.padding(start = 10.dp, top = 2.dp, bottom = 2.dp).width(2.dp).height(18.dp).background(DividerColor))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.ic_location_pin), null, Modifier.size(22.dp))
                        Text(
                            destination, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                    Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(DividerColor))
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                        val vehicle = if (route.vehicleTimeMinutes >= 0)
                            context.getString(R.string.active_trip_vehicle_time_available, route.vehicleTimeMinutes)
                        else context.getString(R.string.active_trip_vehicle_time_unavailable)
                        val total = if (route.totalTimeMinutes >= 0)
                            context.getString(R.string.active_trip_total_time_available, route.totalTimeMinutes)
                        else context.getString(R.string.active_trip_total_time_unavailable)
                        Text("$vehicle\n$total", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            if (route.cost >= 0) String.format(Locale.getDefault(), "Pasaje: S/. %.2f", route.cost)
                            else "Pasaje: Sin dato",
                            color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // ---- Recorrido
            AppCard(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.detail_steps_title), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    val steps = route.stepsDescription
                    Text(
                        if (!steps.isNullOrEmpty()) steps
                        else "Sigue las indicaciones del vehículo de transporte público en tu trayecto.",
                        color = TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // ---- Aviso
            AppCard(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
                shape = RoundedCornerShape(12.dp), containerColor = NoticeBg, borderColor = NoticeStroke
            ) {
                Text(stringResource(R.string.active_trip_notice), color = NoticeText, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
            }

            PrimaryButton(
                text = stringResource(if (isFinished) R.string.active_trip_btn_saved else R.string.active_trip_btn_finish),
                onClick = { if (!isFinished) showDialog = true },
                enabled = !isFinished,
                icon = R.drawable.ic_flag,
                height = 52.dp,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            SecondaryButton(
                text = stringResource(R.string.active_trip_btn_home),
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp).height(48.dp),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.active_trip_dialog_title)) },
            text = { Text(stringResource(R.string.active_trip_dialog_msg)) },
            confirmButton = {
                TextButton(onClick = { showDialog = false; finishTrip() }) {
                    Text(stringResource(R.string.dialog_yes_finish))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text(stringResource(R.string.dialog_cancel)) }
            }
        )
    }
}
