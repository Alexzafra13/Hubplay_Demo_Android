package com.alex.hubplay.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeData
import com.alex.hubplay.data.HomeRailType
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MeEvent
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MeEventsStream
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
    private val _focusedItemForUi = MutableStateFlow<Content?>(null)
    val focusedItemForUi: StateFlow<Content?> = _focusedItemForUi.asStateFlow()

    private val _focusedItem = MutableStateFlow<Content?>(null)

    /**
     * Trailer info for the currently focused item. Fetched lazily when
     * the focused item changes and cached by item id so repeated
     * focuses on the same card don't re-fetch.
     */
    private val _trailerInfo = MutableStateFlow<TrailerInfo?>(null)
    val trailerInfo: StateFlow<TrailerInfo?> = _trailerInfo.asStateFlow()

    /**
     * Hero carousel slide index — selects which trending item the hero
     * region renders when focus is NOT on a rail card. ←/→ on the hero
     * Play button shifts the index; an idle timer auto-rotates every
     * 8s when the hero loses focus (matches web client behaviour, see
     * web/src/components/home/HeroBanner.tsx).
     */
    private val _heroSlideIndex = MutableStateFlow(0)
    val heroSlideIndex: StateFlow<Int> = _heroSlideIndex.asStateFlow()

    /**
     * Shift the hero slide by `delta` (typically ±1). Clamps the index
     * to the current hero size; no-ops if the hero is empty.
     */
    fun shiftHeroSlide(delta: Int) {
        val size = _ui.value.data.hero.size
        if (size <= 0) return
        val current = _heroSlideIndex.value
        // Wrap-around so ← on slot 0 jumps to the last, → on the last
        // jumps back to 0. Mirrors web's dot navigation.
        val next = ((current + delta) % size + size) % size
        _heroSlideIndex.value = next
    }

    private val focusBus = MutableSharedFlow<Content?>(
        replay = 0, extraBufferCapacity = 16,
    )

    private val rebuildDebouncer = MutableSharedFlow<Unit>(
        replay = 0, extraBufferCapacity = 8,
    )

    // Two-collection cache. ConcurrentHashMap doesn't allow null values, so
    // we split "fetched and has a trailer" from "fetched and confirmed no
    // trailer" instead of using a nullable map. Without this, focusing a
    // live channel (or any item the server returns without trailer pair)
    // would crash with NPE inside ConcurrentHashMap.putVal.
    private val trailerCache       = java.util.concurrent.ConcurrentHashMap<String, TrailerInfo>()
    private val noTrailerItems     = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
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

        // `_focusedItem` ya viene estabilizado (debounce FOCUS_DEBOUNCE_MS
        // sobre `focusBus`), así que disparamos el fetch en cuanto cambia el
        // id. Un segundo debounce aquí solo sumaba ~600ms de latencia sin
        // ahorrar llamadas — `focusBus` ya está quieto cuando esto emite, y
        // la caché por id evita refetches sobre la misma card.
        _focusedItem
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

    private fun fetchTrailerInfo(item: Content?) {
        if (item == null) {
            _trailerInfo.value = null
            return
        }
        val cached = trailerCache[item.id]
        if (cached != null) {
            _trailerInfo.value = cached
            return
        }
        if (item.id in noTrailerItems) {
            _trailerInfo.value = null
            return
        }
        viewModelScope.launch {
            val info = runCatching {
                // Only movies and series carry a trailer pair on the
                // /items/{id} response — episodes and live channels never
                // populate it. We pattern-match instead of using a nullable
                // common field so the type system reflects reality.
                val detail = repository.fetchItemDetail(item.id)
                val (key, site) = when (detail) {
                    is Content.Movie  -> detail.trailerKey to detail.trailerSite
                    is Content.Series -> detail.trailerKey to detail.trailerSite
                    else              -> null to null
                }
                if (key != null && site != null) {
                    TrailerInfo(itemId = item.id, key = key, site = site)
                } else null
            }.getOrElse { err ->
                Log.w("HomeViewModel", "trailer fetch failed for ${item.id}: ${err.message}")
                null
            }
            if (info != null) {
                trailerCache[item.id] = info
            } else {
                noTrailerItems.add(item.id)
            }
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
                val cw          = async { safeFetch("continueWatching") { repository.fetchContinueWatching() } }
                val nextUp      = async { safeFetch("nextUp")           { repository.fetchNextUp() } }
                val trending    = async { safeFetch("trending")         { repository.fetchTrending() } }
                val recommended = async { safeFetch("recommended")      { repository.fetchRecommended() } }
                val liveNow     = async { safeFetch("liveNow")          { repository.fetchLiveNow() } }
                // Latest "globally" for the hero — pulled without library
                // filter (server returns newest items across libraries) so
                // the first hero tier can show "Lo último añadido". The
                // per-library Latest rails below stay independent.
                val latestAll   = async {
                    safeFetch("latestAll") { repository.fetchLatest(limit = HERO_POOL_SIZE) }
                }

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

                val trendingItems    = trending.await()
                val recommendedItems = recommended.await()
                val latestAllItems   = latestAll.await()
                val heroSlots        = buildHeroSlots(
                    latest      = latestAllItems,
                    trending    = trendingItems,
                    recommended = recommendedItems,
                )
                HomeData(
                    hero             = heroSlots,
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

    /**
     * Build the hero carousel slots by merging the three discovery
     * sources web/src/components/home/HeroBanner.tsx uses, dedup'd by
     * id and capped at [HERO_MAX_SLOTS]. Mirror behaviour so Android +
     * web feel consistent.
     *
     * Priority order:
     *  1. Latest items added (this calendar year if any qualify; else
     *     the whole `latest` pool) — "Nuevo" tier.
     *  2. Trending — server-wide 7-day plays aggregate.
     *  3. Recommended — genre-affinity picks.
     *
     * Episodes are excluded from the hero (they don't make good landing
     * cards: low context, spoils which episode the user resumes on).
     * Web also filters them out — keeps the surface focused on titles.
     */
    private fun buildHeroSlots(
        latest:      List<Content>,
        trending:    List<Content>,
        recommended: List<Content>,
    ): List<Content> {
        val currentYear = java.time.Year.now().value
        val seen        = mutableSetOf<String>()
        val slots       = mutableListOf<Content>()

        // Tier 1 — "Nuevo": latest items added this year. If nothing
        // qualifies (cold catalogue), fall back to all latest so the
        // hero still has content from this source.
        val thisYearOnly = latest.filter { it.year == currentYear && it !is Content.Episode }
        val newPool      = if (thisYearOnly.isNotEmpty()) thisYearOnly else latest

        // Tier 2 dedupes against Tier 1, Tier 3 against both. Sequence +
        // take() avoids the break/continue pattern detekt flags as too
        // jumpy, and keeps each tier's quota cap honest.
        appendUniqueHeroItems(newPool, slots, seen)
        appendUniqueHeroItems(trending, slots, seen)
        appendUniqueHeroItems(recommended, slots, seen)

        return slots
    }

    /**
     * Push items from [pool] into [slots] until the hero cap is reached,
     * skipping anything already present in [seen], plus Episode and
     * Unknown variants (too low-context for a hero slide).
     */
    private fun appendUniqueHeroItems(
        pool:  List<Content>,
        slots: MutableList<Content>,
        seen:  MutableSet<String>,
    ) {
        val remaining = HERO_MAX_SLOTS - slots.size
        if (remaining <= 0) return
        pool.asSequence()
            .filter { it !is Content.Episode && it !is Content.Unknown }
            .filter { it.id !in seen }
            .take(remaining)
            .forEach { item ->
                seen.add(item.id)
                slots.add(item)
            }
    }

    private inline fun <T : Content> safeFetch(label: String, block: () -> List<T>): List<T> {
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

    fun onCardFocused(item: Content?) {
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
     *   el `Content.id` que tenía el foco.
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

        /**
         * Cap del hero carousel. Coincide con MAX_SLOTS del web client
         * (HeroBanner.tsx) — 5 slots es el equilibrio entre variedad y
         * que la auto-rotación cubra un ciclo en ~40s.
         */
        private const val HERO_MAX_SLOTS = 5

        /**
         * Cuántos items "latest" pedir al servidor para alimentar la
         * primera tier del hero. Más que HERO_MAX_SLOTS para tener
         * margen tras el filtro de "year == currentYear".
         */
        private const val HERO_POOL_SIZE = 12

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
