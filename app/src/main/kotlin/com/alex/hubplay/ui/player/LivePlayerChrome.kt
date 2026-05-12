package com.alex.hubplay.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.theme.Accent
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Player chrome overlay — Rakuten TV / Apple TV inspired.
 *
 *   ┌──────────────────────────────────────────────────────────────────────┐
 *   │ ← Volver                                                  21:49      │  top
 *   │                                                                      │
 *   │                                                                      │
 *   │  ┌────────┐    Programa actual                            [★]        │
 *   │  │        │    22:14 — 00:02 · Acción                                │
 *   │  │  LOGO  │    Sinopsis del programa, 2 líneas, con elipsis…         │
 *   │  │        │    ▓▓▓▓▓▓▓░░░░░░░░░░  · 56 min restantes                 │
 *   │  │   ▲    │                                                          │
 *   │  │  127   │                                                          │
 *   │  │   ▼    │                                                          │
 *   │  └────────┘                                                          │
 *   │                                                                      │
 *   │  ↑↓ Cambia canal   ← Favorito   OK Ocultar   ATRÁS Salir             │  hint
 *   └──────────────────────────────────────────────────────────────────────┘
 *
 * The poster column carries the channel logo and a small "▲ N ▼" badge
 * underneath — the arrows are a visual reminder that D-pad up/down
 * changes channel, and the number is the position of the current
 * channel in the library list. Same idea Rakuten Live and Pluto TV use.
 *
 * If the EPG has no entry for "now", we show an elegant fallback that
 * keeps the channel name + favourite affordance visible instead of a
 * lonely "Sin información…" line.
 */
@Composable
fun LivePlayerChrome(
    visible:         Boolean,
    channel:         LiveChannel?,
    title:           String?,
    nowProgram:      EpgProgram?,
    nextProgram:     EpgProgram?,
    nowInstant:      Instant,
    channelPosition: Int,
    totalChannels:   Int,
    isFavorite:      Boolean,
    modifier:        Modifier = Modifier,
) {
    // Track whether the channel logo failed to load so we can collapse
    // the poster slot entirely (vs. leaving an empty grey rectangle).
    // The state is keyed on `channel?.id` so a channel switch always
    // starts a fresh attempt.
    var logoFailed by remember(channel?.id) { mutableStateOf(false) }
    val hasLogo = !channel?.logoUrl.isNullOrBlank() && !logoFailed

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(animationSpec = tween(220)),
            exit    = fadeOut(animationSpec = tween(180)),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopRow(nowInstant = nowInstant)
        }

        AnimatedVisibility(
            visible = visible,
            enter   = fadeIn(animationSpec = tween(220)) +
                      slideInVertically(animationSpec = tween(260), initialOffsetY = { it / 5 }),
            exit    = fadeOut(animationSpec = tween(200)) +
                      slideOutVertically(animationSpec = tween(220), targetOffsetY = { it / 5 }),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            BottomBlock(
                channel         = channel,
                title           = title,
                nowProgram      = nowProgram,
                nextProgram     = nextProgram,
                nowInstant      = nowInstant,
                channelPosition = channelPosition,
                totalChannels   = totalChannels,
                isFavorite      = isFavorite,
                hasLogo         = hasLogo,
                onLogoFailed    = { logoFailed = true },
            )
        }
    }
}

@Composable
private fun TopRow(nowInstant: Instant) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xDD000000), Color.Transparent),
                    ),
                ),
        )
        // Only the clock — the physical Back button on the remote
        // handles "exit player", no on-screen hint needed.
        Row(
            modifier          = Modifier
                .fillMaxWidth()
                .padding(start = 32.dp, end = 32.dp, top = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            ClockBadge(nowInstant = nowInstant)
        }
    }
}

/**
 * Bottom block.
 *
 * Locked to a fixed height (260 dp) and anchored at the bottom-start
 * of its Box, so the content stays glued to the lower edge of the
 * screen regardless of whether the poster slot is present or not.
 * Before this rewrite, a missing logo collapsed the row and the
 * info column rose into the middle of the picture — that's gone now.
 */
@Composable
private fun BottomBlock(
    channel:         LiveChannel?,
    title:           String?,
    nowProgram:      EpgProgram?,
    nextProgram:     EpgProgram?,
    nowInstant:      Instant,
    channelPosition: Int,
    totalChannels:   Int,
    isFavorite:      Boolean,
    hasLogo:         Boolean,
    onLogoFailed:    () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BOTTOM_BLOCK_HEIGHT)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xEE000000)),
                ),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                // Right padding leaves room for the favourite pill
                // anchored to the bottom-right; without this the info
                // column would slide under it on long titles.
                .padding(start = 40.dp, end = 180.dp, bottom = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasLogo) {
                ChannelPosterBadge(
                    channel         = channel,
                    channelPosition = channelPosition,
                    totalChannels   = totalChannels,
                    onLogoFailed    = onLogoFailed,
                )
                Spacer(Modifier.width(22.dp))
            }
            InfoColumn(
                channel              = channel,
                title                = title,
                nowProgram           = nowProgram,
                nextProgram          = nextProgram,
                nowInstant           = nowInstant,
                showInlinePosition   = !hasLogo,
                channelPosition      = channelPosition,
                totalChannels        = totalChannels,
                modifier             = Modifier.weight(1f),
            )
        }

        // Favourite pill anchored to the bottom-right corner of the
        // chrome — independent of how tall / short the info column
        // turns out. Same spot regardless of EPG availability.
        FavouriteIndicator(
            isFavorite = isFavorite,
            modifier   = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 40.dp, bottom = 32.dp),
        )
    }
}

/**
 * Vertical poster + position badge. Caller guarantees the channel has
 * a usable logoUrl. If the image load eventually fails (404 on the
 * proxy / corrupt cache) we notify the parent so it can hide the
 * whole slot and fall back to an inline channel-number pill next to
 * the title.
 */
@Composable
private fun ChannelPosterBadge(
    channel:         LiveChannel?,
    channelPosition: Int,
    totalChannels:   Int,
    onLogoFailed:    () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // No background or rounded clip — the logo sits directly on the
        // bottom-scrim gradient. M3U logos are PNGs with transparency,
        // and a coloured rectangle behind them looked tacked-on.
        AsyncImage(
            model              = channel?.logoUrl,
            contentDescription = channel?.name,
            contentScale       = ContentScale.Fit,
            modifier           = Modifier
                .width(POSTER_WIDTH)
                .height(POSTER_HEIGHT),
            onState            = { state ->
                if (state is AsyncImagePainter.State.Error) {
                    onLogoFailed()
                }
            },
        )
        Spacer(Modifier.height(8.dp))
        if (channelPosition > 0) {
            ChannelArrowsBadge(position = channelPosition, total = totalChannels)
        }
    }
}

@Composable
private fun ChannelArrowsBadge(position: Int, total: Int) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x88000000))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector       = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint              = Color(0xFFB0B7C5),
            modifier          = Modifier.size(14.dp),
        )
        Text(
            text       = if (total > 0) "$position / $total" else position.toString(),
            color      = Color.White,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector       = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint              = Color(0xFFB0B7C5),
            modifier          = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun InfoColumn(
    channel:              LiveChannel?,
    title:                String?,
    nowProgram:           EpgProgram?,
    nextProgram:          EpgProgram?,
    nowInstant:           Instant,
    showInlinePosition:   Boolean,
    channelPosition:      Int,
    totalChannels:        Int,
    modifier:             Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Inline position pill — only shown when the poster slot
            // is collapsed (channel has no logo). Keeps the ▲ N/total
            // ▼ affordance visible regardless of which branch we hit.
            if (showInlinePosition && channelPosition > 0) {
                InlineChannelPositionPill(position = channelPosition, total = totalChannels)
                Spacer(Modifier.width(10.dp))
            }
            val nameLine = buildString {
                append(channel?.name ?: title ?: "Canal en vivo")
                channel?.groupName?.takeIf { it.isNotBlank() }?.let { g ->
                    append("  ·  ").append(g)
                }
            }
            Text(
                text       = nameLine,
                color      = Color(0xFFCBD2DD),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(6.dp))

        if (nowProgram != null) {
            Text(
                text       = nowProgram.title,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = buildMetaLine(nowProgram, nowInstant),
                color    = Color(0xFFCBD2DD),
                fontSize = 13.sp,
            )
            if (!nowProgram.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = nowProgram.description,
                    color      = Color(0xFFB0B7C5),
                    fontSize   = 12.sp,
                    lineHeight = 16.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress   = { nowProgram.progressAt(nowInstant) },
                modifier   = Modifier
                    .fillMaxWidth(0.7f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color      = Accent,
                trackColor = Color(0x44FFFFFF),
            )
            if (nextProgram != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = "DESPUÉS · ${formatTime(nextProgram.startTime)} · ${nextProgram.title}",
                    color      = Color(0xFF8892A5),
                    fontSize   = 10.sp,
                    letterSpacing = 0.6.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        } else {
            // Elegant fallback — no "Sin información del programa",
            // no "Reproduciendo en directo" subline. Just the channel
            // name as the headline; the line above already carries
            // the channel+group context.
            Text(
                text       = channel?.name ?: title ?: "Emisión en directo",
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 24.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 28.sp,
            )
        }
    }
}

@Composable
private fun FavouriteIndicator(
    isFavorite: Boolean,
    modifier:   Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFavorite) Accent.copy(alpha = 0.18f) else Color(0x66000000))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector       = Icons.Filled.Star,
            contentDescription = null,
            tint              = if (isFavorite) Accent else Color(0x80FFFFFF),
            modifier          = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text       = if (isFavorite) "Favorito" else "Añadir",
            color      = if (isFavorite) Accent else Color(0xFFCBD2DD),
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * Horizontal compact version of [ChannelArrowsBadge] — used inline
 * with the channel name when the poster slot is collapsed.
 */
@Composable
private fun InlineChannelPositionPill(position: Int, total: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x66000000))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector       = Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint              = Color(0xFFB0B7C5),
            modifier          = Modifier.size(12.dp),
        )
        Text(
            text       = if (total > 0) "$position/$total" else position.toString(),
            color      = Color.White,
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.padding(horizontal = 4.dp),
        )
        Icon(
            imageVector       = Icons.Filled.KeyboardArrowDown,
            contentDescription = null,
            tint              = Color(0xFFB0B7C5),
            modifier          = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun ClockBadge(nowInstant: Instant) {
    val text = remember(nowInstant.epochSecond / 60L) {
        CLOCK_FORMAT.format(nowInstant.atZone(ZoneId.systemDefault()))
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x99000000))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text       = text,
            color      = Color.White,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

// ─── Local helpers ───────────────────────────────────────────────────────────

private val CLOCK_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val TIME_FORMAT:  DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun formatTime(instant: Instant): String =
    TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private fun buildMetaLine(program: EpgProgram, now: Instant): String {
    val range  = "${formatTime(program.startTime)} – ${formatTime(program.endTime)}"
    val genre  = program.category.takeIf { it.isNotBlank() }
    val left   = remainingLabel(program, now)
    return listOfNotNull(range, genre, left).joinToString("  ·  ")
}

private fun remainingLabel(program: EpgProgram, now: Instant): String {
    val secondsLeft = program.endTime.epochSecond - now.epochSecond
    if (secondsLeft <= 0L) return "Terminando"
    val minutesLeft = (secondsLeft / 60L).coerceAtLeast(1L)
    return when {
        minutesLeft < 60L -> "$minutesLeft min restantes"
        else              -> "${minutesLeft / 60L} h ${minutesLeft % 60L} min restantes"
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

// ── Dimensions ───────────────────────────────────────────────────────────────
//
// Square logo slot: keeps the visual rhythm consistent across channels
// — wide logos (e.g. "24h", "Antena 3") and tall logos (Disney+ leaf)
// both end up centered in the same 100×100 area, so the chrome
// doesn't feel jumpy when zapping between channels with different
// logo proportions.
private val POSTER_WIDTH        = 100.dp
private val POSTER_HEIGHT       = 100.dp
/**
 * Fixed total height of the bottom chrome — the scrim + content
 * column. Setting this lets us anchor the inner Row to the bottom of
 * a known rectangle so the layout doesn't jump up when the channel
 * has no logo.
 */
private val BOTTOM_BLOCK_HEIGHT = 220.dp
