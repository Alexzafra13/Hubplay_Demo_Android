package com.alex.hubplay.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.ContinueWatchingItem
import com.alex.hubplay.data.HomeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns Home screen state. Triggers a refresh on construction; the screen
 * can also call [refresh] from a swipe-to-refresh gesture later.
 *
 * Errors are reflected as a string so the UI can render an inline retry
 * banner without bouncing back to a global error screen.
 */
class HomeViewModel(
    private val repository: HomeRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(HomeUiState(isLoading = true))
    val ui: StateFlow<HomeUiState> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.fetchContinueWatching() }
                .onSuccess { items ->
                    _ui.value = HomeUiState(
                        isLoading        = false,
                        continueWatching = items,
                    )
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(
                        isLoading = false,
                        error     = err.message ?: "Error al cargar",
                    )
                }
        }
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
    val isLoading:        Boolean = false,
    val continueWatching: List<ContinueWatchingItem> = emptyList(),
    val error:            String? = null,
)
