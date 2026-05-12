package com.alex.hubplay.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.LiveTvRepository
import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.player.ClientCapabilities
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val api:          HubplayApi,
    private val liveTvRepo:   LiveTvRepository,
    private val itemId:       String,
    private val resumePosSec: Long,
) : ViewModel() {

    private val _ui = MutableStateFlow(PlayerUiState(itemId = itemId))
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { resolve(itemId, resumePosSec) }
    }

    private suspend fun resolve(targetItemId: String, resumeSec: Long) {
        val capabilities = ClientCapabilities.probe()
        Log.d(TAG, "resolve itemId=$targetItemId resume=$resumeSec caps=$capabilities")

        val itemResult = runCatching { api.getItem(targetItemId).data }
        val item = itemResult.getOrNull()

        val infoResult = runCatching { api.getStreamInfo(targetItemId, capabilities).data }
        val info       = infoResult.getOrNull()
        val infoErr    = infoResult.exceptionOrNull()
        val infoCode   = (infoErr as? HttpException)?.code()

        if (info != null) {
            // ── Item path (movies / episodes) ─────────────────────────────
            val isDirectPlay = info.method == "direct_play"
            val streamUrl    = if (isDirectPlay) "/api/v1/stream/$targetItemId/direct"
                               else              "/api/v1/stream/$targetItemId/master.m3u8"
            _ui.update {
                it.copy(
                    mode        = PlayerMode.Vod,
                    title       = item?.title ?: "Reproduciendo…",
                    startParams = PlayerStartParams(
                        streamUrl    = streamUrl,
                        resumePosSec = resumeSec,
                        isHls        = !isDirectPlay,
                    ),
                    error       = null,
                )
            }
            return
        }

        if (infoCode == 404) {
            // ── Channel path ──────────────────────────────────────────────
            Log.d(TAG, "channel fallback for $targetItemId")
            _ui.update {
                it.copy(
                    mode        = PlayerMode.Live,
                    title       = item?.title ?: "Canal en vivo",
                    startParams = PlayerStartParams(
                        streamUrl    = "/api/v1/channels/$targetItemId/stream",
                        resumePosSec = 0L,
                        isHls        = true,
                    ),
                    error       = null,
                )
            }
            loadLiveChrome(targetItemId)
            return
        }

        val msg = when {
            infoErr != null -> "Error pidiendo /stream/$targetItemId/info: ${describe(infoErr)}"
            else            -> "El server devolvió /stream/$targetItemId/info sin envelope `data`."
        }
        Log.e(TAG, "resolve failed: $msg", infoErr)
        _ui.update { it.copy(title = item?.title, error = msg) }
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
        _ui.update {
            it.copy(
                title       = channel.name,
                liveChannel = channel,
                startParams = PlayerStartParams(
                    streamUrl    = "/api/v1/channels/${channel.id}/stream",
                    resumePosSec = 0L,
                    isHls        = true,
                ),
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            while (true) {
                delay(15_000L)
                _ui.update { it.copy(nowEpoch = System.currentTimeMillis()) }
            }
        }
        // 5-minute schedule refresher.
        viewModelScope.launch {
            while (true) {
                delay(5L * 60_000L)
                val ids = _ui.value.libraryChannels.map { it.id }
                if (ids.isEmpty()) continue
                runCatching { liveTvRepo.fetchBulkSchedule(ids) }
                    .onSuccess { fresh -> _ui.update { it.copy(scheduleByChannel = fresh) } }
            }
        }
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

        fun factory(
            api:          HubplayApi,
            liveTvRepo:   LiveTvRepository,
            itemId:       String,
            resumePosSec: Long,
        ) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(api, liveTvRepo, itemId, resumePosSec) as T
                }
            }
    }
}

enum class PlayerMode { Unknown, Vod, Live }

data class PlayerUiState(
    val itemId:          String,
    val mode:            PlayerMode = PlayerMode.Unknown,
    val title:           String? = null,
    val startParams:     PlayerStartParams? = null,
    val error:           String? = null,
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
)
