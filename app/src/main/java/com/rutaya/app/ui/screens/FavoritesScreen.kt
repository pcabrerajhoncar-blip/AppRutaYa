package com.rutaya.app.ui.screens

import androidx.compose.foundation.Image
import com.rutaya.app.ui.theme.AccentText
import com.rutaya.app.ui.theme.ButtonBg
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rutaya.app.R
import com.rutaya.app.model.FavoriteRoute
import com.rutaya.app.storage.FavoritesManager
import com.rutaya.app.ui.components.AppCard
import com.rutaya.app.ui.components.IconBadge
import com.rutaya.app.ui.components.ScreenHeader
import com.rutaya.app.ui.components.formatCost
import com.rutaya.app.ui.components.toast
import com.rutaya.app.ui.theme.BgScreen
import com.rutaya.app.ui.theme.DangerBg
import com.rutaya.app.ui.theme.DividerColor
import com.rutaya.app.ui.theme.NoticeBg
import com.rutaya.app.ui.theme.PrimaryNavy
import com.rutaya.app.ui.theme.TextPrimary
import com.rutaya.app.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpenDetail: (FavoriteRoute) -> Unit,
    onSearchNew: () -> Unit
) {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val result = remember(tick) { FavoritesManager.readFavorites(context) }
    val favorites = result.favorites

    Column(Modifier.fillMaxSize().background(BgScreen).systemBarsPadding()) {
        ScreenHeader(
            title = stringResource(R.string.favorites_title),
            subtitle = stringResource(
                if (result.hasReadProblems) R.string.favorites_read_warning_subtitle
                else R.string.feature_favorites_desc
            ),
            onBack = onBack
        )

        if (favorites.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(painterResource(R.drawable.ic_star), null, Modifier.size(80.dp))
                Text(
                    stringResource(
                        if (result.hasReadProblems) R.string.favorites_read_error_title
                        else R.string.empty_favorites_title
                    ),
                    color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)
                )
                Text(
                    stringResource(
                        if (result.hasReadProblems) R.string.favorites_read_error_message
                        else R.string.empty_favorites_message
                    ),
                    color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
                Button(
                    onClick = onSearchNew,
                    modifier = Modifier.padding(top = 24.dp).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ButtonBg, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) { Text(stringResource(R.string.btn_search_from_favorites), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(favorites) { fav ->
                    FavoriteCard(
                        fav,
                        onOpen = { onOpenDetail(fav) },
                        onRemove = {
                            if (FavoritesManager.removeFavorite(context, fav.favoriteId)) {
                                toast(context, context.getString(R.string.toast_fav_removed))
                            } else {
                                toast(context, context.getString(R.string.favorite_storage_error), long = true)
                            }
                            tick++
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteCard(fav: FavoriteRoute, onOpen: () -> Unit, onRemove: () -> Unit) {
    val route = fav.routeOption
    AppCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp), elevation = 3.dp, onClick = onOpen) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(R.drawable.ic_star, NoticeBg, size = 42.dp, iconSize = 24.dp, shape = RoundedCornerShape(12.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        route?.routeName ?: "", color = TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        String.format(Locale.getDefault(), "%s ➔ %s", fav.origin, fav.destination),
                        color = TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(DangerBg).clickable(onClick = onRemove),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painterResource(R.drawable.ic_delete), "Quitar de favoritos", Modifier.size(20.dp))
                }
            }
            Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(1.dp).background(DividerColor))
            if (route != null) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        if (route.timeMinutes >= 0) String.format(Locale.getDefault(), "%d min", route.timeMinutes) else "Sin dato",
                        color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatCost(route.cost),
                        color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                    Text(
                        if (route.walkingMeters >= 0) String.format(Locale.getDefault(), "%d m a pie", route.walkingMeters) else "Sin dato",
                        color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                    )
                }
            }
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(44.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, AccentText),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentText)
            ) { Text(stringResource(R.string.btn_select_route), fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
