package com.rutaya.app.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.R
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.BrandOrange
import com.rutaya.app.ui.theme.CardSurface
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.OrangeTint
import com.rutaya.app.ui.theme.TextMuted

/** Pestañas de la barra inferior. */
enum class MainTab(val label: String, @DrawableRes val icon: Int) {
    INICIO("Inicio", R.drawable.ic_home),
    VIAJES("Viajes", R.drawable.ic_clock),
    FAVORITOS("Favoritos", R.drawable.ic_star_outline),
    SEGURIDAD("Seguridad", R.drawable.ic_shield_check),
    PERFIL("Perfil", R.drawable.ic_person)
}

/** Contenedor de las pantallas principales con la barra de navegación inferior flotante. */
@Composable
fun MainScaffold(tab: MainTab, onTab: (MainTab) -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(BgScreen)) {
        Box(Modifier.weight(1f).statusBarsPadding()) { content() }
        val shape = RoundedCornerShape(30.dp)
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp, top = 4.dp)
                .fillMaxWidth()
                .shadow(10.dp, shape)
                .clip(shape)
                .background(CardSurface)
                .border(1.dp, DividerColor, shape)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (t in MainTab.entries) {
                val selected = t == tab
                val color = if (selected) BrandOrange else TextMuted
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(if (selected) OrangeTint else CardSurface)
                        .clickable { onTab(t) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(painterResource(t.icon), t.label, Modifier.size(24.dp), tint = color)
                    Text(
                        t.label, color = color, fontSize = 11.sp, maxLines = 1,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}
