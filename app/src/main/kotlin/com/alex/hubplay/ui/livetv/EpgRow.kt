package com.alex.hubplay.ui.livetv

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgCard
import com.alex.hubplay.ui.theme.BgOverlay
import com.alex.hubplay.ui.theme.BgSurface
import com.alex.hubplay.ui.theme.Border
import com.alex.hubplay.ui.theme.TextMuted
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Single EPG row — channel header on the left, scrolling programme
 * strip on the right.
 *
 * Visual rules:
 *
 *   - The row itself is transparent (sits on the page BgBase).
 *   - Idle cells use [BgCard] so they read as "info cards" on top of
 *     the BgBase page — the user can see at a glance where one cell
 *     ends and the next begins without needing a border.
 *   - Focused cells lift one more step to [BgOverlay] + Accent border.
 *   - Live programme (not focused) keeps the BgCard fill and adds a
 *     soft Accent border so the "what's airing now" cell is the most
 *     prominent thing on the row even at a glance.
 *
 * Behaviour:
 *   - Focus on ANY programme cell of this row triggers [onFocused]
 *     so the hero up top updates to this channel.
 *   - Short OK → play. Long OK on the header → toggle favourite.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpgRow(
    channel:          LiveChannel,
    programs:         List<EpgProgram>,
    now:              Instant,
    isFavorite:       Boolean,
    onFocused:        (LiveChannel) -> Unit,
    onPlay:           (LiveChannel) -> Unit,
    onToggleFavorite: (LiveChannel) -> Unit,
    modifier:         Modifier = Modifier,
) {
    Row(
        modifier          = modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChannelHeader(
            channel     = channel,
            isFavorite  = isFavorite,
            onClick     = { onPlay(channel) },
            onLongClick = { onToggleFavorite(channel) },
            onFocused   = { onFocused(channel) },
        )
        Spacer(Modifier.width(6.dp))
        ProgramsStrip(
            channel    = channel,
            programs   = programs,
            now        = now,
            onFocused  = { onFocused(channel) },
            onPlay     = { onPlay(channel) },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChannelHeader(
    channel:     LiveChannel,
    isFavorite:  Boolean,
    onClick:     () -> Unit,
    onLongClick: () -> Unit,
    onFocused:   () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.015f else 1.0f,
        animationSpec = tween(140),
        label         = "channel-header-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    val bg = if (focused) BgOverlay else BgCard

    Row(
        modifier = Modifier
            .width(HEADER_WIDTH)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else         Modifier,
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
                onLongClick       = onLongClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderLogo(channel = channel)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (channel.number > 0) {
                    Text(
                        text       = channel.number.toString().padStart(2, '0'),
                        color      = TextMuted,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (isFavorite) {
                    Icon(
                        imageVector       = Icons.Filled.Star,
                        contentDescription = "Favorito",
                        tint              = Accent,
                        modifier          = Modifier.size(12.dp),
                    )
                }
            }
            Text(
                text       = channel.name,
                color      = TextPrimary,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun HeaderLogo(channel: LiveChannel) {
    // Neutral placeholder always — the coloured logo_bg / logo_fg
    // pair from the backend produced too many "saturated coloured
    // squares" in the UI when channels lacked a real tvg-logo. A
    // single muted slot is calmer and lets the real logos pop when
    // they do exist.
    Box(
        modifier = Modifier
            .size(LOGO_SIZE)
            .clip(RoundedCornerShape(6.dp))
            .background(BgSurface),
        contentAlignment = Alignment.Center,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model              = channel.logoUrl,
                contentDescription = channel.name,
                contentScale       = ContentScale.Fit,
                modifier           = Modifier.fillMaxSize().padding(5.dp),
            )
        } else {
            Text(
                text       = (channel.logoInitials ?: initialsFromName(channel.name)).take(2),
                color      = TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize   = 12.sp,
            )
        }
    }
}

@Composable
private fun ProgramsStrip(
    channel:   LiveChannel,
    programs:  List<EpgProgram>,
    now:       Instant,
    onFocused: () -> Unit,
    onPlay:    () -> Unit,
) {
    val upcoming = remember(programs, now) {
        programs.filter { it.endTime.isAfter(now) }
    }
    val liveIndex = remember(upcoming, now) {
        upcoming.indexOfFirst { it.isLiveAt(now) }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState()

    LaunchedEffect(channel.id, upcoming.size) {
        if (upcoming.isNotEmpty()) {
            listState.scrollToItem(liveIndex)
        }
    }

    if (upcoming.isEmpty()) {
        EmptyEpgCell(onFocused = onFocused, onPlay = onPlay)
        return
    }

    LazyRow(
        state                 = listState,
        modifier              = Modifier.fillMaxHeight().fillMaxWidth(),
        contentPadding        = PaddingValues(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(upcoming, key = { "${channel.id}_${it.id.ifBlank { it.startTime.toString() }}" }) { program ->
            EpgProgramCell(
                program   = program,
                isLive    = program.isLiveAt(now),
                now       = now,
                onFocused = onFocused,
                onPlay    = onPlay,
            )
        }
    }
}

@Composable
private fun EmptyEpgCell(
    onFocused: () -> Unit,
    onPlay:    () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val bg = if (focused) BgOverlay else BgCard

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else         Modifier,
            )
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onPlay,
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text     = "Sin guía electrónica · Pulsa OK para reproducir",
            color    = TextMuted,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun EpgProgramCell(
    program:   EpgProgram,
    isLive:    Boolean,
    now:       Instant,
    onFocused: () -> Unit,
    onPlay:    () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.015f else 1.0f,
        animationSpec = tween(140),
        label         = "program-cell-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }

    // Idle cells are BgCard; focused lifts one more step to BgOverlay.
    // The live cell stays at BgCard but gets a soft Accent border so
    // the eye still finds it immediately on a static screen.
    val bg = if (focused) BgOverlay else BgCard
    val borderMod: Modifier = when {
        focused -> Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
        isLive  -> Modifier.border(1.5.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
        else    -> Modifier
    }

    Column(
        modifier = Modifier
            .width(CELL_WIDTH)
            .fillMaxHeight()
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(borderMod)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused()
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onPlay,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text       = program.title,
                color      = TextPrimary,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )
            if (program.category.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text     = program.category,
                    color    = TextMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column {
            if (isLive) {
                LinearProgressIndicator(
                    progress   = { program.progressAt(now) },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color      = Accent,
                    trackColor = Border,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text     = remainingShort(program, now),
                    color    = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text     = "${formatTime(program.startTime)} – ${formatTime(program.endTime)}",
                    color    = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

// ─── Helpers ───────────────────────────────────────────────────────────

private fun formatTime(instant: Instant): String =
    EPG_TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private val EPG_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun remainingShort(program: EpgProgram, now: Instant): String {
    val secondsLeft = program.endTime.epochSecond - now.epochSecond
    if (secondsLeft <= 0L) return "Fin"
    val minutesLeft = (secondsLeft / 60L).coerceAtLeast(1L)
    return when {
        minutesLeft < 60L -> "$minutesLeft min restantes"
        else              -> "${minutesLeft / 60L} h ${minutesLeft % 60L} min"
    }
}

// ── Dimensions ───────────────────────────────────────────────────────────────
private val ROW_HEIGHT    = 84.dp
private val HEADER_WIDTH  = 220.dp
private val LOGO_SIZE     = 54.dp
private val CELL_WIDTH    = 220.dp
