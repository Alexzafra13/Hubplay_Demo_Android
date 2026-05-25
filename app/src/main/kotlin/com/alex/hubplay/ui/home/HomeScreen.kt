package com.alex.hubplay.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import com.alex.hubplay.ui.series.HeroTrailerView
import com.alex.hubplay.ui.theme.BgBase

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
    val trailerInfo by viewModel.trailerInfo.collectAsState()
    val railsListState = rememberLazyListState()

    val isLanding by remember {
        derivedStateOf { focusedItem == null }
    }

    val heroItem by remember {
        derivedStateOf {
            focusedItem ?: ui.data.hero.firstOrNull()
        }
    }

    var trailerRevealed by remember { mutableStateOf(false) }
    val backdropAlpha by animateFloatAsState(
        targetValue = if (trailerRevealed) 0f else 1f,
        animationSpec = tween(durationMillis = 700),
        label = "backdrop-fade",
    )

    val activeTrailer = trailerInfo?.takeIf { it.itemId == heroItem?.id }

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

                    // ── Layer 0: Full-screen backdrop ──────────────────
                    Crossfade(
                        targetState = heroItem?.backdropUrl ?: heroItem?.posterUrl,
                        animationSpec = tween(durationMillis = 500),
                        label = "home-backdrop",
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(backdropAlpha),
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

                    if (activeTrailer != null) {
                        HeroTrailerView(
                            videoKey = activeTrailer.key,
                            site = activeTrailer.site,
                            onReveal = { trailerRevealed = true },
                            onDismiss = { trailerRevealed = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    LaunchedEffect(activeTrailer) {
                        if (activeTrailer == null) trailerRevealed = false
                    }

                    // ── Layer 1: Gradient overlays ──────────────────────
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

                    // ── Layer 2: Content ────────────────────────────────
                    Row(modifier = Modifier.fillMaxSize()) {

                        val visibleTabs = LocalVisibleTabs.current
                        HomeSidebar(
                            onNavigateToTab = onNavigateToTab,
                            onOpenSearch = { onNavigateToTab(Tab.Search) },
                            onOpenSettings = onOpenSettings,
                            visibleTabs = visibleTabs,
                        )

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        ) {
                            // ── Hero info (fixed top) ──────────────────
                            HeroInfo(
                                item = heroItem,
                                onPlay = { it?.let { item -> onPlayItem(item.id, item.resumePosSec) } },
                                onDetails = { it?.let { item -> onOpenItem(item.id, item.kind) } },
                                showControls = isLanding,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.40f),
                            )

                            // ── Rails (LazyColumn) ─────────────────────
                            // LazyColumn decomposes off-screen items,
                            // so previous rails can never leak through.
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(0.60f),
                            ) {
                                LazyColumn(
                                    state = railsListState,
                                    contentPadding = PaddingValues(bottom = 40.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    itemsIndexed(
                                        items = ui.data.rails,
                                        key = { _, config -> config.id },
                                    ) { index, config ->
                                        RenderRail(
                                            config = config,
                                            data = ui.data,
                                            onCardFocused = viewModel::onCardFocused,
                                            onOpenItem = onOpenItem,
                                            onPlayItem = onPlayItem,
                                            parentListState = railsListState,
                                            railIndex = index,
                                        )
                                    }
                                }

                                // Bottom fade
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
    config:          HomeRailConfig,
    data:            com.alex.hubplay.data.HomeData,
    onCardFocused:   (MediaItem) -> Unit,
    onOpenItem:      (String, MediaKind) -> Unit,
    onPlayItem:      (String, Long) -> Unit,
    parentListState: androidx.compose.foundation.lazy.LazyListState,
    railIndex:       Int,
) {
    when (config.type) {
        HomeRailType.ContinueWatching -> HomeRail(
            title = config.title,
            items = data.continueWatching,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, it.resumePosSec) },
            parentListState = parentListState,
            railIndex = railIndex,
        )
        HomeRailType.NextUp -> HomeRail(
            title = config.title,
            items = data.nextUp,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, 0L) },
            parentListState = parentListState,
            railIndex = railIndex,
        )
        HomeRailType.Trending -> HomeRail(
            title = config.title,
            items = data.trending,
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onOpenItem(it.id, it.kind) },
            parentListState = parentListState,
            railIndex = railIndex,
        )
        HomeRailType.LatestInLibrary -> HomeRail(
            title = config.title,
            items = data.latestByRailId[config.id].orEmpty(),
            style = CardStyle.Landscape,
            onFocused = onCardFocused,
            onClick = { onOpenItem(it.id, it.kind) },
            parentListState = parentListState,
            railIndex = railIndex,
        )
        HomeRailType.LiveNow -> LiveNowRail(
            title = config.title,
            items = data.liveNow,
            onFocused = onCardFocused,
            onClick = { onPlayItem(it.id, 0L) },
            parentListState = parentListState,
            railIndex = railIndex,
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
