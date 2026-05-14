package com.alex.hubplay.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.HeroSection
import com.alex.hubplay.ui.home.components.HomeRail
import com.alex.hubplay.ui.home.components.LiveNowRail
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav
import com.alex.hubplay.ui.theme.BgBase

/**
 * Home — Netflix-style vertical paging.
 *
 *   [TopNav (sticky)]
 *   [Hero — 420dp cinematic spotlight with auto-rotation]
 *   [Rails — natural height, snap-on-focus with 40dp peek of the
 *    prior section above; the bottom of the viewport fades to bg so
 *    the next rail's title peeks but its cards never read as a
 *    competing layout]
 *
 * The two gradient overlays inside the content Box are what make
 * the transition between sections feel like Netflix rather than
 * "rails stacked in a scroll view":
 *
 *   - Top fade (40dp, BgBase → Transparent) softens the leading
 *     edge so the prior section's content fades into the new one
 *     instead of cutting sharply.
 *   - Bottom fade (200dp, Transparent → BgBase) is the key one.
 *     With rails at natural height there's enough vertical room
 *     to show 2-3 rails at once. The fade darkens the lower part
 *     of the viewport so only titles and a sliver of the next
 *     rail's cards stay readable — the "siempre saca la sección y
 *     un poco de abajo con el título" effect the user pointed
 *     to in the Netflix screenshots.
 */
@Composable
fun HomeScreen(
    viewModel:       HomeViewModel,
    onOpenItem:      (itemId: String, kind: MediaKind) -> Unit,
    onPlayItem:      (itemId: String, resumePosSec: Long) -> Unit,
    onNavigateToTab: (Tab) -> Unit,
    onLogOut:        () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            TopNav(
                selectedTab    = Tab.Home,
                onTabSelected  = onNavigateToTab,
                onSearch       = { /* Search modal — next sprint */ },
                onLogOut       = onLogOut,
            )

            // Content Box claims the remaining height via weight(1f).
            // The fade overlays are siblings of the scrollable Column
            // so they stay anchored to the viewport edges instead of
            // scrolling with the content.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when {
                    ui.isLoading && ui.data.continueWatching.isEmpty()
                                                && ui.data.trending.isEmpty() -> CenteredSpinner()
                    ui.error != null                                                -> ErrorBanner(
                        message = ui.error!!,
                        onRetry = viewModel::refresh,
                    )
                    else -> Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        HeroSection(
                            spotlight    = ui.data.hero,
                            onPlay       = { onPlayItem(it.id, it.resumePosSec) },
                            onDetails    = { onOpenItem(it.id, it.kind) },
                            parentScroll = scrollState,
                        )

                        ui.data.rails.forEach { config ->
                            RenderRail(
                                config        = config,
                                data          = ui.data,
                                onCardFocused = viewModel::onCardFocused,
                                onOpenItem    = onOpenItem,
                                onPlayItem    = onPlayItem,
                                parentScroll  = scrollState,
                            )
                        }

                        Spacer(Modifier.height(40.dp))
                    }
                }

                // Top fade — short, softens the peek of the previous
                // section's bottom edge.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to BgBase,
                                1f to Color.Transparent,
                            ),
                        ),
                )

                // Bottom fade — tall, hides the bulk of the next
                // rail's cards while letting its title remain
                // legible. Sized so a portrait rail's title (40dp)
                // + a sliver of its first card (~40dp) read clearly
                // and the rest dissolves into bg.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                1f to BgBase,
                            ),
                        ),
                )
            }
        }
    }
}

/**
 * Pick the right items + style + click intent for each rail kind.
 *
 *   Continue Watching / Next Up   → Landscape stills, tap = Player (resume).
 *   Latest / Trending             → Portrait posters, tap = Detail.
 *   Live Now                      → Landscape logos, tap = Player.
 */
@Composable
private fun RenderRail(
    config:        HomeRailConfig,
    data:          com.alex.hubplay.data.HomeData,
    onCardFocused: (MediaItem) -> Unit,
    onOpenItem:    (String, MediaKind) -> Unit,
    onPlayItem:    (String, Long) -> Unit,
    parentScroll:  ScrollState,
) {
    when (config.type) {
        HomeRailType.ContinueWatching -> HomeRail(
            title        = config.title,
            items        = data.continueWatching,
            style        = CardStyle.Landscape,
            onFocused    = onCardFocused,
            onClick      = { onPlayItem(it.id, it.resumePosSec) },
            parentScroll = parentScroll,
        )
        HomeRailType.NextUp -> HomeRail(
            title        = config.title,
            items        = data.nextUp,
            style        = CardStyle.Landscape,
            onFocused    = onCardFocused,
            onClick      = { onPlayItem(it.id, 0L) },
            parentScroll = parentScroll,
        )
        HomeRailType.Trending -> HomeRail(
            title        = config.title,
            items        = data.trending,
            style        = CardStyle.Portrait,
            onFocused    = onCardFocused,
            onClick      = { onOpenItem(it.id, it.kind) },
            parentScroll = parentScroll,
        )
        HomeRailType.LatestInLibrary -> HomeRail(
            title        = config.title,
            items        = data.latestByRailId[config.id].orEmpty(),
            style        = CardStyle.Portrait,
            onFocused    = onCardFocused,
            onClick      = { onOpenItem(it.id, it.kind) },
            parentScroll = parentScroll,
        )
        HomeRailType.LiveNow -> LiveNowRail(
            title        = config.title,
            items        = data.liveNow,
            onFocused    = onCardFocused,
            onClick      = { onPlayItem(it.id, 0L) },
            parentScroll = parentScroll,
        )
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text  = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}
