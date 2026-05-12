package com.alex.hubplay.ui.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.components.BackPill
import com.alex.hubplay.ui.components.HeroCtaButton
import com.alex.hubplay.ui.components.HeroIconButton
import com.alex.hubplay.ui.series.HeroTrailerView
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase

/**
 * Movie detail surface — same cinematic Netflix-style hero pattern as
 * the SeriesScreen, but adapted for movies:
 *
 *   - Backdrop fullscreen as the ambient layer.
 *   - Optional YouTube trailer overlay (same HeroTrailerView the
 *     series uses) — fades the backdrop out when it reveals.
 *   - Top-left: back pill + "HubPlay PELÍCULAS" brand row.
 *   - Centre-left split into:
 *       * Poster (vertical, 160dp wide) — movies keep the cover art
 *         visible because the cover is itself a strong visual signal
 *         the user reads from the home rails. Series rely on the
 *         studio logo for the same job.
 *       * Info column: studio logo (if available) or title text, the
 *         tagline as subtitle, meta row, overview, and the Play / Mi
 *         lista CTAs stacked vertically.
 */
@Composable
fun DetailScreen(
    viewModel: DetailViewModel,
    onPlay:    (itemId: String, resumePosSec: Long) -> Unit,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        when {
            ui.isLoading       -> CenteredSpinner()
            ui.error != null   -> ErrorBanner(message = ui.error!!, onRetry = viewModel::load)
            ui.item != null    -> HeroFull(item = ui.item!!, onPlay = onPlay, onBack = onBack)
        }
    }
}

@Composable
private fun HeroFull(
    item:   MediaItem,
    onPlay: (String, Long) -> Unit,
    onBack: () -> Unit,
) {
    // Backdrop ↔ trailer crossfade — same machinery as the SeriesScreen.
    var trailerRevealed by remember(item.id) { mutableStateOf(false) }
    val backdropAlpha by animateFloatAsState(
        targetValue   = if (trailerRevealed) 0f else 1f,
        animationSpec = tween(durationMillis = 700),
        label         = "backdrop-fade",
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Fullscreen backdrop ────────────────────────────────────────────
        AsyncImage(
            model              = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier
                .fillMaxSize()
                .alpha(backdropAlpha),
        )

        // ── Optional trailer overlay ───────────────────────────────────────
        if (item.trailerKey != null && item.trailerSite != null) {
            HeroTrailerView(
                videoKey  = item.trailerKey,
                site      = item.trailerSite,
                onReveal  = { trailerRevealed = true },
                onDismiss = { trailerRevealed = false },
            )
        }

        // ── Left fade so info reads against the backdrop ──────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f    to BgBase.copy(alpha = 0.92f),
                        0.55f to BgBase.copy(alpha = 0.40f),
                        1f    to Color.Transparent,
                    ),
                ),
        )
        // Subtle bottom vertical fade.
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

        // ── Back + brand row, top-left ─────────────────────────────────────
        // No "PELÍCULAS" sub-label here — the word didn't read as nicely
        // as "SERIES" on this surface, and the brand alone is enough to
        // anchor the section. SeriesScreen keeps its "SERIES" label.
        Row(
            modifier          = Modifier
                .align(Alignment.TopStart)
                .padding(start = 24.dp, top = 20.dp)
                .zIndex(10f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackPill(onBack = onBack)
            Spacer(Modifier.width(16.dp))
            Image(
                painter            = painterResource(R.drawable.brand_wordmark),
                contentDescription = "HubPlay",
                modifier           = Modifier.height(28.dp),
            )
        }

        // ── 3-dots more-options, top-right ─────────────────────────────────
        // Living up here (mirror of the back pill's position) keeps the
        // poster + Play block clean and unambiguous below, and reads as
        // "section-level options" rather than "actions on this item",
        // which suits the kind of menu that's going here anyway (marcar
        // visto, info del archivo, eliminar descarga, etc.).
        HeroIconButton(
            icon               = Icons.Default.MoreVert,
            contentDescription = "Más opciones",
            onClick            = { /* TODO: overflow menu (marcar visto, info, eliminar) */ },
            modifier           = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 24.dp, top = 20.dp)
                .zIndex(10f),
        )

        // ── Two-column layout: poster + Play left, info + secondary right
        Row(
            modifier              = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(0.8f)
                .padding(start = 48.dp, end = 24.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            // ── Left column: poster + Play button right under it ─────────
            PosterAndPlayColumn(item = item, onPlay = onPlay)

            // ── Right column: logo/title, meta, overview, secondary CTAs
            InfoColumn(item = item)
        }
    }
}

@Composable
private fun PosterAndPlayColumn(item: MediaItem, onPlay: (String, Long) -> Unit) {
    val playLabel = if (item.resumePosSec > 0) {
        val mins = item.resumePosSec / 60
        val secs = item.resumePosSec % 60
        "Reanudar ${mins}:${secs.toString().padStart(2, '0')}"
    } else "Reproducir"

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(item.id) {
        runCatching { playFocus.requestFocus() }
    }

    Column(
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Poster — drops gracefully when the item has no poster_url.
        if (item.posterUrl != null) {
            Box(
                modifier = Modifier
                    .width(190.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                AsyncImage(
                    model              = item.posterUrl,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
        }
        // Play takes the full poster width — clean, unambiguous, and
        // the label never gets truncated on smaller viewports. The
        // 3-dots overflow lives in the top-right of the hero so it
        // doesn't fight Play for space.
        HeroCtaButton(
            label          = playLabel,
            icon           = Icons.Default.PlayArrow,
            primary        = true,
            focusRequester = playFocus,
            onClick        = { onPlay(item.id, item.resumePosSec) },
            modifier       = Modifier.width(190.dp),
        )
    }
}

@Composable
private fun InfoColumn(item: MediaItem) {
    Column(
        modifier            = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        // Studio logo preferred over title text when the server has one
        // for the movie. Same fallback chain as SeriesScreen.
        if (!item.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model              = item.logoUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier
                    .heightIn(min = 64.dp, max = 120.dp)
                    .widthIn(max = 480.dp),
            )
        } else {
            Text(
                text       = item.title,
                style      = MaterialTheme.typography.displayMedium,
                color      = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )
        }

        if (!item.subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text     = item.subtitle,
                style    = MaterialTheme.typography.titleMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.height(14.dp))
        MetaRow(item)

        if (!item.overview.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text     = item.overview,
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MetaRow(item: MediaItem) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.rating?.let {
            Text(
                text       = "★ ${"%.1f".format(it)}",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Accent,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 1,
            )
        }
        item.year?.let {
            Text(
                text     = it.toString(),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        if (item.durationSec > 0) {
            Text("·", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text     = "${item.durationSec / 60} min",
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
        }
        item.genres.take(3).forEach { genre ->
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
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}
