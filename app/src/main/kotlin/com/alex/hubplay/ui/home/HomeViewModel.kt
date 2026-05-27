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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
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
    private val repository:  HomeRepository,
    private val eventsFlow:  Flow<MeEvent>,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState(isLoading = true))
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    // DOS flows desde el mismo focusBus, con debounces distintos:
    //  - focusedItemForUi (150ms): drive heroItem + backdrop. Snappy.
    //  - focusedItem (500ms): drive trailer fetch + activate. Evita spam
    //    de loads cuando el usuario hace D-pad rápido por las cards.
    // Esta separación es lo que da el "feel" de Netflix/Prime: la UI
    // (cover + sinopsis) cambia casi al instante, el trailer espera a
    // que el foco se estabilice.
    private val _focusedItemForUi = MutableStateFlow<MediaItem?>(null)
    val focusedItemForUi: StateFlow<MediaItem?> = _focusedItemForUi.asStateFlow()

    private val _focusedItem = MutableStateFlow<MediaItem?>(null)

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

    private val trailerCache = java.util.concurrent.ConcurrentHashMap<String, TrailerInfo?>()
    private var refreshJob: Job? = null

    init {
        refresh()
        // Flow rápido para la UI: hero + backdrop deben sentirse instantáneos.
        focusBus
            .debounce(UI_FOCUS_DEBOUNCE_MS)
            .onEach { _focusedItemForUi.value = it }
            .launchIn(viewModelScope)

        // Flow lento para el trailer: evita reloads en navegación rápida.
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

        eventsFlow
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
        refreshJob?.cancel()
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        refreshJob = viewModelScope.launch {
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

                val trendingItems = trending.await()
                HomeData(
                    hero             = trendingItems.take(5),
                    continueWatching = cw.await(),
                    nextUp           = nextUp.await(),
                    trending         = trendingItems,
                    liveNow          = liveNow.await(),
                    rails            = layout,
                    latestByRailId   = latestByRailId,
                )
            }
            val hasContent = data.trending.isNotEmpty() || data.continueWatching.isNotEmpty()
                || data.nextUp.isNotEmpty() || data.liveNow.isNotEmpty()
                || data.latestByRailId.values.any { it.isNotEmpty() }
            _ui.value = if (hasContent) {
                HomeUiState(isLoading = false, data = data)
            } else {
                HomeUiState(
                    isLoading = false,
                    data = data,
                    error = "No se pudo cargar el contenido. Comprueba tu conexión e inténtalo de nuevo.",
                )
            }
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

    private val _trailerCurrentTimeSec = MutableStateFlow(0L)
    val trailerCurrentTimeSec: StateFlow<Long> = _trailerCurrentTimeSec.asStateFlow()

    fun onTrailerTimeUpdate(sec: Long) { _trailerCurrentTimeSec.value = sec }

    private var firstFocusConsumed: Boolean = false

    /**
     * Reseteamos la "puerta del primer foco" al re-entrar a HomeScreen.
     * Sin esto, al volver de Detail el ViewModel sigue creyendo que ya
     * consumió el primer foco, así que el auto-focus que dispara el sistema
     * sobre el primer item pisaría `_focusedItem` y borraría la card que el
     * usuario tenía enfocada antes de irse.
     */
    fun resetFirstFocusGate() { firstFocusConsumed = false }

    fun onCardFocused(item: MediaItem?) {
        if (!firstFocusConsumed) {
            firstFocusConsumed = true
            return
        }
        focusBus.tryEmit(item)
    }

    /**
     * Snapshot del estado de navegación del Home persistido en el VM para
     * sobrevivir Home → Detail → back. El NavHost guarda `rememberSaveable`
     * pero algunas piezas (qué item enfocado en cada rail) no caben ahí —
     * las guardamos aquí para que la recomposición tras back las pueda
     * leer y reanudar `LazyRow.scrollToItem` + `FocusRequester.requestFocus`.
     *
     * - [railIndex]: scroll vertical del `LazyColumn` de rails.
     * - [focusedItemIdByRail]: para cada rail (keyed por `config.id`),
     *   el `MediaItem.id` que tenía el foco.
     */
    @androidx.compose.runtime.Immutable
    data class HomeScrollSnapshot(
        val railIndex:           Int = 0,
        val focusedItemIdByRail: Map<String, String> = emptyMap(),
    )

    private val _scrollSnapshot = MutableStateFlow(HomeScrollSnapshot())
    val scrollSnapshot: StateFlow<HomeScrollSnapshot> = _scrollSnapshot.asStateFlow()

    fun saveScrollSnapshot(railIndex: Int, focusedItemIdByRail: Map<String, String>) {
        _scrollSnapshot.value = HomeScrollSnapshot(railIndex, focusedItemIdByRail)
    }

    companion object {
        // UI snappy: heroItem/backdrop responden en 150ms tras el último focus.
        private const val UI_FOCUS_DEBOUNCE_MS = 150L
        // Trailer: 500ms para evitar spam de loads en barrido rápido.
        private const val FOCUS_DEBOUNCE_MS = 500L
        // Refresh por SSE: subido de 1.5s → 5s. ProgressUpdated llega
        // varias veces por segundo durante playback en otra superficie;
        // recargar todo el Home cada 1.5s era caro.
        private const val SSE_REFRESH_DEBOUNCE_MS = 5_000L
        private const val TRAILER_FETCH_DELAY_MS = 600L

        fun factory(repository: HomeRepository, meEventsStream: MeEventsStream) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HomeViewModel(repository, meEventsStream.events()) as T
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
