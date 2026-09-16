package com.alex.hubplay.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.api.dto.HomeSectionDto
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Qué rails salen en Inicio y en qué orden. Se guarda en el servidor
 * (`PUT /me/home/layout`) para el usuario del token, es decir, por perfil.
 * Cada cambio se aplica en pantalla al instante y se guarda detrás; si el
 * guardado falla, la lista vuelve a lo que el servidor devolvió.
 */
class HomeLayoutViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _ui = MutableStateFlow(HomeLayoutUiState())
    val ui: StateFlow<HomeLayoutUiState> = _ui.asStateFlow()

    init {
        load()
    }

    fun load() {
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.fetchHomeSections() }
                .onSuccess { sections -> _ui.update { it.copy(sections = sections, isLoading = false) } }
                .onFailure { err ->
                    _ui.update { it.copy(isLoading = false, error = friendlyError(err, "No se pudo cargar la configuración")) }
                }
        }
    }

    fun toggleVisible(id: String) = mutate { list ->
        list.map { if (it.id == id) it.copy(visible = !it.visible) else it }
    }

    fun moveUp(id: String) = mutate { list -> list.swapped(list.indexOfFirst { it.id == id }, -1) }

    fun moveDown(id: String) = mutate { list -> list.swapped(list.indexOfFirst { it.id == id }, +1) }

    fun clearError() = _ui.update { it.copy(error = null) }

    private fun mutate(transform: (List<HomeSectionDto>) -> List<HomeSectionDto>) {
        val before = _ui.value.sections
        val next = transform(before)
        if (next == before) return
        _ui.update { it.copy(sections = next, error = null) }
        viewModelScope.launch {
            runCatching { repository.saveHomeSections(next) }
                .onSuccess { saved -> _ui.update { it.copy(sections = saved) } }
                .onFailure { err ->
                    _ui.update { it.copy(sections = before, error = friendlyError(err, "No se pudo guardar")) }
                }
        }
    }

    private fun List<HomeSectionDto>.swapped(index: Int, delta: Int): List<HomeSectionDto> {
        val other = index + delta
        if (index !in indices || other !in indices) return this
        return toMutableList().also {
            it[index] = this[other]
            it[other] = this[index]
        }
    }

    companion object {
        fun factory(repository: HomeRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeLayoutViewModel(repository) as T
        }
    }
}

@androidx.compose.runtime.Immutable
data class HomeLayoutUiState(
    val sections:  List<HomeSectionDto> = emptyList(),
    val isLoading: Boolean = true,
    val error:     String? = null,
)
