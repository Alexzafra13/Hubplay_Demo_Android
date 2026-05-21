package com.alex.hubplay.ui.collections

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.CollectionDetail
import com.alex.hubplay.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads /collections/{id} and exposes hero + member movies to the
 * Detail screen. Single-shot load; pull-to-refresh isn't a thing on
 * TV but Retry is wired on the error state.
 */
class CollectionDetailViewModel(
    private val repository:   HomeRepository,
    private val collectionId: String,
) : ViewModel() {

    private val _ui = MutableStateFlow(CollectionDetailUi(isLoading = true))
    val ui: StateFlow<CollectionDetailUi> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.fetchCollectionDetail(collectionId) }
                .onSuccess { _ui.value = CollectionDetailUi(isLoading = false, detail = it) }
                .onFailure { err ->
                    Log.w(TAG, "load collection $collectionId failed", err)
                    _ui.value = _ui.value.copy(
                        isLoading = false,
                        error     = err.message ?: "No se pudo cargar la colección",
                    )
                }
        }
    }

    companion object {
        private const val TAG = "CollectionDetailVM"

        fun factory(repository: HomeRepository, collectionId: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    CollectionDetailViewModel(repository, collectionId) as T
            }
    }
}

@Immutable
data class CollectionDetailUi(
    val isLoading: Boolean           = false,
    val detail:    CollectionDetail? = null,
    val error:     String?           = null,
)
