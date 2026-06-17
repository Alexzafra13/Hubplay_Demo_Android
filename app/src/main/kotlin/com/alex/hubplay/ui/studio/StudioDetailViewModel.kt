package com.alex.hubplay.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.StudioDetail
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads /studios/{slug} and exposes the studio + its catalogue. Mirrors
 * [com.alex.hubplay.ui.person.PersonDetailViewModel]: read-only, one
 * StateFlow, retry via [load].
 */
class StudioDetailViewModel(
    private val repository: HomeRepository,
    private val slug:       String,
) : ViewModel() {

    private val _ui = MutableStateFlow(StudioUiState(isLoading = true))
    val ui: StateFlow<StudioUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.fetchStudio(slug) }
                .onSuccess { studio ->
                    _ui.value = StudioUiState(isLoading = false, studio = studio)
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(
                        isLoading = false,
                        error     = friendlyError(err, "No se pudo cargar el estudio"),
                    )
                }
        }
    }

    companion object {
        fun factory(repository: HomeRepository, slug: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return StudioDetailViewModel(repository, slug) as T
                }
            }
    }
}

@androidx.compose.runtime.Immutable
data class StudioUiState(
    val isLoading: Boolean       = false,
    val studio:    StudioDetail? = null,
    val error:     String?       = null,
)
