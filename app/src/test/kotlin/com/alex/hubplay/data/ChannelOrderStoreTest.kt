package com.alex.hubplay.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spec for [ChannelOrderStore.applyPrefs] and the order-view sibling.
 *
 * These are the pure functions that decide how a fresh channel list from
 * the backend gets reshaped by the saved order + hidden set. They drive
 * what the user sees on the Live TV main screen and on the reorder
 * screen, so the rules around new channels and hidden channels need to
 * be locked down in tests rather than reverse-engineered from the UI.
 */
class ChannelOrderStoreTest {

    private fun channel(id: String, number: Int = 0, name: String = id) = LiveChannel(
        id            = id,
        name          = name,
        number        = number,
        groupName     = "",
        category      = "",
        logoUrl       = null,
        logoInitials  = null,
        logoBg        = null,
        logoFg        = null,
        libraryId     = "lib1",
        isActive      = true,
        healthStatus  = "ok",
    )

    @Test
    fun `empty prefs returns channels unchanged`() {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val result = ChannelOrderStore.applyPrefs(channels, ChannelPrefs())
        assertThat(result.map { it.id }).containsExactly("a", "b", "c").inOrder()
    }

    @Test
    fun `saved order moves known channels to the front`() {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val prefs    = ChannelPrefs(order = listOf("c", "a"))
        val result   = ChannelOrderStore.applyPrefs(channels, prefs)
        // 'c' and 'a' obey the saved order; 'b' is unknown so it lands at
        // the tail in its original incoming position.
        assertThat(result.map { it.id }).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `unknown channels keep relative incoming order at the tail`() {
        val channels = listOf(channel("a"), channel("b"), channel("c"), channel("d"))
        val prefs    = ChannelPrefs(order = listOf("c"))
        val result   = ChannelOrderStore.applyPrefs(channels, prefs)
        // 'a', 'b', 'd' are unknown to the saved order; they keep their
        // incoming order behind the one pinned channel.
        assertThat(result.map { it.id }).containsExactly("c", "a", "b", "d").inOrder()
    }

    @Test
    fun `hidden channels drop out of applyPrefs`() {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val prefs    = ChannelPrefs(order = listOf("c", "a"), hidden = listOf("a"))
        val result   = ChannelOrderStore.applyPrefs(channels, prefs)
        assertThat(result.map { it.id }).containsExactly("c", "b").inOrder()
    }

    @Test
    fun `applyPrefsForOrderView keeps hidden channels in view`() {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val prefs    = ChannelPrefs(order = listOf("c", "a"), hidden = listOf("a"))
        val result   = ChannelOrderStore.applyPrefsForOrderView(channels, prefs)
        // 'a' is hidden but the reorder UI must still show it so the user
        // can unhide it. Order honoured the same way.
        assertThat(result.map { it.id }).containsExactly("c", "a", "b").inOrder()
    }

    @Test
    fun `stale order entries pointing at removed channels are tolerated`() {
        // Server dropped 'b' between sessions. Saved order still mentions it.
        val channels = listOf(channel("a"), channel("c"))
        val prefs    = ChannelPrefs(order = listOf("a", "b", "c"))
        val result   = ChannelOrderStore.applyPrefs(channels, prefs)
        // Missing IDs just don't appear; existing IDs honour their slot.
        assertThat(result.map { it.id }).containsExactly("a", "c").inOrder()
    }

    @Test
    fun `buildKey is stable when trailing slash differs`() {
        // The store trims trailing slashes so a server URL written with vs.
        // without one resolves to the same library's prefs after store
        // normalisation — guards against the user typing
        // "https://host/" once and "https://host" another time.
        val a = ChannelOrderStore.buildKey("https://hubplay.local/", "lib-1")
        val b = ChannelOrderStore.buildKey("https://hubplay.local",  "lib-1")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun `empty prefs round-trip helper`() {
        assertThat(ChannelPrefs().isEmpty()).isTrue()
        assertThat(ChannelPrefs(order = listOf("a")).isEmpty()).isFalse()
        assertThat(ChannelPrefs(hidden = listOf("a")).isEmpty()).isFalse()
    }
}
