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
 * Pixel dimensions for the in-rail spotlight. Match the original
 * Netflix-on-web look the user signed off on: 225dp tall (= portrait
 * card height), 475dp wide (315 backdrop + 240 panel - 80 overlap).
 *
 * The focused card's slot in the LazyRow widens to [totalWidth] so
 * neighbouring cards get pushed to the right instead of being
 * covered. The spotlight overlay then renders ON TOP of that wider
 * slot, perfectly fitting it.
 */
object SpotlightDims {
    val height: Dp        = 225.dp
    val backdropWidth: Dp = 315.dp
    val panelWidth: Dp    = 240.dp
    val panelOverlap: Dp  = 80.dp
    val panelSlant: Dp    = 56.dp
    val totalWidth: Dp    = backdropWidth + (panelWidth - panelOverlap)   // 475dp
}

/** Tween shared with [HomeRail]'s slot-width and scroll animations. */
const val SPOTLIGHT_ANIM_MS = 320

/**
 * Trapezoid clip for the spotlight's info panel: top edge full width,
 * bottom edge inset by [bottomSlantDp] on the LEFT. Diagonal cut so
 * the boundary between backdrop and white panel never reads as a
 * clean vertical line.
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
 * Persistent spotlight overlay for a Portrait rail. Rendered ABOVE
 * the LazyRow at a fixed viewport position (the leftmost focused
 * slot, after the rail's leading content padding).
 *
 * Mounted once per rail. Only the inner content swaps via
 * [AnimatedContent] — old item slides left/right, new item slides
 * in from the opposite side. The spotlight itself never collapses
 * while focus stays inside the rail; it only fades in/out via the
 * caller's AnimatedVisibility on entry/exit.
 *
 * @param state  Item + navigation direction. `direction` = +1 means
 *               the user pressed RIGHT (new content slides in from
 *               the right), -1 means LEFT, 0 means first reveal
 *               (pure fade).
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
                        start  = SpotlightDims.panelSlant + 16.dp,
                        end    = 18.dp,
                        top    = 18.dp,
                        bottom = 18.dp,
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
            style      = MaterialTheme.typography.titleMedium,
            color      = panelTextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item.rating?.let {
                Text(
                    text       = "★ ${"%.1f".format(it)}",
                    style      = MaterialTheme.typography.labelMedium,
                    color      = Accent,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                )
            }
            item.year?.let {
                Text(
                    text     = it.toString(),
                    style    = MaterialTheme.typography.labelMedium,
                    color    = panelTextSecondary,
                    maxLines = 1,
                )
            }
            item.genres.firstOrNull()?.let { g ->
                Text("·", style = MaterialTheme.typography.labelMedium,
                     color = panelTextSecondary)
                Text(
                    text     = g,
                    style    = MaterialTheme.typography.labelMedium,
                    color    = panelTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (!item.overview.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text     = item.overview,
                style    = MaterialTheme.typography.bodySmall,
                color    = panelTextSecondary,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
