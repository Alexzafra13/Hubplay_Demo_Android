package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent

/**
 * Pixel dimensions for the spotlight rendered BELOW a Portrait rail.
 *
 * Position changed vs. the older overlay version: the spotlight no
 * longer sits on top of the row, so it never covers neighbouring
 * cards. It is laid out in its own slot underneath the LazyRow with
 * an AnimatedVisibility wrapper that pushes the next rail down when
 * it appears. Compact dimensions keep that vertical jump small.
 */
object SpotlightDims {
    val height: Dp        = 135.dp                 // 16:9 backdrop dictates this
    val backdropWidth: Dp = 240.dp                 // 240 / 135 ≈ 16:9
    val panelWidth: Dp    = 300.dp                 // info column
    val panelOverlap: Dp  = 50.dp                  // panel sits over the right side of backdrop
    val panelSlant: Dp    = 36.dp                  // diagonal cut on the panel's left edge
    val totalWidth: Dp    = backdropWidth + (panelWidth - panelOverlap)
}

/** Tween shared with [HomeRail]'s slide animation. */
const val SPOTLIGHT_ANIM_MS = 320

/**
 * Trapezoid clip for the spotlight's info panel: top edge full width,
 * bottom edge inset by [bottomSlantDp] on the LEFT. Same diagonal cut
 * the previous in-card panel used, scaled down to match the new
 * compact spotlight height.
 */
private class PanelTrapezoidShape(private val bottomSlantDp: Dp) : Shape {
    override fun createOutline(
        size:            Size,
        layoutDirection: LayoutDirection,
        density:         Density,
    ): Outline {
        val slantPx = with(density) { bottomSlantDp.toPx() }
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width, size.height)
            lineTo(slantPx, size.height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Persistent expanded preview rendered UNDER the focused rail's row
 * (not on top of it). Mounted once per rail; only its content swaps
 * via [AnimatedContent] (slide left/right depending on navigation
 * direction). The HomeRail wraps it in an AnimatedVisibility that
 * expands/shrinks the row vertically — so when nothing is focused,
 * the spotlight occupies zero space and the rails below sit flush
 * with the cards.
 *
 * @param state  Current focus snapshot. `direction` tells the slide
 *               which way to push old/new content; +1 = navigated
 *               forward (right), -1 = navigated backward (left), 0 =
 *               first appearance (no slide, just a fade).
 */
@Composable
fun RailSpotlight(
    state:    SpotlightState,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState     = state,
        contentKey      = { it.item.id },
        transitionSpec  = { spotlightTransition(targetState.direction) },
        modifier        = modifier
            .width(SpotlightDims.totalWidth)
            .height(SpotlightDims.height),
        label           = "rail-spotlight",
    ) { snap ->
        SpotlightCard(item = snap.item)
    }
}

/** Item + navigation direction snapshot driving the spotlight. */
data class SpotlightState(
    val item:      MediaItem,
    val direction: Int,
)

/**
 * Slide horizontally based on [direction], with a short crossfade so
 * the swap reads as smooth rather than mechanical. `0` (first reveal)
 * gets a pure fade — there is no "previous content" to push aside.
 */
private fun AnimatedContentTransitionScope<SpotlightState>.spotlightTransition(
    direction: Int,
) = when {
    direction > 0 -> {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(SPOTLIGHT_ANIM_MS),
        ) + fadeIn(tween(SPOTLIGHT_ANIM_MS)) togetherWith
        slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Left,
            animationSpec = tween(SPOTLIGHT_ANIM_MS),
        ) + fadeOut(tween(SPOTLIGHT_ANIM_MS))
    }
    direction < 0 -> {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(SPOTLIGHT_ANIM_MS),
        ) + fadeIn(tween(SPOTLIGHT_ANIM_MS)) togetherWith
        slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.Right,
            animationSpec = tween(SPOTLIGHT_ANIM_MS),
        ) + fadeOut(tween(SPOTLIGHT_ANIM_MS))
    }
    else -> {
        fadeIn(tween(SPOTLIGHT_ANIM_MS)) togetherWith fadeOut(tween(SPOTLIGHT_ANIM_MS))
    }
}

@Composable
private fun SpotlightCard(item: MediaItem) {
    Box(
        modifier = Modifier
            .width(SpotlightDims.totalWidth)
            .height(SpotlightDims.height)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(SpotlightDims.backdropWidth)
                .fillMaxHeight(),
        ) {
            AsyncImage(
                model              = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(SpotlightDims.panelWidth)
                .fillMaxHeight()
                .clip(PanelTrapezoidShape(bottomSlantDp = SpotlightDims.panelSlant))
                .background(Color.White),
        ) {
            SpotlightInfoPanel(
                item = item,
                modifier = Modifier
                    .width(SpotlightDims.panelWidth)
                    .fillMaxHeight()
                    .padding(
                        start  = SpotlightDims.panelSlant + 14.dp,
                        end    = 16.dp,
                        top    = 12.dp,
                        bottom = 12.dp,
                    ),
            )
        }
    }
}

@Composable
private fun SpotlightInfoPanel(item: MediaItem, modifier: Modifier = Modifier) {
    val panelTextPrimary   = Color(0xFF1A1A1A)
    val panelTextSecondary = Color(0xFF555B66)

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text       = item.title,
            style      = MaterialTheme.typography.titleSmall,
            color      = panelTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.rating?.let {
                Text(
                    text       = "★ ${"%.1f".format(it)}",
                    style      = MaterialTheme.typography.labelSmall,
                    color      = Accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                )
            }
            item.year?.let {
                Text(
                    text     = it.toString(),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = panelTextSecondary,
                    maxLines = 1,
                )
            }
            item.genres.firstOrNull()?.let { g ->
                Text("·", style = MaterialTheme.typography.labelSmall,
                     color = panelTextSecondary)
                Text(
                    text     = g,
                    style    = MaterialTheme.typography.labelSmall,
                    color    = panelTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!item.overview.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text     = item.overview,
                style    = MaterialTheme.typography.bodySmall,
                color    = panelTextSecondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
