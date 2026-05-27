package com.alex.hubplay.ui.series

import com.alex.hubplay.data.Content

/**
 * Picks the "right" episode to play when the user hits Reproducir on a
 * series. Mirrors web/src/hooks/useSeriesResumeTarget.ts so behaviour
 * stays consistent across clients.
 *
 * Resolution order:
 *   1. RESUME   — an in-progress episode of this series (<95%) in CW.
 *   2. NEXT_UP  — a queued next episode under /me/next-up for this series.
 *   3. START    — no progress: lowest-numbered season → lowest-numbered
 *                  episode. Requires expanding the season's children.
 *   4. NONE     — series has no episodes yet (still indexing, empty source).
 */
enum class SeriesResumeMode { RESUME, NEXT_UP, START, NONE }

data class SeriesResumeTarget(
    val mode:        SeriesResumeMode,
    val episodeId:   String?,
    val resumeSec:   Long,
    /**
     * Display label for the Play button, e.g. "Reanudar S2 · E4" or
     * "Reproducir S1 · E1". Null when [mode] is NONE.
     */
    val playLabel:   String?,
)

object SeriesResumeResolver {

    /**
     * Resolve the resume target given the data the SeriesViewModel
     * already collected — no new network calls inside.
     *
     * Inputs are sealed [Content] subtypes so callers can't pass us a
     * malformed list (e.g. movies into the CW filter): the type system
     * enforces "episodes only" where it matters.
     *
     * @param seriesId            the series the user is viewing.
     * @param continueWatching    the user's CW rail (already filtered by the API to <95%).
     * @param nextUp              the user's /me/next-up list.
     * @param firstSeasonEpisodes episodes of the first season in scanner order.
     */
    fun resolve(
        seriesId:            String,
        continueWatching:    List<Content.Resumable>,
        nextUp:              List<Content.Episode>,
        firstSeasonEpisodes: List<Content>,
    ): SeriesResumeTarget {
        // 1. Resume — CW already excludes >=95% server-side. Only Episodes
        //    carry a seriesId; movies in CW are skipped naturally.
        val inProgress = continueWatching.firstOrNull {
            it is Content.Episode && it.seriesId == seriesId
        } as? Content.Episode
        if (inProgress != null) {
            return SeriesResumeTarget(
                mode      = SeriesResumeMode.RESUME,
                episodeId = inProgress.id,
                resumeSec = inProgress.resumePosSec,
                playLabel = "Reanudar ${epLabel(inProgress)}",
            )
        }

        // 2. Next-up — server already returns one episode per series.
        val queued = nextUp.firstOrNull { it.seriesId == seriesId }
        if (queued != null) {
            return SeriesResumeTarget(
                mode      = SeriesResumeMode.NEXT_UP,
                episodeId = queued.id,
                resumeSec = 0L,
                playLabel = "Reproducir ${epLabel(queued)}",
            )
        }

        // 3. Cold start — first episode of the first available season.
        val firstEp = firstSeasonEpisodes
            .filterIsInstance<Content.Episode>()
            .minByOrNull { it.episodeNumber ?: Int.MAX_VALUE }
        if (firstEp != null) {
            return SeriesResumeTarget(
                mode      = SeriesResumeMode.START,
                episodeId = firstEp.id,
                resumeSec = 0L,
                playLabel = "Reproducir ${epLabel(firstEp)}",
            )
        }

        return SeriesResumeTarget(SeriesResumeMode.NONE, null, 0L, null)
    }

    private fun epLabel(item: Content.Episode): String {
        val s = item.seasonNumber; val e = item.episodeNumber
        return when {
            s != null && e != null -> "S$s · E$e"
            e != null               -> "E$e"
            else                    -> "el siguiente"
        }
    }
}
