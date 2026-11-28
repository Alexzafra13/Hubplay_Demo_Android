package com.alex.hubplay.ui.shared

import android.util.Log
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.Content
import com.alex.hubplay.data.MediaKind
import com.alex.hubplay.data.PeersRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
            when {
                item == null                        -> Unit
                item.remoteKind == MediaKind.Series -> loadSeasons(item)
                else                                -> refreshProgress()
            }
        }
    }

    /**
     * Posición guardada (al entrar y al volver del reproductor). En una
     * serie, la de los episodios de la temporada abierta.
     */
    fun refreshProgress() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val series = _ui.value.series
            if (series != null) {
                loadProgress(series.selectedEpisodes)
                return@launch
            }
            runCatching { repository.resumePosition(target.peerId, target.itemId) }
                .onSuccess { p -> _ui.update { it.copy(resumeSec = p.resumeSec) } }
        }
    }

    /** Abre una temporada; sus episodios se piden la primera vez. */
    fun selectSeason(seasonId: String) {
        updateSeries { it.copy(selectedSeasonId = seasonId) }
        val state = _ui.value.series ?: return
        if (state.episodesBySeason.containsKey(seasonId)) return
        val season = state.seasons.firstOrNull { it.id == seasonId } ?: return
        viewModelScope.launch {
            val episodes = runCatching { repository.children(season) }
                .onFailure { Log.w(TAG, "episodes ${target.peerId}/$seasonId", it) }
                .getOrDefault(emptyList())
                .filter { it.remoteKind == MediaKind.Episode }
            updateSeries { it.copy(episodesBySeason = it.episodesBySeason + (seasonId to episodes)) }
            loadProgress(episodes)
        }
    }

    private fun loadSeasons(series: Content.Shared) {
        _ui.update { it.copy(series = SharedSeriesState()) }
        viewModelScope.launch {
            val children = runCatching { repository.children(series) }
                .onFailure { Log.w(TAG, "seasons ${target.peerId}/${series.id}", it) }
                .getOrNull()
            if (children == null) {
                updateSeries { SharedSeriesState(isLoading = false, unavailable = true) }
                return@launch
            }
            val seasons = children.filter { it.remoteKind == MediaKind.Season }
            if (seasons.isNotEmpty()) {
                updateSeries { it.copy(seasons = seasons, isLoading = false) }
                firstRegularSeason(seasons)?.let { selectSeason(it.id) }
                return@launch
            }
            // Serie sin carpetas de temporada: los episodios cuelgan de ella
            // y la propia serie hace de temporada única.
            val episodes = children.filter { it.remoteKind == MediaKind.Episode }
            val single = series.copy(remoteKind = MediaKind.Season, seasonNumber = null)
            updateSeries {
                SharedSeriesState(
                    seasons          = listOf(single),
                    selectedSeasonId = single.id,
                    episodesBySeason = mapOf(single.id to episodes),
                    isLoading        = false,
                )
            }
            loadProgress(episodes)
        }
    }

    /** Progreso de [episodes] en paralelo (vive en nuestro servidor: barato). */
    private suspend fun loadProgress(episodes: List<Content.Shared>) {
        if (episodes.isEmpty()) return
        val found = coroutineScope {
            episodes.map { ep ->
                async { runCatching { ep.id to repository.resumePosition(target.peerId, ep.id) }.getOrNull() }
            }.awaitAll().filterNotNull()
        }
        updateSeries { it.copy(progress = it.progress + found) }
    }

    private fun updateSeries(change: (SharedSeriesState) -> SharedSeriesState) {
        _ui.update { ui -> ui.series?.let { ui.copy(series = change(it)) } ?: ui }
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
    /** Solo si el item es una serie: sus temporadas y episodios. */
    val series:    SharedSeriesState? = null,
)
