package com.alex.hubplay.player

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import com.alex.hubplay.data.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

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
    /**
     * The same [OkHttpClient] the rest of the app uses — its
     * AuthInterceptor refreshes the JWT on 401 and replays the failed
     * request. Routing ExoPlayer's HTTP through this client makes
     * long live-streams survive token expiry (HLS sessions
     * previously froze after ~30 min when the bearer expired and the
     * DefaultHttpDataSource had no path to refresh it).
     */
    okHttpClient: OkHttpClient,
) {
    private val httpFactory = OkHttpDataSource.Factory(okHttpClient)
        .setUserAgent("HubPlay-Android/0.1.0")

    /**
     * Los VTT los extrae el servidor con ffmpeg leyendo el fichero entero:
     * la primera vez en un MKV grande son decenas de segundos, más que el
     * timeout de lectura del cliente normal (30 s), y ExoPlayer reintentaba
     * en bucle. Mismo pool e interceptores, solo cambia el timeout.
     */
    private val subtitleClient = okHttpClient.newBuilder()
        .readTimeout(SUBTITLE_READ_TIMEOUT_S, TimeUnit.SECONDS)
        .build()

    private val subtitleHttpFactory = OkHttpDataSource.Factory(subtitleClient)
        .setUserAgent("HubPlay-Android/0.1.0")

    private val mediaSourceFactory = DefaultMediaSourceFactory(context)
        .setDataSourceFactory(httpFactory)

    /**
     * Aggressive buffer config tuned for fast channel switching on
     * live IPTV. Defaults:  minBuffer 50 s, bufferForPlayback 2.5 s,
     * bufferForPlaybackAfterRebuffer 5 s — those are great for VOD
     * over flaky mobile networks but they mean ~3-5 s of black screen
     * every time the user D-pads UP / DOWN.
     *
     * Trade-off: on a poor network this triggers more rebuffers; on
     * a TV box hard-wired or on solid Wi-Fi it makes zap feel close
     * to satellite-receiver responsive.
     */
    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs                  = */ 2_000,
            /* maxBufferMs                  = */ 15_000,
            /* bufferForPlaybackMs          = */ 500,
            /* bufferForPlaybackAfterRebufferMs = */ 1_500,
        )
        .setPrioritizeTimeOverSizeThresholds(true)
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context, LegacyTextRenderersFactory(context))
        .setMediaSourceFactory(mediaSourceFactory)
        .setLoadControl(loadControl)
        .build()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state

    // Held so release() can detach it. ExoPlayer.release() already tears the
    // player down, but removing the listener first is the documented order
    // and stops any in-flight callback from touching _state post-release.
    private val playerListener = object : Player.Listener {
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

        // Diagnóstico de subtítulos: la captura de pantalla de la Mi TV sale
        // en blanco con vídeo por hardware, así que el logcat es la única
        // forma de saber qué pista de texto está activa y si llegan cues.
        override fun onTracksChanged(tracks: Tracks) {
            val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            val selected = text.firstOrNull { it.isSelected }?.getTrackFormat(0)
            Log.d(TAG, "tracks: text=${text.size} selected=${selected?.id ?: "none"} lang=${selected?.language}")
        }
        override fun onCues(cueGroup: CueGroup) {
            if (cueGroup.cues.isNotEmpty()) Log.v(TAG, "cue: ${cueGroup.cues.first().text}")
        }
    }

    private companion object {
        const val TAG = "HubplayPlayer"
        const val SUBTITLE_READ_TIMEOUT_S = 180L
    }

    init {
        exoPlayer.addListener(playerListener)
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
    fun play(
        streamUrl:    String,
        resumePosSec: Long,
        isHls:        Boolean,
        /**
         * Subtítulos de texto que se cargan aparte del vídeo (WebVTT del
         * servidor). Ninguno se pide hasta que la pantalla lo selecciona
         * en ExoPlayer; el fallo de una descarga no tumba la reproducción.
         */
        subtitles:    List<SideloadedSubtitle> = emptyList(),
    ) {
        val absoluteUrl = absolutize(streamUrl)
        val mediaItem = MediaItem.fromUri(absoluteUrl)
        val video = if (isHls) {
            HlsMediaSource.Factory(httpFactory)
                // Don't wait for every renditions' media playlist to
                // be fetched before kicking off playback. Cuts the
                // start-up latency dramatically on multi-variant
                // HLS manifests.
                .setAllowChunklessPreparation(true)
                .createMediaSource(mediaItem)
        } else {
            mediaSourceFactory.createMediaSource(mediaItem)
        }
        val source = if (subtitles.isEmpty()) video else mergeSubtitles(video, subtitles)
        exoPlayer.setMediaSource(source, resumePosSec * 1000L)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        subtitles.firstOrNull()?.let(::warmSubtitleCache)
    }

    /**
     * Pide una pista de texto nada más arrancar y tira la respuesta: el
     * servidor extrae todas las pistas del fichero en esa primera petición
     * y las deja en caché, así que cuando el usuario elija una, llega al
     * instante en vez de congelar el vídeo mientras ffmpeg lee el MKV.
     */
    private fun warmSubtitleCache(subtitle: SideloadedSubtitle) {
        val request = Request.Builder().url(absolutize(subtitle.url)).build()
        subtitleClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "subtitle warm-up failed: ${e.message}")
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    /**
     * El HlsMediaSource.Factory ignora las `subtitleConfigurations` del
     * MediaItem (solo DefaultMediaSourceFactory las mezcla), así que la
     * mezcla se hace a mano. `adjustPeriodTimeOffsets` alinea el inicio
     * del vídeo con el 0 del VTT.
     */
    private fun mergeSubtitles(video: MediaSource, subtitles: List<SideloadedSubtitle>): MediaSource {
        val factory = SingleSampleMediaSource.Factory(subtitleHttpFactory)
            .setTreatLoadErrorsAsEndOfStream(true)
        val subtitleSources = subtitles.map { sub ->
            val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(absolutize(sub.url)))
                .setMimeType(sub.mimeType)
                .setId(sub.id)
                .setLanguage(sub.language)
                .setLabel(sub.label)
                .build()
            factory.createMediaSource(config, C.TIME_UNSET)
        }
        val adjustPeriodTimeOffsets = true
        // MergingMediaSource solo tiene constructor vararg.
        @Suppress("SpreadOperator")
        return MergingMediaSource(adjustPeriodTimeOffsets, video, *subtitleSources.toTypedArray())
    }

    fun release() {
        exoPlayer.removeListener(playerListener)
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

/**
 * Media3 1.5 solo acepta subtítulos ya parseados en extracción
 * (`application/x-media3-cues`); las pistas de [SingleSampleMediaSource]
 * llegan como `text/vtt` crudo y el TextRenderer lanza "Legacy decoding is
 * disabled". Se usa SingleSample a propósito porque es el único camino que
 * NO descarga el VTT hasta que el usuario elige la pista (ProgressiveMediaSource
 * lo leería entero al preparar, y el servidor tarda decenas de segundos en
 * extraerlo de un MKV grande la primera vez).
 */
@UnstableApi
private class LegacyTextRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context:               Context,
        output:                TextOutput,
        outputLooper:          Looper,
        extensionRendererMode: Int,
        out:                   ArrayList<Renderer>,
    ) {
        super.buildTextRenderers(context, output, outputLooper, extensionRendererMode, out)
        out.filterIsInstance<TextRenderer>().forEach { it.experimentalSetLegacyDecodingEnabled(true) }
    }
}

/**
 * Pista de texto que ExoPlayer carga aparte del vídeo. `id` acaba en
 * `Format.id` de la pista, que es como la pantalla la localiza para
 * activarla. `url` puede ser relativa al servidor emparejado.
 */
data class SideloadedSubtitle(
    val id:       String,
    val url:      String,
    val language: String?,
    val label:    String,
    val mimeType: String = MimeTypes.TEXT_VTT,
)

data class PlayerState(
    val isBuffering: Boolean = false,
    val isReady:     Boolean = false,
    val isPlaying:   Boolean = false,
    val isEnded:     Boolean = false,
    val error:       String? = null,
)
