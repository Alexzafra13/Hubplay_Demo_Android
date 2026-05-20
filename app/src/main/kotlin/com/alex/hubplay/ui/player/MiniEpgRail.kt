package com.alex.hubplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.theme.Accent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Horizontal carousel of channel cards that lives above the player.
 *
 * The carousel is meant for **zap-by-preview** UX: the user can see
 * what's airing on neighbouring channels (and their own current one)
 * without leaving the video. D-pad ←/→ scrolls through; OK on a card
 * switches to that channel — same affordance Movistar+ Box and Sky Q
 * use for "channel pop".
 *
 * Three card states:
 *   - **Current** — the channel the player is on. Outlined in Accent,
 *     slightly taller, "● EN VIVO" badge in the corner.
 *   - **Focused (D-pad)** — what the remote is hovering. Brighter
 *     border, slight scale. Distinct from "current".
 *   - **Neighbour** — every other card.
 *
 * The rail centers the current channel on first show; the screen also
 * passes a [FocusRequester] so D-pad UP/DOWN events from the player
 * shell can hand focus to the rail.
 */
@Composable
fun MiniEpgRail(
    channels:        List<LiveChannel>,
    currentChannel:  LiveChannel?,
    nowInstant:      Instant,
    nowProgramOf:    (String) -> EpgProgram?,
    onPickChannel:   (LiveChannel) -> Unit,
    focusRequester:  FocusRequester,
    modifier:        Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Center the current channel on first composition. Otherwise the
    // user opens the carousel and sees channel 1; jarring when they're
    // watching channel 35.
    LaunchedEffect(channels, currentChannel?.id) {
        val idx = channels.indexOfFirst { it.id == currentChannel?.id }
        if (idx >= 0) {
            // Scroll so the current card sits near the start (a third in).
            val anchor = (idx - 2).coerceAtLeast(0)
            listState.scrollToItem(anchor)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().height(170.dp),
    ) {
        LazyRow(
            state                 = listState,
            contentPadding        = PaddingValues(horizontal = 32.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier              = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                val isCurrent = channel.id == currentChannel?.id
                MiniEpgCard(
                    channel        = channel,
                    nowProgram     = nowProgramOf(channel.id),
                    nowInstant     = nowInstant,
                    isCurrent      = isCurrent,
                    onClick        = { onPickChannel(channel) },
                    listState      = listState,
                    indexInRail    = index,
                    focusRequester = focusRequester.takeIf { isCurrent },
                )
            }
        }
    }
}

@Composable
private fun MiniEpgCard(
    channel:        LiveChannel,
    nowProgram:     EpgProgram?,
    nowInstant:     Instant,
    isCurrent:      Boolean,
    onClick:        () -> Unit,
    listState:      LazyListState,
    indexInRail:    Int,
    focusRequester: FocusRequester?,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val borderColor = when {
        focused && isCurrent -> Accent
        focused              -> Accent
        isCurrent            -> Accent.copy(alpha = 0.55f)
        else                 -> Color(0xFF2C3340)
    }
    val borderWidth = if (focused) 2.dp else if (isCurrent) 1.5.dp else 1.dp
    val bg = when {
        focused   -> Color(0xFF1B2230)
        isCurrent -> Color(0xFF161D2A)
        else      -> Color(0xFF11151D)
    }

    // When focus lands on the focused card, scroll it into view (the
    // initial center already happened on rail open, but D-pad LEFT
    // off-screen needs this too).
    LaunchedEffect(focused) {
        if (focused) {
            listState.animateScrollToItem(
                index        = indexInRail,
                scrollOffset = -120,
            )
        }
    }

    Column(
        modifier = Modifier
            .width(CARD_WIDTH)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            )
            .padding(10.dp)
            .let { mod -> focusRequester?.let { mod.focusRequester(it) } ?: mod },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLogo(channel = channel)
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = if (channel.number > 0) channel.number.toString().padStart(2, '0') else "—",
                    color      = Color(0xFF7B8597),
                    fontSize   = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text       = channel.name,
                    color      = Color.White,
                    fontSize   = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                LiveDot()
            }
        }
        Spacer(Modifier.height(8.dp))
        if (nowProgram != null) {
            Text(
                text     = formatTime(nowProgram.startTime),
                color    = Color(0xFF8892A5),
                fontSize = 10.sp,
            )
            Text(
                text     = nowProgram.title,
                color    = Color(0xFFCBD2DD),
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress   = { nowProgram.progressAt(nowInstant) },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color      = Accent,
                trackColor = Color(0x553A4150),
            )
        } else {
            Text(
                text     = stringResource(R.string.livetv_epg_no_guide_short),
                color    = Color(0xFF6A7488),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun CardLogo(channel: LiveChannel) {
    Box(
        modifier = Modifier
            .size(LOGO_SIZE)
            .clip(RoundedCornerShape(6.dp))
            .background(parseHex(channel.logoBg) ?: Color(0xFF1F2530)),
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model              = channel.logoUrl,
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize().padding(4.dp),
            )
        } else {
            Text(
                text       = (channel.logoInitials ?: initialsFromName(channel.name)).take(2),
                color      = parseHex(channel.logoFg) ?: Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 11.sp,
            )
        }
    }
}

@Composable
private fun LiveDot() {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFFF3B30)),
    )
}

// ─── Local helpers (small enough not to deserve a shared util module) ──

private fun formatTime(instant: Instant): String =
    TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun parseHex(s: String?): Color? {
    if (s.isNullOrBlank()) return null
    return try {
        val hex = s.trim().removePrefix("#")
        when (hex.length) {
            6 -> Color(("ff$hex").toLong(16))
            8 -> Color(hex.toLong(16))
            else -> null
        }
    } catch (_: Throwable) {
        null
    }
}

private fun initialsFromName(name: String): String {
    val cleaned = name.trim()
    if (cleaned.isEmpty()) return "TV"
    val tokens = cleaned.split(Regex("\\s+"))
    return when {
        tokens.size >= 2     -> "${tokens[0].first()}${tokens[1].first()}".uppercase()
        cleaned.length >= 2  -> cleaned.take(2).uppercase()
        else                 -> cleaned.uppercase()
    }
}

private val CARD_WIDTH  = 200.dp
private val CARD_HEIGHT = 130.dp
private val LOGO_SIZE   = 36.dp
