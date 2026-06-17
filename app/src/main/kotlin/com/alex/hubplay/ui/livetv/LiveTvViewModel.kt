package com.alex.hubplay.ui.livetv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.ChannelOrderStore
import com.alex.hubplay.data.EpgProgram
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.LiveTvRepository
import com.alex.hubplay.data.MeEvent
import com.alex.hubplay.data.MeEventsStream
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Drives the new LiveTV screen.
 *
 * What it owns:
 *  1. **Inventory** — every channel in every IPTV library the user can
 *     access, plus the group_name list (for filter chips) and the
 *     favourites set. The backend already applies the caller's per-user
 *     channel order + hidden overlay before responding, so we don't
 *     re-sort or filter client-side — that gives us multi-device sync
 *     for free.
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
 *  - Reorder edits made on the personalisation screen trigger a refetch
 *    via the [ChannelOrderStore] flow: the store is used as a pure
 *    signalling channel today, not as a source of truth (the backend is).
 */
class LiveTvViewModel(
    private val repository:        LiveTvRepository,
    private val channelOrderStore: ChannelOrderStore,
    private val meEventsStream:    MeEventsStream,
) : ViewModel() {

    private val _ui = MutableStateFlow(LiveTvUiState(isLoading = true))
    val ui: StateFlow<LiveTvUiState> = _ui.asStateFlow()

    init {
        load()
        startNowTicker()
        startScheduleRefresher()
        observeOrderEdits()
        observeServerEvents()
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
                            recentIds        = snapshot.recentIds,
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
                            error     = friendlyError(err, "No se pudo cargar la TV en vivo"),
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

    /**
     * Called from the screen right before navigating to the player.
     * Prepends the channel to the local recents optimistically so the
     * "recently watched" filter is fresh when the user comes back from
     * the player — the server copy catches up via the beacon.
     */
    fun recordWatch(channelId: String) {
        _ui.update {
            val bumped = listOf(channelId) + it.recentIds.filterNot { id -> id == channelId }
            it.copy(recentIds = bumped.take(LiveTvRepository.RECENT_CHANNELS_LIMIT))
        }
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
                recentIds = emptyList(),
                schedule  = emptyMap(),
            )
        }

        // 2) Fan out: channels + groups per library, plus favourites once.
        //    The channels endpoint already returns the user-personalised
        //    view (admin overlay + per-user order + hidden filter applied
        //    server-side), so we keep the response order verbatim — no
        //    further sorting or filtering on this side. That's what makes
        //    multi-device sync transparent: TV2 sees TV1's order on next
        //    list call without the client knowing anything about overlays.
        val channelDeferreds = libraries.map { lib ->
            async { repository.fetchChannels(lib.id) }
        }
        val groupDeferreds = libraries.map { lib ->
            async { repository.fetchGroups(lib.id) }
        }
        val favoritesDeferred = async { repository.fetchFavoriteIds() }
        // Best-effort: an empty recents list just hides the sidebar filter,
        // it must never take the whole Live TV screen down with it.
        val recentsDeferred = async {
            runCatching { repository.fetchRecentChannelIds() }
                .onFailure { Log.w(TAG, "fetchRecentChannelIds failed", it) }
                .getOrDefault(emptyList())
        }

        val channels = channelDeferreds.awaitAll().flatten()
            .map { ch -> ch.copy(groupName = normalizeGroup(ch.groupName)) }

        val groups = groupDeferreds.awaitAll().flatten()
            .map { normalizeGroup(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val favorites = favoritesDeferred.await()
        val recentIds = recentsDeferred.await()

        // 3) Bulk schedule. One round-trip even with hundreds of channels.
        val schedule = if (channels.isEmpty()) {
            emptyMap()
        } else {
            runCatching { repository.fetchBulkSchedule(channels.map { it.id }) }
                .onFailure { Log.w(TAG, "bulkSchedule failed", it) }
                .getOrDefault(emptyMap())
        }

        InventorySnapshot(channels, groups, favorites, recentIds, schedule)
    }

    /**
     * The reorder screen writes a cache entry through [ChannelOrderStore]
     * on every successful backend write — we treat that emission as a
     * signal to refetch from the server, picking up the freshly-persisted
     * order/hidden state. `drop(1)` skips the Flow's initial replay so we
     * don't refetch immediately after our own [load].
     */
    private fun observeOrderEdits() {
        channelOrderStore.prefsFlow
            .distinctUntilChanged()
            .drop(1)
            .onEach { load() }
            .launchIn(viewModelScope)
    }

    /**
     * Sibling devices of the same user push a `channel.order.updated`
     * event via /me/events when they edit ordering or visibility — we
     * refetch on receipt so this device's Live TV reflects the change
     * without waiting for a manual reload. The own-device path already
     * refetches via [observeOrderEdits]; receiving the SSE echo for our
     * own write triggers a second `load()` but that's a cheap roundtrip
     * to the same backend state we already have.
     */
    private fun observeServerEvents() {
        meEventsStream.events()
            .onEach { event ->
                if (event is MeEvent.ChannelOrderUpdated) load()
            }
            .launchIn(viewModelScope)
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
        val recentIds: List<String>,
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

        fun factory(
            repository:        LiveTvRepository,
            channelOrderStore: ChannelOrderStore,
            meEventsStream:    MeEventsStream,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LiveTvViewModel(repository, channelOrderStore, meEventsStream) as T
            }
        }
    }
}

// ─── UI state ────────────────────────────────────────────────────────────────

@androidx.compose.runtime.Immutable
data class LiveTvUiState(
    val isLoading:         Boolean = false,
    val error:             String? = null,
    val channels:          List<LiveChannel> = emptyList(),
    val groups:            List<String> = emptyList(),
    val favorites:         Set<String> = emptySet(),
    /** Most recently watched channel ids, newest first (server-fed). */
    val recentIds:         List<String> = emptyList(),
    val scheduleByChannel: Map<String, List<EpgProgram>> = emptyMap(),
    val filter:            ChannelFilter = ChannelFilter.All,
    val focusedChannelId:  String? = null,
    /** Bumped by the 30s ticker so derived views recompute. */
    val nowEpoch:          Long = System.currentTimeMillis(),
    val lastLoadedAt:      Instant? = null,
) {
    /**
     * Channels in the recents list that still exist in the inventory,
     * in recency order (newest first). Ids pointing at channels the
     * user has since hidden — or that dropped off the M3U — silently
     * disappear because the lookup misses.
     */
    val recentChannels: List<LiveChannel> get() {
        if (recentIds.isEmpty()) return emptyList()
        val byId = channels.associateBy { it.id }
        return recentIds.mapNotNull { byId[it] }
    }

    /**
     * Applies the active filter to the channel list, preserving the order
     * already baked in by [LiveTvViewModel.fetchInventory] (per-library:
     * default LCN sort overridden by the user's saved reorder). The
     * Recents filter is the exception: it renders in recency order.
     */
    val visibleChannels: List<LiveChannel> get() = when (val f = filter) {
        ChannelFilter.All       -> channels
        ChannelFilter.Recent    -> recentChannels
        ChannelFilter.Favorites -> channels.filter { it.id in favorites }
        is ChannelFilter.Group  -> channels.filter { it.groupName.equals(f.name, ignoreCase = true) }
    }

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

    /** Auto-generated "recently watched" filter — hidden until there's history. */
    object Recent     : ChannelFilter()
    data class Group(val name: String) : ChannelFilter()
}
