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
 * Layout:
 *   [Full-screen backdrop of focused/hero item]
 *     [Left sidebar (collapsed icons / expanded with labels)]
 *     [Content area]
 *       [HeroInfo — logo, description, CTAs (visible when at top)]
 *       [Rails — horizontal carousels with snap-on-focus]
 *
 * The first rail drives the hero backdrop: when the user focuses a
 * card in the first rail, the backdrop crossfades to that item's
 * backdrop image. When scrolled past the first rail, the backdrop
 * fades to the base background colour.
 *
 * The sidebar is always visible as a collapsed icon strip. When
 * the user presses D-pad Left from the content area, the sidebar
 * expands with labels. Pressing Right collapses it back.
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
                    // Left gradient — ensures sidebar and hero info read clearly
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
                    // Bottom gradient — fades to BgBase toward the rails
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(0.55f)
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.4f to BgBase.copy(alpha = 0.6f),
                                    1f to BgBase,
                                ),
                            ),
                    )

                    // ── Layer 2: Content (sidebar + scrollable area) ────
                    Row(modifier = Modifier.fillMaxSize()) {

                        // Sidebar
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

                        // Main content column
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                // Hero info — visible when near top
                                HeroInfo(
                                    item = heroItem,
                                    onPlay = { it?.let { item -> onPlayItem(item.id, item.resumePosSec) } },
                                    onDetails = { it?.let { item -> onOpenItem(item.id, item.kind) } },
                                    parentScroll = scrollState,
                                )

                                // Rails
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

                                Spacer(Modifier.height(60.dp))
                            }

                            // Bottom fade overlay — anchored to viewport
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .height(120.dp)
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
