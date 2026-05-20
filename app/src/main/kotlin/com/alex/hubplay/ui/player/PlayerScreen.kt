package com.alex.hubplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.activity.compose.BackHandler
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alex.hubplay.data.AuthState
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

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel:    PlayerViewModel,
    authState:    AuthState,
    okHttpClient: OkHttpClient,
    onBack:       () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    val player = remember { HubplayPlayer(context, authState, okHttpClient) }
    val playerState by player.state.collectAsState()

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
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    useController = !isLive
                    controllerShowTimeoutMs = 4_000
                    controllerAutoShow      = true
                }
            },
            update = { view ->
                view.useController = !isLive
                if (isLive) view.hideController()
            },
        )

        if (isLive) {
            LiveLayer(
                viewModel = viewModel,
                onBack    = onBack,
            )
        } else {
            // VOD fallback back button (Media3 chrome handles the rest).
            IconButton(
                onClick  = onBack,
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(
                    imageVector       = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint              = Color.White,
                )
            }
            // Audio + subtitle picker — only relevant for VOD HLS (live
            // IPTV streams typically expose a single audio track).
            var showTrackSheet by remember { mutableStateOf(false) }
            IconButton(
                onClick  = { showTrackSheet = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    imageVector        = Icons.Outlined.ClosedCaption,
                    contentDescription = "Audio y subtítulos",
                    tint               = Color.White,
                )
            }
            if (showTrackSheet) {
                TrackSelectionSheet(
                    player    = player.exoPlayer,
                    onDismiss = { showTrackSheet = false },
                )
            }
            BackHandler(onBack = onBack)
        }

        // Loading overlay.
        //  - VOD: centered spinner with the title.
        //  - Live: nothing. The PlayerView itself shows a small
        //    buffering wheel in the centre when needed; an extra
        //    overlay near the clock badge just cluttered the corner.
        if ((playerState.isBuffering || ui.startParams == null) && !isLive) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(12.dp))
                Text(
                    text  = ui.title ?: "Preparando…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
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
