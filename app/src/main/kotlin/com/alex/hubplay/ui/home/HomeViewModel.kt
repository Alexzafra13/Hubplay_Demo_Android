package com.alex.hubplay.ui.home

import android.util.Log
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
import kotlinx.coroutines.supervisorScope

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
            // supervisorScope so a single rail's failure doesn't cancel
            // the others. Each rail is wrapped in runCatching so its
            // exception is captured locally and the rail simply renders
            // empty — the page as a whole still paints with whichever
            // rails came back.
            val data = supervisorScope {
                val cw       = async { safeFetch("continueWatching") { repository.fetchContinueWatching() } }
                val trending = async { safeFetch("trending")         { repository.fetchTrending() } }
                val latest   = async { safeFetch("latest")           { repository.fetchLatest() } }
                val liveNow  = async { safeFetch("liveNow")          { repository.fetchLiveNow() } }
                listOf(cw, trending, latest, liveNow).awaitAll()
                HomeData(
                    hero             = trending.await().take(5),  // top 5 → hero spotlight
                    continueWatching = cw.await(),
                    latest           = latest.await(),
                    trending         = trending.await(),
                    liveNow          = liveNow.await(),
                )
            }
            _ui.value = HomeUiState(isLoading = false, data = data)
        }
    }

    private inline fun safeFetch(label: String, block: () -> List<MediaItem>): List<MediaItem> {
        return runCatching(block).getOrElse { err ->
            Log.w("HomeViewModel", "rail '$label' failed: ${err.message}", err)
            emptyList()
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
