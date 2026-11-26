package com.alex.hubplay.ui.search

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.PeersRepository
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search screen ViewModel with offset-based pagination.
 *
 * Text input writes to [onQueryChange] synchronously so the field never
 * feels laggy. A debounced flow (~300ms) drives the actual /items/search
 * request. Scrolling near the grid bottom triggers [loadMore] which
 * appends the next page for the current query.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: HomeRepository,
    /** Federación: también se busca en los amigos (null = no). */
    private val peers:      PeersRepository? = null,
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    private var requestSeq: Long = 0L
    private var currentOffset = 0
    private var hasMore = false

    init {
        _ui
            .map { it.query }
            .distinctUntilChanged()
            .debounce(DEBOUNCE_MS)
            .filter { it.trim().length >= MIN_QUERY_LEN }
            .onEach { runSearch(it) }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(text: String) {
        _ui.update {
            if (text.trim().length < MIN_QUERY_LEN) {
                it.copy(
                    query = text, results = emptyList(), sharedResults = emptyList(),
                    isSearching = false, error = null, canLoadMore = false,
                )
            } else {
                it.copy(query = text, isSearching = true, error = null)
            }
        }
    }

    fun clear() {
        currentOffset = 0
        hasMore = false
        _ui.value = SearchUiState()
    }

    fun loadMore() {
        if (!hasMore || _ui.value.isLoadingMore || _ui.value.isSearching) return
        val query = _ui.value.query.trim()
        if (query.length < MIN_QUERY_LEN) return

        _ui.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching { repository.searchItems(query, limit = PAGE_SIZE, offset = currentOffset) }
                .onSuccess { newItems ->
                    currentOffset += newItems.size
                    hasMore = newItems.size >= PAGE_SIZE
                    _ui.update {
                        it.copy(
                            results = it.results + newItems,
                            isLoadingMore = false,
                            canLoadMore = hasMore,
                        )
                    }
                }
                .onFailure {
                    Log.w(TAG, "loadMore search failed", it)
                    _ui.update { it.copy(isLoadingMore = false) }
                }
        }
    }

    private fun runSearch(text: String) {
        val rid = ++requestSeq
        currentOffset = 0
        hasMore = false
        searchPeers(text, rid)
        viewModelScope.launch {
            runCatching { repository.searchItems(text, limit = PAGE_SIZE) }
                .onSuccess { hits ->
                    if (rid != requestSeq) return@onSuccess
                    currentOffset = hits.size
                    hasMore = hits.size >= PAGE_SIZE
                    _ui.update {
                        it.copy(
                            results = hits,
                            isSearching = false,
                            error = null,
                            canLoadMore = hasMore,
                        )
                    }
                }
                .onFailure { err ->
                    if (rid != requestSeq) return@onFailure
                    _ui.update { it.copy(isSearching = false, error = friendlyError(err, "Error de búsqueda")) }
                }
        }
    }

    /**
     * En paralelo a la búsqueda propia: el servidor pregunta a cada amigo
     * (2 s máx. por amigo), así que llega después y no la retrasa. Un fallo
     * solo deja la sección vacía.
     */
    private fun searchPeers(text: String, rid: Long) {
        val repo = peers ?: return
        _ui.update { it.copy(sharedResults = emptyList()) }
        viewModelScope.launch {
            val hits = runCatching { repo.search(text) }
                .onFailure { Log.d(TAG, "peer search failed: ${it.message}") }
                .getOrDefault(emptyList())
            if (rid == requestSeq) _ui.update { it.copy(sharedResults = hits) }
        }
    }

    companion object {
        private const val TAG = "SearchViewModel"
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LEN = 2
        private const val PAGE_SIZE = 60

        fun factory(repository: HomeRepository, peers: PeersRepository? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(repository, peers) as T
            }
        }
    }
}

@androidx.compose.runtime.Immutable
data class SearchUiState(
    val query:         String           = "",
    val results:       List<Content>    = emptyList(),
    /** Coincidencias en los servidores de los amigos (sección aparte). */
    val sharedResults: List<Content.Shared> = emptyList(),
    val isSearching:   Boolean          = false,
    val isLoadingMore: Boolean          = false,
    val canLoadMore:   Boolean          = false,
    val error:         String?          = null,
)
