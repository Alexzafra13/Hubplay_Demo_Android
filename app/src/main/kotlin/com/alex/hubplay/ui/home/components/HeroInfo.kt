package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.OnAccent
import kotlinx.coroutines.launch

/**
 * Prime Video style hero info panel.
 *
 * Sits in the top portion of the content area, showing the
 * currently focused (or default spotlight) item's metadata:
 * logo/title, overview, year/rating/genres, and Play/Details CTAs.
 *
 * The background is handled by HomeScreen's backdrop layer —
 * this composable only renders the text/CTA overlay on the
 * left side.
 *
 * Height is ~340dp to leave room for the first rail to peek
 * below, matching the "you see the hero info and a strip of
 * content cards at the bottom" Prime Video pattern.
 */
@Composable
fun HeroInfo(
    item:         MediaItem?,
    onPlay:       (MediaItem?) -> Unit,
    onDetails:    (MediaItem?) -> Unit,
    parentScroll: ScrollState,
    modifier:     Modifier = Modifier,
) {
    if (item == null) return

    var sectionTopY by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { playFocusRequester.requestFocus() }
    }

    AnimatedContent(
        targetState = item,
        label = "hero-info",
        transitionSpec = {
            (fadeIn(tween(400)) togetherWith fadeOut(tween(250)))
        },
        modifier = modifier
            .fillMaxWidth()
            .height(340.dp)
            .onGloballyPositioned { coords ->
                sectionTopY = coords.positionInParent().y
            }
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    scope.launch {
                        parentScroll.animateScrollTo(
                            value = sectionTopY.toInt().coerceAtLeast(0),
                            animationSpec = tween(durationMillis = 350),
                        )
                    }
                }
            },
    ) { displayItem ->
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .padding(start = 16.dp, top = 48.dp, end = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.Bottom,
        ) {
            // Eyebrow
            Text(
                text = stringResource(R.string.home_hero_eyebrow),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(8.dp))

            // Logo or title
            if (!displayItem.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = displayItem.logoUrl,
                    contentDescription = displayItem.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(min = 60.dp, max = 100.dp)
                        .widthIn(max = 360.dp),
                )
            } else {
                Text(
                    text = displayItem.title,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))

            // Meta row
            HeroMetaRow(displayItem)
            Spacer(Modifier.height(10.dp))

            // Overview
            displayItem.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(16.dp))
            } ?: Spacer(Modifier.height(16.dp))

            // CTA buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                var playFocused by remember { mutableStateOf(false) }
                var detailsFocused by remember { mutableStateOf(false) }
                val playScale by animateFloatAsState(
                    targetValue = if (playFocused) 1.06f else 1.0f,
                    animationSpec = tween(180),
                    label = "hero-play-scale",
                )
                val detailsScale by animateFloatAsState(
                    targetValue = if (detailsFocused) 1.06f else 1.0f,
                    animationSpec = tween(180),
                    label = "hero-details-scale",
                )
                Button(
                    onClick = { onPlay(displayItem) },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = OnAccent,
                    ),
                    modifier = Modifier
                        .focusRequester(playFocusRequester)
                        .onFocusChanged { playFocused = it.isFocused }
                        .scale(playScale)
                        .then(
                            if (playFocused)
                                Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                            else Modifier,
                        ),
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_play), fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = { onDetails(displayItem) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .onFocusChanged { detailsFocused = it.isFocused }
                        .scale(detailsScale)
                        .then(
                            if (detailsFocused)
                                Modifier.border(2.dp, Accent, RoundedCornerShape(10.dp))
                            else Modifier,
                        ),
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.home_view_details))
                }
            }
        }
    }
}

@Composable
private fun HeroMetaRow(item: MediaItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item.rating?.let { rating ->
            Text(
                text = "★ ${"%.1f".format(rating)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item.year?.let {
            Text(
                text = it.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.genres.take(3).forEach { genre ->
            Text(
                text = "· $genre",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
