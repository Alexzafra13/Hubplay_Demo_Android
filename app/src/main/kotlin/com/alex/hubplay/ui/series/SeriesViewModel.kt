package com.alex.hubplay.ui.series

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.MediaItem
import com.alex.hubplay.data.MediaKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

/**
 * Drives the Series screen. Owns:
 *   - The series metadata (`/items/{seriesId}`).
 *   - The seasons (`/items/{seriesId}/children`).
 *   - The episodes for the currently-selected season
 *     (`/items/{seasonId}/children`, lazy on first selection).
 *   - The resume target (Resume / Next-Up / Start / None) — derived from
 *     CW + Next-Up + first season episodes.
 */
class SeriesViewModel(
    private val repository: HomeRepository,
    private val seriesId:   String,
) : ViewModel() {

    private val _ui = MutableStateFlow(SeriesUiState(isLoading = true))
    val ui: StateFlow<SeriesUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            val data = supervisorScope {
                val seriesDef   = async { runCatching { repository.fetchItemDetail(seriesId) }
                                            .onFailure { Log.w(TAG, "fetchItemDetail($seriesId)", it) }
                                            .getOrNull() }
                val seasonsDef  = async { runCatching { repository.fetchChildren(seriesId) }
                                            .onFailure { Log.w(TAG, "fetchChildren($seriesId)", it) }
                                            .getOrElse { emptyList() } }
                val cwDef       = async { runCatching { repository.fetchContinueWatching() }
                                            .onFailure { Log.w(TAG, "fetchContinueWatching", it) }
                                            .getOrElse { emptyList() } }
                val nextUpDef   = async { runCatching { repository.fetchNextUp() }
                                            .onFailure { Log.w(TAG, "fetchNextUp", it) }
                                            .getOrElse { emptyList() } }

                val series  = seriesDef.await()
                val seasons = seasonsDef.await()
                    .filter { it.kind == MediaKind.Season }
                    .sortedBy { it.seasonNumber ?: Int.MAX_VALUE }
                val cw      = cwDef.await()
                val nextUp  = nextUpDef.await()

                // Fetch episodes for the first season up-front so the
                // resume resolver has a cold-start fallback ready, and
                // the screen can paint episode rows immediately.
                val firstSeasonId = seasons.firstOrNull()?.id
                val firstEpisodes = firstSeasonId?.let { sid ->
                    runCatching { repository.fetchChildren(sid) }
                        .onFailure { Log.w(TAG, "fetchChildren($sid) season episodes", it) }
                        .getOrElse { emptyList() }
                        .filter { it.kind == MediaKind.Episode }
                        .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
                } ?: emptyList()

                val resume = SeriesResumeResolver.resolve(
                    seriesId            = seriesId,
                    continueWatching    = cw,
                    nextUp              = nextUp,
                    firstSeasonEpisodes = firstEpisodes,
                )

                SeriesData(
                    series             = series,
                    seasons            = seasons,
                    episodesBySeasonId = if (firstSeasonId != null) mapOf(firstSeasonId to firstEpisodes) else emptyMap(),
                    selectedSeasonId   = firstSeasonId,
                    resume             = resume,
                )
            }
            _ui.value = SeriesUiState(isLoading = false, data = data)

            // Phase 3 — background pre-fetch of every OTHER season's
            // episodes so the SeasonRow can show "Y episodios" next to
            // each entry, not just the currently-selected one. Runs
            // after the initial paint so the user sees the hero +
            // selected season immediately; counts trickle in as the
            // fetches resolve. Failures degrade gracefully (the row
            // stays count-less).
            prefetchRemainingSeasonEpisodes()
        }
    }

    private fun prefetchRemainingSeasonEpisodes() {
        viewModelScope.launch {
            val current = _ui.value.data ?: return@launch
            val pending = current.seasons.filter { !current.episodesBySeasonId.containsKey(it.id) }
            if (pending.isEmpty()) return@launch

            supervisorScope {
                pending.map { season ->
                    async {
                        val episodes = runCatching { repository.fetchChildren(season.id) }
                            .onFailure { Log.w(TAG, "prefetch fetchChildren(${season.id})", it) }
                            .getOrElse { emptyList() }
                            .filter { it.kind == MediaKind.Episode }
                            .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
                        season.id to episodes
                    }
                }.awaitAll().forEach { (id, episodes) ->
                    val data = _ui.value.data ?: return@forEach
                    _ui.value = _ui.value.copy(
                        data = data.copy(
                            episodesBySeasonId = data.episodesBySeasonId + (id to episodes),
                        ),
                    )
                }
            }
        }
    }

    /**
     * Toggle the favourite flag on the series itself. Episodes and
     * seasons share the same series-level heart — semantically a user
     * "favourites Breaking Bad", not "favourites S03E07". Optimistic
     * with a revert on failure, same pattern as DetailViewModel.
     */
    fun toggleFavorite() {
        val current = _ui.value.data?.series ?: return
        val optimistic = current.copy(isFavorite = !current.isFavorite)
        _ui.value = _ui.value.copy(
            data = _ui.value.data?.copy(series = optimistic),
        )
        viewModelScope.launch {
            runCatching { repository.toggleItemFavorite(current.id) }
                .onSuccess { actual ->
                    val data = _ui.value.data ?: return@launch
                    _ui.value = _ui.value.copy(
                        data = data.copy(series = optimistic.copy(isFavorite = actual)),
                    )
                }
                .onFailure {
                    val data = _ui.value.data ?: return@launch
                    _ui.value = _ui.value.copy(data = data.copy(series = current))
                }
        }
    }

    /**
     * User picked a different season. If we don't have those episodes
     * cached yet, fetch them; otherwise just flip the selection.
     */
    fun selectSeason(seasonId: String) {
        val current = _ui.value.data ?: return
        if (current.selectedSeasonId == seasonId) return

        // Optimistically flip selection so the UI feels responsive even
        // while episodes are loading.
        _ui.value = _ui.value.copy(
            data = current.copy(selectedSeasonId = seasonId),
        )

        if (current.episodesBySeasonId.containsKey(seasonId)) return

        viewModelScope.launch {
            val episodes = runCatching { repository.fetchChildren(seasonId) }
                .onFailure { Log.w(TAG, "fetchChildren($seasonId)", it) }
                .getOrElse { emptyList() }
                .filter { it.kind == MediaKind.Episode }
                .sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
            val updated = _ui.value.data ?: return@launch
            _ui.value = _ui.value.copy(
                data = updated.copy(
                    episodesBySeasonId = updated.episodesBySeasonId + (seasonId to episodes),
                ),
            )
        }
    }

    companion object {
        private const val TAG = "SeriesViewModel"

        fun factory(repository: HomeRepository, seriesId: String) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SeriesViewModel(repository, seriesId) as T
                }
            }
    }
}

data class SeriesUiState(
    val isLoading: Boolean      = false,
    val data:      SeriesData?  = null,
    val error:     String?      = null,
)

data class SeriesData(
    val series:             MediaItem?,
    val seasons:            List<MediaItem>,
    val episodesBySeasonId: Map<String, List<MediaItem>>,
    val selectedSeasonId:   String?,
    val resume:             SeriesResumeTarget,
)
