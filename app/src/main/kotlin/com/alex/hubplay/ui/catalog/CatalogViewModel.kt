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
import kotlinx.coroutines.launch

/**
 * Drives the Películas / Series screens.
 *
 * Each [Source] maps to a different repository call:
 *   - Movies → /items?type=movie ordered by added_at desc
 *   - Series → /items?type=series ordered by added_at desc
 *
 * LiveTv used to live here too but now has its own screen
 * ([com.alex.hubplay.ui.livetv.LiveTvScreen]) — it needs EPG + favourites
 * + filter chips, which don't fit the generic catalog shell.
 */
class CatalogViewModel(
    private val repository: HomeRepository,
    private val source:     Source,
) : ViewModel() {

    enum class Source { Movies, Series }

    private val _ui = MutableStateFlow(CatalogUiState(isLoading = true))
    val ui: StateFlow<CatalogUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                when (source) {
                    Source.Movies -> repository.fetchCatalogue(type = "movie", limit = 120)
                    Source.Series -> repository.fetchCatalogue(type = "series", limit = 120)
                }
            }
            result
                .onSuccess { items ->
                    _ui.value = CatalogUiState(isLoading = false, items = items, error = null)
                }
                .onFailure { err ->
                    Log.w(TAG, "load($source) failed", err)
                    _ui.value = CatalogUiState(
                        isLoading = false,
                        items     = _ui.value.items,
                        error     = err.message ?: "No se pudo cargar el contenido",
                    )
                }
        }
    }

    companion object {
        private const val TAG = "CatalogViewModel"

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
    val isLoading: Boolean         = false,
    val items:     List<MediaItem> = emptyList(),
    val error:     String?         = null,
)
