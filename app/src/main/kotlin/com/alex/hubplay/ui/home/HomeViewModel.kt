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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

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
     * Trailer info for the currently focused item. Fetched lazily when
     * the focused item changes and cached by item id so repeated
     * focuses on the same card don't re-fetch.
     */
    private val _trailerInfo = MutableStateFlow<TrailerInfo?>(null)
    val trailerInfo: StateFlow<TrailerInfo?> = _trailerInfo.asStateFlow()

    private val focusBus = MutableSharedFlow<MediaItem?>(
        replay = 0, extraBufferCapacity = 16,
    )

    private val rebuildDebouncer = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 8,
    )

    private val trailerCache = mutableMapOf<String, TrailerInfo?>()

    init {
        refresh()
        focusBus
            .debounce(FOCUS_DEBOUNCE_MS)
            .onEach { _focusedItem.value = it }
            .launchIn(viewModelScope)

        // When the focused item settles, fetch trailer info.
        _focusedItem
            .debounce(TRAILER_FETCH_DELAY_MS)
            .distinctUntilChangedBy { it?.id }
            .onEach { item -> fetchTrailerInfo(item) }
            .launchIn(viewModelScope)

        meEventsStream.events()
            .onEach { event ->
                when (event) {
                    is MeEvent.ProgressUpdated, is MeEvent.PlayedToggled -> rebuildDebouncer.tryEmit(Unit)
                    is MeEvent.FavoriteToggled                            -> Unit
                    MeEvent.ChannelOrderUpdated                           -> Unit
                }
            }
            .launchIn(viewModelScope)
        rebuildDebouncer
            .debounce(SSE_REFRESH_DEBOUNCE_MS)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    private fun fetchTrailerInfo(item: MediaItem?) {
        if (item == null) {
            _trailerInfo.value = null
            return
        }
        val cached = trailerCache[item.id]
        if (cached != null) {
            _trailerInfo.value = cached
            return
        }
        if (trailerCache.containsKey(item.id)) {
            _trailerInfo.value = null
            return
        }
        viewModelScope.launch {
            val info = runCatching {
                val detail = repository.fetchItemDetail(item.id)
                if (detail.trailerKey != null && detail.trailerSite != null) {
                    TrailerInfo(
                        itemId = item.id,
                        key = detail.trailerKey,
                        site = detail.trailerSite,
                    )
                } else null
            }.getOrElse { err ->
                Log.w("HomeViewModel", "trailer fetch failed for ${item.id}: ${err.message}")
                null
            }
            trailerCache[item.id] = info
            if (_focusedItem.value?.id == item.id) {
                _trailerInfo.value = info
            }
        }
    }

    fun refresh() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val data = supervisorScope {
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

    private var firstFocusConsumed: Boolean = false

    fun onCardFocused(item: MediaItem?) {
        if (!firstFocusConsumed) {
            firstFocusConsumed = true
            return
        }
        focusBus.tryEmit(item)
    }

    companion object {
        private const val FOCUS_DEBOUNCE_MS = 350L
        private const val SSE_REFRESH_DEBOUNCE_MS = 1_500L
        private const val TRAILER_FETCH_DELAY_MS = 300L

        fun factory(repository: HomeRepository, meEventsStream: MeEventsStream) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository, meEventsStream) as T
                }
            }
    }
}

@androidx.compose.runtime.Immutable
data class HomeUiState(
    val isLoading: Boolean   = false,
    val data:      HomeData  = HomeData(),
    val error:     String?   = null,
)

@androidx.compose.runtime.Immutable
data class TrailerInfo(
    val itemId: String,
    val key:    String,
    val site:   String,
)
