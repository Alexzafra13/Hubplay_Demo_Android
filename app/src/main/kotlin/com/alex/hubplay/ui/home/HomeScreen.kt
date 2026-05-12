package com.alex.hubplay.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.FocusedHero
import com.alex.hubplay.ui.home.components.HeroSection
import com.alex.hubplay.ui.home.components.HomeRail
import com.alex.hubplay.ui.home.components.LiveNowRail
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.home.components.TopNav

/**
 * Home — rich layout matching the web client.
 *
 *   [TopNav (sticky)]
 *   [Hero — auto-rotating Trending, INDEPENDENT of focus below]
 *   [Rails in the order /me/home/layout returned, hidden ones skipped]
 *
 * Hero stability matters: it doesn't react to which rail card is
 * focused. The future "card focus preview" surface lives next to the
 * card itself, not by hijacking the hero. Until that lands, the
 * focused-item flow on the ViewModel is collected but not displayed —
 * keeps the wiring ready without UX cost.
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
    val focused by viewModel.focusedItem.collectAsState()

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

            when {
                ui.isLoading && ui.data.continueWatching.isEmpty()
                                                && ui.data.trending.isEmpty() -> CenteredSpinner()
                ui.error != null                                                -> ErrorBanner(
                    message = ui.error!!,
                    onRetry = viewModel::refresh,
                )
                else                                                            -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    // The Hero band is one of two presentations:
                    //   - HeroSection (rotating spotlight) when no card
                    //     on the page is focused.
                    //   - FocusedHero (backdrop + info panel) when the
                    //     user is navigating cards — same idea as
                    //     Netflix's home preview.
                    // Crossfade swaps them in place so the band height
                    // stays constant and the rails below never jump.
                    androidx.compose.animation.Crossfade(
                        targetState   = focused,
                        animationSpec = androidx.compose.animation.core.tween(450),
                        label         = "home-hero-crossfade",
                    ) { focusedItem ->
                        if (focusedItem != null) {
                            FocusedHero(item = focusedItem)
                        } else {
                            HeroSection(
                                spotlight = ui.data.hero,
                                onPlay    = { onPlayItem(it.id, it.resumePosSec) },
                                onDetails = { onOpenItem(it.id, it.kind) },
                            )
                        }
                    }

                    // Render rails in the order the server (or default)
                    // gave us. Empty rails self-hide inside HomeRail.
                    ui.data.rails.forEach { config ->
                        RenderRail(
                            config     = config,
                            data       = ui.data,
                            onCardFocused = viewModel::onCardFocused,
                            onOpenItem = onOpenItem,
                            onPlayItem = onPlayItem,
                        )
                    }

                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }
}

/**
 * Pick the right items + style + click intent for each rail kind.
 *
 *   Continue Watching / Next Up   → Landscape stills, tap = Player (resume).
 *   Latest / Trending             → Portrait posters, tap = Detail.
 *   Live Now                      → Landscape logos, tap = Player (no detail
 *                                   page exists for a channel yet).
 *
 * `latest_in_library` rails (one per content library on the server)
 * all share the same source today — fetchLatest returns a global mix.
 * A future iteration can fetch per-libraryId.
 */
@Composable
private fun RenderRail(
    config:        HomeRailConfig,
    data:          com.alex.hubplay.data.HomeData,
    onCardFocused: (MediaItem) -> Unit,
    onOpenItem:    (String, MediaKind) -> Unit,
    onPlayItem:    (String, Long) -> Unit,
) {
    when (config.type) {
        HomeRailType.ContinueWatching -> HomeRail(
            title     = config.title,
            items     = data.continueWatching,
            style     = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick   = { onPlayItem(it.id, it.resumePosSec) },
        )
        HomeRailType.NextUp -> HomeRail(
            title     = config.title,
            items     = data.nextUp,
            style     = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick   = { onPlayItem(it.id, 0L) },
        )
        HomeRailType.Trending -> HomeRail(
            title     = config.title,
            items     = data.trending,
            style     = CardStyle.Portrait,
            onFocused = onCardFocused,
            onClick   = { onOpenItem(it.id, it.kind) },
        )
        HomeRailType.LatestInLibrary -> HomeRail(
            title     = config.title,
            // Each library has its own filtered list — keyed by the
            // rail config id so re-orders / new libraries on the
            // server's "Personalizar inicio" page don't desync the
            // mapping.
            items     = data.latestByRailId[config.id].orEmpty(),
            style     = CardStyle.Portrait,
            onFocused = onCardFocused,
            onClick   = { onOpenItem(it.id, it.kind) },
        )
        HomeRailType.LiveNow -> LiveNowRail(
            title     = config.title,
            items     = data.liveNow,
            onFocused = onCardFocused,
            onClick   = { onPlayItem(it.id, 0L) },
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
