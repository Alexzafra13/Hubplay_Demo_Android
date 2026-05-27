package com.alex.hubplay.ui.catalog

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the Películas / Series screens with offset-based pagination.
 *
 * The initial load fetches [PAGE_SIZE] items; scrolling near the bottom
 * of the grid triggers [loadMore] which appends the next page. The grid
 * grows until the backend returns fewer items than PAGE_SIZE (end of
 * catalogue) or an error occurs.
 */
class CatalogViewModel(
    private val repository: HomeRepository,
    private val source:     Source,
) : ViewModel() {

    enum class Source { Movies, Series }

    private val type = when (source) {
        Source.Movies -> "movie"
        Source.Series -> "series"
    }

    private val _ui = MutableStateFlow(CatalogUiState(isLoading = true))
    val ui: StateFlow<CatalogUiState> = _ui.asStateFlow()

    private var currentOffset = 0
    private var hasMore = true

    init { load() }

    fun load() {
        currentOffset = 0
        hasMore = true
        _ui.value = CatalogUiState(isLoading = true)
        viewModelScope.launch {
            runCatching {
                repository.fetchCatalogue(type = type, limit = PAGE_SIZE, offset = 0)
            }.onSuccess { items ->
                currentOffset = items.size
                hasMore = items.size >= PAGE_SIZE
                _ui.value = CatalogUiState(isLoading = false, items = items, canLoadMore = hasMore)
            }.onFailure { err ->
                Log.w(TAG, "load($source) failed", err)
                _ui.value = CatalogUiState(
                    isLoading = false,
                    error = err.message ?: "No se pudo cargar el contenido",
                )
            }
        }
    }

    fun loadMore() {
        if (!hasMore || _ui.value.isLoadingMore || _ui.value.isLoading) return
        _ui.update { it.copy(isLoadingMore = true) }
        viewModelScope.launch {
            runCatching {
                repository.fetchCatalogue(type = type, limit = PAGE_SIZE, offset = currentOffset)
            }.onSuccess { newItems ->
                currentOffset += newItems.size
                hasMore = newItems.size >= PAGE_SIZE
                _ui.update {
                    it.copy(
                        items = it.items + newItems,
                        isLoadingMore = false,
                        canLoadMore = hasMore,
                    )
                }
            }.onFailure { err ->
                Log.w(TAG, "loadMore($source, offset=$currentOffset) failed", err)
                _ui.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    companion object {
        private const val TAG = "CatalogViewModel"
        private const val PAGE_SIZE = 60

        fun factory(repository: HomeRepository, source: Source) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CatalogViewModel(repository, source) as T
                }
            }
    }
}

@androidx.compose.runtime.Immutable
data class CatalogUiState(
    val isLoading:     Boolean         = false,
    val isLoadingMore: Boolean         = false,
    val canLoadMore:   Boolean         = false,
    val items:         List<MediaItem> = emptyList(),
    val error:         String?         = null,
)
