package com.alex.hubplay.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.alex.hubplay.data.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Thin wrapper around ExoPlayer that knows how to play HubPlay streams.
 *
 * Responsibilities the wrapper handles so the UI doesn't have to:
 *   - Build an HLS-capable MediaSource using the user's bearer token
 *     (HubPlay HLS playlists are protected — every segment fetch
 *     re-checks the token, so we attach it on the data-source level).
 *   - Resolve relative `streamUrl` from the server against the paired
 *     server URL (`http://server.placeholder/api/v1/...` style).
 *   - Surface playback state as Flows so Compose can render reactively.
 *
 * Lifecycle: create one instance per `PlayerScreen` mount via
 * `remember { … }`, release on disposal. Keeping the player short-lived
 * matches Media3's design — long-lived players belong inside a
 * MediaSessionService for background playback (next iteration).
 */
@UnstableApi
class HubplayPlayer(
    context: Context,
    private val authState: AuthState,
) {
    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setUserAgent("HubPlay-Android/0.1.0")
        .also { factory ->
            authState.accessToken?.let { token ->
                factory.setDefaultRequestProperties(
                    mapOf("Authorization" to "Bearer $token"),
                )
            }
        }

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(httpFactory)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .build()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        isReady     = playbackState == Player.STATE_READY,
                        isEnded     = playbackState == Player.STATE_ENDED,
                    )
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.update { it.copy(isPlaying = isPlaying) }
            }
            override fun onPlayerError(error: PlaybackException) {
                _state.update { it.copy(error = error.errorCodeName) }
            }
        })
    }

    /**
     * Start playback.
     *
     * @param streamUrl the URL or path returned by /stream/{id}/info
     *                  (`stream_url` field). May be absolute or relative.
     * @param resumePosSec where to start from; 0 means start at the beginning.
     * @param isHls true if the stream is an HLS .m3u8 (transcode/direct-stream),
     *              false for direct-play of a progressive container.
     */
    fun play(streamUrl: String, resumePosSec: Long, isHls: Boolean) {
        val absoluteUrl = absolutize(streamUrl)
        val mediaItem = MediaItem.fromUri(absoluteUrl)
        val source = if (isHls) {
            HlsMediaSource.Factory(httpFactory).createMediaSource(mediaItem)
        } else {
            mediaSourceFactory.createMediaSource(mediaItem)
        }
        exoPlayer.setMediaSource(source, resumePosSec * 1000L)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    fun release() {
        exoPlayer.release()
    }

    /**
     * Backend often returns a URL like `/api/v1/stream/abc/master.m3u8`
     * instead of the absolute one. Glue it onto the user's serverUrl so
     * ExoPlayer's HTTP stack hits the right host.
     */
    private fun absolutize(url: String): String {
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        val base = authState.serverUrl?.trimEnd('/') ?: return url
        val path = if (url.startsWith("/")) url else "/$url"
        return "$base$path"
    }
}

data class PlayerState(
    val isBuffering: Boolean = false,
    val isReady:     Boolean = false,
    val isPlaying:   Boolean = false,
    val isEnded:     Boolean = false,
    val error:       String? = null,
)
