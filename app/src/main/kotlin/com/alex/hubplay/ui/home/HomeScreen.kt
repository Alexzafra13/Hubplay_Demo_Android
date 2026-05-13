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
 * Home — Netflix-style vertical paging across sections.
 *
 *   [TopNav (sticky)]
 *   [Hero section — fills the viewport, auto-rotating spotlight]
 *   [Rail section per /me/home/layout — also viewport-tall]
 *
 * Each section's outer container reserves `sectionMinHeight` so that
 * focusing into a section snaps the parent vertical scroll so the
 * section's top sits just under TopNav. That's what makes "estoy en
 * Tendencias y solo veo Tendencias" work: the previous Hero stays
 * off-screen above, the next rail stays off-screen below, and the
 * focused rail occupies the whole content area.
 *
 * Each section owns the snap behaviour internally (it knows its own y
 * via onGloballyPositioned and animates `parentScroll` on focus). The
 * screen just hands down the shared [ScrollState] and the section
 * height — no per-rail registration up here.
 *
 * The previous Hero/FocusedHero Crossfade was removed: with snap-
 * scrolling the Hero is off-screen whenever the user is on a rail, so
 * the FocusedHero never reads visually AND its lack of focusable
 * descendants made D-pad UP from a rail skip past the Hero entirely
 * (focus jumped to the top nav). Card preview can come back later as
 * a side surface that doesn't replace the Hero's focus targets.
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

            // BoxWithConstraints lives OUTSIDE the verticalScroll so
            // maxHeight is the content-area viewport (total height
            // minus TopNav). The inner scrollable Column inherits
            // this as `sectionHeight` and hands it to every section,
            // which uses it as a minimum height so one section
            // exactly fills the screen.
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val sectionHeight: Dp = maxHeight

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
                            sectionMinHeight = sectionHeight,
                        )

                        // Render rails in the order the server (or
                        // default) gave us. Empty rails self-hide
                        // inside HomeRail.
                        ui.data.rails.forEach { config ->
                            RenderRail(
                                config           = config,
                                data             = ui.data,
                                onCardFocused    = viewModel::onCardFocused,
                                onOpenItem       = onOpenItem,
                                onPlayItem       = onPlayItem,
                                parentScroll     = scrollState,
                                sectionMinHeight = sectionHeight,
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
 *   Live Now                      → Landscape logos, tap = Player (no detail
 *                                   page exists for a channel yet).
 *
 * `latest_in_library` rails (one per content library on the server)
 * all share the same source today — fetchLatest returns a global mix.
 * A future iteration can fetch per-libraryId.
 */
@Composable
private fun RenderRail(
    config:           HomeRailConfig,
    data:             com.alex.hubplay.data.HomeData,
    onCardFocused:    (MediaItem) -> Unit,
    onOpenItem:       (String, MediaKind) -> Unit,
    onPlayItem:       (String, Long) -> Unit,
    parentScroll:     ScrollState,
    sectionMinHeight: Dp,
) {
    when (config.type) {
        HomeRailType.ContinueWatching -> HomeRail(
            title            = config.title,
            items            = data.continueWatching,
            style            = CardStyle.Landscape,
            onFocused        = onCardFocused,
            onClick          = { onPlayItem(it.id, it.resumePosSec) },
            parentScroll     = parentScroll,
            sectionMinHeight = sectionMinHeight,
        )
        HomeRailType.NextUp -> HomeRail(
            title            = config.title,
            items            = data.nextUp,
            style            = CardStyle.Landscape,
            onFocused        = onCardFocused,
            onClick          = { onPlayItem(it.id, 0L) },
            parentScroll     = parentScroll,
            sectionMinHeight = sectionMinHeight,
        )
        HomeRailType.Trending -> HomeRail(
            title            = config.title,
            items            = data.trending,
            style            = CardStyle.Portrait,
            onFocused        = onCardFocused,
            onClick          = { onOpenItem(it.id, it.kind) },
            parentScroll     = parentScroll,
            sectionMinHeight = sectionMinHeight,
        )
        HomeRailType.LatestInLibrary -> HomeRail(
            title            = config.title,
            // Each library has its own filtered list — keyed by the
            // rail config id so re-orders / new libraries on the
            // server's "Personalizar inicio" page don't desync the
            // mapping.
            items            = data.latestByRailId[config.id].orEmpty(),
            style            = CardStyle.Portrait,
            onFocused        = onCardFocused,
            onClick          = { onOpenItem(it.id, it.kind) },
            parentScroll     = parentScroll,
            sectionMinHeight = sectionMinHeight,
        )
        HomeRailType.LiveNow -> LiveNowRail(
            title            = config.title,
            items            = data.liveNow,
            onFocused        = onCardFocused,
            onClick          = { onPlayItem(it.id, 0L) },
            parentScroll     = parentScroll,
            sectionMinHeight = sectionMinHeight,
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
