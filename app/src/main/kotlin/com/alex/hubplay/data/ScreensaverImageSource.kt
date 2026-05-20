package com.alex.hubplay.data

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pool of backdrops the screensaver crossfades between.
 *
 * The screensaver shouldn't make HTTP calls on activation — that creates
 * a black-screen-then-image moment that breaks the "ambient" feeling.
 * Instead this source pre-loads a list of items at app start (and after
 * the user finishes pairing) and exposes them via a StateFlow.
 *
 * Source mix:
 *   1. Trending — the most cinematic backdrops the server picks.
 *   2. Latest movies — recent additions, varied catalogue coverage.
 * Items without a backdrop are dropped (no point showing a grey
 * rectangle); the result is shuffled so consecutive screensaver runs
 * don't replay the same five posters.
 *
 * Refresh policy: re-fetch on every successful auth state (so the pool
 * follows whatever server the user is paired with) and on a soft 6 h
 * timer for long-running sessions. A failed refresh keeps the old pool
 * — empty is worse than stale.
 */
class ScreensaverImageSource(
    private val homeRepository: HomeRepository,
) {

    private val _slides = MutableStateFlow<List<ScreensaverSlide>>(emptyList())
    val slides: StateFlow<List<ScreensaverSlide>> = _slides.asStateFlow()

    /**
     * Build a fresh pool. Fan-out is sequential to keep code simple; the
     * call happens off the main thread and the screensaver gracefully
     * handles an empty pool until this completes.
     */
    suspend fun refresh() {
        val trending = runCatching { homeRepository.fetchTrending(limit = 30) }
            .getOrElse {
                Log.w(TAG, "trending fetch failed", it); emptyList()
            }
        val latestMovies = runCatching { homeRepository.fetchLatest(libraryId = null, type = "movie") }
            .getOrElse {
                Log.w(TAG, "latest movies fetch failed", it); emptyList()
            }
        val latestSeries = runCatching { homeRepository.fetchLatest(libraryId = null, type = "series") }
            .getOrElse {
                Log.w(TAG, "latest series fetch failed", it); emptyList()
            }

        val combined = (trending + latestMovies + latestSeries)
            .asSequence()
            .filter { !it.backdropUrl.isNullOrBlank() }
            // De-dup on item id — Trending + Latest overlap heavily.
            .distinctBy { it.id }
            .map { ScreensaverSlide(
                id          = it.id,
                backdropUrl = it.backdropUrl!!,
                title       = it.title,
                year        = it.year,
            ) }
            .toMutableList()
            .also { it.shuffle() }

        _slides.value = combined
        Log.d(TAG, "refreshed ${combined.size} screensaver slides")
    }

    companion object {
        private const val TAG = "ScreensaverImageSource"
    }
}

data class ScreensaverSlide(
    val id:          String,
    val backdropUrl: String,
    val title:       String,
    val year:        Int?,
)
