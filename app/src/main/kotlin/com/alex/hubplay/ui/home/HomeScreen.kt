package com.alex.hubplay.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.HomeRailConfig
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.home.components.CardStyle
import com.alex.hubplay.ui.home.components.HeroInfo
import com.alex.hubplay.ui.home.components.HomeSidebar
import com.alex.hubplay.ui.home.components.HomeRail
import com.alex.hubplay.ui.home.components.LiveNowRail
import com.alex.hubplay.ui.home.components.LocalVisibleTabs
import com.alex.hubplay.ui.home.components.Tab
import com.alex.hubplay.ui.theme.BgBase

/**
 * Home — Amazon Prime Video style layout for Android TV.
 *
 * The screen is split into two persistent zones:
 *
 *   ┌──────┬───────────────────────────────────┐
 *   │      │  HERO (always visible)            │
 *   │ Side │  Logo/title + meta + Play/Details │
 *   │ bar  ├───────────────────────────────────┤
 *   │      │  RAILS (scrollable)               │
 *   │      │  [card] [card] [card] ...         │
 *   │      │  ─── Next rail title peek ───     │
 *   └──────┴───────────────────────────────────┘
 *
 * Every rail drives the hero: when the user focuses a card in ANY
 * rail, the hero crossfades to that item's backdrop + info. Jumping
 * from one rail to the next is simply "the hero changes to the new
 * rail's focused card". The hero never scrolls away — it's a fixed
 * section that always occupies the upper portion of the screen.
 *
 * The backdrop image is full-screen behind everything, with gradient
 * overlays for legibility. The sidebar is a collapsed icon strip
 * that expands on D-pad Left.
 */
@Composable
fun HomeScreen(
    viewModel:       HomeViewModel,
    onOpenItem:      (itemId: String, kind: MediaKind) -> Unit,
    onPlayItem:      (itemId: String, resumePosSec: Long) -> Unit,
    onNavigateToTab: (Tab) -> Unit,
    onLogOut:        () -> Unit,
    onOpenSettings:  () -> Unit = {},
    profileName:     String?   = null,
) {
    val ui by viewModel.ui.collectAsState()
    val focusedItem by viewModel.focusedItem.collectAsState()
    val scrollState = rememberScrollState()
    var sidebarExpanded by remember { mutableStateOf(false) }

    val heroItem by remember {
        derivedStateOf {
            focusedItem ?: ui.data.hero.firstOrNull()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BgBase,
    ) {
        when {
            ui.isLoading && ui.data.continueWatching.isEmpty()
                && ui.data.trending.isEmpty() -> CenteredSpinner()
            ui.error != null -> ErrorBanner(
                message = ui.error!!,
                onRetry = viewModel::refresh,
            )
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {

                    // ── Layer 0: Full-screen backdrop ────────────────────
                    Crossfade(
                        targetState = heroItem?.backdropUrl ?: heroItem?.posterUrl,
                        animationSpec = tween(durationMillis = 500),
                        label = "home-backdrop",
                        modifier = Modifier.fillMaxSize(),
                    ) { url ->
                        if (url != null) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    // ── Layer 1: Gradient overlays ───────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(0.65f)
                            .background(
                                Brush.horizontalGradient(
                                    0f to BgBase.copy(alpha = 0.95f),
                                    0.6f to BgBase.copy(alpha = 0.75f),
                                    1f to Color.Transparent,
                                ),
                            ),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.6f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.3f to BgBase.copy(alpha = 0.65f),
                                    1f to BgBase,
                                ),
                            ),
                    )

                    // ── Layer 2: Content ─────────────────────────────────
                    Row(modifier = Modifier.fillMaxSize()) {

                        val visibleTabs = LocalVisibleTabs.current
                        HomeSidebar(
                            profileName = profileName,
                            expanded = sidebarExpanded,
                            onExpandChange = { sidebarExpanded = it },
                            onNavigateToTab = onNavigateToTab,
                            onOpenSearch = { onNavigateToTab(Tab.Search) },
                            onOpenSettings = onOpenSettings,
                            visibleTabs = visibleTabs,
                        )

                        // Main content: hero (top, fixed) + rails (bottom, scroll)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            // ── Hero info (fixed top section) ───────────
                            // Takes ~45% of screen height. Always visible,
                            // content crossfades when focused item changes.
                            HeroInfo(
                                item = heroItem,
                                onPlay = { it?.let { item -> onPlayItem(item.id, item.resumePosSec) } },
                                onDetails = { it?.let { item -> onOpenItem(item.id, item.kind) } },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.45f),
                            )

                            // ── Rails (scrollable bottom section) ───────
                            // Takes ~55% of screen height. Shows the
                            // current rail + peek of the next one below.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.55f),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(scrollState),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    ui.data.rails.forEach { config ->
                                        RenderRail(
                                            config = config,
                                            data = ui.data,
                                            onCardFocused = viewModel::onCardFocused,
                                            onOpenItem = onOpenItem,
                                            onPlayItem = onPlayItem,
                                            parentScroll = scrollState,
                                        )
                                    }

                                    Spacer(Modifier.height(40.dp))
                                }

                                // Bottom fade so the peek of the next
                                // rail dissolves into the background.
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(80.dp)
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
            }
        }
    }
}

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
            title = config.title,
            items = data.continueWatching,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, it.resumePosSec) },
            parentScroll = parentScroll,
        )
        HomeRailType.NextUp -> HomeRail(
            title = config.title,
            items = data.nextUp,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, 0L) },
            parentScroll = parentScroll,
        )
        HomeRailType.Trending -> HomeRail(
            title = config.title,
            items = data.trending,
            style = CardStyle.Portrait,
            onFocused = onCardFocused,
            onClick = { onOpenItem(it.id, it.kind) },
            parentScroll = parentScroll,
        )
        HomeRailType.LatestInLibrary -> HomeRail(
            title = config.title,
            items = data.latestByRailId[config.id].orEmpty(),
            style = CardStyle.Portrait,
            onFocused = onCardFocused,
            onClick = { onOpenItem(it.id, it.kind) },
            parentScroll = parentScroll,
        )
        HomeRailType.LiveNow -> LiveNowRail(
            title = config.title,
            items = data.liveNow,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, 0L) },
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
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}
