package com.alex.hubplay.ui.livetv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.alex.hubplay.R
import com.alex.hubplay.data.AuthState
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Hero band — Pluto TV / Rakuten TV / Xiaomi TV+ inspired.
 *
 * Three Z-layers, *not* a Row of bordered boxes:
 *
 *   ╔═══════════════════════════════════════════════════════════════════╗
 *   ║ ░░░░░░░░░░░░░░░░░░░░░░░░░░░ ▒▒▒▒▒▒▒▒▒░░░░░░░░░ ←  L0  preview     ║
 *   ║ ░░░░░░░░░░░░░░░░░░░░░░░░░░░ ▒▒▒▒▒▒▒▒▒░░░░░░░░░    full-height,    ║
 *   ║ ░░░░░░░░░░░░░░░░░░░░░░░░░░░ ▒▒▒▒▒▒▒▒▒░░░░░░░░░    right 60%       ║
 *   ║                                                                   ║
 *   ║ ██████████████████████████ ░░░░░░░░░░░░░░░░░░░ ←  L1  fade        ║
 *   ║ ██████████████████████████ ░░░░░░░░░░░░░░░░░░░    BgBase L→R      ║
 *   ║                                                                   ║
 *   ║ [POSTER] EN VIVO  Canal · Género                ←  L2  content    ║
 *   ║          Programa actual                            poster + info ║
 *   ║          ▓▓▓░░  18 min restantes                                  ║
 *   ╚═══════════════════════════════════════════════════════════════════╝
 *
 * Crucial: there are NO bounding boxes around the preview or the info.
 * Everything sits on the same [BgBase] colour and the fade is what
 * separates the video from the text — same trick Movistar+ / Rakuten
 * TV use. The video bleeds into the page background; no rectangle
 * outline, no inset card.
 *
 * AnimatedContent crossfades the poster + info when the focused
 * channel changes; the video preview manages its own debounce.
 */
@Composable
fun LiveHero(
    channel:      LiveChannel?,
    nowProgram:   EpgProgram?,
    nextProgram:  EpgProgram?,
    nowInstant:   Instant,
    authState:    AuthState,
    okHttpClient: okhttp3.OkHttpClient,
    onAutoTune:   ((LiveChannel) -> Unit)? = null,
    modifier:     Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(HERO_HEIGHT)
            // Mandatory: PlayerView with RESIZE_MODE_ZOOM scales the
            // video to fill its container — and the overflow ISN'T
            // clipped by default. Without this clipToBounds, the video
            // bleeds over the TopNav above and the first EPG row below.
            .clipToBounds()
            .background(BgBase),
    ) {
        // ── L0 · Preview video — bleeds into the right edge ──────────
        // Width chosen so the video starts past the info column and
        // ends at the screen edge. No background, no clip, no border:
        // the video is the only thing visible here.
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .fillMaxWidth(VIDEO_FRACTION)
                .clipToBounds(),
        ) {
            ChannelPreviewPlayer(
                channel      = channel,
                authState    = authState,
                okHttpClient = okHttpClient,
                onAutoTune   = onAutoTune,
                modifier     = Modifier.fillMaxSize(),
                fallback     = { PreviewFallback(channel = channel, nowProgram = nowProgram) },
            )
        }

        // ── L1 · Horizontal fade — soft dissolves the video's left
        //     edge into the page background. The opacity stops are
        //     hand-tuned so the title side reads cleanly while the
        //     right side of the video stays untouched.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.horizontalGradient(
                        0.00f to BgBase,
                        0.42f to BgBase,
                        0.55f to BgBase.copy(alpha = 0.85f),
                        0.72f to BgBase.copy(alpha = 0.30f),
                        1.00f to Color.Transparent,
                    ),
                ),
        )

        // ── L2 · Poster + info ───────────────────────────────────────
        // Fixed to the left ~50% so the info column never overlaps the
        // visible part of the video.
        //
        // The poster used to be a permanent slot with a coloured
        // fallback when the logo failed to load — that produced
        // visible empty squares for channels whose tvg-logo URL
        // resolved to a 404 on the proxy. Now we render the image
        // bare (no background) and the parent removes the slot
        // entirely on load failure. Looks like Pluto / Apple TV:
        // "logo if exists, nothing otherwise".
        AnimatedContent(
            targetState = channel?.id ?: "__empty__",
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith
                fadeOut(animationSpec = tween(180))
            },
            label    = "hero-content",
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(CONTENT_FRACTION),
        ) { _ ->
            HeroContentRow(channel = channel, nowProgram = nowProgram, nextProgram = nextProgram, nowInstant = nowInstant)
        }
    }
}

/**
 * The animated content of the hero. Split out so we can host the
 * `artFailed` state safely under the AnimatedContent target — the
 * state resets cleanly when the channel changes (the lambda recomposes
 * with a new `channel?.id` key).
 */
@Composable
private fun HeroContentRow(
    channel:     LiveChannel?,
    nowProgram:  EpgProgram?,
    nextProgram: EpgProgram?,
    nowInstant:  Instant,
) {
    val artSource = nowProgram?.iconUrl?.takeIf { it.isNotBlank() }
        ?: channel?.logoUrl?.takeIf { it.isNotBlank() }
    var artFailed by remember(artSource) { mutableStateOf(false) }
    val showArt = artSource != null && !artFailed
    val useCrop = !nowProgram?.iconUrl.isNullOrBlank()

    Row(
        modifier          = Modifier
            .fillMaxSize()
            .padding(start = 32.dp, end = 12.dp, top = 18.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showArt) {
            AsyncImage(
                model              = artSource,
                contentDescription = channel?.name,
                contentScale       = if (useCrop) ContentScale.Crop else ContentScale.Fit,
                modifier           = Modifier
                    .width(HERO_POSTER_WIDTH)
                    .height(HERO_POSTER_HEIGHT)
                    .then(
                        // Crop mode benefits from the rounded clip
                        // because the image fills the slot fully;
                        // Fit-mode (channel logos) gets no clip so
                        // logos with transparency don't show a soft
                        // rectangle around them.
                        if (useCrop) Modifier.clip(RoundedCornerShape(10.dp)) else Modifier,
                    ),
                onState = { state ->
                    if (state is AsyncImagePainter.State.Error) {
                        artFailed = true
                    }
                },
            )
            Spacer(Modifier.width(20.dp))
        }
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            HeroInfo(
                channel     = channel,
                nowProgram  = nowProgram,
                nextProgram = nextProgram,
                nowInstant  = nowInstant,
            )
        }
    }
}

@Composable
private fun HeroInfo(
    channel:     LiveChannel?,
    nowProgram:  EpgProgram?,
    nextProgram: EpgProgram?,
    nowInstant:  Instant,
) {
    Column(
        modifier            = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.Center,
    ) {
        // Single-line "Canal · Género" so long channel names (e.g.
        // "3Cat Exclusiu 3 (1080p) [Geo-blocked]") don't squeeze the
        // group label and rotate it into vertical letters. The whole
        // line truncates with ellipsis on its own line.
        val defaultName = stringResource(R.string.livetv_hero_default_title)
        val titleLine = buildString {
            append(channel?.name ?: defaultName)
            channel?.groupName?.takeIf { it.isNotBlank() }?.let { g ->
                append("  ·  ").append(g)
            }
        }
        Text(
            text       = titleLine,
            color      = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 13.sp,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        if (nowProgram != null) {
            Text(
                text       = nowProgram.title,
                color      = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize   = 22.sp,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                lineHeight = 26.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = buildMetaLine(nowProgram, nowInstant, remainingLabel(nowProgram, nowInstant)),
                color    = Color(0xFFCBD2DD),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress   = { nowProgram.progressAt(nowInstant) },
                modifier   = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color      = Accent,
                trackColor = Color(0x55FFFFFF),
            )
            if (!nowProgram.description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = nowProgram.description,
                    color      = Color(0xFFB0B7C5),
                    fontSize   = 11.sp,
                    lineHeight = 15.sp,
                    maxLines   = 2,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
            if (nextProgram != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text       = stringResource(R.string.livetv_after_program_format, formatTime(nextProgram.startTime), nextProgram.title),
                    color      = Color(0xFF7B8597),
                    fontSize   = 10.sp,
                    letterSpacing = 0.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
            }
        } else if (channel != null) {
            Text(
                text       = stringResource(R.string.livetv_hero_no_program_title),
                color      = Color.White,
                fontSize   = 18.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text     = stringResource(R.string.livetv_hero_no_program_hint),
                color    = Color(0xFF8892A5),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Rendered behind the preview while it warms up (or on failure), so
 * the hero's right side never goes black. No background, no rounded
 * corners — the [LiveHero] fade does the visual integration with the
 * page background.
 */
@Composable
private fun PreviewFallback(
    channel:    LiveChannel?,
    nowProgram: EpgProgram?,
) {
    // No bg colour, no coloured initials — when there's nothing to
    // show, the slot stays empty and the hero's fade dissolves the
    // empty area into the page background. Same rule as the poster:
    // we never paint a saturated coloured square here.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            !nowProgram?.iconUrl.isNullOrBlank() -> {
                AsyncImage(
                    model              = nowProgram?.iconUrl,
                    contentDescription = nowProgram?.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            !channel?.logoUrl.isNullOrBlank() -> {
                AsyncImage(
                    model              = channel?.logoUrl,
                    contentDescription = channel?.name,
                    contentScale       = ContentScale.Fit,
                    modifier           = Modifier.fillMaxSize().padding(28.dp),
                )
            }
            // No art at all → render nothing. The hero looks
            // perfectly fine without a backdrop on the right side.
            else -> {}
        }
    }
}

private fun buildMetaLine(program: EpgProgram, now: Instant, remaining: String): String {
    val range  = "${formatTime(program.startTime)} – ${formatTime(program.endTime)}"
    val genre  = program.category.takeIf { it.isNotBlank() }
    return listOfNotNull(range, genre, remaining).joinToString("  ·  ")
}

@androidx.compose.runtime.Composable
private fun remainingLabel(program: EpgProgram, now: Instant): String {
    val secondsLeft = program.endTime.epochSecond - now.epochSecond
    if (secondsLeft <= 0L) return stringResource(R.string.livetv_remaining_ending)
    val minutesLeft = (secondsLeft / 60L).coerceAtLeast(1L)
    return when {
        minutesLeft < 60L -> stringResource(R.string.livetv_remaining_minutes, minutesLeft)
        else              -> stringResource(R.string.livetv_remaining_hours_minutes, minutesLeft / 60L, minutesLeft % 60L)
    }
}

private fun formatTime(instant: Instant): String =
    TIME_FORMAT.format(instant.atZone(ZoneId.systemDefault()))

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

// Shared util — also used by ChannelRow / EpgRow / LivePlayerChrome.
internal fun parseHex(s: String?): Color? {
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

internal fun initialsFromName(name: String): String {
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
private val HERO_HEIGHT        = 220.dp
private val HERO_POSTER_WIDTH  = 110.dp
private val HERO_POSTER_HEIGHT = 175.dp

/** Right-side video band as a fraction of the hero's width. */
private const val VIDEO_FRACTION   = 0.60f
/** Left-side content band (poster + info). Slight overlap with the
 *  video band is desired — the fade gradient is what hides the seam. */
private const val CONTENT_FRACTION = 0.52f
