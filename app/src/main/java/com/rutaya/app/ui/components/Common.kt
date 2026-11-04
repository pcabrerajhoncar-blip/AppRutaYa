package com.rutaya.app.ui.components

import android.content.Context
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.R
import com.rutaya.app.ui.theme.AccentCyan
import com.rutaya.app.ui.theme.CardSurface
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.HeaderEnd
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.PrimaryNavyLight
import com.rutaya.app.ui.theme.TextPrimary
import java.util.Locale

val HeaderBrush: Brush = Brush.verticalGradient(listOf(PrimaryNavy, PrimaryNavyLight, HeaderEnd))

fun toast(context: Context, message: String, long: Boolean = false) {
    Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
}

fun formatCost(cost: Double): String =
    if (cost >= 0) String.format(Locale.getDefault(), "S/. %.2f", cost) else "Sin dato"

/** Tarjeta blanca con borde fino, como las MaterialCardView del diseño original. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = CardSurface,
    borderColor: Color = DividerColor,
    elevation: Dp = 2.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, borderColor)
    val cardElevation = CardDefaults.cardElevation(defaultElevation = elevation)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, border = border,
            elevation = cardElevation, content = content)
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border,
            elevation = cardElevation, content = content)
    }
}

/** Círculo/cuadro de color con un ícono al centro (insignias de accesos rápidos, etc.). */
@Composable
fun IconBadge(
    @DrawableRes icon: Int,
    background: Color,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(size).clip(shape).background(background),
        contentAlignment = Alignment.Center
    ) {
        Image(painterResource(icon), contentDescription, Modifier.size(iconSize))
    }
}

/** Cabecera azul marino con degradado, botón de volver, título y subtítulo. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HeaderBrush)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Image(painterResource(R.drawable.ic_arrow_back), "Volver", Modifier.size(24.dp))
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) {
                Text(subtitle, color = Color(0xFFE2E8F0), fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 54.dp,
    @DrawableRes icon: Int? = null,
    containerColor: Color = PrimaryNavy
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor, contentColor = Color.White,
            disabledContainerColor = Color(0xFF94A3B8), disabledContentColor = Color.White
        )
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(painterResource(icon), null, Modifier.size(20.dp), tint = AccentCyan)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
    fontSize: Int = 14,
    shape: Shape = RoundedCornerShape(12.dp)
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = shape,
        border = BorderStroke(1.dp, DividerColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryNavy)
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(painterResource(icon), null, Modifier.size(16.dp), tint = AccentCyan)
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        }
        Text(text, fontSize = fontSize.sp)
    }
}

@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = com.rutaya.app.ui.theme.AccentBlue, fontSize = 12.sp)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, modifier = modifier, color = TextPrimary, fontSize = 18.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
fun SpacerV(height: Dp) = androidx.compose.foundation.layout.Spacer(Modifier.size(height))

val spacedBy8 = Arrangement.spacedBy(8.dp)
