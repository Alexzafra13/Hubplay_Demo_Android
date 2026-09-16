package com.alex.hubplay.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alex.hubplay.data.AuthState
import com.alex.hubplay.data.withImageWidth
import com.alex.hubplay.player.HubplayPlayer
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import java.time.Instant

/**
 * Two-state chrome model for the live mode:
 *
 *  - **Hidden**  — only the video.
 *  - **Visible** — top + bottom dim overlays, channel + programme info.
 *                  Auto-collapses to Hidden after [AUTO_HIDE_MS] of no
 *                  input.
 *
 * D-pad UP/DOWN performs an *instant zap* to the previous/next channel
 * (the chrome briefly reappears so the user sees the new channel
 * name). BACK exits the chrome first, then the player.
 */
private enum class ChromeState { Hidden, Visible }

private const val AUTO_HIDE_MS = 4_500L
private const val MS_PER_SECOND = 1_000L

/** Anchos pedidos al backend para las imágenes del chrome de VOD. */
private const val IMG_W_BACKDROP = 1280
private const val IMG_W_LOGO = 400

/**
 * Ruta de imagen del backend → URL absoluta con `?w=`. Las URLs ya
 * absolutas (TMDb) se dejan como vienen.
 */
private fun absoluteImage(path: String?, server: String?, width: Int): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return withImageWidth(path, width)
    val base = server?.trimEnd('/') ?: return null
    val abs = base + (if (path.startsWith("/")) path else "/$path")
    return if (abs.contains("w=")) withImageWidth(abs, width) else abs + (if ('?' in abs) "&" else "?") + "w=$width"
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel:       PlayerViewModel,
    authState:       AuthState,
    okHttpClient:    OkHttpClient,
    idleController:  com.alex.hubplay.data.IdleController,
    onBack:          () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    val player = remember { HubplayPlayer(context, authState, okHttpClient) }
    val playerState by player.state.collectAsState()

    // While the player is up, the in-app screensaver must stay off: the
    // user IS watching, idle timer would be wrong. Paired with
    // PlayerView.keepScreenOn below which handles the OS-level
    // screensaver / daydream.
    DisposableEffect(Unit) {
        idleController.setSuspended(true)
        onDispose { idleController.setSuspended(false) }
    }

    // Re-play whenever startParams changes — covers both the first
    // resolve AND surfChannel switching to a new channel without
    // tearing down the ExoPlayer.
    LaunchedEffect(ui.startParams) {
        ui.startParams?.let {
            player.play(it.streamUrl, it.resumePosSec, it.isHls)
        }
    }

    // 5s polling loop that feeds the VM's ProgressReporter. The reporter
    // does its own throttling/dedup — we just need to push positions
    // often enough that the 10s write window captures live activity.
    // No-op while not in VOD mode (reporter is null inside the VM).
    LaunchedEffect(ui.mode) {
        if (ui.mode != PlayerMode.Vod) return@LaunchedEffect
        while (true) {
            val pos = player.exoPlayer.currentPosition
            val dur = player.exoPlayer.duration.let { if (it > 0) it else 0L }
            viewModel.onPlaybackTick(pos, dur, player.exoPlayer.isPlaying)
            kotlinx.coroutines.delay(5_000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Capture the final position BEFORE releasing the player —
            // exoPlayer.currentPosition returns 0 after release().
            val finalPos = player.exoPlayer.currentPosition
            viewModel.onPlaybackDispose(finalPos)
            player.release()
        }
    }

    val isLive = ui.mode == PlayerMode.Live

    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player.exoPlayer
                    setKeepContentOnPlayerReset(true)
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Ni controlador ni rueda de buffering de Media3: el
                    // chrome (VOD y directo) es de Compose. El controlador
                    // de Media3 nunca recibía las teclas del mando porque el
                    // foco lo tenía el árbol de Compose de encima.
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    useController = false
                    // La vista no debe quedarse con el foco de las teclas:
                    // el mando lo gestiona el chrome de Compose.
                    isFocusable = false
                    isFocusableInTouchMode = false
                    descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    // Tells the OS to suppress screen-dim / daydream
                    // while this View is visible. Without it Android TV
                    // boxes return to the launcher after ~5 min even
                    // mid-movie, which has been the bug.
                    keepScreenOn = true
                }
            },
        )

        if (isLive) {
            LiveLayer(
                viewModel = viewModel,
                onBack    = onBack,
            )
        } else {
            // Salir con Back: se compone ANTES del chrome para que el
            // BackHandler del chrome (ocultarlo) gane mientras esté visible.
            BackHandler(onBack = onBack)

            // Audio + subtitle picker — only relevant for VOD HLS (live
            // IPTV streams typically expose a single audio track).
            var trackSection by remember { mutableStateOf<TrackSection?>(null) }
            val server = authState.serverUrl
            val info = remember(ui.title, ui.subtitle, ui.backdropUrl, ui.logoUrl, ui.nextEpisode, server) {
                VodChromeInfo(
                    title       = ui.title,
                    subtitle    = ui.subtitle,
                    backdropUrl = absoluteImage(ui.backdropUrl, server, IMG_W_BACKDROP),
                    logoUrl     = absoluteImage(ui.logoUrl, server, IMG_W_LOGO),
                    hasNext     = ui.nextEpisode != null,
                )
            }
            VodPlayerLayer(
                info        = info,
                exo         = player.exoPlayer,
                playerState = playerState,
                preparing   = ui.startParams == null,
                sheetOpen   = trackSection != null,
                actions     = VodChromeActions(
                    onOpenAudio     = { trackSection = TrackSection.Audio },
                    onOpenSubtitles = { trackSection = TrackSection.Subtitles },
                    onNextEpisode = {
                        val durMs = player.exoPlayer.duration
                        viewModel.playNextEpisode(if (durMs > 0) durMs / MS_PER_SECOND else 0L)
                    },
                ),
            )
            trackSection?.let { section ->
                TrackSelectionSheet(
                    player              = player.exoPlayer,
                    onDismiss           = { trackSection = null },
                    section             = section,
                    serverAudio         = ui.audioTracks,
                    selectedServerAudio = ui.selectedAudio,
                    onSelectServerAudio = { ordinal ->
                        viewModel.selectAudio(ordinal, positionSec = player.exoPlayer.currentPosition / MS_PER_SECOND)
                    },
                )
            }
            // Direct play: el fichero lleva todas las pistas, la elegida se
            // aplica en ExoPlayer (N-ésimo grupo de audio) cuando hay pistas.
            LaunchedEffect(ui.directAudioOrdinal, playerState.isReady) {
                val ordinal = ui.directAudioOrdinal ?: return@LaunchedEffect
                val exo = player.exoPlayer
                val group = exo.currentTracks.groups
                    .filter { it.type == androidx.media3.common.C.TRACK_TYPE_AUDIO }
                    .getOrNull(ordinal) ?: return@LaunchedEffect
                exo.trackSelectionParameters = exo.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .build()
            }

            // ── Auto-play siguiente episodio ─────────────────────────
            // When the episode reaches STATE_ENDED and the VM resolved
            // a follow-up, show the countdown card. Cancel parks the
            // player on the end screen; the flag resets per episode so
            // the next finale gets its own window. The dismiss
            // BackHandler is composed AFTER the exit one on purpose —
            // the innermost enabled handler wins, so BACK closes the
            // card first and only then exits the player.
            var autoPlayDismissed by remember(ui.itemId) { mutableStateOf(false) }
            val nextEpisode = ui.nextEpisode
            if (playerState.isEnded && nextEpisode != null && !autoPlayDismissed) {
                NextEpisodeOverlay(
                    next      = nextEpisode,
                    onPlayNow = {
                        val durMs = player.exoPlayer.duration
                        viewModel.playNextEpisode(if (durMs > 0) durMs / MS_PER_SECOND else 0L)
                    },
                    onCancel  = { autoPlayDismissed = true },
                    modifier  = Modifier.align(Alignment.BottomEnd),
                )
                BackHandler { autoPlayDismissed = true }
            }
        }

        ui.error?.let { err ->
            Text(
                text      = err,
                color     = MaterialTheme.colorScheme.error,
                style     = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier  = Modifier.padding(24.dp).align(Alignment.Center),
            )
        }
    }
}

/**
 * Live-mode overlay + input layer. Lives inside the PlayerScreen as a
 * separate Composable so the shared elements (buffering / error) stay
 * mode-agnostic in the parent.
 *
 * Owns:
 *  - The chrome Hidden/Visible state machine + the auto-hide timer
 *  - D-pad key handling:
 *      UP/DOWN  → instant zap (previous / next channel)
 *      OK       → toggle chrome visibility
 *  - The BackHandler that "peels" the chrome off before exiting the
 *    player
 */
@Composable
private fun LiveLayer(
    viewModel: PlayerViewModel,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    var chromeState by remember { mutableStateOf(ChromeState.Visible) }
    var lastInteractionAt by remember { mutableStateOf(System.currentTimeMillis()) }
    fun touch() {
        lastInteractionAt = System.currentTimeMillis()
    }

    LaunchedEffect(lastInteractionAt, chromeState) {
        if (chromeState == ChromeState.Hidden) return@LaunchedEffect
        val target = lastInteractionAt + AUTO_HIDE_MS
        val wait = (target - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(wait)
        if (System.currentTimeMillis() >= lastInteractionAt + AUTO_HIDE_MS) {
            chromeState = ChromeState.Hidden
        }
    }

    BackHandler(enabled = chromeState != ChromeState.Hidden) {
        chromeState = ChromeState.Hidden
        touch()
    }

    val keyFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { keyFocus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(keyFocus)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                touch()
                when (event.key) {
                    Key.DirectionUp -> {
                        viewModel.surfChannel(-1)
                        chromeState = ChromeState.Visible
                        true
                    }
                    Key.DirectionDown -> {
                        viewModel.surfChannel(+1)
                        chromeState = ChromeState.Visible
                        true
                    }
                    Key.DirectionLeft -> {
                        viewModel.toggleFavorite()
                        chromeState = ChromeState.Visible
                        true
                    }
                    Key.DirectionCenter,
                    Key.Enter,
                    Key.NumPadEnter -> {
                        chromeState = if (chromeState == ChromeState.Hidden)
                            ChromeState.Visible
                        else
                            ChromeState.Hidden
                        true
                    }
                    else -> false
                }
            },
    ) {
        LivePlayerChrome(
            visible           = chromeState != ChromeState.Hidden,
            channel           = ui.liveChannel,
            title             = ui.title,
            nowProgram        = ui.nowProgram(),
            nextProgram       = ui.nextProgram(),
            nowInstant        = Instant.ofEpochMilli(ui.nowEpoch),
            channelPosition   = ui.currentChannelPosition,
            totalChannels     = ui.libraryChannels.size,
            isFavorite        = ui.isCurrentChannelFavorite,
        )
    }
}
