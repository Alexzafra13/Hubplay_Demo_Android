package com.alex.hubplay.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure tests for [NextEpisodeResolver] — the arithmetic behind the
 * auto-play card. The interesting cases are ordering (children may
 * arrive in scanner order, not episode order), season finales, and the
 * specials-as-season-0 layout TMDb uses.
 */
class NextEpisodeResolverTest {

    @Test
    fun `nextAfter picks the following episode even when input is unordered`() {
        val episodes = listOf(ep("e3", number = 3), ep("e1", number = 1), ep("e2", number = 2))

        val next = NextEpisodeResolver.nextAfter("e1", episodes)

        assertThat(next?.id).isEqualTo("e2")
    }

    @Test
    fun `nextAfter returns null for the last episode of the season`() {
        val episodes = listOf(ep("e1", number = 1), ep("e2", number = 2))

        assertThat(NextEpisodeResolver.nextAfter("e2", episodes)).isNull()
    }

    @Test
    fun `nextAfter returns null when the current episode is not in the list`() {
        val episodes = listOf(ep("e1", number = 1))

        assertThat(NextEpisodeResolver.nextAfter("ghost", episodes)).isNull()
    }

    @Test
    fun `nextAfter pushes unnumbered episodes to the end`() {
        val episodes = listOf(ep("extra", number = null), ep("e1", number = 1), ep("e2", number = 2))

        // After e2 comes the unnumbered extra (sorted last), not a crash.
        assertThat(NextEpisodeResolver.nextAfter("e2", episodes)?.id).isEqualTo("extra")
    }

    @Test
    fun `seasonFollowing moves from season 1 to season 2 ignoring specials at 0`() {
        val seasons = listOf(
            SeasonRef("specials", seasonNumber = 0),
            SeasonRef("s2",       seasonNumber = 2),
            SeasonRef("s1",       seasonNumber = 1),
        )

        assertThat(NextEpisodeResolver.seasonFollowing("s1", seasons)?.id).isEqualTo("s2")
    }

    @Test
    fun `seasonFollowing returns null after the last season`() {
        val seasons = listOf(SeasonRef("s1", seasonNumber = 1), SeasonRef("s2", seasonNumber = 2))

        assertThat(NextEpisodeResolver.seasonFollowing("s2", seasons)).isNull()
    }

    @Test
    fun `firstEpisode picks the lowest numbered one`() {
        val episodes = listOf(ep("e5", number = 5), ep("e1", number = 1), ep("e3", number = 3))

        assertThat(NextEpisodeResolver.firstEpisode(episodes)?.id).isEqualTo("e1")
    }

    @Test
    fun `label renders season and episode in the series screen shape`() {
        val info = NextEpisodeInfo(id = "x", title = "Piloto", seasonNumber = 2, episodeNumber = 5)

        assertThat(info.label).isEqualTo("S2 · E5")
    }

    private fun ep(id: String, number: Int?) = EpisodeRef(id = id, episodeNumber = number)
}
