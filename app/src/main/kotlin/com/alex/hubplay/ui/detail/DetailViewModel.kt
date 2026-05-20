package com.alex.hubplay.ui.detail

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
 * Loads /items/{id} and exposes a single MediaItem to the Detail screen.
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

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.fetchItemDetail(itemId) }
                .onSuccess { item -> _ui.value = DetailUiState(isLoading = false, item = item) }
                .onFailure { err  -> _ui.value = _ui.value.copy(
                    isLoading = false,
                    error     = err.message ?: "No se pudo cargar el detalle",
                ) }
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
        val current = _ui.value.item ?: return
        val optimistic = current.copy(isFavorite = !current.isFavorite)
        _ui.value = _ui.value.copy(item = optimistic)
        viewModelScope.launch {
            runCatching { repository.toggleItemFavorite(current.id) }
                .onSuccess { actual ->
                    _ui.value = _ui.value.copy(item = optimistic.copy(isFavorite = actual))
                }
                .onFailure {
                    _ui.value = _ui.value.copy(item = current) // revert
                }
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

data class DetailUiState(
    val isLoading: Boolean    = false,
    val item:      MediaItem? = null,
    val error:     String?    = null,
)
