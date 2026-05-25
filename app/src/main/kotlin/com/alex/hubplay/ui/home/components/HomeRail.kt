package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val RailContentPadding = 32.dp
private val EdgeFadeWidth = 24.dp
private val RailScaleHeadroom = 14.dp

private const val SPOTLIGHT_OPEN_DELAY_MS = 1800L
private const val SECTION_SNAP_ANIM_MS = 350

@Composable
fun HomeRail(
    title:           String,
    items:           List<MediaItem>,
    onFocused:       (MediaItem) -> Unit,
    onClick:         (MediaItem) -> Unit,
    parentListState: LazyListState,
    railIndex:       Int,
    style:           CardStyle = CardStyle.Landscape,
    modifier:        Modifier  = Modifier,
) {
    if (items.isEmpty()) return

    val listState    = rememberLazyListState()
    val scope        = rememberCoroutineScope()
    val canSpotlight = style == CardStyle.Portrait

    var focusedIndex         by remember { mutableStateOf<Int?>(null) }
    var spotlightTargetIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedIndex, canSpotlight) {
        if (!canSpotlight) {
            spotlightTargetIndex = null
            return@LaunchedEffect
        }
        val current = focusedIndex
        spotlightTargetIndex = null
        if (current == null) return@LaunchedEffect
        delay(SPOTLIGHT_OPEN_DELAY_MS)
        if (focusedIndex == current) {
            spotlightTargetIndex = current
        }
    }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.scrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    scope.launch {
                        parentListState.animateScrollToItem(
                            index = railIndex,
                        )
                    }
                }
            },
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )

        Box(modifier = Modifier.fillMaxWidth()) {
            LazyRow(
                state                 = listState,
                contentPadding        = PaddingValues(horizontal = RailContentPadding),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier              = Modifier
                    .fillMaxWidth()
                    .horizontalEdgeFade()
                    .padding(vertical = RailScaleHeadroom)
                    .onFocusChanged { focusState ->
                        if (!focusState.hasFocus) focusedIndex = null
                    },
            ) {
                items(
                    count = items.size,
                    key   = { index -> index },
                ) { index ->
                    val item = items[index]
                    val isActiveSlot = canSpotlight && index == spotlightTargetIndex
                    val slotWidth by animateDpAsState(
                        targetValue   = if (isActiveSlot) SpotlightDims.totalWidth else style.defaultWidth,
                        animationSpec = tween(SPOTLIGHT_ANIM_MS),
                        label         = "slot-width",
                    )

                    MediaCard(
                        item        = item,
                        style       = style,
                        slotWidth   = slotWidth,
                        hideContent = isActiveSlot,
                        onFocused   = { focusedItem ->
                            focusedIndex = index
                            onFocused(focusedItem)
                        },
                        onClick     = onClick,
                    )
                }
            }

            if (canSpotlight) {
                val spotlightAlpha by animateFloatAsState(
                    targetValue   = if (spotlightTargetIndex != null) 1f else 0f,
                    animationSpec = tween(SPOTLIGHT_ANIM_MS),
                    label         = "spotlight-alpha",
                )
                val current = spotlightTargetIndex?.let { items.getOrNull(it) }
                if (spotlightAlpha > 0.01f && current != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = RailScaleHeadroom)
                            .width(RailContentPadding)
                            .height(SpotlightDims.height)
                            .alpha(spotlightAlpha)
                            .background(MaterialTheme.colorScheme.background),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = RailContentPadding,
                                top   = RailScaleHeadroom,
                            )
                            .alpha(spotlightAlpha),
                    ) {
                        RailSpotlight(
                            state = SpotlightState(
                                item      = current,
                                direction = 0,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveNowRail(
    title:           String,
    items:           List<MediaItem>,
    onFocused:       (MediaItem) -> Unit,
    onClick:         (MediaItem) -> Unit,
    parentListState: LazyListState,
    railIndex:       Int,
    modifier:        Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val listState  = rememberLazyListState()
    val scope      = rememberCoroutineScope()

    var focusedIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(focusedIndex) {
        val target = focusedIndex ?: return@LaunchedEffect
        listState.scrollToItem(index = target, scrollOffset = 0)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { state ->
                if (state.hasFocus) {
                    scope.launch {
                        parentListState.animateScrollToItem(
                            index = railIndex,
                        )
                    }
                }
            },
    ) {
        Text(
            text       = title,
            style      = MaterialTheme.typography.titleLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = RailContentPadding, vertical = 8.dp),
        )

        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = RailContentPadding),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier              = Modifier
                .fillMaxWidth()
                .horizontalEdgeFade()
                .padding(vertical = RailScaleHeadroom)
                .onFocusChanged { focusState ->
                    if (!focusState.hasFocus) focusedIndex = null
                },
        ) {
            items(
                count = items.size,
                key   = { index -> index },
            ) { index ->
                val item = items[index]
                LiveChannelCard(
                    item      = item,
                    onFocused = { focusedItem ->
                        focusedIndex = index
                        onFocused(focusedItem)
                    },
                    onClick   = onClick,
                )
            }
        }
    }
}

private fun Modifier.horizontalEdgeFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val fadePx = EdgeFadeWidth.toPx().coerceAtMost(size.width / 2f)
        if (fadePx <= 0f) return@drawWithContent
        val leftStop  = fadePx / size.width
        val rightStop = 1f - leftStop
        drawRect(
            brush = Brush.horizontalGradient(
                0f        to Color.Transparent,
                leftStop  to Color.Black,
                rightStop to Color.Black,
                1f        to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
