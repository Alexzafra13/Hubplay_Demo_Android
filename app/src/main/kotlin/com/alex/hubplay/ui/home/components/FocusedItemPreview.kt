package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.BgCard

/**
 * Lateral preview that appears when the user is hovering on a card in
 * any rail (D-pad focus on TV, mouse hover on web/desktop). Patterned
 * on what Netflix shows next to the focused tile in their TV apps.
 *
 * Layout (row):
 *   [Backdrop, fixed width 360dp]   [Title / meta / overview]
 *
 * Sits between the Hero and the first rail. Uses AnimatedVisibility
 * with fade + expandVertically so it slides in/out without jumping the
 * page layout. Renders nothing when no card is focused (collapsed
 * height = 0).
 *
 * Conscious decisions worth flagging:
 *   - One global preview, not one per rail. Simpler state, single
 *     source of truth (HomeViewModel.focusedItem). The card itself
 *     still shows its own border + scale for the focused-now signal.
 *   - 200dp height keeps the rail-shifted cost bounded. The user can
 *     still see the next 1-2 rails below.
 *   - Backdrop crops 16:9 to fill — never letterboxed.
 */
@Composable
fun FocusedItemPreview(
    item:      MediaItem?,
    modifier:  Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = item != null,
        enter   = fadeIn() + expandVertically(),
        exit    = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        item ?: return@AnimatedVisibility
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // ── Backdrop (left)
            Box(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
            ) {
                AsyncImage(
                    model              = item.backdropUrl ?: item.posterUrl,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
                // Right-fade so the info column reads cleanly when the
                // backdrop is bright.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.6f to Color.Transparent,
                                1f   to BgCard,
                            ),
                        ),
                )
            }

            // ── Info (right)
            Column(
                modifier            = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text       = item.title,
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                MetaRow(item)
                Spacer(Modifier.height(12.dp))
                if (!item.overview.isNullOrBlank()) {
                    Text(
                        text     = item.overview,
                        style    = MaterialTheme.typography.bodyMedium,
                        color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaRow(item: MediaItem) {
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item.rating?.let {
            Text(
                text       = "★ ${"%.1f".format(it)}",
                style      = MaterialTheme.typography.bodyMedium,
                color      = Accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item.year?.let {
            Text(it.toString(), style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item.genres.take(3).forEach { genre ->
            Text("· $genre", style = MaterialTheme.typography.bodyMedium,
                 color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
