package com.alex.hubplay.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeData
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MeEvent
import com.alex.hubplay.data.MeEventsStream
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Drives the Home screen.
 *
 * Two pieces of state worth flagging:
 *   - `ui` carries every rail in one snapshot. Rails are fetched in
 *     parallel; whichever's slowest gates the loading spinner. A
 *     future refinement is per-rail loading so the page paints rails
 *     progressively as they arrive.
 *   - `focusedItem` is what the Hero observes to crossfade to whatever
 *     card the user (D-pad) is currently focused on. It defaults to
 *     null so the Hero falls back to its auto-rotating spotlight.
 */
@OptIn(FlowPreview::class)
class HomeViewModel(
    private val repository:     HomeRepository,
    private val meEventsStream: MeEventsStream,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState(isLoading = true))
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private val _focusedItem = MutableStateFlow<MediaItem?>(null)
    val focusedItem: StateFlow<MediaItem?> = _focusedItem.asStateFlow()

    /**
     * Raw focus events from rail cards. Debounced before reaching
     * [_focusedItem] so that quick D-pad navigation across cards
     * doesn't repaint the FocusedHero on every step — only a deliberate
     * pause (>= [FOCUS_DEBOUNCE_MS]) commits the new selection. This is
     * the "hover delay" the user asked for; matches how Netflix /
     * Apple TV billboards settle on a card.
     */
    private val focusBus = MutableSharedFlow<MediaItem?>(
        replay = 0, extraBufferCapacity = 16,
    )

    /**
     * Collapses bursts of `/me/events` notifications into a single
     * refresh. Declared BEFORE `init` so the init block can subscribe
     * to it without tripping a forward-reference null access — Kotlin
     * runs property initialisers and init blocks in source order.
     */
    private val rebuildDebouncer = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 8,
    )

    init {
        refresh()
        focusBus
            .debounce(FOCUS_DEBOUNCE_MS)
            .onEach { _focusedItem.value = it }
            .launchIn(viewModelScope)

        // Listen for cross-device events. ProgressUpdated and
        // PlayedToggled both affect Continue Watching / Next Up, so we
        // refresh the page. FavoriteToggled only matters once we add a
        // Favorites rail — silently ignored for now to avoid pointless
        // refresh storms when the user marks a movie as favourite from
        // another device. Debounced so a burst of progress events
        // (e.g. someone seeking on the laptop) collapses into one fetch.
        meEventsStream.events()
            .onEach { event ->
                when (event) {
                    is MeEvent.ProgressUpdated, is MeEvent.PlayedToggled -> rebuildDebouncer.tryEmit(Unit)
                    is MeEvent.FavoriteToggled                            -> Unit
                }
            }
            .launchIn(viewModelScope)
        rebuildDebouncer
            .debounce(SSE_REFRESH_DEBOUNCE_MS)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }


    fun refresh() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            // supervisorScope so a single rail's failure doesn't cancel
            // the others. Each rail is wrapped in safeFetch so its
            // exception renders that rail empty without taking the
            // page down with it.
            val data = supervisorScope {
                // Phase 1 — fetch the page-level dependencies in parallel.
                // We need both `layout` (which rails to render and in
                // what order) AND `libraries` (id → content_type) before
                // we can fan out the per-library Latest fetches.
                val layoutDef = async {
                    runCatching { repository.fetchHomeLayout() }
                        .getOrElse {
                            Log.w("HomeViewModel", "home layout fetch failed: ${it.message}")
                            emptyList()
                        }
                }
                val librariesDef = async {
                    runCatching { repository.fetchLibraries() }
                        .getOrElse {
                            Log.w("HomeViewModel", "libraries fetch failed: ${it.message}")
                            emptyMap()
                        }
                }
                val cw       = async { safeFetch("continueWatching") { repository.fetchContinueWatching() } }
                val nextUp   = async { safeFetch("nextUp")           { repository.fetchNextUp() } }
                val trending = async { safeFetch("trending")         { repository.fetchTrending() } }
                val liveNow  = async { safeFetch("liveNow")          { repository.fetchLiveNow() } }

                val layout    = layoutDef.await()
                val libraries = librariesDef.await()

                // Phase 2 — for each `latest_in_library` rail, fan out
                // a fetch with the right library_id + type filter so we
                // get the right shape of card (movies → posters of
                // movies; shows → posters of series with new-episode
                // counts via the activity-aware path).
                val latestRailFetches = layout
                    .filter { it.type == HomeRailType.LatestInLibrary }
                    .map { config ->
                        async {
                            val type = libraries[config.libraryId]?.toLatestType()
                            val items = safeFetch("latest:${config.libraryId ?: "all"}") {
                                repository.fetchLatest(libraryId = config.libraryId, type = type)
                            }
                            config.id to items
                        }
                    }
                val latestByRailId = latestRailFetches.awaitAll().toMap()

                HomeData(
                    hero             = trending.await().take(5),
                    continueWatching = cw.await(),
                    nextUp           = nextUp.await(),
                    trending         = trending.await(),
                    liveNow          = liveNow.await(),
                    rails            = layout,
                    latestByRailId   = latestByRailId,
                )
            }
            _ui.value = HomeUiState(isLoading = false, data = data)
        }
    }

    /**
     * Library content_type → /items/latest `type=` filter.
     * Returns null when the type isn't useful (mixed / unknown / livetv);
     * the handler then defaults to its own ordering.
     */
    private fun String.toLatestType(): String? = when (this) {
        "movies" -> "movie"
        "shows"  -> "series"
        else     -> null
    }

    private inline fun safeFetch(label: String, block: () -> List<MediaItem>): List<MediaItem> {
        return runCatching(block).getOrElse { err ->
            Log.w("HomeViewModel", "rail '$label' failed: ${err.message}", err)
            emptyList()
        }
    }

    /**
     * Drops the very first focus event we see after Home loads. Compose's
     * LazyRow auto-grants focus to its first visible item on initial
     * composition (so a D-pad user can start scrolling); we don't want
     * that auto-focus to summon the lateral preview before the user
     * has navigated anywhere.
     */
    private var firstFocusConsumed: Boolean = false

    /**
     * Called from MediaCard when it gains focus. The Home screen's
     * FocusedItemPreview observes [focusedItem] and slides in with the
     * item's backdrop + meta + overview. The Hero is independent and
     * does NOT react to this signal — see HeroSection.
     */
    fun onCardFocused(item: MediaItem?) {
        if (!firstFocusConsumed) {
            firstFocusConsumed = true
            return
        }
        focusBus.tryEmit(item)
    }

    companion object {
        /**
         * Hover dwell required before the FocusedHero crossfades to
         * the focused card. Long enough that fast D-pad scans don't
         * trigger it; short enough that deliberate stops feel snappy.
         */
        private const val FOCUS_DEBOUNCE_MS = 800L

        /**
         * Collapse a burst of SSE events into a single refresh — when the
         * user scrubs on another device the server emits one progress
         * update per chunk. We don't want to repaint the page 30 times
         * a minute.
         */
        private const val SSE_REFRESH_DEBOUNCE_MS = 1_500L

        fun factory(repository: HomeRepository, meEventsStream: MeEventsStream) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository, meEventsStream) as T
                }
            }
    }
}

data class HomeUiState(
    val isLoading: Boolean   = false,
    val data:      HomeData  = HomeData(),
    val error:     String?   = null,
)
