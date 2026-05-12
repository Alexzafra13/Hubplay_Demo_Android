package com.alex.hubplay.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.data.AuthState
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav
import com.alex.hubplay.ui.theme.BgBase
import java.time.Instant

/**
 * Live TV — final layout (Xiaomi TV+ / Pluto inspired):
 *
 *   ┌─────────────────────────────────────────────────────────────────────┐
 *   │  TopNav                                                             │
 *   ├─────────────────────────────────────────────────────────────────────┤
 *   │                                                                     │
 *   │                       HERO (full width, 220dp)                      │
 *   │                                                                     │
 *   ├──────────┬──────────────────────────────────────────────────────────┤
 *   │ Todos    │  EpgRow                                                  │
 *   │ ★ Fav    │  EpgRow                                                  │
 *   │ Cine     │  EpgRow                                                  │
 *   │ Deportes │  EpgRow                                                  │
 *   │ Noticias │                                                          │
 *   │ …        │                                                          │
 *   └──────────┴──────────────────────────────────────────────────────────┘
 *      220dp                            rest
 *
 * Sidebar height = the EPG grid's height (not the whole screen). Hero
 * runs the full width so the preview slot on the right has plenty of
 * room to breathe.
 *
 * D-pad traversal:
 *   - On the sidebar: ↑/↓ between categories, → jumps to the first
 *     focusable in the EPG grid (Compose's default focus search).
 *   - On the grid:   ←/→ between programmes of the same channel,
 *                    ↑/↓ between channels; ← from leftmost programme
 *                    of a row jumps back to the sidebar.
 */
@Composable
fun LiveTvScreen(
    viewModel:     LiveTvViewModel,
    authState:     AuthState,
    okHttpClient:  okhttp3.OkHttpClient,
    onPlayChannel: (String) -> Unit,
    onTabSelected: (Tab) -> Unit,
    onLogOut:      () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val now = Instant.ofEpochMilli(ui.nowEpoch)

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopNav(
                selectedTab   = Tab.LiveTv,
                onTabSelected = onTabSelected,
                onSearch      = { /* search modal — next sprint */ },
                onLogOut      = onLogOut,
            )

            when {
                ui.isLoading && ui.channels.isEmpty() -> SkeletonState()
                ui.error != null && ui.channels.isEmpty() ->
                    ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
                ui.channels.isEmpty() -> EmptyBanner(
                    title    = "No hay canales IPTV",
                    subtitle = "Añade un origen M3U desde la app web para empezar a ver TV en directo.",
                )
                else -> MainLayout(
                    ui           = ui,
                    now          = now,
                    authState    = authState,
                    okHttpClient = okHttpClient,
                    onFilter     = viewModel::setFilter,
                    onFocused    = { ch -> viewModel.setFocusedChannel(ch.id) },
                    onPlay       = { ch ->
                        viewModel.recordWatch(ch.id)
                        onPlayChannel(ch.id)
                    },
                    onToggleFav  = { ch -> viewModel.toggleFavorite(ch.id) },
                )
            }
        }
    }
}

@Composable
private fun MainLayout(
    ui:           LiveTvUiState,
    now:          Instant,
    authState:    AuthState,
    okHttpClient: okhttp3.OkHttpClient,
    onFilter:     (ChannelFilter) -> Unit,
    onFocused:    (LiveChannel) -> Unit,
    onPlay:       (LiveChannel) -> Unit,
    onToggleFav:  (LiveChannel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Hero spans the full width above everything ──────────────
        LiveHero(
            channel      = ui.heroChannel,
            nowProgram   = ui.heroChannel?.let { ui.nowProgramFor(it.id) },
            nextProgram  = ui.heroChannel?.let { ui.nextProgramFor(it.id) },
            nowInstant   = now,
            authState    = authState,
            okHttpClient = okHttpClient,
            onAutoTune   = { ch -> onPlay(ch) },
        )

        // ── Sidebar + EPG grid share the rest of the height. Both
        //    sit on the same BgBase so the page reads as one surface;
        //    the cells inside the grid are what carry the slight lift
        //    (BgCard / BgOverlay) so the eye reads them as "info
        //    cards on top of the page", not "page chrome".
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LiveTvSidebar(
                groups          = ui.groups,
                selectedFilter  = ui.filter,
                favoritesCount  = ui.favorites.size,
                onFilterChanged = onFilter,
                modifier        = Modifier.fillMaxHeight(),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(BgBase),
            ) {
                if (ui.visibleChannels.isEmpty()) {
                    EmptyFilterBanner(filter = ui.filter)
                } else {
                    // We hoist the scroll state up so the fade above
                    // can hide itself when the list is at the top —
                    // the first row should never look "cut off" on
                    // load; only the rows above the viewport should
                    // dissolve.
                    val listState = rememberLazyListState()
                    val showTopFade by remember {
                        derivedStateOf {
                            listState.firstVisibleItemIndex > 0 ||
                                listState.firstVisibleItemScrollOffset > 4
                        }
                    }
                    ChannelsList(
                        ui              = ui,
                        now             = now,
                        listState       = listState,
                        onFocused       = onFocused,
                        onPlay          = onPlay,
                        onToggleFav     = onToggleFav,
                    )
                    // Top edge fade — rows scrolling up dissolve into
                    // the BgBase above. Only drawn once the user has
                    // scrolled at least a few pixels; without that,
                    // the first row looks chopped at the top on cold
                    // start.
                    if (showTopFade) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    brush = Brush.verticalGradient(
                                        0.0f to BgBase,
                                        1.0f to Color.Transparent,
                                    ),
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelsList(
    ui:           LiveTvUiState,
    now:          Instant,
    listState:    LazyListState,
    onFocused:    (LiveChannel) -> Unit,
    onPlay:       (LiveChannel) -> Unit,
    onToggleFav:  (LiveChannel) -> Unit,
) {
    LazyColumn(
        modifier              = Modifier.fillMaxSize(),
        state                 = listState,
        contentPadding        = PaddingValues(start = 24.dp, end = 24.dp, top = 0.dp, bottom = 24.dp),
        verticalArrangement   = Arrangement.spacedBy(6.dp),
    ) {
        items(ui.visibleChannels, key = { it.id }) { channel ->
            EpgRow(
                channel          = channel,
                programs         = ui.scheduleByChannel[channel.id].orEmpty(),
                now              = now,
                isFavorite       = channel.id in ui.favorites,
                onFocused        = onFocused,
                onPlay           = onPlay,
                onToggleFavorite = onToggleFav,
            )
        }
    }
}

// ─── Loading / error / empty states ──────────────────────────────────────────

@Composable
private fun SkeletonState() {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        0.0f to Color(0xFF161C28),
                        1.0f to Color(0xFF0E121B),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(16.dp))
        Column(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement   = Arrangement.spacedBy(6.dp),
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF11151D)),
                )
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = message,
            color     = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun EmptyBanner(title: String, subtitle: String) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = title,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 18.sp,
            textAlign  = TextAlign.Center,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text      = subtitle,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyFilterBanner(filter: ChannelFilter) {
    val (title, subtitle) = when (filter) {
        is ChannelFilter.Favorites ->
            "Aún no tienes favoritos" to "Mantén pulsado OK sobre un canal para marcarlo."
        is ChannelFilter.Group ->
            "No hay canales en ${filter.name}" to ""
        else ->
            "No hay canales que mostrar" to ""
    }
    Column(
        modifier              = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = title,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 16.sp,
            textAlign  = TextAlign.Center,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text      = subtitle,
                color     = Color(0xFF8892A5),
                fontSize  = 13.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
