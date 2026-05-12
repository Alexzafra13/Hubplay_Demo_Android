package com.alex.hubplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * Full-screen player. Hosts an Android [PlayerView] inside Compose via
 * AndroidView (the official interop path — Media3 doesn't yet ship a
 * pure-Compose PlayerView replacement, though `media3-ui-compose` adds
 * incremental Composables we can adopt later).
 *
 * Lifecycle:
 *   - The player is constructed on first composition and released in
 *     onDispose. Rotations + Compose recompositions don't re-create it
 *     because we use `remember` keyed on nothing.
 *   - When the resolution from the ViewModel arrives, LaunchedEffect
 *     calls `player.play(...)`.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    authState: AuthState,
    onBack: () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    val player = remember { HubplayPlayer(context, authState) }
    val playerState by player.state.collectAsState()

    // Kick off playback the moment the resolution arrives.
    LaunchedEffect(ui.startParams) {
        ui.startParams?.let {
            player.play(it.streamUrl, it.resumePosSec, it.isHls)
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    // Hardware Back key — the PlayerView with useController=true
    // swallows D-pad key events for its internal controls, so the
    // floating IconButton below never receives D-pad focus. BackHandler
    // intercepts the remote's Back button at the Activity level and
    // takes us out of the player even while the controls are hidden.
    BackHandler(onBack = onBack)

    Box(
        modifier         = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player.exoPlayer
                    useController = true
                    setKeepContentOnPlayerReset(true)
                    // FIT keeps the stream's native aspect ratio (no
                    // crop, no stretch). Some IPTV streams come with
                    // odd SAR / non-standard resolutions and any zoom
                    // mode would crop the picture — defaulting to FIT
                    // shows it as the broadcaster intended, with bars
                    // when the source aspect doesn't match the screen.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    // Show the play/pause/seek bar from the first frame
                    // so users on a TV remote always see the affordance
                    // — auto-hide is good on phones with touch, bad on
                    // a 10ft viewing distance.
                    controllerShowTimeoutMs = 4_000
                    controllerAutoShow      = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
        )

        // Top-left back button overlay. Useful when running with a
        // touchscreen / mouse where there's no hardware Back; on a TV
        // remote the BackHandler above does the actual job.
        IconButton(
            onClick  = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Volver",
                tint = Color.White,
            )
        }

        // Loading state — covers the empty PlayerView until the first
        // frame paints, so the user doesn't stare at black.
        if (playerState.isBuffering || ui.startParams == null) {
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

        // Hard error surface (e.g. /stream/info returned no URL).
        ui.error?.let { err ->
            Text(
                text     = err,
                color    = MaterialTheme.colorScheme.error,
                style    = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp).align(Alignment.Center),
            )
        }
    }
}
