package com.alex.hubplay.ui.livetv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.alex.hubplay.data.AuthState
import com.alex.hubplay.data.LiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

/**
 * Muted live preview of a channel — lives inside [LiveHero] on the
 * right side. Pluto TV / Rakuten TV / Xiaomi TV+ all do this:
 * navigating the grid plays a soundless thumbnail of the focused
 * channel so the user can decide before committing.
 *
 * Implementation rules so this doesn't burn the server or the device:
 *
 *  1. **Debounce 1.2 s** — only prepare the stream after the focused
 *     channel has been stable for that long. Zapping through the grid
 *     never spawns new transmux sessions on the backend.
 *  2. **Audio disabled** at the track-selection level (not just muted)
 *     so the decoder doesn't even create an AudioTrack.
 *  3. **Single ExoPlayer instance** held by the Composable; we swap
 *     MediaSource on each channel change. Re-creating an ExoPlayer on
 *     every D-pad press would burn ~50 ms per zap.
 *  4. **Auth via OkHttp data-source factory** — same Bearer header
 *     the main player uses. Without it, every preview fetch would
 *     401 against the protected HLS endpoint.
 *  5. **No chrome of its own** — no background, no clip, no border.
 *     [LiveHero] owns the fade gradient that dissolves the video's
 *     left edge into the page background. This is what makes the
 *     preview feel like part of the hero instead of a video card
 *     pasted onto it.
 *
 * The preview is opt-in via [enabled]; pass `false` to suppress
 * (e.g. low-power mode or a future "data saver" setting).
 */
@OptIn(UnstableApi::class)
@Composable
fun ChannelPreviewPlayer(
    channel:      LiveChannel?,
    authState:    AuthState,
    okHttpClient: OkHttpClient,
    enabled:      Boolean = true,
    onAutoTune:   ((LiveChannel) -> Unit)? = null,
    modifier:     Modifier = Modifier,
    fallback:     @Composable () -> Unit,
) {
    val context = LocalContext.current

    val controller = remember(authState.serverUrl, okHttpClient) {
        PreviewController(context, authState, okHttpClient)
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    val state by controller.state.collectAsState()

    LaunchedEffect(channel?.id, enabled) {
        if (!enabled || channel == null) {
            controller.stop()
            return@LaunchedEffect
        }
        delay(DEBOUNCE_MS)
        controller.play(channel)
    }

    // Auto-tune: when the preview has been playing the same channel
    // for AUTO_TUNE_MS, fire onAutoTune so the screen opens the
    // full-screen player. Tracks `lastAutoTunedId` to avoid an instant
    // re-tune when the user backs out of the player and the same
    // channel is still focused.
    var lastAutoTunedId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(channel?.id, state) {
        val ch = channel ?: return@LaunchedEffect
        val callback = onAutoTune ?: return@LaunchedEffect
        if (state !is PreviewState.Playing) return@LaunchedEffect
        if (ch.id == lastAutoTunedId) return@LaunchedEffect
        delay(AUTO_TUNE_MS)
        lastAutoTunedId = ch.id
        callback(ch)
    }

    Box(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize()) { fallback() }

        AnimatedVisibility(
            visible  = state is PreviewState.Playing,
            enter    = fadeIn(animationSpec = tween(220)),
            exit     = fadeOut(animationSpec = tween(160)),
            modifier = Modifier.fillMaxSize(),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory  = { ctx ->
                    PlayerView(ctx).apply {
                        player        = controller.exoPlayer
                        useController = false
                        setKeepContentOnPlayerReset(true)
                        resizeMode    = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
            )
        }
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────

@UnstableApi
private class PreviewController(
    context:   android.content.Context,
    private val authState: AuthState,
    okHttpClient: OkHttpClient,
) {
    // Same auto-refreshing OkHttp the rest of the app uses — so a
    // token expiry mid-preview doesn't silently freeze the thumbnail.
    private val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("HubPlay-Android/0.1.0 preview")

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context).build().apply {
        // Audio enabled — the preview plays with sound so navigating
        // the grid feels like channel-surfing on a real TV. User
        // controls volume with the remote.
        playWhenReady = true
        repeatMode    = Player.REPEAT_MODE_OFF
    }

    private val _state = MutableStateFlow<PreviewState>(PreviewState.Idle)
    val state = _state.asStateFlow()

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.value = when (playbackState) {
                    Player.STATE_BUFFERING -> PreviewState.Loading
                    Player.STATE_READY     -> PreviewState.Playing
                    Player.STATE_ENDED     -> PreviewState.Idle
                    Player.STATE_IDLE      -> currentIdleState()
                    else                   -> PreviewState.Idle
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                _state.value = PreviewState.Error(error.errorCodeName)
            }
        })
    }

    private fun currentIdleState(): PreviewState =
        if (_state.value is PreviewState.Error) _state.value else PreviewState.Idle

    private var currentChannelId: String? = null

    fun play(channel: LiveChannel) {
        if (currentChannelId == channel.id && _state.value is PreviewState.Playing) return
        currentChannelId = channel.id
        _state.value = PreviewState.Loading
        val absoluteUrl = absolutize("/api/v1/channels/${channel.id}/stream")
        val mediaItem = MediaItem.fromUri(absoluteUrl)
        val source = HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
    }

    fun stop() {
        currentChannelId = null
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _state.value = PreviewState.Idle
    }

    fun release() {
        exoPlayer.release()
    }

    private fun absolutize(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val base = authState.serverUrl?.trimEnd('/') ?: return path
        val clean = if (path.startsWith("/")) path else "/$path"
        return "$base$clean"
    }
}

private sealed class PreviewState {
    object Idle    : PreviewState()
    object Loading : PreviewState()
    object Playing : PreviewState()
    data class Error(val code: String) : PreviewState()
}

private const val DEBOUNCE_MS = 1_200L
/**
 * How long the preview has to keep playing the same channel before
 * we auto-tune the user into the full-screen player. 8 s gives plenty
 * of room to D-pad away if the user just landed on this channel
 * mid-zap and doesn't actually want to commit — 5 s felt too eager.
 */
private const val AUTO_TUNE_MS = 8_000L
