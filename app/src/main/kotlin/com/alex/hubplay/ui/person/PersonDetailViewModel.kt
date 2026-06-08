package com.alex.hubplay.ui.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.PersonDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Loads /people/{id} and exposes the person + filmography to the
 * PersonDetail screen. Same shape as [com.alex.hubplay.ui.detail.DetailViewModel]:
 * a single StateFlow, optimistic-free (read-only screen), retry via [load].
 */
class PersonDetailViewModel(
    private val repository: HomeRepository,
    private val personId:   String,
) : ViewModel() {

    private val _ui = MutableStateFlow(PersonUiState(isLoading = true))
    val ui: StateFlow<PersonUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.fetchPerson(personId) }
                .onSuccess { person ->
                    _ui.value = PersonUiState(isLoading = false, person = person)
                }
                .onFailure { err ->
                    _ui.value = _ui.value.copy(
                        isLoading = false,
                        error     = err.message ?: "No se pudo cargar la persona",
                    )
                }
        }
    }

    companion object {
        fun factory(repository: HomeRepository, personId: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PersonDetailViewModel(repository, personId) as T
                }
            }
    }
}

@androidx.compose.runtime.Immutable
data class PersonUiState(
    val isLoading: Boolean       = false,
    val person:    PersonDetail? = null,
    val error:     String?       = null,
)
