package com.alex.hubplay.ui.collections

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.CollectionSummary
import com.alex.hubplay.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Collections index — TMDb sagas matched by the scanner.
 * Same shape as [com.alex.hubplay.ui.catalog.CatalogViewModel] for
 * Movies / Series, just emitting [CollectionSummary] instead of
 * MediaItem because the tile shows an item-count badge that doesn't
 * fit the MediaItem model.
 */
class CollectionsViewModel(
    private val repository: HomeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(CollectionsUi(isLoading = true))
    val ui: StateFlow<CollectionsUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.fetchCollections() }
                .onSuccess { entries ->
                    _ui.value = CollectionsUi(isLoading = false, entries = entries)
                }
                .onFailure { err ->
                    Log.w(TAG, "load collections failed", err)
                    _ui.value = CollectionsUi(
                        isLoading = false,
                        entries   = _ui.value.entries,
                        error     = err.message ?: "No se pudieron cargar las colecciones",
                    )
                }
        }
    }

    companion object {
        private const val TAG = "CollectionsVM"

        fun factory(repository: HomeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                CollectionsViewModel(repository) as T
        }
    }
}

@Immutable
data class CollectionsUi(
    val isLoading: Boolean                 = false,
    val entries:   List<CollectionSummary> = emptyList(),
    val error:     String?                 = null,
)
