package com.alex.hubplay.ui.series

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.components.HeroActions
import com.alex.hubplay.ui.components.HeroCta
import com.alex.hubplay.ui.components.HeroCtas
import com.alex.hubplay.ui.components.HeroDetailConfig
import com.alex.hubplay.ui.components.HeroDetailScaffold
import com.alex.hubplay.ui.components.HeroHeader
import com.alex.hubplay.ui.components.HeroMeta
import com.alex.hubplay.ui.components.HeroMetaRow
import com.alex.hubplay.ui.components.HeroNav
import com.alex.hubplay.ui.components.HeroRails
import com.alex.hubplay.ui.components.HeroToggles
import com.alex.hubplay.ui.components.IdentifyOverlay
import com.alex.hubplay.ui.metadata.MetadataToolsState
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.AccentSoft
import com.alex.hubplay.ui.theme.BgBase

/**
 * Series detail surface — Netflix-style two-view flow:
 *
 *   1. SeriesHeroFull (initial)  → fullscreen cinematic landing with
 *      backdrop, brand mark, logo/title, info, synopsis and two
 *      vertical CTAs (Reproducir + Episodios).
 *   2. SeriesEpisodesPanel (after Episodios) → split layout with the
 *      seasons/sections selector on the left and a vertical episode
 *      list on the right.
 *
 * The hero ↔ episodes toggle lives entirely in this Composable so we
 * don't burn a new nav route on what is really one screen with two
 * presentations of the same data.
 */
@Composable
fun SeriesScreen(
    viewModel:     SeriesViewModel,
    onPlayEpisode: (itemId: String, resumePosSec: Long) -> Unit,
    onBack:        () -> Unit,
    onOpenPerson:  (personId: String) -> Unit = {},
    onOpenItem:    (itemId: String, kind: MediaKind) -> Unit = { _, _ -> },
    onOpenStudio:  (studioSlug: String) -> Unit = {},
    onOpenCollection: (collectionId: String) -> Unit = {},
) {
    val ui by viewModel.ui.collectAsState()
    val tools by viewModel.toolsState.collectAsState()
    var showEpisodes by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(tools.notice) {
        val notice = tools.notice ?: return@LaunchedEffect
        Toast.makeText(context, notice, Toast.LENGTH_SHORT).show()
        viewModel.tools.clearNotice()
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        val data = ui.data
        when {
            ui.isLoading && data == null -> CenteredSpinner()
            ui.error != null             -> ErrorBanner(
                message = ui.error!!,
                onRetry = viewModel::load,
            )
            data != null                 -> {
                if (showEpisodes) {
                    SeriesEpisodesPanel(
                        data           = data,
                        onSelectSeason = viewModel::selectSeason,
                        onPlayEpisode  = onPlayEpisode,
                        onBack         = { showEpisodes = false },
                    )
                } else {
                    SeriesHero(
                        data      = data,
                        related   = ui.related,
                        tools     = tools,
                        viewModel = viewModel,
                        callbacks = SeriesHeroCallbacks(
                            onPlayEpisode  = onPlayEpisode,
                            onShowEpisodes = { showEpisodes = true },
                            onBack         = onBack,
                        ),
                        nav       = remember(onOpenCollection, onOpenStudio, onOpenPerson, onOpenItem) {
                            HeroNav(
                                onOpenCollection = onOpenCollection,
                                onOpenStudio     = onOpenStudio,
                                onOpenPerson     = onOpenPerson,
                                onOpenItem       = onOpenItem,
                            )
                        },
                    )
                }
                tools.identify?.let { state ->
                    IdentifyOverlay(
                        state     = state,
                        onSearch  = viewModel.tools::searchCandidates,
                        onPick    = viewModel.tools::applyIdentify,
                        onRefresh = {
                            viewModel.tools.closeIdentify()
                            viewModel.tools.refreshMetadata()
                        },
                        onDismiss = viewModel.tools::closeIdentify,
                    )
                }
            }
        }
    }
}

/**
 * Hero de serie sobre la ficha compartida ([HeroDetailScaffold]): mismo
 * póster + Reproducir + Episodios + iconos, sinopsis con "Ver más",
 * reparto y "más como esto", vista previa con el tráiler. Sin toggle de
 * visto (marcar una serie entera no tiene endpoint dedicado).
 */
/** Callbacks del hero de serie que no son navegación a otra ficha. */
@androidx.compose.runtime.Immutable
private class SeriesHeroCallbacks(
    val onPlayEpisode:  (String, Long) -> Unit,
    val onShowEpisodes: () -> Unit,
    val onBack:         () -> Unit,
)

@Composable
private fun SeriesHero(
    data:      SeriesData,
    related:   List<Content>,
    tools:     MetadataToolsState,
    viewModel: SeriesViewModel,
    callbacks: SeriesHeroCallbacks,
    nav:       HeroNav,
) {
    val series = data.series ?: return
    val resume = data.resume
    val config = HeroDetailConfig(
        item    = series,
        header  = HeroHeader(
            sectionLabel = stringResource(R.string.series_section_label),
            meta         = {
                MetaRow(
                    item         = series,
                    seasonsCount = data.seasons.size,
                    episodeCount = data.episodesBySeasonId.values.sumOf { it.size },
                )
            },
        ),
        ctas    = HeroCtas(
            play = HeroCta(
                label   = resume.playLabel ?: stringResource(R.string.series_play_empty),
                icon    = Icons.Default.PlayArrow,
                enabled = resume.episodeId != null,
                onClick = { resume.episodeId?.let { callbacks.onPlayEpisode(it, resume.resumeSec) } },
            ),
            secondary = HeroCta(
                label   = stringResource(R.string.series_episodes_action),
                icon    = Icons.Default.VideoLibrary,
                onClick = callbacks.onShowEpisodes,
            ),
        ),
        toggles = HeroToggles(showWatched = false, canEditMetadata = tools.canEditMetadata),
        actions = HeroActions(
            onBack           = callbacks.onBack,
            onToggleFavorite = viewModel::toggleFavorite,
            onToggleWatched  = null,
            onIdentify       = { viewModel.tools.openIdentify(series.title, series.year) },
        ),
        nav     = nav,
    )
    HeroDetailScaffold(
        config     = config,
        rails      = HeroRails(people = series.people, related = related),
        dialogOpen = tools.identify != null,
    )
}

/**
 * Inline metadata strip. Shows `year · {N temporadas | N episodios} ·
 * ★ rating · genres`. Picks "temporadas" when the series has more than
 * one season (then a generic episode count would be ambiguous — which
 * season?) and "episodios" when it's a one-season series (where the
 * count IS meaningful).
 *
 * @param compact when true the row drops `genres` — used in the
 *   episodes panel's narrow 360dp left rail where long genre strings
 *   ("Action & Adventure") wouldn't fit and Compose would render the
 *   text vertically, one character per line.
 */
@Composable
private fun MetaRow(
    item:         Content.Series?,
    seasonsCount: Int,
    episodeCount: Int,
    compact:      Boolean = false,
) {
    if (item == null) return
    val countLabel: String? = when {
        seasonsCount > 1                      -> stringResource(R.string.series_seasons_count, seasonsCount)
        seasonsCount == 1 && episodeCount > 0 -> stringResource(R.string.series_episodes_count, episodeCount)
        else                                  -> null
    }
    // Un solo Text (ver HeroMetaRow): cada dato como nodo propio pesaba en
    // el primer frame de la ficha.
    val parts = buildList {
        item.year?.let { add(HeroMeta(it.toString())) }
        countLabel?.let { add(HeroMeta(it)) }
        item.rating?.let { add(HeroMeta("★ ${"%.1f".format(it)}", accent = true)) }
        if (!compact) item.genres.take(MAX_META_GENRES).forEach { add(HeroMeta(it)) }
    }
    HeroMetaRow(parts)
}

private const val MAX_META_GENRES = 2

// ─── Episodes panel (split view) ────────────────────────────────────────────

@Composable
private fun SeriesEpisodesPanel(
    data:           SeriesData,
    onSelectSeason: (String) -> Unit,
    onPlayEpisode:  (itemId: String, resumePosSec: Long) -> Unit,
    onBack:         () -> Unit,
) {
    val series = data.series

    Box(modifier = Modifier.fillMaxSize()) {
        // Soft backdrop behind the panel — same backdrop, heavily faded
        // toward BgBase so the lists read clearly.
        AsyncImage(
            model              = series?.backdropUrl ?: series?.posterUrl,
            contentDescription = null,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .alpha(0.18f),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgBase.copy(alpha = 0.65f)),
        )

        // Back + brand together top-left, same pattern as the Hero so
        // the user sees a consistent header across both views.
        Row(
            modifier            = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 20.dp)
                .zIndex(10f),
            verticalAlignment   = Alignment.CenterVertically,
        ) {
            BackPill(onBack = onBack)
            Spacer(Modifier.width(16.dp))
            Image(
                painter            = painterResource(R.drawable.brand_wordmark),
                contentDescription = stringResource(R.string.brand_hubplay),
                modifier           = Modifier.height(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text          = stringResource(R.string.series_section_label),
                style         = MaterialTheme.typography.labelMedium,
                color         = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
        }

        Row(modifier = Modifier.fillMaxSize().padding(top = 90.dp)) {
            // ── Left rail: series logo/title + meta + season selector ────
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.Top,
            ) {
                if (!series?.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model              = series.logoUrl,
                        contentDescription = series.title,
                        contentScale       = ContentScale.Fit,
                        modifier           = Modifier
                            .heightIn(min = 60.dp, max = 100.dp)
                            .widthIn(max = 280.dp),
                    )
                } else {
                    Text(
                        text       = series?.title.orEmpty(),
                        style      = MaterialTheme.typography.headlineMedium,
                        color      = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(10.dp))
                MetaRow(
                    item         = series,
                    seasonsCount = data.seasons.size,
                    episodeCount = data.episodesBySeasonId.values.sumOf { it.size },
                    compact      = true,  // narrow 360dp panel — drop genres
                )

                Spacer(Modifier.height(28.dp))

                // Season selector — render even when there's a single
                // season so the "current selection" feels deliberate
                // and the focus has somewhere to land on first arrival.
                if (data.seasons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        data.seasons.forEach { season ->
                            val epCount = data.episodesBySeasonId[season.id]?.size
                            SeasonRow(
                                season    = season,
                                episodeCount = epCount,
                                selected  = data.selectedSeasonId == season.id,
                                onClick   = { onSelectSeason(season.id) },
                            )
                        }
                    }
                }
            }

            // ── Right rail: episode list ─────────────────────────────────
            val episodes = data.selectedSeasonId
                ?.let { data.episodesBySeasonId[it] }
                .orEmpty()
            if (episodes.isEmpty()) {
                Box(
                    modifier         = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LazyColumn(
                    modifier              = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(end = 48.dp, bottom = 24.dp),
                    verticalArrangement   = Arrangement.spacedBy(14.dp),
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        EpisodeRow(
                            episode = ep,
                            onClick = { onPlayEpisode(ep.id, ep.resumePosSec) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonRow(
    season:       Content.Season,
    episodeCount: Int?,
    selected:     Boolean,
    onClick:      () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.03f else 1.0f,
        animationSpec = tween(180),
        label         = "season-row-scale",
    )
    val label    = season.seasonNumber?.let { stringResource(R.string.series_season_format, it) } ?: season.title
    val sublabel = episodeCount?.takeIf { it > 0 }?.let { stringResource(R.string.series_episodes_count, it) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .background(
                when {
                    selected -> AccentSoft.copy(alpha = 0.25f)
                    focused  -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    else     -> Color.Transparent
                },
            )
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodyLarge,
            color      = if (selected) Accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (sublabel != null) {
            Text(
                text  = sublabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Content.Episode,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.03f else 1.0f,
        animationSpec = tween(180),
        label         = "episode-row-scale",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment     = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Thumbnail with episode number overlay (Netflix pattern).
        Box(
            modifier = Modifier
                .width(220.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
        ) {
            AsyncImage(
                model              = episode.backdropUrl ?: episode.posterUrl,
                contentDescription = episode.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
            episode.episodeNumber?.let { num ->
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text       = stringResource(R.string.series_episode_label, num.toString()),
                        style      = MaterialTheme.typography.labelMedium,
                        color      = Color.White,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (episode.progressPct > 0f) {
                LinearProgressIndicator(
                    progress   = { episode.progressPct },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter),
                    color      = Accent,
                    trackColor = Color.Transparent,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
            Text(
                text       = episode.title.ifBlank { stringResource(R.string.series_episode_label, episode.episodeNumber?.toString() ?: "") },
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            episode.overview?.takeIf { it.isNotBlank() }?.let { o ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text     = o,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episode.durationSec > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text  = stringResource(R.string.series_episode_duration_short, episode.durationSec / 60),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Shared bits ────────────────────────────────────────────────────────────

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = message,
            style     = MaterialTheme.typography.bodyLarge,
            color     = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

