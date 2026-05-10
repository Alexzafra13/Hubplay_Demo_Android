package com.alex.hubplay.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeData
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MediaItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Drives the Home screen.
 *
 * Two pieces of state worth flagging:
 *   - `ui` carries every rail in one snapshot. Rails are fetched in
 *     parallel; whichever's slowest gates the loading spinner. A
 *     future refinement is per-rail loading so the page paints rails
 *     progressively as they arrive.
 *   - `focusedItem` is what the Hero observes to crossfade to whatever
 *     card the user (D-pad) is currently focused on. It defaults to
 *     null so the Hero falls back to its auto-rotating spotlight.
 */
class HomeViewModel(
    private val repository: HomeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState(isLoading = true))
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    private val _focusedItem = MutableStateFlow<MediaItem?>(null)
    val focusedItem: StateFlow<MediaItem?> = _focusedItem.asStateFlow()

    init { refresh() }

    fun refresh() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching {
                // Fan out the rail fetches; awaitAll() so a slow one
                // can't block the rest from at least starting.
                val cw       = async { repository.fetchContinueWatching() }
                val trending = async { repository.fetchTrending() }
                val latest   = async { repository.fetchLatest() }
                val liveNow  = async { repository.fetchLiveNow() }
                listOf(cw, trending, latest, liveNow).awaitAll()
                HomeData(
                    hero             = trending.await().take(5),  // top 5 → hero spotlight
                    continueWatching = cw.await(),
                    latest           = latest.await(),
                    trending         = trending.await(),
                    liveNow          = liveNow.await(),
                )
            }
                .onSuccess { data -> _ui.value = HomeUiState(isLoading = false, data = data) }
                .onFailure { err  -> _ui.value = _ui.value.copy(
                    isLoading = false,
                    error     = err.message ?: "Error al cargar",
                ) }
        }
    }

    /** Called from MediaCard when it gains focus. Hero observes and crossfades. */
    fun onCardFocused(item: MediaItem?) {
        _focusedItem.value = item
    }

    companion object {
        fun factory(repository: HomeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository) as T
            }
        }
    }
}

data class HomeUiState(
    val isLoading: Boolean   = false,
    val data:      HomeData  = HomeData(),
    val error:     String?   = null,
)
