package com.alex.hubplay.ui.livetv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.LiveTvRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Drives the new LiveTV screen.
 *
 * What it owns:
 *  1. **Inventory** — every channel in every IPTV library the user can
 *     access, plus the group_name list (for filter chips) and the
 *     favourites set.
 *  2. **EPG window** — bulk schedule for all visible channels covering
 *     now-1h .. now+6h. Refreshed every 5 minutes so late-arriving EPG
 *     and rolled-over slots stay in sync.
 *  3. **Now ticker** — a cheap 30s heartbeat that just bumps a `nowEpoch`
 *     value so the screen recomputes progress bars and "next" rotations
 *     without refetching.
 *  4. **Filter** — All / Favourites / Group(name).
 *  5. **Optimistic favourites** — local set updates immediately; server
 *     call backfills; revert on failure.
 *
 * Design notes:
 *  - Multiple IPTV libraries are *merged* in Fase 1 (no library picker).
 *    The vast majority of users have one M3U source; if that changes we
 *    can add a sub-tab later without rewriting state.
 *  - Channels with `healthStatus == "dead"` are still listed today so
 *    the user can verify the state matches reality. We can hide them
 *    later behind a setting.
 */
class LiveTvViewModel(
    private val repository: LiveTvRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(LiveTvUiState(isLoading = true))
    val ui: StateFlow<LiveTvUiState> = _ui.asStateFlow()

    init {
        load()
        startNowTicker()
        startScheduleRefresher()
    }

    fun load() {
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { fetchInventory() }
                .onSuccess { snapshot ->
                    _ui.update {
                        it.copy(
                            isLoading        = false,
                            error            = null,
                            channels         = snapshot.channels,
                            groups           = snapshot.groups,
                            favorites        = snapshot.favorites,
                            scheduleByChannel = snapshot.schedule,
                            lastLoadedAt     = Instant.now(),
                            nowEpoch         = System.currentTimeMillis(),
                        )
                    }
                }
                .onFailure { err ->
                    Log.w(TAG, "load failed", err)
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            error     = err.message ?: "No se pudo cargar la TV en vivo",
                        )
                    }
                }
        }
    }

    /** Toggle a chip in the filter row. */
    fun setFilter(filter: ChannelFilter) {
        _ui.update { it.copy(filter = filter) }
    }

    /**
     * Driven by [ChannelRow.onFocusChanged]. The hero on top of the
     * screen renders whichever channel is currently focused so the user
     * gets a Movistar-style preview as they scroll the list.
     *
     * `null` means "no row focused yet" — we'll still show a sensible
     * default (the first visible channel) in [LiveTvUiState.heroChannel].
     */
    fun setFocusedChannel(channelId: String?) {
        if (_ui.value.focusedChannelId == channelId) return
        _ui.update { it.copy(focusedChannelId = channelId) }
    }

    /** Optimistic favourite toggle — flips locally, then calls the server. */
    fun toggleFavorite(channelId: String) {
        val current = _ui.value.favorites
        val nowFavorite = channelId !in current
        _ui.update {
            it.copy(
                favorites = if (nowFavorite) current + channelId else current - channelId,
            )
        }
        viewModelScope.launch {
            val result = runCatching {
                if (nowFavorite) repository.addFavorite(channelId)
                else             repository.removeFavorite(channelId)
            }
            result.onFailure { err ->
                Log.w(TAG, "toggleFavorite($channelId, now=$nowFavorite) failed", err)
                // Revert
                _ui.update {
                    val reverted = if (nowFavorite) it.favorites - channelId
                                   else             it.favorites + channelId
                    it.copy(favorites = reverted)
                }
            }
        }
    }

    /** Called from the screen right before navigating to the player. */
    fun recordWatch(channelId: String) {
        viewModelScope.launch {
            runCatching { repository.recordWatch(channelId) }
                .onFailure { Log.w(TAG, "recordWatch($channelId) failed", it) }
        }
    }

    private suspend fun fetchInventory(): InventorySnapshot = coroutineScope {
        // 1) Discover IPTV libraries. If there are zero, we surface an
        //    empty state in the UI rather than crashing.
        val libraries = repository.fetchLiveLibraries()
        if (libraries.isEmpty()) {
            return@coroutineScope InventorySnapshot(
                channels  = emptyList(),
                groups    = emptyList(),
                favorites = emptySet(),
                schedule  = emptyMap(),
            )
        }

        // 2) Fan out: channels + groups per library, plus favourites once.
        val channelDeferreds = libraries.map { lib ->
            async { repository.fetchChannels(lib.id) }
        }
        val groupDeferreds = libraries.map { lib ->
            async { repository.fetchGroups(lib.id) }
        }
        val favoritesDeferred = async { repository.fetchFavoriteIds() }

        val rawChannels = channelDeferreds.awaitAll().flatten()
        val rawGroups   = groupDeferreds.awaitAll().flatten()

        // Normalize the group name on every channel. Some M3U sources
        // arrive with semicolon-joined XMLTV categories (e.g.
        // "Animation;Kids;Public") because they lack a real group-title
        // and the server falls back to EPG categories. We split on `;`,
        // take the first token, trim and title-case. Two channels that
        // used to live under "Animation;Kids" and "Animation;Kids;Public"
        // both collapse to "Animation" — exactly what the filter chips
        // need.
        val channels = rawChannels.map { ch ->
            ch.copy(groupName = normalizeGroup(ch.groupName))
        }
        val groups = rawGroups
            .map { normalizeGroup(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val favorites = favoritesDeferred.await()

        // 3) Bulk schedule. One round-trip even with hundreds of channels.
        val schedule = if (channels.isEmpty()) {
            emptyMap()
        } else {
            runCatching { repository.fetchBulkSchedule(channels.map { it.id }) }
                .onFailure { Log.w(TAG, "bulkSchedule failed", it) }
                .getOrDefault(emptyMap())
        }

        InventorySnapshot(channels, groups, favorites, schedule)
    }

    /**
     * 30s heartbeat — bumps `nowEpoch` so progress bars and current-program
     * picks recompute. Does NOT refetch.
     */
    private fun startNowTicker() {
        viewModelScope.launch {
            while (true) {
                delay(NOW_TICK_MS)
                _ui.update { it.copy(nowEpoch = System.currentTimeMillis()) }
            }
        }
    }

    /**
     * 5-minute schedule refresher — re-pulls bulk EPG so late-arriving
     * programs and slot rollovers stay accurate. Channels list is NOT
     * refetched here; M3U churn is rare and would be a separate trigger.
     */
    private fun startScheduleRefresher() {
        viewModelScope.launch {
            while (true) {
                delay(SCHEDULE_REFRESH_MS)
                val channelIds = _ui.value.channels.map { it.id }
                if (channelIds.isEmpty()) continue
                runCatching { repository.fetchBulkSchedule(channelIds) }
                    .onSuccess { fresh ->
                        _ui.update { it.copy(scheduleByChannel = fresh) }
                    }
                    .onFailure { Log.w(TAG, "schedule refresh failed", it) }
            }
        }
    }

    private data class InventorySnapshot(
        val channels:  List<LiveChannel>,
        val groups:    List<String>,
        val favorites: Set<String>,
        val schedule:  Map<String, List<EpgProgram>>,
    )

    companion object {
        private const val TAG                  = "LiveTvViewModel"
        private const val NOW_TICK_MS          = 30_000L
        private const val SCHEDULE_REFRESH_MS  = 5L * 60_000L

        /**
         * Visible spelling of a raw group name as it arrives from the
         * backend. Handles the two common shapes:
         *   - "Comedy;Public" → "Comedy" (XMLTV semicolon-joined)
         *   - "comedy"        → "Comedy" (title-cased)
         *   - "" / null       → ""       (filtered upstream)
         *
         * Title-casing is intentionally simple — non-ASCII letters keep
         * their first-letter uppercase, which is fine for Spanish /
         * Catalan / English alike.
         */
        internal fun normalizeGroup(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            val first = raw.split(';').firstOrNull()?.trim().orEmpty()
            if (first.isEmpty()) return ""
            return first.replaceFirstChar { c ->
                if (c.isLowerCase()) c.titlecase() else c.toString()
            }
        }

        fun factory(repository: LiveTvRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LiveTvViewModel(repository) as T
                }
            }
    }
}

// ─── UI state ────────────────────────────────────────────────────────────────

data class LiveTvUiState(
    val isLoading:         Boolean = false,
    val error:             String? = null,
    val channels:          List<LiveChannel> = emptyList(),
    val groups:            List<String> = emptyList(),
    val favorites:         Set<String> = emptySet(),
    val scheduleByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val filter:            ChannelFilter = ChannelFilter.All,
    val focusedChannelId:  String? = null,
    /** Bumped by the 30s ticker so derived views recompute. */
    val nowEpoch:          Long = System.currentTimeMillis(),
    val lastLoadedAt:      Instant? = null,
) {
    /** Applies the active filter; returns channels sorted by number then name. */
    val visibleChannels: List<LiveChannel> get() = channels
        .asSequence()
        .filter { ch ->
            when (val f = filter) {
                ChannelFilter.All        -> true
                ChannelFilter.Favorites  -> ch.id in favorites
                is ChannelFilter.Group   -> ch.groupName.equals(f.name, ignoreCase = true)
            }
        }
        .sortedWith(compareBy({ it.number.takeIf { n -> n > 0 } ?: Int.MAX_VALUE }, { it.name.lowercase() }))
        .toList()

    /**
     * The channel the hero panel should render right now.
     *  - If a row is focused → that channel.
     *  - Else → the first visible channel (cold-start preview).
     *  - Else → null (empty state — hero collapses).
     */
    val heroChannel: LiveChannel? get() {
        val visible = visibleChannels
        val pinned = focusedChannelId?.let { id -> visible.firstOrNull { it.id == id } }
        return pinned ?: visible.firstOrNull()
    }

    /** Returns the program airing on this channel at the current tick, if any. */
    fun nowProgramFor(channelId: String): EpgProgram? {
        val now = Instant.ofEpochMilli(nowEpoch)
        return scheduleByChannel[channelId]?.firstOrNull { it.isLiveAt(now) }
    }

    /** Returns the first program starting after `now` on this channel. */
    fun nextProgramFor(channelId: String): EpgProgram? {
        val now = Instant.ofEpochMilli(nowEpoch)
        return scheduleByChannel[channelId]?.firstOrNull { it.startTime.isAfter(now) }
    }
}

sealed class ChannelFilter {
    object All        : ChannelFilter()
    object Favorites  : ChannelFilter()
    data class Group(val name: String) : ChannelFilter()
}
