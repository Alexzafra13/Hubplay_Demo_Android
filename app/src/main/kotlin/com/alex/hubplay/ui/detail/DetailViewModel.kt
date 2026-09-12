package com.alex.hubplay.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.CollectionDetail
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.IdentifyCandidate
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Loads /items/{id} and exposes a single Content to the Detail screen.
 *
 * Detail only renders meaningfully for Movies and Series; Episodes /
 * Seasons / LiveChannels / Unknown that arrive via deep link fall back
 * to the common Content fields (title, overview, images) without
 * showing trailer / collection / favourite chrome.
 *
 * Repo doubles as our items-fetcher for now (the Home rails already
 * route through it); a dedicated ItemsRepository can split out later
 * when we need /items/{id}/recommendations or /items/{id}/children.
 */
class DetailViewModel(
    private val repository: HomeRepository,
    private val itemId:     String,
) : ViewModel() {

    private val _ui = MutableStateFlow(DetailUiState(isLoading = true))
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()

    init {
        load()
        loadPermissions()
    }

    fun load() {
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.fetchItemDetail(itemId) }
                .onSuccess { item ->
                    _ui.update { it.copy(isLoading = false, item = item, error = null) }
                    loadRelated()
                    loadCollection(item)
                }
                .onFailure { err ->
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            error     = friendlyError(err, "No se pudo cargar el detalle"),
                        )
                    }
                }
        }
    }

    /**
     * Permiso de metadatos (GET /me, cacheado en el repositorio). Falla en
     * silencio: sin permiso confirmado, la fila de acciones simplemente no
     * enseña "Actualizar metadatos" / "Identificar".
     */
    private fun loadPermissions() {
        viewModelScope.launch {
            runCatching { repository.fetchCanEditMetadata() }
                .onSuccess { can -> _ui.update { it.copy(canEditMetadata = can) } }
        }
    }

    /** Recarga silenciosa del item (sin spinner) tras cambiar sus metadatos. */
    private fun reloadItem() {
        viewModelScope.launch {
            runCatching { repository.fetchItemDetail(itemId) }
                .onSuccess { item ->
                    _ui.update { it.copy(item = item) }
                    loadRelated()
                    loadCollection(item)
                }
        }
    }

    /**
     * Saga (TMDb collection) de una película, para el rail "Forma parte de
     * la colección". Best-effort como [loadRelated]: sin saga o con error,
     * el rail simplemente no aparece.
     */
    private fun loadCollection(item: Content) {
        val collectionId = (item as? Content.Movie)?.collectionId
        if (collectionId == null) {
            _ui.update { it.copy(collection = null) }
            return
        }
        viewModelScope.launch {
            runCatching { repository.fetchCollectionDetail(collectionId) }
                .onSuccess { c -> _ui.update { it.copy(collection = c) } }
        }
    }

    /** "Actualizar metadatos": re-corre el enrich del scanner sobre el item. */
    fun refreshMetadata() {
        viewModelScope.launch {
            runCatching { repository.refreshItemMetadata(itemId) }
                .onSuccess {
                    _ui.update { it.copy(notice = "Metadatos actualizados") }
                    reloadItem()
                }
                .onFailure { err ->
                    _ui.update { it.copy(notice = friendlyError(err, "No se pudieron actualizar los metadatos")) }
                }
        }
    }

    /** Abre el diálogo de identificar sembrado con el título y año actuales. */
    fun openIdentify() {
        val item = _ui.value.item ?: return
        searchCandidates(query = item.title, year = item.year)
    }

    fun searchCandidates(query: String, year: Int?) {
        _ui.update {
            it.copy(identify = IdentifyState(query = query, year = year, loading = true))
        }
        viewModelScope.launch {
            runCatching { repository.fetchIdentifyCandidates(itemId, query, year) }
                .onSuccess { list ->
                    updateIdentify { it.copy(candidates = list, loading = false) }
                }
                .onFailure { err ->
                    updateIdentify { it.copy(loading = false, error = friendlyError(err, "No se pudo buscar en TMDb")) }
                }
        }
    }

    fun applyIdentify(externalId: String) {
        updateIdentify { it.copy(applying = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.identifyItem(itemId, externalId) }
                .onSuccess {
                    _ui.update { it.copy(identify = null, notice = "Ficha actualizada") }
                    reloadItem()
                }
                .onFailure { err ->
                    updateIdentify { it.copy(applying = false, error = friendlyError(err, "No se pudo aplicar")) }
                }
        }
    }

    fun closeIdentify() { _ui.update { it.copy(identify = null) } }

    fun clearNotice() { _ui.update { it.copy(notice = null) } }

    private fun updateIdentify(transform: (IdentifyState) -> IdentifyState) {
        _ui.update { st -> st.identify?.let { st.copy(identify = transform(it)) } ?: st }
    }

    /**
     * "Más como esto" — best-effort. A failure (or no recs) just leaves the
     * rail hidden; it never blocks or errors the main detail view.
     */
    private fun loadRelated() {
        viewModelScope.launch {
            runCatching { repository.fetchRecommendations(itemId) }
                .onSuccess { related -> _ui.update { it.copy(related = related) } }
        }
    }

    /**
     * Toggle the favourite heart. Optimistic — we flip the local UI
     * immediately so the icon animates without a round-trip, then call
     * the server. On failure we revert. The server emits a
     * `user.favorite.toggled` event which Home picks up later for its
     * own refresh — that's a separate flow from the local revert path.
     */
    fun toggleFavorite() {
        // Only Movies / Series / Episodes carry an `isFavorite` flag; the
        // Detail screen hides the heart button for other variants, so we
        // should never be invoked outside those cases. Bail early if we
        // somehow get called on an Unknown / Season / LiveChannel.
        val current = _ui.value.item ?: return
        val optimistic: Content = when (current) {
            is Content.Movie   -> current.copy(isFavorite = !current.isFavorite)
            is Content.Series  -> current.copy(isFavorite = !current.isFavorite)
            is Content.Episode -> current.copy(isFavorite = !current.isFavorite)
            else               -> return
        }
        _ui.update { it.copy(item = optimistic) }
        viewModelScope.launch {
            runCatching { repository.toggleItemFavorite(current.id) }
                .onSuccess { actual ->
                    val applied: Content = when (optimistic) {
                        is Content.Movie   -> optimistic.copy(isFavorite = actual)
                        is Content.Series  -> optimistic.copy(isFavorite = actual)
                        is Content.Episode -> optimistic.copy(isFavorite = actual)
                        else               -> optimistic
                    }
                    _ui.update { it.copy(item = applied) }
                }
                .onFailure {
                    _ui.update { it.copy(item = current) } // revert
                }
        }
    }

    /**
     * Toggle the played / unplayed state from the overflow menu.
     * Optimistic, same as [toggleFavorite]: flip the local flag so the
     * menu label and the Play button update instantly, then reconcile
     * with the server and revert on failure.
     *
     * Marking watched also clears the local resume position — the server
     * wipes `position_ticks` on `markPlayed`, so the Play CTA should drop
     * back to "Reproducir" instead of showing a stale "Reanudar 12:34".
     */
    fun toggleWatched() {
        val current = _ui.value.item ?: return
        val target = when (current) {
            is Content.Movie   -> !current.watched
            is Content.Series  -> !current.watched
            is Content.Episode -> !current.watched
            else               -> return
        }
        val optimistic: Content = when (current) {
            is Content.Movie -> current.copy(
                watched      = target,
                progressPct  = if (target) 0f else current.progressPct,
                resumePosSec = if (target) 0L else current.resumePosSec,
            )
            is Content.Series  -> current.copy(watched = target)
            is Content.Episode -> current.copy(
                watched      = target,
                progressPct  = if (target) 0f else current.progressPct,
                resumePosSec = if (target) 0L else current.resumePosSec,
            )
            else -> return
        }
        _ui.update { it.copy(item = optimistic) }
        viewModelScope.launch {
            runCatching { repository.setItemWatched(current.id, target) }
                .onFailure { _ui.update { it.copy(item = current) } } // revert
        }
    }

    companion object {
        fun factory(repository: HomeRepository, itemId: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DetailViewModel(repository, itemId) as T
                }
            }
    }
}

@androidx.compose.runtime.Immutable
data class DetailUiState(
    val isLoading:       Boolean        = false,
    val item:            Content?       = null,
    val related:         List<Content>  = emptyList(),
    /** Saga de la película (rail bajo el reparto). `null` si no pertenece a ninguna. */
    val collection:      CollectionDetail? = null,
    val error:           String?        = null,
    /** El usuario puede editar metadatos (admin o `can_edit_metadata`). */
    val canEditMetadata: Boolean        = false,
    /** Diálogo "Identificar" abierto, con su búsqueda. `null` = cerrado. */
    val identify:        IdentifyState? = null,
    /** Aviso transitorio (Toast). La pantalla lo limpia al mostrarlo. */
    val notice:          String?        = null,
)

@androidx.compose.runtime.Immutable
data class IdentifyState(
    val query:      String                  = "",
    val year:       Int?                    = null,
    val candidates: List<IdentifyCandidate> = emptyList(),
    val loading:    Boolean                 = false,
    val applying:   Boolean                 = false,
    val error:      String?                 = null,
)
