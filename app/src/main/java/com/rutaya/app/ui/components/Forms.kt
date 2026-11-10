package com.rutaya.app.ui.components

import androidx.compose.foundation.background
import com.rutaya.app.ui.theme.CardSurface
import com.rutaya.app.ui.theme.ButtonBg
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.ui.theme.AccentBlue
import com.rutaya.app.ui.theme.AccentGreen
import com.rutaya.app.ui.theme.AccentOrange
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextMuted
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary

/** Campo de texto con el estilo de la app. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        shape = RoundedCornerShape(12.dp),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType)
    )
}

/** Fila con título, descripción opcional y un interruptor. */
@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Opción seleccionable tipo píldora: seleccionada = tinte naranja con borde naranja. */
@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Text(
        text,
        modifier = modifier
            .background(if (selected) com.rutaya.app.ui.theme.OrangeTint else CardSurface, shape)
            .border(1.dp, if (selected) com.rutaya.app.ui.theme.BrandOrange else DividerColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        color = TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium
    )
}

/** Fila de opciones (chips) alineadas a la izquierda. */
@Composable
fun ChipRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
}

/** Pantalla/pestaña que exige cuenta: explica para qué sirve y ofrece iniciar sesión. */
@Composable
fun GuestGate(emoji: String, title: String, message: String, onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 52.sp)
        Text(
            title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp)
        )
        Text(
            message, color = TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)
        )
        PrimaryButton("Iniciar sesión o crear cuenta", onClick = onLogin)
        Text(
            "Puedes seguir usando la app sin cuenta para planificar y hacer viajes.",
            color = TextMuted, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

/** Mensaje corto de error (naranja) o de éxito (verde). Si el texto es null no muestra nada. */
@Composable
fun MessageText(text: String?, error: Boolean = true, modifier: Modifier = Modifier) {
    if (text.isNullOrBlank()) return
    Text(
        text, color = if (error) AccentOrange else AccentGreen, fontSize = 13.sp,
        modifier = modifier.padding(vertical = 6.dp)
    )
}

/** Cabecera de una pestaña principal (sin botón de volver), estilo claro. */
@Composable
fun TabHeader(title: String, subtitle: String? = null, trailing: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 14.sp)
        }
        if (trailing != null) trailing()
    }
}

/** Fila tocable de menú (Perfil, Configuración...). */
@Composable
fun MenuRow(emoji: String, title: String, subtitle: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp, modifier = Modifier.padding(end = 14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) Text(subtitle, color = TextMuted, fontSize = 12.sp)
        }
        Text("›", color = AccentBlue, fontSize = 22.sp)
    }
}
