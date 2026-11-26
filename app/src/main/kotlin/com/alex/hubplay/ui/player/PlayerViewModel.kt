package com.alex.hubplay.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.LiveTvRepository
import com.alex.hubplay.data.PeersRepository
import com.alex.hubplay.data.ProgressReporter
import com.alex.hubplay.data.StreamSessions
import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.ItemDetailDto
import com.alex.hubplay.data.api.dto.ItemSummaryDto
import com.alex.hubplay.data.api.dto.StreamInfoDto
import com.alex.hubplay.player.ClientCapabilities
import com.alex.hubplay.player.SideloadedSubtitle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.time.Instant

/**
 * Resolves playback for an itemId and — when the id points to a TV
 * channel — owns the live-TV experience around the stream:
 *
 *  - **Channel surfing**: caches the full ordered channel list of the
 *    live library so the screen can call [surfChannel] on D-pad ↑/↓
 *    and we re-emit a fresh [PlayerStartParams] for the next channel.
 *    The Compose screen reacts and the player re-uses its ExoPlayer
 *    (no re-creation), so the switch feels instant.
 *  - **EPG window**: bulk schedule for *all* channels in the library
 *    (one round-trip) so the mini-EPG carousel can show now/next for
 *    every neighbour. Refreshed every 5 min like the catalog screen.
 *  - **Now ticker**: bumps `nowEpoch` every 15s so progress bars and
 *    "min restantes" labels update without polling.
 *
 * VOD ids stay on the simple path — no schedule, no neighbours, no
 * ticker work. The cost is paid only by live items.
 */
class PlayerViewModel(
    private val deps:    PlayerDeps,
    private val request: PlayerRequest,
) : ViewModel() {

    private val api:        HubplayApi       get() = deps.api
    private val liveTvRepo: LiveTvRepository get() = deps.liveTvRepo

    private val _ui = MutableStateFlow(PlayerUiState(itemId = request.itemId))
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    /**
     * Reports playback position back to the server every ~10s, on pause,
     * and on dispose. Constructed lazily after [resolve] decides whether
     * the id is a VOD item (reporter enabled) or a live channel (left
     * null — live streams have no concept of progress). Escribe en el
     * scope de la app: el guardado final al salir del reproductor se
     * lanza justo cuando este ViewModel se destruye y en `viewModelScope`
     * se cancelaba antes de llegar al servidor.
     */
    private var progressReporter: ProgressReporter? = null

    /** Id de viewer de este reproductor en el transmux de los canales. */
    private val viewerId = StreamSessions.newViewerId()

    /** Canal que suena ahora (para darse de baja al zapear o salir). */
    private var currentLiveChannelId: String? = null

    /**
     * The long-lived live-chrome coroutines (metadata fetch + 15s now-ticker
     * + 5-min schedule refresher). Held so a re-resolve (e.g. auto-play next
     * episode lands back on a channel) cancels the previous channel's tickers
     * instead of stacking another pair of infinite `while(true)` loops onto
     * viewModelScope. Only touched on the main dispatcher (from [resolve]).
     */
    private val liveChromeJobs = mutableListOf<Job>()

    /** Carga del manifiesto de miniaturas del item en curso (se cancela al cambiar de item). */
    private var trickplayJob: Job? = null

    /**
     * Miniaturas de la barra. La primera vez el servidor las genera con
     * ffmpeg (30–90 s) y responde 503 mientras tanto: se reintenta cada
     * [TRICKPLAY_RETRY_MS] unas cuantas veces. 404 / 503 por desactivado /
     * cualquier otro fallo → sin miniaturas, el reproductor sigue igual.
     */
    private fun loadTrickplay(targetItemId: String) {
        trickplayJob?.cancel()
        _ui.update { it.copy(trickplay = null) }
        trickplayJob = viewModelScope.launch {
            repeat(TRICKPLAY_ATTEMPTS) {
                val result = runCatching { api.getTrickplay(targetItemId) }
                result.getOrNull()?.let { dto ->
                    _ui.update { it.copy(trickplay = TrickplayInfo.from(targetItemId, dto)) }
                    return@launch
                }
                val pending = (result.exceptionOrNull() as? HttpException)?.code() == HTTP_UNAVAILABLE
                if (!pending) return@launch
                kotlinx.coroutines.delay(TRICKPLAY_RETRY_MS)
            }
        }
    }

    init {
        viewModelScope.launch {
            val peerId = request.peerId
            when {
                peerId != null -> resolvePeer(peerId, request.itemId, request.resumePosSec)
                // La navegación ya sabe que es un canal: directo al stream,
                // sin las dos peticiones (`/items` + `/info`) que daban 404.
                request.live   -> startLive(request.itemId, title = null)
                else           -> resolve(request.itemId, request.resumePosSec)
            }
        }
    }

    override fun onCleared() {
        // Margen al salir: si la vista previa de TV en directo vuelve a pedir
        // el mismo canal se une antes y el servidor no corta la sesión.
        currentLiveChannelId?.let { deps.sessions.leaveChannel(it, viewerId, StreamSessions.LEAVE_GRACE_MS) }
        val state = _ui.value
        if (state.mode == PlayerMode.Vod && request.peerId == null) deps.sessions.stopVod(state.itemId)
    }

    private suspend fun resolve(targetItemId: String, resumeSec: Long) {
        val capabilities = withContext(Dispatchers.Default) { ClientCapabilities.probe() }
        Log.d(TAG, "resolve itemId=$targetItemId resume=$resumeSec")

        // En paralelo: `/info` puede esperar hasta 3 s al índice de keyframes
        // y la ficha no depende de él. Antes iban una detrás de otra.
        val (itemResult, infoResult) = coroutineScope {
            val itemDef = async { runCatching { api.getItem(targetItemId).data } }
            val infoDef = async { runCatching { api.getStreamInfo(targetItemId, capabilities).data } }
            itemDef.await() to infoDef.await()
        }
        val item     = itemResult.getOrNull()
        val info     = infoResult.getOrNull()
        val infoErr  = infoResult.exceptionOrNull()
        val infoCode = (infoErr as? HttpException)?.code()

        when {
            info != null    -> startVod(targetItemId, item, info, resumeSec)
            // Un canal no tiene `/info`: 404 → directo.
            infoCode == 404 -> startLive(targetItemId, item?.title)
            else            -> {
                val detail = infoErr?.let { "Error pidiendo /stream/$targetItemId/info: ${describe(it)}" }
                    ?: "El server devolvió /stream/$targetItemId/info sin envelope `data`."
                Log.e(TAG, "resolve failed: $detail", infoErr)
                // User-facing copy stays clean — the diagnostic detail (endpoint
                // path, HTTP status) goes to logcat only, never to the TV screen.
                _ui.update { it.copy(title = item?.title, error = "No se pudo iniciar la reproducción. Inténtalo de nuevo.") }
            }
        }
    }

    /** Item de nuestro catálogo (película / episodio) con la decisión de `/info`. */
    private fun startVod(targetItemId: String, item: ItemDetailDto?, info: StreamInfoDto, resumeSec: Long) {
        val isDirectPlay   = info.method == "direct_play"
        val streamUrl      = if (isDirectPlay) {
            "/api/v1/stream/$targetItemId/direct"
        } else {
            TrackOptions.masterUrl(targetItemId, audio = -1, burnSub = -1)
        }
        val subtitleTracks = item?.let { TrackOptions.subtitles(it.mediaStreams) }.orEmpty()
        progressReporter = ProgressReporter(api, deps.backgroundScope, targetItemId)
        _ui.update {
            it.copy(
                mode               = PlayerMode.Vod,
                title              = item?.title ?: "Reproduciendo…",
                subtitle           = item?.let(::subtitleFor),
                backdropUrl        = item?.backdropUrl ?: item?.posterUrl,
                logoUrl            = item?.logoUrl,
                audioTracks        = item?.let { d -> TrackOptions.audio(d.mediaStreams) }.orEmpty(),
                subtitleTracks     = subtitleTracks,
                // Otro fichero, otras pistas: las elecciones no se arrastran.
                selectedAudio      = -1,
                directAudioOrdinal = null,
                selectedSubtitle   = -1,
                chapters           = item?.let { d -> PlayerExtras.chapters(d.chapters) }.orEmpty(),
                skipSegments       = item?.let { d -> PlayerExtras.segments(d.segments) }.orEmpty(),
                streamMethod       = info.method,
                startParams        = PlayerStartParams(
                    streamUrl    = streamUrl,
                    resumePosSec = resumeSec,
                    isHls        = !isDirectPlay,
                    subtitles    = TrackOptions.sideloaded(targetItemId, subtitleTracks),
                ),
                error              = null,
            )
        }
        if (item != null && item.type == "episode") maybeResolveNextEpisode(item)
        loadTrickplay(targetItemId)
    }

    /** Canal de TV: stream con nuestro viewer y el chrome de directo. */
    private fun startLive(channelId: String, title: String?) {
        Log.d(TAG, "live channel $channelId")
        currentLiveChannelId = channelId
        _ui.update {
            it.copy(
                mode        = PlayerMode.Live,
                title       = title ?: it.title ?: "Canal en vivo",
                startParams = liveParams(channelId),
                error       = null,
            )
        }
        loadLiveChrome(channelId)
    }

    private fun liveParams(channelId: String) = PlayerStartParams(
        streamUrl    = StreamSessions.channelStreamPath(channelId, viewerId),
        resumePosSec = 0L,
        isHls        = true,
    )

    /**
     * Película de un servidor amigo: el amigo abre la sesión con nuestras
     * capacidades y nuestro servidor proxya el HLS. Sin pistas ni capítulos
     * (la federación no los comparte); el progreso va a `/me/peers/…`.
     */
    private suspend fun resolvePeer(peerId: String, itemId: String, resumeSec: Long) {
        val shared = deps.peers.cached(peerId, itemId)
        _ui.update {
            it.copy(
                title       = shared?.title ?: it.title,
                subtitle    = shared?.peerName?.takeIf { n -> n.isNotBlank() },
                backdropUrl = shared?.backdropUrl,
            )
        }
        val capabilities = withContext(Dispatchers.Default) { ClientCapabilities.probe() }
        val result = runCatching { deps.peers.startStream(peerId, itemId, capabilities) }
        val stream = result.getOrNull()
        if (stream == null) {
            Log.e(TAG, "peer stream $peerId/$itemId failed", result.exceptionOrNull())
            _ui.update {
                it.copy(error = "No se pudo reproducir desde el servidor de tu amigo. Puede que esté apagado.")
            }
            return
        }
        progressReporter = ProgressReporter(deps.peers.progressSink(peerId, itemId), deps.backgroundScope)
        _ui.update {
            it.copy(
                mode         = PlayerMode.Vod,
                title        = it.title ?: "Reproduciendo…",
                streamMethod = stream.method,
                startParams  = PlayerStartParams(
                    streamUrl    = stream.masterPath,
                    resumePosSec = resumeSec,
                    isHls        = true,
                ),
                error        = null,
            )
        }
    }

    /**
     * Switch to the previous (-1) or next (+1) channel in the library.
     * No-op when the player isn't in live mode, or when the library
     * list isn't loaded yet, or when we'd run off either end.
     *
     * The screen drives this from D-pad ↑/↓. Wrap-around isn't desired:
     * scrolling past the last channel should "stick" rather than loop
     * back to channel 1 (Movistar's behaviour, and the more predictable
     * one for a remote).
     */
    fun surfChannel(direction: Int) {
        val state = _ui.value
        if (state.mode != PlayerMode.Live) return
        val list = state.libraryChannels
        val current = state.liveChannel ?: return
        if (list.isEmpty()) return
        val currentIdx = list.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: return
        val nextIdx = (currentIdx + direction).coerceIn(0, list.lastIndex)
        if (nextIdx == currentIdx) return  // edge hit; stick.
        val next = list[nextIdx]
        switchToChannel(next)
    }

    /** Driven by the mini-EPG carousel: jump directly to a known channel. */
    fun playChannel(channel: LiveChannel) {
        if (_ui.value.liveChannel?.id == channel.id) return
        switchToChannel(channel)
    }

    // ─── Auto-play next episode (VOD episodes only) ─────────────────────────

    /**
     * Switch playback to the already-resolved next episode IN PLACE —
     * same screen, same ExoPlayer — so binge-watching doesn't bounce
     * through the navigation stack. Driven by the countdown overlay
     * (auto-fire or "Reproducir ya").
     *
     * @param finalPositionSec where the finished episode ended. Flushed
     *        with `completed = true` so continue-watching / next-up
     *        advance server-side even if the 95% markPlayed trigger
     *        never fired (think short post-credits stingers).
     */
    fun playNextEpisode(finalPositionSec: Long) {
        val next = _ui.value.nextEpisode ?: return
        Log.d(TAG, "playNextEpisode -> ${next.id} (${next.label})")
        progressReporter?.flush(positionSec = finalPositionSec, completed = true)
        // El ffmpeg del episodio terminado ocupa el cupo de transcode del
        // usuario: sin soltarlo, el siguiente podía dar 503 durante 90 s.
        deps.sessions.stopVod(_ui.value.itemId)
        _ui.update {
            it.copy(
                itemId      = next.id,
                title       = next.title ?: it.title,
                nextEpisode = null,
                startParams = null,
            )
        }
        viewModelScope.launch { resolve(next.id, 0L) }
    }

    /**
     * Best-effort: a failed lookup just means no auto-play card — it
     * must never disturb the playback that's already rolling.
     */
    private fun maybeResolveNextEpisode(current: ItemDetailDto) {
        viewModelScope.launch {
            val next = runCatching { findNextEpisode(current) }
                .onFailure { Log.w(TAG, "next-episode lookup failed for ${current.id}", it) }
                .getOrNull()
            _ui.update { it.copy(nextEpisode = next) }
        }
    }

    /**
     * Deterministic sibling walk: next episode within the season, and
     * when the credits belong to a season finale, the first episode of
     * the following season. Returns null at the very end of the series.
     */
    private suspend fun findNextEpisode(current: ItemDetailDto): NextEpisodeInfo? {
        val seasonId = current.parentId ?: return null
        val siblings = api.getChildren(seasonId).data.orEmpty().mapNotNull { it.toEpisodeRef() }
        NextEpisodeResolver.nextAfter(current.id, siblings)?.let { return it.toInfo() }

        val seriesId = current.seriesId ?: return null
        val seasons = api.getChildren(seriesId).data.orEmpty()
            .filter { it.type == "season" }
            .map { SeasonRef(id = it.id, seasonNumber = it.seasonNumber) }
        val nextSeason = NextEpisodeResolver.seasonFollowing(seasonId, seasons) ?: return null
        val episodes = api.getChildren(nextSeason.id).data.orEmpty().mapNotNull { it.toEpisodeRef() }
        return NextEpisodeResolver.firstEpisode(episodes)?.toInfo()
    }

    private fun ItemSummaryDto.toEpisodeRef(): EpisodeRef? {
        if (type != null && type != "episode") return null
        return EpisodeRef(id = id, title = title, seasonNumber = seasonNumber, episodeNumber = episodeNumber)
    }

    private fun EpisodeRef.toInfo() = NextEpisodeInfo(
        id            = id,
        title         = title,
        seasonNumber  = seasonNumber,
        episodeNumber = episodeNumber,
    )

    // ─── Progress reporting (VOD only) ──────────────────────────────────────
    //
    // The screen drives this with a 5s polling loop over the ExoPlayer's
    // current position. The reporter handles its own throttling (10s
    // between writes) and the 95 % markPlayed trigger. We expose two
    // entry points so the screen doesn't need to know about ticks units,
    // throttle windows, or completion semantics.

    /** Called every few seconds from the PlayerScreen polling loop. */
    fun onPlaybackTick(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        val reporter = progressReporter ?: return
        reporter.reportPosition(
            positionSec = positionMs / 1000L,
            durationSec = (durationMs / 1000L).coerceAtLeast(0L),
            isPlaying   = isPlaying,
        )
    }

    /**
     * Final progress write on dispose. Run from the screen's
     * DisposableEffect so backing out of the player still persists the
     * latest position to /me/progress.
     */
    fun onPlaybackDispose(positionMs: Long) {
        progressReporter?.flush(positionSec = positionMs / 1000L, completed = false)
    }

    /**
     * Toggle the live channel's favourite status. Optimistic — flips
     * the local set first, then calls the server; reverts on failure.
     * No-op when not in live mode or when the live channel isn't
     * loaded yet.
     */
    fun toggleFavorite() {
        val channelId = _ui.value.liveChannel?.id ?: return
        val nowFavorite = channelId !in _ui.value.favorites
        _ui.update {
            it.copy(
                favorites = if (nowFavorite) it.favorites + channelId
                            else             it.favorites - channelId,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                if (nowFavorite) liveTvRepo.addFavorite(channelId)
                else             liveTvRepo.removeFavorite(channelId)
            }
            result.onFailure { err ->
                Log.w(TAG, "toggleFavorite($channelId, now=$nowFavorite) failed", err)
                _ui.update {
                    val reverted = if (nowFavorite) it.favorites - channelId
                                   else             it.favorites + channelId
                    it.copy(favorites = reverted)
                }
            }
        }
    }

    private fun switchToChannel(channel: LiveChannel) {
        Log.d(TAG, "switchToChannel ${channel.id} (${channel.name})")
        // Baja inmediata del canal anterior: libera su ffmpeg y la conexión
        // al proveedor IPTV (muchos solo permiten una por cuenta).
        currentLiveChannelId?.takeIf { it != channel.id }?.let { deps.sessions.leaveChannel(it, viewerId) }
        currentLiveChannelId = channel.id
        _ui.update {
            it.copy(
                title       = channel.name,
                liveChannel = channel,
                startParams = liveParams(channel.id),
            )
        }
        // Beacon the new channel so continue-watching reflects reality.
        viewModelScope.launch {
            runCatching { liveTvRepo.recordWatch(channel.id) }
        }
    }

    /**
     * Loads the live-only metadata: the originating channel, the
     * library's full channel list (for surfing + mini-EPG), and a bulk
     * EPG window so every neighbour has now/next data.
     */
    private fun loadLiveChrome(channelId: String) {
        // Cancel any chrome coroutines from a previous channel so a
        // re-resolve doesn't stack duplicate now-tickers / refreshers.
        liveChromeJobs.forEach { it.cancel() }
        liveChromeJobs.clear()
        liveChromeJobs += viewModelScope.launch {
            runCatching {
                val libraries = liveTvRepo.fetchLiveLibraries()
                // Find which library owns this channel. We walk libraries
                // one by one to keep the call sequential — once we find
                // the channel we stop and cache the whole library list.
                var matchedLibraryChannels: List<LiveChannel> = emptyList()
                var matchedChannel: LiveChannel? = null
                for (lib in libraries) {
                    val channels = runCatching { liveTvRepo.fetchChannels(lib.id) }
                        .getOrDefault(emptyList())
                    val hit = channels.firstOrNull { it.id == channelId }
                    if (hit != null) {
                        matchedChannel = hit
                        matchedLibraryChannels = channels.sortedWith(
                            compareBy({ it.number.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.name.lowercase() }),
                        )
                        break
                    }
                }
                val schedule = if (matchedLibraryChannels.isNotEmpty()) {
                    runCatching {
                        liveTvRepo.fetchBulkSchedule(matchedLibraryChannels.map { it.id })
                    }.getOrDefault(emptyMap())
                } else emptyMap()
                val favs = runCatching { liveTvRepo.fetchFavoriteIds() }
                    .getOrDefault(emptySet())
                ChromeMeta(matchedChannel, matchedLibraryChannels, schedule, favs)
            }.onSuccess { meta ->
                _ui.update {
                    it.copy(
                        liveChannel       = meta.channel ?: it.liveChannel,
                        title             = meta.channel?.name ?: it.title,
                        libraryChannels   = meta.libraryChannels,
                        scheduleByChannel = meta.schedule,
                        favorites         = meta.favorites,
                    )
                }
            }.onFailure {
                Log.w(TAG, "live chrome metadata fetch failed", it)
            }
        }
        // 15s now-ticker for the chrome progress bar + clock minute resolution.
        liveChromeJobs += viewModelScope.launch {
            while (true) {
                delay(15_000L)
                _ui.update { it.copy(nowEpoch = System.currentTimeMillis()) }
            }
        }
        // 5-minute schedule refresher.
        liveChromeJobs += viewModelScope.launch {
            while (true) {
                delay(5L * 60_000L)
                val ids = _ui.value.libraryChannels.map { it.id }
                if (ids.isEmpty()) continue
                runCatching { liveTvRepo.fetchBulkSchedule(ids) }
                    .onSuccess { fresh -> _ui.update { it.copy(scheduleByChannel = fresh) } }
            }
        }
    }

    /**
     * Cambia la pista de audio a la [ordinal]-ésima del fichero y sigue
     * desde [positionSec]. El backend transcodifica UNA pista por sesión
     * (`-map 0:a:N`), así que con HLS hay que pedir otro master con
     * `?audio=N`. En direct play el fichero lleva todas las pistas: se
     * elige en ExoPlayer (`directAudioOrdinal`, lo aplica la pantalla).
     */
    fun selectAudio(ordinal: Int, positionSec: Long) {
        val current = _ui.value
        if (current.mode != PlayerMode.Vod || ordinal !in current.audioTracks.indices) return
        _ui.update { it.copy(selectedAudio = ordinal) }
        restartVod(positionSec, audio = ordinal, burnSub = current.burnSubtitleOrdinal)
    }

    /**
     * Elige el subtítulo [ordinal] (-1 = desactivados). Los de texto van
     * aparte como WebVTT y la pantalla los activa en ExoPlayer sin cortar
     * la reproducción; los de imagen (PGS/DVD/ASS) los quema el servidor,
     * así que hay que pedir otro master con `?subtitle=N` desde
     * [positionSec]. Dejar un subtítulo quemado también relanza (sin él).
     */
    fun selectSubtitle(ordinal: Int, positionSec: Long) {
        val current = _ui.value
        if (current.mode != PlayerMode.Vod || ordinal == current.selectedSubtitle) return
        val next = current.subtitleTracks.firstOrNull { it.ordinal == ordinal }
        if (ordinal >= 0 && next == null) return
        val wasBurned = current.burnSubtitleOrdinal >= 0
        _ui.update { it.copy(selectedSubtitle = ordinal) }
        val burnNow = if (next?.burnIn == true) ordinal else -1
        if (burnNow >= 0 || wasBurned) restartVod(positionSec, audio = current.selectedAudio, burnSub = burnNow)
    }

    /**
     * Recalcula cómo reproducir con la pista de audio [audio] y el subtítulo
     * quemado [burnSub] (-1 = ninguno) y sigue desde [positionSec]. Con
     * burn-in siempre HLS (el servidor transcodifica). Sin él se repide
     * /info porque la decisión depende de la pista de audio (una DTS fuerza
     * transcode aunque la default fuese AAC); si sale direct play y ya se
     * estaba en direct play no se recarga nada: la pista se elige en
     * ExoPlayer.
     */
    private fun restartVod(positionSec: Long, audio: Int, burnSub: Int) {
        viewModelScope.launch {
            val id = _ui.value.itemId
            val direct = burnSub < 0 && runCatching {
                api.getStreamInfo(id, ClientCapabilities.probe(), audio = audio.takeIf { it >= 0 }).data
            }.getOrNull()?.method == "direct_play"
            _ui.update { state ->
                val alreadyDirect = direct && state.startParams?.isHls == false
                val streamUrl = if (direct) "/api/v1/stream/$id/direct" else TrackOptions.masterUrl(id, audio, burnSub)
                val restarted = PlayerStartParams(
                    streamUrl    = streamUrl,
                    resumePosSec = positionSec,
                    isHls        = !direct,
                    subtitles    = TrackOptions.sideloaded(id, state.subtitleTracks),
                )
                state.copy(
                    directAudioOrdinal = audio.takeIf { direct && it >= 0 },
                    startParams        = if (alreadyDirect) state.startParams else restarted,
                )
            }
        }
    }

    /** "Serie · T1 · E3" para episodios; nada para el resto (bajo el logo solo va el logo). */
    private fun subtitleFor(item: ItemDetailDto): String? {
        if (item.type == "episode") {
            val code = listOfNotNull(
                item.seasonNumber?.let { "T$it" },
                item.episodeNumber?.let { "E$it" },
            ).joinToString(" · ")
            return listOfNotNull(item.seriesTitle, code.takeIf { it.isNotEmpty() })
                .joinToString(" · ")
                .ifEmpty { null }
        }
        return null
    }

    private fun describe(t: Throwable): String = when (t) {
        is HttpException -> "HTTP ${t.code()} ${t.message()}"
        else             -> "${t.javaClass.simpleName}: ${t.message ?: "sin mensaje"}"
    }

    private data class ChromeMeta(
        val channel:         LiveChannel?,
        val libraryChannels: List<LiveChannel>,
        val schedule:        Map<String, List<EpgProgram>>,
        val favorites:       Set<String>,
    )

    companion object {
        private const val TAG = "PlayerViewModel"
        private const val TRICKPLAY_ATTEMPTS = 10
        private const val TRICKPLAY_RETRY_MS = 10_000L
        private const val HTTP_UNAVAILABLE = 503

        fun factory(deps: PlayerDeps, request: PlayerRequest) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(deps, request) as T
                }
            }
    }
}

/** Qué reproducir (argumentos de la ruta del reproductor). */
data class PlayerRequest(
    val itemId:       String,
    val resumePosSec: Long = 0L,
    /** La navegación sabe que es un canal: se salta `/items` + `/info`. */
    val live:         Boolean = false,
    /** Item de un servidor amigo (federación); null = nuestro catálogo. */
    val peerId:       String? = null,
)

/** Dependencias del reproductor (DI manual desde [com.alex.hubplay.data.AppContainer]). */
class PlayerDeps(
    val api:             HubplayApi,
    val liveTvRepo:      LiveTvRepository,
    val sessions:        StreamSessions,
    val peers:           PeersRepository,
    /** Scope de la app: el guardado final de progreso sobrevive al ViewModel. */
    val backgroundScope: CoroutineScope,
)

enum class PlayerMode { Unknown, Vod, Live }

@androidx.compose.runtime.Immutable
data class PlayerUiState(
    val itemId:          String,
    val mode:            PlayerMode = PlayerMode.Unknown,
    val title:           String? = null,
    /** Bajo el título: "Serie · T1 · E3" en episodios, el año en películas. */
    val subtitle:        String? = null,
    /** Imágenes del item (rutas del backend, sin absolutizar). La pantalla de carga usa el backdrop. */
    val backdropUrl:     String? = null,
    val logoUrl:         String? = null,
    val startParams:     PlayerStartParams? = null,
    val error:           String? = null,
    /** Pistas de audio del fichero (todas, aunque el HLS solo lleve una). */
    val audioTracks:     List<AudioTrackOption> = emptyList(),
    /** Ordinal elegido por el usuario; -1 = la pista por defecto del fichero. */
    val selectedAudio:   Int = -1,
    /** En direct play, ordinal de audio a aplicar en ExoPlayer (la pantalla lo hace). */
    val directAudioOrdinal: Int? = null,
    /** Subtítulos del fichero (texto y de imagen). */
    val subtitleTracks:  List<SubtitleTrackOption> = emptyList(),
    /** Ordinal del subtítulo elegido; -1 = desactivados (también al empezar). */
    val selectedSubtitle: Int = -1,
    /** Episode to auto-play when this one ends. Null = end of series / not an episode. */
    val nextEpisode:     NextEpisodeInfo? = null,
    /** Capítulos del fichero (menú Opciones). */
    val chapters:        List<PlayerChapter> = emptyList(),
    /** Intro / resumen / créditos con botón "Saltar". */
    val skipSegments:    List<SkipSegment> = emptyList(),
    /** direct_play | direct_stream | transcode (Información de reproducción). */
    val streamMethod:    String? = null,
    /** Miniaturas para la barra al avanzar/retroceder; null = sin ellas. */
    val trickplay:       TrickplayInfo? = null,
    // ── Live-mode extras ─────────────────────────────────────────────────
    val liveChannel:     LiveChannel? = null,
    /** Every channel in the library the current live channel belongs to. */
    val libraryChannels: List<LiveChannel> = emptyList(),
    /** Bulk EPG keyed by channelId — covers every channel in libraryChannels. */
    val scheduleByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    /** User's favourite channel ids. Drives the chrome's star indicator. */
    val favorites:       Set<String> = emptySet(),
    /** Bumped by the 15s ticker so the chrome's progress bar advances. */
    val nowEpoch:        Long = System.currentTimeMillis(),
) {
    /** True when the currently playing live channel is in the user's favourites. */
    val isCurrentChannelFavorite: Boolean
        get() = liveChannel?.id?.let { it in favorites } == true

    /** El subtítulo elegido, si hay. */
    private val selectedSubtitleTrack: SubtitleTrackOption?
        get() = subtitleTracks.firstOrNull { it.ordinal == selectedSubtitle }

    /** Ordinal del subtítulo quemado por el servidor en el stream actual; -1 = ninguno. */
    val burnSubtitleOrdinal: Int
        get() = selectedSubtitleTrack?.takeIf { it.burnIn }?.ordinal ?: -1

    /**
     * `Format.id` de la pista de texto que ExoPlayer debe activar; null =
     * texto apagado (sin subtítulo, o uno quemado que ya va en el vídeo).
     */
    val activeSubtitleId: String?
        get() = selectedSubtitleTrack?.takeIf { !it.burnIn }?.let { TrackOptions.sideloadId(it.streamIndex) }

    /**
     * 1-based position of the current channel in the library list,
     * for the "channel number badge" in the chrome. Falls back to 0
     * when the channel hasn't loaded yet.
     */
    val currentChannelPosition: Int
        get() {
            val id = liveChannel?.id ?: return 0
            val idx = libraryChannels.indexOfFirst { it.id == id }
            return if (idx >= 0) idx + 1 else 0
        }

    /** Program airing right now on the live channel, if any. */
    fun nowProgram(): EpgProgram? = nowProgramFor(liveChannel?.id)

    /** First program starting after the current one. */
    fun nextProgram(): EpgProgram? = nextProgramFor(liveChannel?.id)

    fun nowProgramFor(channelId: String?): EpgProgram? {
        val id = channelId ?: return null
        val now = Instant.ofEpochMilli(nowEpoch)
        return scheduleByChannel[id]?.firstOrNull { it.isLiveAt(now) }
    }

    fun nextProgramFor(channelId: String?): EpgProgram? {
        val id = channelId ?: return null
        val now = Instant.ofEpochMilli(nowEpoch)
        return scheduleByChannel[id]?.firstOrNull { it.startTime.isAfter(now) }
    }

    /**
     * Helper for [com.alex.hubplay.ui.player.MiniEpgRail] — neighbour
     * window centered on the current channel.
     *
     * @param radius how many channels to take to either side. 3 ⇒ up
     *               to 7 cards total (3 + current + 3), clamped at the
     *               ends of the list.
     */
    fun channelNeighbourhood(radius: Int = 3): List<LiveChannel> {
        val list = libraryChannels
        if (list.isEmpty()) return emptyList()
        val current = liveChannel ?: return list.take(2 * radius + 1)
        val idx = list.indexOfFirst { it.id == current.id }.takeIf { it >= 0 }
            ?: return list.take(2 * radius + 1)
        val from = (idx - radius).coerceAtLeast(0)
        val to   = (idx + radius + 1).coerceAtMost(list.size)
        return list.subList(from, to)
    }
}

data class PlayerStartParams(
    val streamUrl:    String,
    val resumePosSec: Long,
    val isHls:        Boolean,
    /** Subtítulos de texto que ExoPlayer carga aparte (solo VOD). */
    val subtitles:    List<SideloadedSubtitle> = emptyList(),
)
