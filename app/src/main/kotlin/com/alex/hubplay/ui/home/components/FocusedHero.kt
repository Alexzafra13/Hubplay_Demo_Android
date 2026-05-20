package com.alex.hubplay.ui.home.components

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase

/**
 * Netflix-style hero preview shown when a card on the page is focused.
 *
 * Replaces the rotating spotlight HeroSection for as long as the user
 * is navigating cards — backdrop occupies the left half (decorative
 * atmosphere), info column lives on the right (legible against a
 * gradient fade to BgBase). Once focus leaves every card, the
 * HomeScreen swaps this out for the spotlight rotator again via a
 * Crossfade.
 *
 * Width is the full Hero band (matches HeroSection's height = 420.dp)
 * so the visual shift between rotator and focus preview happens in
 * place — no layout jumps below.
 */
@Composable
fun FocusedHero(
    item:     MediaItem,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(420.dp),
    ) {
        // ── Backdrop (full bleed) ──────────────────────────────────────────
        AsyncImage(
            model              = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )
        // ── Right fade so the info column reads on any backdrop ───────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f    to Color.Transparent,
                        0.40f to Color.Transparent,
                        1f    to BgBase.copy(alpha = 0.92f),
                    ),
                ),
        )
        // ── Soft vertical fade at the very bottom for rail transition ─────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f   to Color.Transparent,
                        0.75f to Color.Transparent,
                        1f   to BgBase.copy(alpha = 0.45f),
                    ),
                ),
        )

        // ── Info column on the right ──────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(max = 520.dp)
                .padding(start = 24.dp, top = 36.dp, end = 56.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // Small section eyebrow — same brand cue as the detail/series
            // hero, lighter so it doesn't fight the title for attention.
            Text(
                text          = stringResource(R.string.home_focused_eyebrow),
                style         = MaterialTheme.typography.labelMedium,
                color         = Accent,
                fontWeight    = FontWeight.SemiBold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))

            // Logo art preferred over text. Same fallback chain as the
            // detail/series heroes.
            if (!item.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model              = item.logoUrl,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier
                        .heightIn(min = 70.dp, max = 110.dp)
                        .widthIn(max = 460.dp),
                )
            } else {
                Text(
                    text       = item.title,
                    style      = MaterialTheme.typography.displaySmall,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))
            FocusedMetaRow(item)

            if (!item.overview.isNullOrBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text     = item.overview,
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FocusedMetaRow(item: MediaItem) {
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
                text     = stringResource(R.string.detail_duration_minutes, item.durationSec / 60),
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
