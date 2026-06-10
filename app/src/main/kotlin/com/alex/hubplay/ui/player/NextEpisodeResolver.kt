package com.alex.hubplay.ui.player

import androidx.compose.runtime.Immutable

/**
 * Minimal projection of a sibling episode for next-episode arithmetic.
 * Built from `/items/{id}/children` rows; deliberately tiny so the
 * resolver stays a pure, JVM-testable function of plain data.
 */
data class EpisodeRef(
    val id:            String,
    val title:         String? = null,
    val seasonNumber:  Int? = null,
    val episodeNumber: Int? = null,
)

/** A season row under a series, as returned by `/items/{seriesId}/children`. */
data class SeasonRef(
    val id:           String,
    val seasonNumber: Int? = null,
)

/**
 * What the auto-play overlay needs to announce and start the episode
 * that follows the one currently playing.
 */
@Immutable
data class NextEpisodeInfo(
    val id:            String,
    val title:         String? = null,
    val seasonNumber:  Int? = null,
    val episodeNumber: Int? = null,
) {
    /** "S2 · E5" — same shape the series screen prints. Empty when unnumbered. */
    val label: String get() = when {
        seasonNumber != null && episodeNumber != null -> "S$seasonNumber · E$episodeNumber"
        episodeNumber != null                         -> "E$episodeNumber"
        else                                          -> ""
    }
}

/**
 * Pure "which episode comes next?" arithmetic, deterministic on the
 * client so auto-play never races the server's next-up bookkeeping
 * (markPlayed fires at 95% — at the moment the credits end, /me/next-up
 * may or may not have advanced yet).
 *
 * Ordering rule everywhere: sort by the number when present, push
 * unnumbered rows last (scanner order is preserved among ties because
 * [sortedBy] is stable).
 */
object NextEpisodeResolver {

    /** The episode after `currentEpisodeId` within its own season, or null at the end. */
    fun nextAfter(currentEpisodeId: String, seasonEpisodes: List<EpisodeRef>): EpisodeRef? {
        val ordered = seasonEpisodes.sortedBy { it.episodeNumber ?: Int.MAX_VALUE }
        val idx = ordered.indexOfFirst { it.id == currentEpisodeId }
        if (idx < 0) return null
        return ordered.getOrNull(idx + 1)
    }

    /**
     * The season that follows `currentSeasonId` in season-number order,
     * or null when it's the last one. Index-based on the *sorted* list,
     * so specials filed as season 0 sort first and never get picked
     * "after" a regular season.
     */
    fun seasonFollowing(currentSeasonId: String, seasons: List<SeasonRef>): SeasonRef? {
        val ordered = seasons.sortedBy { it.seasonNumber ?: Int.MAX_VALUE }
        val idx = ordered.indexOfFirst { it.id == currentSeasonId }
        if (idx < 0) return null
        return ordered.getOrNull(idx + 1)
    }

    /** Lowest-numbered episode of a season — the cross-season landing target. */
    fun firstEpisode(episodes: List<EpisodeRef>): EpisodeRef? =
        episodes.minByOrNull { it.episodeNumber ?: Int.MAX_VALUE }
}
