package com.alex.hubplay.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        _ui.value = _ui.value.copy(item = optimistic)
        viewModelScope.launch {
            runCatching { repository.toggleItemFavorite(current.id) }
                .onSuccess { actual ->
                    val applied: Content = when (optimistic) {
                        is Content.Movie   -> optimistic.copy(isFavorite = actual)
                        is Content.Series  -> optimistic.copy(isFavorite = actual)
                        is Content.Episode -> optimistic.copy(isFavorite = actual)
                        else               -> optimistic
                    }
                    _ui.value = _ui.value.copy(item = applied)
                }
                .onFailure {
                    _ui.value = _ui.value.copy(item = current) // revert
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
        _ui.value = _ui.value.copy(item = optimistic)
        viewModelScope.launch {
            runCatching { repository.setItemWatched(current.id, target) }
                .onFailure { _ui.value = _ui.value.copy(item = current) } // revert
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
    val isLoading: Boolean    = false,
    val item:      Content?  = null,
    val error:     String?    = null,
)
