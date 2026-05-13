package com.alex.hubplay.ui.home

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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

/**
 * Home — section-based vertical navigation.
 *
 *   [TopNav (sticky)]
 *   [Hero — viewport-tall, cinematic, auto-rotating spotlight]
 *   [Rails — natural height, snap-on-focus with 60dp peek of the
 *    previous section above and the next section peeking below]
 *
 * Why the asymmetry: the Hero has a full-bleed backdrop and reads
 * best at viewport height (the "cogiendo toda la pantalla" the user
 * asked for on the FIRST section). Rails are short by nature (title
 * + a row of cards, ~330dp) — forcing them to viewport height
 * leaves a 600+ dp black void below the cards, which the user
 * specifically flagged as broken. Natural height + a small peek
 * offset gives the "Netflix on TV" feel: focused row prominent at
 * the top of the visible area, previous row's bottom faded above,
 * next row's title visible below.
 *
 * Each section owns its own snap behaviour: it knows its own y via
 * onGloballyPositioned and animates the shared [ScrollState] on
 * focus enter. The screen just hands down `parentScroll` and (for
 * the Hero) the viewport height.
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

            // BoxWithConstraints exposes the content-area viewport
            // (total height minus TopNav). Used only as the Hero's
            // minimum height — rails take their natural height so
            // the page can show 2-3 rails at once with peek
            // transitions.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewportHeight: Dp = maxHeight

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
                            spotlight        = ui.data.hero,
                            onPlay           = { onPlayItem(it.id, it.resumePosSec) },
                            onDetails        = { onOpenItem(it.id, it.kind) },
                            parentScroll     = scrollState,
                            sectionMinHeight = viewportHeight,
                        )

                        // Rails render in the order /me/home/layout
                        // returned. Empty rails self-hide inside
                        // HomeRail.
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
