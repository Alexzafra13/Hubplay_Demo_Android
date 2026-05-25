package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.alex.hubplay.ui.theme.OnAccent
import com.alex.hubplay.ui.theme.TextSecondary

@Composable
fun HeroInfo(
    item:         MediaItem?,
    onPlay:       (MediaItem?) -> Unit,
    onDetails:    (MediaItem?) -> Unit,
    showControls: Boolean,
    modifier:     Modifier = Modifier,
) {
    if (item == null) return

    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(showControls) {
        if (showControls) {
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier
            .heightIn(min = 340.dp)
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        AnimatedContent(
            targetState = item,
            label = "hero-info",
            transitionSpec = {
                (fadeIn(tween(400)) togetherWith fadeOut(tween(250)))
            },
        ) { displayItem ->
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(start = 24.dp, end = 32.dp, bottom = 4.dp),
            ) {
                // Title — large and bold like Prime Video
                if (!displayItem.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = displayItem.logoUrl,
                        contentDescription = displayItem.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(min = 48.dp, max = 90.dp)
                            .widthIn(max = 400.dp),
                    )
                } else {
                    Text(
                        text = displayItem.title,
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 52.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Meta row: genre · duration · year · rating
                HeroMetaRow(displayItem)

                // Overview
                displayItem.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCCCFD6),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp,
                    )
                }

                // CTA buttons
                if (showControls) {
                    Spacer(Modifier.height(16.dp))
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
    }
}

@Composable
private fun HeroMetaRow(item: MediaItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val parts = mutableListOf<@Composable () -> Unit>()

        item.genres.firstOrNull()?.let { genre ->
            parts.add {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        if (item.durationSec > 0) {
            val mins = item.durationSec / 60
            parts.add {
                Text(
                    text = "${mins} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        item.year?.let { year ->
            parts.add {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        item.rating?.let { rating ->
            parts.add {
                Text(
                    text = "★ ${"%.1f".format(rating)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        parts.forEachIndexed { index, composable ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            composable()
        }
    }
}
