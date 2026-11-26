package com.alex.hubplay.ui.shared

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.PeersRepository
import com.alex.hubplay.data.SharedLibrary
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * "Compartido contigo": un rail por biblioteca compartida (de todos los
 * amigos) más "Seguir viendo" arriba. Todo se pide en paralelo; una
 * biblioteca que falla (amigo apagado y sin caché) no tumba las demás.
 */
class SharedViewModel(private val repository: PeersRepository) : ViewModel() {

    private val _ui = MutableStateFlow(SharedUiState(isLoading = true))
    val ui: StateFlow<SharedUiState> = _ui.asStateFlow()

    private var loadJob: Job? = null
    private var continueJob: Job? = null

    init { load() }

    fun load() {
        loadJob?.cancel()
        _ui.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            val result = runCatching {
                supervisorScope {
                    val cw   = async { runCatching { repository.continueWatching() }.getOrDefault(emptyList()) }
                    val libs = repository.libraries()
                    val rails = libs.map { lib -> async { loadRail(lib) } }.awaitAll()
                    cw.await() to rails
                }
            }
            result.onSuccess { (cw, rails) ->
                _ui.value = SharedUiState(isLoading = false, continueWatching = cw, rails = rails)
            }.onFailure { err ->
                Log.w(TAG, "shared libraries failed", err)
                _ui.value = SharedUiState(
                    isLoading = false,
                    error     = "No se pudo cargar lo compartido. Comprueba tu conexión e inténtalo de nuevo.",
                )
            }
        }
    }

    /** Al volver de la ficha o del reproductor: solo "Seguir viendo" cambia. */
    fun refreshContinueWatching() {
        if (_ui.value.isLoading) return
        continueJob?.cancel()
        continueJob = viewModelScope.launch {
            runCatching { repository.continueWatching() }
                .onSuccess { cw -> _ui.update { it.copy(continueWatching = cw) } }
        }
    }

    private suspend fun loadRail(library: SharedLibrary): SharedRail =
        runCatching { repository.libraryItems(library) }
            .fold(
                onSuccess = { page -> SharedRail(library, page.items, page.fromCache, failed = false) },
                onFailure = { err ->
                    Log.w(TAG, "library ${library.key} failed: ${err.message}")
                    SharedRail(library, emptyList(), fromCache = false, failed = true)
                },
            )

    companion object {
        private const val TAG = "SharedViewModel"

        fun factory(repository: PeersRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = SharedViewModel(repository) as T
        }
    }
}

@Immutable
data class SharedUiState(
    val isLoading:        Boolean = false,
    val error:            String? = null,
    val continueWatching: List<Content.Shared> = emptyList(),
    val rails:            List<SharedRail> = emptyList(),
) {
    val isEmpty: Boolean get() = continueWatching.isEmpty() && rails.all { it.items.isEmpty() && !it.failed }
}

/** Una biblioteca compartida y su primera página. */
@Immutable
data class SharedRail(
    val library:   SharedLibrary,
    val items:     List<Content.Shared>,
    /** El amigo no respondió: es lo último que guardó nuestro servidor. */
    val fromCache: Boolean,
    val failed:    Boolean,
)
