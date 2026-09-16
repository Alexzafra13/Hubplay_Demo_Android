package com.alex.hubplay.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
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
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Subtitles
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
    val onOpenAudio:     () -> Unit,
    val onOpenSubtitles: () -> Unit,
    val onNextEpisode:   () -> Unit,
)

@Composable
fun VodPlayerLayer(
    info:        VodChromeInfo,
    exo:         ExoPlayer,
    playerState: PlayerState,
    preparing:   Boolean,
    actions:     VodChromeActions,
    /** Hoja de audio/subtítulos abierta: el chrome no se oculta y, al cerrarla, el foco vuelve al icono. */
    sheetOpen:   Boolean = false,
) {
    var visible by remember { mutableStateOf(true) }
    var lastInteractionAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    fun touch() { lastInteractionAt = System.currentTimeMillis() }

    // Auto-ocultar solo mientras reproduce: en pausa el usuario quiere ver
    // dónde está.
    LaunchedEffect(sheetOpen) { if (!sheetOpen) touch() }
    LaunchedEffect(lastInteractionAt, visible, playerState.isPlaying, sheetOpen) {
        if (!visible || !playerState.isPlaying || sheetOpen) return@LaunchedEffect
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
        ChromeOverlay(
            state       = ChromeVisibility(shown = visible && !showBackdrop, sheetOpen = sheetOpen),
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

/** Si el chrome se ve y si hay una hoja (audio/subtítulos) abierta encima. */
private class ChromeVisibility(val shown: Boolean, val sheetOpen: Boolean)

/** Título arriba a la izquierda y controles abajo, con sus fundidos. */
@Composable
private fun BoxScope.ChromeOverlay(
    state:       ChromeVisibility,
    info:        VodChromeInfo,
    exo:         ExoPlayer,
    playerState: PlayerState,
    playFocus:   FocusRequester,
    actions:     VodChromeActions,
    callbacks:   ControlsCallbacks,
) {
    // Ocultar: el logo se va hacia arriba mientras los controles bajan,
    // se desvanecen y se encogen un pelín (como si se apagaran). Mostrar:
    // suben con frenada suave.
    val shown = state.shown
    AnimatedVisibility(
        visible  = shown,
        enter    = fadeIn(animationSpec = tween(FADE_IN_MS)) +
            slideInVertically(animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing)) { -it / SLIDE_FRACTION },
        exit     = fadeOut(animationSpec = tween(HIDE_MS)) +
            slideOutVertically(animationSpec = tween(HIDE_MS, easing = FastOutLinearInEasing)) { -it / SLIDE_FRACTION },
        modifier = Modifier.align(Alignment.TopStart),
    ) {
        TopTitle(info = info)
    }
    AnimatedVisibility(
        visible  = shown,
        enter    = fadeIn(animationSpec = tween(FADE_IN_MS)),
        exit     = fadeOut(animationSpec = tween(HIDE_MS)),
        modifier = Modifier.align(Alignment.TopEnd),
    ) {
        ClockCorner()
    }
    AnimatedVisibility(
        visible  = shown,
        enter    = fadeIn(animationSpec = tween(FADE_IN_MS)) +
            slideInVertically(animationSpec = tween(SLIDE_MS, easing = FastOutSlowInEasing)) { it / SLIDE_FRACTION },
        exit     = fadeOut(animationSpec = tween(HIDE_MS)) +
            slideOutVertically(animationSpec = tween(HIDE_MS, easing = FastOutLinearInEasing)) { it / SLIDE_FRACTION } +
            scaleOut(animationSpec = tween(HIDE_MS), targetScale = HIDE_SCALE, transformOrigin = TransformOrigin(0f, 1f)),
        modifier = Modifier.align(Alignment.BottomStart),
    ) {
        ControlsBlock(
            info        = info,
            exo         = exo,
            playerState = playerState,
            playFocus   = playFocus,
            actions     = actions,
            callbacks   = callbacks,
            sheetOpen   = state.sheetOpen,
        )
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
    // Zoom lentísimo del backdrop (Ken Burns) mientras carga: una capa
    // gráfica, sin recomponer. Da vida a la espera sin distraer.
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        zoom.animateTo(KEN_BURNS_SCALE, tween(KEN_BURNS_MS, easing = LinearEasing))
    }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (info.backdropUrl != null) {
            AsyncImage(
                model              = info.backdropUrl,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.value
                        scaleY = zoom.value
                    },
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
        TopTitle(info = info)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = CHROME_PADDING, bottom = CHROME_BOTTOM, end = CHROME_PADDING),
        ) {
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
    sheetOpen:   Boolean,
) {
    val onSeek   = callbacks.onSeek
    val playhead = remember { Playhead() }
    // El foco va a Play en cuanto existen los botones: al entrar, el chrome
    // ya está visible pero los controles se montan tras el primer frame, y
    // la petición de foco anterior se había perdido en el vacío.
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
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
            .padding(start = CHROME_PADDING, end = CHROME_PADDING, bottom = CHROME_BOTTOM, top = CONTROLS_TOP_FADE),
    ) {
        SeekBar(
            playhead = playhead,
            onSeek   = { delta ->
                onSeek(delta)
                playhead.read(exo)
            },
        )
        Spacer(Modifier.height(2.dp))
        TimeRow(playhead = playhead)
        Spacer(Modifier.height(8.dp))
        ControlButtons(info, playerState, playFocus, actions, callbacks, sheetOpen)
    }
}

/** Fila de botones: play/pausa, −30, +30, siguiente episodio, audio, subtítulos. */
@Composable
private fun ControlButtons(
    info:        VodChromeInfo,
    playerState: PlayerState,
    playFocus:   FocusRequester,
    actions:     VodChromeActions,
    callbacks:   ControlsCallbacks,
    sheetOpen:   Boolean,
) {
    val onSeek       = callbacks.onSeek
    val onTogglePlay = callbacks.onTogglePlay
    val onInteract   = callbacks.onInteract
    // Al cerrar la hoja, el foco vuelve al icono que la abrió (audio o
    // subtítulos), no a Play: así se abren las dos seguidas sin navegar.
    val audioFocus = remember { FocusRequester() }
    val subsFocus  = remember { FocusRequester() }
    var opener by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(sheetOpen) {
        if (!sheetOpen) opener?.let { runCatching { it.requestFocus() } }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val playing = playerState.isPlaying
        HeroIconButton(
            icon               = Icons.Filled.Replay30,
            contentDescription = stringResource(R.string.player_rewind_30),
            onClick            = { onSeek(-SEEK_STEP_MS) },
            modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
            size               = CONTROL_BUTTON,
        )
        HeroIconButton(
            icon               = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = stringResource(if (playing) R.string.player_pause else R.string.player_play),
            onClick            = onTogglePlay,
            modifier           = Modifier.focusRequester(playFocus).onFocusChanged { if (it.isFocused) onInteract() },
            size               = CONTROL_BUTTON,
        )
        HeroIconButton(
            icon               = Icons.Filled.Forward30,
            contentDescription = stringResource(R.string.player_forward_30),
            onClick            = { onSeek(SEEK_STEP_MS) },
            modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
            size               = CONTROL_BUTTON,
        )
        if (info.hasNext) {
            HeroIconButton(
                icon               = Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.player_next_episode_label),
                onClick            = actions.onNextEpisode,
                modifier           = Modifier.onFocusChanged { if (it.isFocused) onInteract() },
                size               = CONTROL_BUTTON,
            )
        }
        HeroIconButton(
            icon               = Icons.Outlined.Audiotrack,
            contentDescription = stringResource(R.string.player_section_audio),
            onClick            = {
                opener = audioFocus
                actions.onOpenAudio()
            },
            modifier           = Modifier.focusRequester(audioFocus).onFocusChanged { if (it.isFocused) onInteract() },
            size               = CONTROL_BUTTON,
        )
        HeroIconButton(
            icon               = Icons.Outlined.Subtitles,
            contentDescription = stringResource(R.string.player_section_subtitles),
            onClick            = {
                opener = subsFocus
                actions.onOpenSubtitles()
            },
            modifier           = Modifier.focusRequester(subsFocus).onFocusChanged { if (it.isFocused) onInteract() },
            size               = CONTROL_BUTTON,
        )
    }
}

/** Logo/título y subtítulo en la esquina superior izquierda (carga y chrome). */
@Composable
private fun TopTitle(info: VodChromeInfo) {
    Column(modifier = Modifier.padding(start = CHROME_PADDING, top = CHROME_TOP, end = CHROME_PADDING)) {
        TitleBlock(info = info)
    }
}

/** Logo del item (o título) y subtítulo. */
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

/** Debajo de la barra: `55:36 / 1:39:25` a la izquierda y `Termina 1:52` a la derecha. */
@Composable
private fun TimeRow(playhead: Playhead) {
    val duration = playhead.durationMs
    val elapsed = if (duration > 0) "${formatTime(playhead.positionMs)} / ${formatTime(duration)}" else formatTime(playhead.positionMs)
    Row(modifier = Modifier.fillMaxWidth().height(TIME_ROW_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Text(text = elapsed, color = TextPrimary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        if (duration > 0) {
            val remaining = (duration - playhead.positionMs).coerceAtLeast(0L)
            val endsAt = LocalTime.now().plusSeconds(remaining / MS_PER_SECOND).format(END_TIME_FORMAT)
            Text(text = stringResource(R.string.player_ends_at, endsAt), color = TextSecondary, fontSize = 13.sp)
        }
    }
}

/** Hora actual, sutil, en la esquina superior derecha; se actualiza cada medio minuto. */
@Composable
private fun ClockCorner(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(CLOCK_TICK_MS)
        }
    }
    Text(
        text     = now.format(END_TIME_FORMAT),
        color    = Color.White.copy(alpha = CLOCK_ALPHA),
        fontSize = 15.sp,
        modifier = modifier.padding(top = CHROME_TOP, end = CHROME_PADDING),
    )
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
private const val CLOCK_TICK_MS = 30_000L
private const val CLOCK_ALPHA = 0.7f
private const val SEEK_STEP_MS = 30_000L

/** Teclas que, con el chrome oculto, solo lo enseñan. */
private val SHOW_KEYS = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.DirectionUp, Key.DirectionDown)
private const val PLAYHEAD_POLL_MS = 500L
private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L

private const val FADE_IN_MS = 240
private const val SLIDE_MS = 320
private const val HIDE_MS = 380
private const val SLIDE_FRACTION = 4
private const val HIDE_SCALE = 0.96f
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
private val CHROME_TOP = 24.dp
private val TIME_ROW_HEIGHT = 20.dp
private val CHROME_BOTTOM = 32.dp
private val CONTROLS_TOP_FADE = 72.dp
private val CONTROL_BUTTON = 40.dp
private val LOADING_BAR_WIDTH = 220.dp
private val LOGO_HEIGHT = 56.dp
private val LOGO_MAX_WIDTH = 320.dp
private val SEEK_BAR_HEIGHT = 3.dp
private val SEEK_BAR_FOCUSED = 5.dp
private val SEEK_HIT_HEIGHT = 14.dp
private val SEEK_THUMB = 12.dp

/** Zoom lento del backdrop durante la carga. */
private const val KEN_BURNS_SCALE = 1.08f
private const val KEN_BURNS_MS = 12_000
private val END_TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
