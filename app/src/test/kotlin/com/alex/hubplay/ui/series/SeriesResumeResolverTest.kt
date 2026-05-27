package com.alex.hubplay.ui.series

import com.alex.hubplay.data.Content
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The resume resolver decides which episode plays when the user hits
 * "Reproducir" on a series. Its precedence (RESUME > NEXT_UP > START >
 * NONE) is the single most user-visible piece of logic in the app — get
 * it wrong and a user with a half-watched S03E04 mid-season ends up
 * being thrown back to S01E01.
 *
 * These tests pin each branch of [SeriesResumeResolver.resolve] against
 * realistic shapes the repository hands it.
 */
class SeriesResumeResolverTest {

    @Test
    fun `RESUME wins over everything else when an in-progress episode exists`() {
        val target = SeriesResumeResolver.resolve(
            seriesId = "s-bb",
            continueWatching = listOf(
                episode(id = "ep-s03e04", seriesId = "s-bb", season = 3, episode = 4, resumeSec = 600),
            ),
            nextUp = listOf(
                episode(id = "ep-s03e05", seriesId = "s-bb", season = 3, episode = 5),
            ),
            firstSeasonEpisodes = listOf(
                episode(id = "ep-s01e01", seriesId = "s-bb", season = 1, episode = 1),
            ),
        )

        assertThat(target.mode).isEqualTo(SeriesResumeMode.RESUME)
        assertThat(target.episodeId).isEqualTo("ep-s03e04")
        assertThat(target.resumeSec).isEqualTo(600L)
        assertThat(target.playLabel).contains("S3 · E4")
    }

    @Test
    fun `CW from another series is ignored`() {
        val target = SeriesResumeResolver.resolve(
            seriesId = "s-bb",
            continueWatching = listOf(
                episode(id = "ep-other", seriesId = "s-other", season = 1, episode = 2, resumeSec = 300),
            ),
            nextUp = emptyList(),
            firstSeasonEpisodes = listOf(
                episode(id = "ep-s01e01", seriesId = "s-bb", season = 1, episode = 1),
            ),
        )

        // No CW match for this series → falls through to START.
        assertThat(target.mode).isEqualTo(SeriesResumeMode.START)
        assertThat(target.episodeId).isEqualTo("ep-s01e01")
    }

    @Test
    fun `NEXT_UP fires when no CW but a queued episode exists`() {
        val target = SeriesResumeResolver.resolve(
            seriesId = "s-bb",
            continueWatching = emptyList(),
            nextUp = listOf(
                episode(id = "ep-s03e05", seriesId = "s-bb", season = 3, episode = 5),
            ),
            firstSeasonEpisodes = listOf(
                episode(id = "ep-s01e01", seriesId = "s-bb", season = 1, episode = 1),
            ),
        )

        assertThat(target.mode).isEqualTo(SeriesResumeMode.NEXT_UP)
        assertThat(target.episodeId).isEqualTo("ep-s03e05")
        assertThat(target.resumeSec).isEqualTo(0L)
        assertThat(target.playLabel).contains("Reproducir")
        assertThat(target.playLabel).contains("S3 · E5")
    }

    @Test
    fun `START picks the lowest-numbered episode of the first season`() {
        val target = SeriesResumeResolver.resolve(
            seriesId = "s-bb",
            continueWatching = emptyList(),
            nextUp = emptyList(),
            firstSeasonEpisodes = listOf(
                episode(id = "ep-s01e02", seriesId = "s-bb", season = 1, episode = 2),
                episode(id = "ep-s01e01", seriesId = "s-bb", season = 1, episode = 1),
                episode(id = "ep-s01e03", seriesId = "s-bb", season = 1, episode = 3),
            ),
        )

        assertThat(target.mode).isEqualTo(SeriesResumeMode.START)
        assertThat(target.episodeId).isEqualTo("ep-s01e01")
    }

    @Test
    fun `START ignores non-episode children (seasons, specials with no number)`() {
        // The repository sometimes hands a season alongside its episodes
        // (older API quirk); the resolver should filter those out via
        // its sealed-type pattern matching instead of relying on a
        // string-based kind check.
        val season = Content.Season(id = "season-1", title = "Season 1", seasonNumber = 1)
        val target = SeriesResumeResolver.resolve(
            seriesId = "s-bb",
            continueWatching = emptyList(),
            nextUp = emptyList(),
            firstSeasonEpisodes = listOf(
                season,
                episode(id = "ep-s01e01", seriesId = "s-bb", season = 1, episode = 1),
            ),
        )

        assertThat(target.mode).isEqualTo(SeriesResumeMode.START)
        assertThat(target.episodeId).isEqualTo("ep-s01e01")
    }

    @Test
    fun `NONE when no CW, no next-up and no episodes`() {
        val target = SeriesResumeResolver.resolve(
            seriesId = "s-empty",
            continueWatching = emptyList(),
            nextUp = emptyList(),
            firstSeasonEpisodes = emptyList(),
        )

        assertThat(target.mode).isEqualTo(SeriesResumeMode.NONE)
        assertThat(target.episodeId).isNull()
        assertThat(target.playLabel).isNull()
    }

    private fun episode(
        id:        String,
        seriesId:  String,
        season:    Int,
        episode:   Int,
        resumeSec: Long = 0L,
    ): Content.Episode = Content.Episode(
        id            = id,
        title         = "Episodio $episode",
        progressPct   = if (resumeSec > 0) 0.2f else 0f,
        resumePosSec  = resumeSec,
        seriesId      = seriesId,
        seasonNumber  = season,
        episodeNumber = episode,
    )
}
