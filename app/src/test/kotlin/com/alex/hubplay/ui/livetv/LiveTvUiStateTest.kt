package com.alex.hubplay.ui.livetv

import com.alex.hubplay.data.LiveChannel
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-state tests for [LiveTvUiState] — the filter projection is the
 * only non-trivial logic the screen relies on, and it runs on every
 * recomposition, so it's worth pinning down:
 *
 *  - All / Favorites / Group preserve the inventory order (the backend
 *    already personalised it).
 *  - Recent re-orders by recency (newest first) and silently drops ids
 *    whose channel is gone from the inventory (hidden / M3U churn).
 */
class LiveTvUiStateTest {

    @Test
    fun `recent filter returns channels in recency order, not inventory order`() {
        val ui = state(
            channels  = listOf(channel("c1"), channel("c2"), channel("c3")),
            recentIds = listOf("c3", "c1"),
            filter    = ChannelFilter.Recent,
        )

        assertThat(ui.visibleChannels.map { it.id }).containsExactly("c3", "c1").inOrder()
    }

    @Test
    fun `recent filter drops ids that no longer exist in the inventory`() {
        val ui = state(
            channels  = listOf(channel("c1")),
            recentIds = listOf("gone", "c1", "also-gone"),
            filter    = ChannelFilter.Recent,
        )

        assertThat(ui.visibleChannels.map { it.id }).containsExactly("c1")
    }

    @Test
    fun `recentChannels is empty when there is no history`() {
        val ui = state(channels = listOf(channel("c1")), recentIds = emptyList())

        assertThat(ui.recentChannels).isEmpty()
    }

    @Test
    fun `all filter preserves inventory order`() {
        val ui = state(
            channels  = listOf(channel("c2"), channel("c1")),
            recentIds = listOf("c1", "c2"),
            filter    = ChannelFilter.All,
        )

        assertThat(ui.visibleChannels.map { it.id }).containsExactly("c2", "c1").inOrder()
    }

    @Test
    fun `favorites filter keeps inventory order and ignores recency`() {
        val ui = state(
            channels  = listOf(channel("c1"), channel("c2"), channel("c3")),
            recentIds = listOf("c3", "c2"),
            filter    = ChannelFilter.Favorites,
        ).copy(favorites = setOf("c3", "c2"))

        assertThat(ui.visibleChannels.map { it.id }).containsExactly("c2", "c3").inOrder()
    }

    @Test
    fun `group filter matches case-insensitively`() {
        val ui = state(
            channels = listOf(
                channel("c1", group = "Deportes"),
                channel("c2", group = "Cine"),
            ),
            filter   = ChannelFilter.Group("deportes"),
        )

        assertThat(ui.visibleChannels.map { it.id }).containsExactly("c1")
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    private fun state(
        channels:  List<LiveChannel>,
        recentIds: List<String> = emptyList(),
        filter:    ChannelFilter = ChannelFilter.All,
    ) = LiveTvUiState(
        channels  = channels,
        recentIds = recentIds,
        filter    = filter,
    )

    private fun channel(id: String, group: String = "") = LiveChannel(
        id           = id,
        name         = "Channel $id",
        number       = 1,
        groupName    = group,
        category     = "",
        logoUrl      = null,
        logoInitials = null,
        logoBg       = null,
        logoFg       = null,
        libraryId    = "lib1",
        isActive     = true,
        healthStatus = "ok",
    )
}
