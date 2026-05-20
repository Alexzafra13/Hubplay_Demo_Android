package com.alex.hubplay.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Search screen ViewModel.
 *
 *  - Text input writes to [query] synchronously so the field never feels laggy.
 *  - A debounced flow over [query] (~300 ms) drives the actual /items/search
 *    request — short enough to feel live, long enough that typing "the
 *    matrix" doesn't fire seven requests.
 *  - Queries shorter than 2 characters short-circuit to empty results
 *    without hitting the network. The backend handles them fine but the
 *    UX of getting 60 garbage matches for "a" is bad.
 *  - Concurrent searches are NOT cancelled — coroutines run to completion
 *    but only the LATEST result wins via the `requestId` guard. Cancelling
 *    in-flight HTTP would also work; the guard is simpler and behaves
 *    identically for our payload sizes.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val repository: HomeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val ui: StateFlow<SearchUiState> = _ui.asStateFlow()

    private var requestSeq: Long = 0L

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
            // When the input drops below the min, clear results immediately
            // so the UI doesn't keep showing stale matches.
            if (text.trim().length < MIN_QUERY_LEN) {
                it.copy(query = text, results = emptyList(), isSearching = false, error = null)
            } else {
                it.copy(query = text, isSearching = true, error = null)
            }
        }
    }

    fun clear() {
        _ui.value = SearchUiState()
    }

    private fun runSearch(text: String) {
        val rid = ++requestSeq
        viewModelScope.launch {
            runCatching { repository.searchItems(text) }
                .onSuccess { hits ->
                    if (rid != requestSeq) return@onSuccess
                    _ui.update { it.copy(results = hits, isSearching = false, error = null) }
                }
                .onFailure { err ->
                    if (rid != requestSeq) return@onFailure
                    _ui.update { it.copy(isSearching = false, error = err.message ?: "Error de búsqueda") }
                }
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 300L
        private const val MIN_QUERY_LEN = 2

        fun factory(repository: HomeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(repository) as T
            }
        }
    }
}

@androidx.compose.runtime.Immutable
data class SearchUiState(
    val query:       String           = "",
    val results:     List<MediaItem>  = emptyList(),
    val isSearching: Boolean          = false,
    val error:       String?          = null,
)
