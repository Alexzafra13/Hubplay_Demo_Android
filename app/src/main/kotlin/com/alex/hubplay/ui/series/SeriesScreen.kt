package com.alex.hubplay.ui.series

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.components.HeroCtaButton
import com.alex.hubplay.ui.components.HeroIconButton
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
) {
    val ui by viewModel.ui.collectAsState()
    var showEpisodes by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        when {
            ui.isLoading && ui.data == null -> CenteredSpinner()
            ui.error != null                -> ErrorBanner(
                message = ui.error!!,
                onRetry = viewModel::load,
            )
            ui.data != null                 -> {
                if (showEpisodes) {
                    SeriesEpisodesPanel(
                        data           = ui.data!!,
                        onSelectSeason = viewModel::selectSeason,
                        onPlayEpisode  = onPlayEpisode,
                        onBack         = { showEpisodes = false },
                    )
                } else {
                    SeriesHeroFull(
                        data             = ui.data!!,
                        onPlay           = { id, resume -> onPlayEpisode(id, resume) },
                        onShowEpisodes   = { showEpisodes = true },
                        onBack           = onBack,
                        onToggleFavorite = viewModel::toggleFavorite,
                    )
                }
            }
        }
    }
}

// ─── Hero (cinematic landing) ───────────────────────────────────────────────

@Composable
private fun SeriesHeroFull(
    data:             SeriesData,
    onPlay:           (itemId: String, resumePosSec: Long) -> Unit,
    onShowEpisodes:   () -> Unit,
    onBack:           () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val series = data.series

    // Backdrop ↔ trailer crossfade. The trailer view is the source of
    // truth via onReveal / onDismiss callbacks; we just animate alpha.
    var trailerRevealed by remember(series?.id) { mutableStateOf(false) }
    val backdropAlpha by animateFloatAsState(
        targetValue   = if (trailerRevealed) 0f else 1f,
        animationSpec = tween(durationMillis = 700),
        label         = "backdrop-fade",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Fullscreen backdrop (fades out when the trailer reveals) ──────
        AsyncImage(
            model              = series?.backdropUrl ?: series?.posterUrl,
            contentDescription = series?.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha),
        )
        // ── Optional YouTube trailer overlay ──────────────────────────────
        // Only mounted when the server picked a trailer for this series.
        // The component handles its own oEmbed pre-flight, load delay,
        // reveal timing and watchdog — see HeroTrailerView.kt.
        if (series?.trailerKey != null && series.trailerSite != null) {
            HeroTrailerView(
                videoKey = series.trailerKey,
                site     = series.trailerSite,
                onReveal = { trailerRevealed = true },
                onDismiss = { trailerRevealed = false },
            )
        }
        // Left side fade — left half is fully BgBase tone for legibility,
        // right half preserves the backdrop for atmosphere. Netflix /
        // Plex pattern: text always on the dark side, hero art on the
        // light side.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f   to BgBase.copy(alpha = 0.92f),
                        0.55f to BgBase.copy(alpha = 0.40f),
                        1f   to Color.Transparent,
                    ),
                ),
        )
        // Subtle vertical fade at bottom so the controls aren't fighting
        // the backdrop edge.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to BgBase.copy(alpha = 0.55f),
                    ),
                ),
        )

        // ── Back button + brand wordmark, top-left ────────────────────────
        // Back is the first element so D-pad up lands on it; the brand is
        // decorative right next to it (same idea as Netflix logo + back).
        Row(
            modifier             = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 20.dp)
                .zIndex(10f),
            verticalAlignment    = Alignment.CenterVertically,
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

        // ── Favourite heart, top-right ─────────────────────────────────────
        // Series-level toggle — same item id the backend stores user_data
        // against, so a heart here matches what the user sees from the web.
        HeroIconButton(
            icon               = if (series?.isFavorite == true) Icons.Default.Favorite
                                 else                            Icons.Default.FavoriteBorder,
            contentDescription = if (series?.isFavorite == true) stringResource(R.string.cd_remove_favorite)
                                 else                            stringResource(R.string.cd_add_favorite),
            onClick            = onToggleFavorite,
            modifier           = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 20.dp)
                .zIndex(10f),
        )

        // ── Info column on the left half ──────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.55f)
                .padding(start = 48.dp, end = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo art if the server has one for this series; otherwise
            // fall back to a big bold title.
            if (!series?.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model              = series.logoUrl,
                    contentDescription = series.title,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .heightIn(min = 80.dp, max = 140.dp)
                        .widthIn(max = 460.dp),
                )
            } else {
                Text(
                    text       = series?.title.orEmpty(),
                    style      = MaterialTheme.typography.displayLarge,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))
            MetaRow(
                item         = series,
                seasonsCount = data.seasons.size,
                episodeCount = data.episodesBySeasonId.values.sumOf { it.size },
            )

            series?.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text     = overview,
                    style    = MaterialTheme.typography.bodyLarge,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(28.dp))
            HeroCtaColumn(
                resume         = data.resume,
                onPlay         = onPlay,
                onShowEpisodes = onShowEpisodes,
            )
        }
    }
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
    item:         MediaItem?,
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
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.year?.let {
            Text(
                text     = it.toString(),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        countLabel?.let {
            Text("·", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text     = it,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        item.rating?.let {
            Text("·", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text       = "★ ${"%.1f".format(it)}",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Accent,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
            )
        }
        if (!compact) {
            item.genres.take(2).forEach { genre ->
                Text("·", style = MaterialTheme.typography.bodyMedium,
                     color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text     = genre,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HeroCtaColumn(
    resume:         SeriesResumeTarget,
    onPlay:         (String, Long) -> Unit,
    onShowEpisodes: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(resume.episodeId) {
        if (resume.episodeId != null) {
            runCatching { playFocus.requestFocus() }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HeroCtaButton(
            label    = resume.playLabel ?: stringResource(R.string.series_play_empty),
            icon     = Icons.Default.PlayArrow,
            primary  = true,
            enabled  = resume.episodeId != null,
            focusRequester = playFocus,
            onClick  = {
                resume.episodeId?.let { onPlay(it, resume.resumeSec) }
            },
            modifier = Modifier.fillMaxWidth(0.62f),
        )
        HeroCtaButton(
            label    = stringResource(R.string.series_episodes_action),
            icon     = Icons.Default.VideoLibrary,
            primary  = false,
            onClick  = onShowEpisodes,
            modifier = Modifier.fillMaxWidth(0.62f),
        )
    }
}

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
    season:       MediaItem,
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
    episode: MediaItem,
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

