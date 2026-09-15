package com.alex.hubplay.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.exoplayer.ExoPlayer
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.player.PlayerState
import com.alex.hubplay.ui.components.HeroIconButton
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

// ═══════════════════════════════════════════════════════════════════════════
//  Chrome del reproductor de VOD (películas y episodios), todo en Compose.
//
//  Antes el chrome era el PlayerControlView de Media3 centrado en pantalla:
//  en TV el foco del mando lo tenían los botones de Compose de encima y las
//  teclas nunca llegaban al controlador ("toco botones y no hace nada").
//
//   ┌──────────────────────────────────────────────────────────────────┐
//   │                                                        ◌ (buffer)│
//   │                                                                  │
//   │                                                                  │
//   │  LOGO / Título                                                   │
//   │  Serie · T1 · E3                                                 │
//   │  ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │
//   │  55:36                                    1:39:25 · Termina 1:52 │
//   │  (⏸) (↺10) (↻10) (⏭) (CC)                                        │
//   └──────────────────────────────────────────────────────────────────┘
//
//  - Mientras carga: el backdrop del item con el título y una línea de
//    progreso fina, nada de spinner grande en el centro.
//  - Chrome oculto: ← → saltan ±10 s (y lo enseñan), OK lo enseña,
//    Play/Pause del mando alterna. Visible: el foco va a los botones,
//    ← → en la barra saltan, Back lo oculta; se oculta solo a los 4,5 s
//    mientras reproduce (en pausa se queda).
// ═══════════════════════════════════════════════════════════════════════════

/** Lo que el chrome necesita del item; las URLs ya absolutas. */
@androidx.compose.runtime.Immutable
class VodChromeInfo(
    val title:       String?,
    val subtitle:    String?,
    val backdropUrl: String?,
    val logoUrl:     String?,
    val hasNext:     Boolean,
)

/** Acciones del chrome hacia la pantalla. */
@androidx.compose.runtime.Immutable
class VodChromeActions(
    val onOpenTracks:  () -> Unit,
    val onNextEpisode: () -> Unit,
)

@Composable
fun VodPlayerLayer(
    info:        VodChromeInfo,
    exo:         ExoPlayer,
    playerState: PlayerState,
    preparing:   Boolean,
    actions:     VodChromeActions,
) {
    var visible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun touch() { lastInteractionAt = System.currentTimeMillis() }

    // Auto-ocultar solo mientras reproduce: en pausa el usuario quiere ver
    // dónde está.
    LaunchedEffect(lastInteractionAt, visible, playerState.isPlaying) {
        if (!visible || !playerState.isPlaying) return@LaunchedEffect
        val target = lastInteractionAt + AUTO_HIDE_MS
        delay((target - System.currentTimeMillis()).coerceAtLeast(0L))
        if (System.currentTimeMillis() >= lastInteractionAt + AUTO_HIDE_MS) visible = false
    }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(visible) {
        runCatching { if (visible) playFocus.requestFocus() else rootFocus.requestFocus() }
    }

    // El primer frame real: hasta entonces se ve el backdrop, no el negro.
    var firstFrameShown by remember(info.title) { mutableStateOf(false) }
    LaunchedEffect(playerState.isReady) { if (playerState.isReady) firstFrameShown = true }
    val showBackdrop = preparing || !firstFrameShown

    val seek = { deltaMs: Long ->
        val dur = exo.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
        exo.seekTo((exo.currentPosition + deltaMs).coerceIn(0L, dur))
    }
    val togglePlay = { if (exo.isPlaying) exo.pause() else exo.play() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                handleKey(
                    event      = event,
                    visible    = visible,
                    seek       = seek,
                    togglePlay = togglePlay,
                    show       = {
                        visible = true
                        touch()
                    },
                    hide       = {
                        visible = false
                        touch()
                    },
                )
            },
    ) {
        LoadingAndBuffering(info = info, showBackdrop = showBackdrop, buffering = playerState.isBuffering)

        AnimatedVisibility(
            visible  = visible && !showBackdrop,
            enter    = fadeIn(animationSpec = tween(FADE_IN_MS)) +
                slideInVertically(animationSpec = tween(SLIDE_MS), initialOffsetY = { it / SLIDE_FRACTION }),
            exit     = fadeOut(animationSpec = tween(FADE_OUT_MS)) +
                slideOutVertically(animationSpec = tween(SLIDE_MS), targetOffsetY = { it / SLIDE_FRACTION }),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            ControlsBlock(
                info        = info,
                exo         = exo,
                playerState = playerState,
                playFocus   = playFocus,
                actions     = actions,
                callbacks   = ControlsCallbacks(
                    onSeek = { delta ->
                        seek(delta)
                        touch()
                    },
                    onTogglePlay = {
                        togglePlay()
                        touch()
                    },
                    onInteract = ::touch,
                ),
            )
        }
    }
}

/**
 * Teclas con el chrome oculto: ← → saltan y lo enseñan, OK/↑/↓ lo enseñan,
 * Play/Pause alterna. Con el chrome visible: Back lo oculta (se consume
 * aquí, no vía BackHandler, para que el orden de registro no importe),
 * Play/Pause alterna, y el resto refresca el temporizador y sigue su curso
 * hacia el control enfocado.
 */
@Suppress("ReturnCount")
private fun handleKey(
    event:      KeyEvent,
    visible:    Boolean,
    seek:       (Long) -> Unit,
    togglePlay: () -> Unit,
    show:       () -> Unit,
    hide:       () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val isPlayPause = event.key == Key.MediaPlayPause || event.key == Key.MediaPlay || event.key == Key.MediaPause
    if (isPlayPause) {
        togglePlay()
        show()
        return true
    }
    if (visible) {
        if (event.key == Key.Back) {
            hide()
            return true
        }
        show()
        return false
    }
    val seekDelta = when (event.key) {
        Key.DirectionLeft  -> -SEEK_STEP_MS
        Key.DirectionRight -> SEEK_STEP_MS
        else               -> 0L
    }
    val shows = seekDelta != 0L || event.key in SHOW_KEYS
    if (!shows) return false
    if (seekDelta != 0L) seek(seekDelta)
    show()
    return true
}

// ─── Carga ──────────────────────────────────────────────────────────────────

/** Backdrop de carga (fundido) y, ya reproduciendo, la rueda discreta de buffering. */
@Composable
private fun BoxScope.LoadingAndBuffering(info: VodChromeInfo, showBackdrop: Boolean, buffering: Boolean) {
    AnimatedVisibility(
        visible = showBackdrop,
        enter   = fadeIn(animationSpec = tween(FADE_IN_MS)),
        exit    = fadeOut(animationSpec = tween(BACKDROP_FADE_OUT_MS)),
    ) {
        LoadingBackdrop(info = info)
    }
    // Buffering en mitad de la reproducción: discreto, arriba a la derecha.
    if (buffering && !showBackdrop) {
        CircularProgressIndicator(
            color       = Color.White.copy(alpha = BUFFER_SPINNER_ALPHA),
            strokeWidth = 2.dp,
            modifier    = Modifier
                .align(Alignment.TopEnd)
                .padding(28.dp)
                .size(24.dp),
        )
    }
}

/** Backdrop del item con título y una línea de progreso fina mientras arranca. */
@Composable
private fun LoadingBackdrop(info: VodChromeInfo) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (info.backdropUrl != null) {
            AsyncImage(
                model              = info.backdropUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f                 to Color.Black.copy(alpha = LOADING_TOP_ALPHA),
                        LOADING_MID_STOP   to Color.Black.copy(alpha = LOADING_MID_ALPHA),
                        1f                 to Color.Black.copy(alpha = LOADING_BOTTOM_ALPHA),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = CHROME_PADDING, bottom = CHROME_PADDING, end = CHROME_PADDING),
        ) {
            TitleBlock(info = info)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LinearProgressIndicator(
                    color      = Accent,
                    trackColor = Color.White.copy(alpha = TRACK_ALPHA),
                    modifier   = Modifier.width(LOADING_BAR_WIDTH).height(3.dp).clip(CircleShape),
                )
                Text(
                    text     = stringResource(R.string.player_loading),
                    color    = TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

// ─── Controles ──────────────────────────────────────────────────────────────

/** Posición/duración leídas del ExoPlayer a intervalos mientras el chrome está visible. */
private class Playhead {
    var positionMs by mutableLongStateOf(0L)
    var bufferedMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    fun read(exo: ExoPlayer) {
        positionMs = exo.currentPosition.coerceAtLeast(0L)
        bufferedMs = exo.bufferedPosition.coerceAtLeast(0L)
        durationMs = exo.duration.takeIf { it > 0 } ?: 0L
    }
}

/** Lo que los controles devuelven a la capa: saltos, play/pausa y "hubo interacción". */
private class ControlsCallbacks(
    val onSeek:       (Long) -> Unit,
    val onTogglePlay: () -> Unit,
    val onInteract:   () -> Unit,
)

@Composable
private fun ControlsBlock(
    info:        VodChromeInfo,
    exo:         ExoPlayer,
    playerState: PlayerState,
    playFocus:   FocusRequester,
    actions:     VodChromeActions,
    callbacks:   ControlsCallbacks,
) {
    val onSeek       = callbacks.onSeek
    val onTogglePlay = callbacks.onTogglePlay
    val onInteract   = callbacks.onInteract
    val playhead = remember { Playhead() }
    LaunchedEffect(Unit) {
        while (true) {
            playhead.read(exo)
            delay(PLAYHEAD_POLL_MS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = CONTROLS_SCRIM_ALPHA),
                ),
            )
            .padding(start = CHROME_PADDING, end = CHROME_PADDING, bottom = CHROME_PADDING, top = CONTROLS_TOP_FADE),
    ) {
        TitleBlock(info = info)
        Spacer(Modifier.height(14.dp))
        SeekBar(
            playhead = playhead,
            onSeek   = { delta ->
                onSeek(delta)
                playhead.read(exo)
            },
        )
        Spacer(Modifier.height(6.dp))
        TimeRow(playhead = playhead)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val playing = playerState.isPlaying
            HeroIconButton(
                icon               = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
                onClick            = onTogglePlay,
                modifier           = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) onInteract() },
            )
            HeroIconButton(
                icon               = Icons.Filled.Replay10,
                contentDescription = stringResource(R.string.player_rewind_10),
                onClick            = { onSeek(-SEEK_STEP_MS) },
                modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
            )
            HeroIconButton(
                icon               = Icons.Filled.Forward10,
                contentDescription = stringResource(R.string.player_forward_10),
                onClick            = { onSeek(SEEK_STEP_MS) },
                modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
            )
            if (info.hasNext) {
                HeroIconButton(
                    icon               = Icons.Filled.SkipNext,
                    contentDescription = stringResource(R.string.player_next_episode_label),
                    onClick            = actions.onNextEpisode,
                    modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
                )
            }
            HeroIconButton(
                icon               = Icons.Outlined.ClosedCaption,
                contentDescription = stringResource(R.string.player_audio_subtitles),
                onClick            = actions.onOpenTracks,
                modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
            )
        }
    }
}

/** Logo del item (o título) y subtítulo. Compartido por la carga y el chrome. */
@Composable
private fun TitleBlock(info: VodChromeInfo) {
    if (!info.logoUrl.isNullOrBlank()) {
        AsyncImage(
            model              = info.logoUrl,
            contentDescription = info.title,
            contentScale       = ContentScale.Fit,
            alignment          = Alignment.CenterStart,
            modifier           = Modifier.height(LOGO_HEIGHT).widthIn(max = LOGO_MAX_WIDTH),
        )
    } else {
        Text(
            text       = info.title.orEmpty(),
            color      = TextPrimary,
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
    info.subtitle?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            text     = it,
            color    = TextSecondary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Barra de progreso enfocable: ← → saltan ±10 s. Sin pulgar cuando no tiene
 * el foco; con foco, pulgar y barra un poco más alta.
 */
@Composable
private fun SeekBar(playhead: Playhead, onSeek: (Long) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val duration = playhead.durationMs
    val progress = if (duration > 0) (playhead.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val buffered = if (duration > 0) (playhead.bufferedMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val barHeight = if (focused) SEEK_BAR_FOCUSED else SEEK_BAR_HEIGHT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(SEEK_HIT_HEIGHT)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft  -> -SEEK_STEP_MS
                    Key.DirectionRight -> SEEK_STEP_MS
                    else               -> return@onPreviewKeyEvent false
                }
                onSeek(delta)
                true
            }
            .focusable(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = TRACK_ALPHA)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(buffered)
                .height(barHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = BUFFERED_ALPHA)),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(barHeight)
                .clip(CircleShape)
                .background(Accent),
        )
        if (focused) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.fillMaxWidth(progress).fillMaxHeight())
                Box(
                    modifier = Modifier
                        .size(SEEK_THUMB)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
    }
}

/** `55:36` a la izquierda; `1:39:25 · Termina 1:52` a la derecha. */
@Composable
private fun TimeRow(playhead: Playhead) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(text = formatTime(playhead.positionMs), color = TextPrimary, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
        val duration = playhead.durationMs
        if (duration > 0) {
            val remaining = (duration - playhead.positionMs).coerceAtLeast(0L)
            val endsAt = LocalTime.now().plusSeconds(remaining / MS_PER_SECOND).format(END_TIME_FORMAT)
            Text(
                text     = "${formatTime(duration)}  ·  ${stringResource(R.string.player_ends_at, endsAt)}",
                color    = TextSecondary,
                fontSize = 14.sp,
            )
        }
    }
}

/** `h:mm:ss` a partir de una hora, `mm:ss` por debajo. */
internal fun formatTime(ms: Long): String {
    val totalSec = (ms / MS_PER_SECOND).coerceAtLeast(0L)
    val h = totalSec / SECONDS_PER_HOUR
    val m = (totalSec % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val s = totalSec % SECONDS_PER_MINUTE
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)
}

// ─── Constantes ─────────────────────────────────────────────────────────────

private const val AUTO_HIDE_MS = 4_500L
private const val SEEK_STEP_MS = 10_000L

/** Teclas que, con el chrome oculto, solo lo enseñan. */
private val SHOW_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown)
private const val PLAYHEAD_POLL_MS = 500L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

private const val FADE_IN_MS = 220
private const val FADE_OUT_MS = 180
private const val SLIDE_MS = 260
private const val SLIDE_FRACTION = 6
private const val BACKDROP_FADE_OUT_MS = 500

private const val LOADING_TOP_ALPHA = 0.35f
private const val LOADING_MID_STOP = 0.55f
private const val LOADING_MID_ALPHA = 0.45f
private const val LOADING_BOTTOM_ALPHA = 0.92f
private const val CONTROLS_SCRIM_ALPHA = 0.88f
private const val TRACK_ALPHA = 0.22f
private const val BUFFERED_ALPHA = 0.35f
private const val BUFFER_SPINNER_ALPHA = 0.75f

private val CHROME_PADDING = 48.dp
private val CONTROLS_TOP_FADE = 120.dp
private val LOADING_BAR_WIDTH = 220.dp
private val LOGO_HEIGHT = 64.dp
private val LOGO_MAX_WIDTH = 360.dp
private val SEEK_BAR_HEIGHT = 4.dp
private val SEEK_BAR_FOCUSED = 6.dp
private val SEEK_HIT_HEIGHT = 20.dp
private val SEEK_THUMB = 14.dp
private val END_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
