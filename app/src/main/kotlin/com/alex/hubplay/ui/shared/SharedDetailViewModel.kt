package com.alex.hubplay.ui.shared

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.PeersRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Ficha de un título de un amigo. El item sale de la caché del
 * repositorio (se acaba de ver en un listado) y la posición propia de
 * `/me/peers/{peer}/items/{item}/progress`, que se vuelve a pedir al
 * volver del reproductor.
 */
class SharedDetailViewModel(
    private val repository: PeersRepository,
    private val target:     SharedTarget,
) : ViewModel() {

    private val _ui = MutableStateFlow(SharedDetailUiState(isLoading = true))
    val ui: StateFlow<SharedDetailUiState> = _ui.asStateFlow()

    private var progressJob: Job? = null

    init { load() }

    fun load() {
        _ui.update { it.copy(isLoading = true, notFound = false) }
        viewModelScope.launch {
            val item = runCatching { repository.find(target.peerId, target.libraryId, target.itemId) }
                .onFailure { Log.w(TAG, "find ${target.peerId}/${target.itemId}", it) }
                .getOrNull()
            _ui.update { it.copy(isLoading = false, item = item, notFound = item == null) }
            if (item != null) refreshProgress()
        }
    }

    /** Posición guardada (al entrar y al volver del reproductor). */
    fun refreshProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            runCatching { repository.resumePosition(target.peerId, target.itemId) }
                .onSuccess { p -> _ui.update { it.copy(resumeSec = p.resumeSec) } }
        }
    }

    companion object {
        private const val TAG = "SharedDetailViewModel"

        fun factory(repository: PeersRepository, target: SharedTarget) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SharedDetailViewModel(repository, target) as T
        }
    }
}

/** Qué ficha abrir: item de [peerId], en la biblioteca [libraryId] si se sabe. */
data class SharedTarget(
    val peerId:    String,
    val itemId:    String,
    val libraryId: String?,
)

@Immutable
data class SharedDetailUiState(
    val isLoading: Boolean = false,
    val item:      Content.Shared? = null,
    val notFound:  Boolean = false,
    /** Segundo desde el que reanudar; 0 = empezar. */
    val resumeSec: Long = 0L,
)
