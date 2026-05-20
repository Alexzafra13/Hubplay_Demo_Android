package com.alex.hubplay.data

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Spec around [ChannelOrderStore]'s key-building and [ChannelPrefs]
 * housekeeping. The store itself is a thin DataStore wrapper used as a
 * write-through cache + refresh-signal bus; the heavy logic lives on
 * the backend now, so there isn't a sort/filter policy to test here
 * anymore.
 */
class ChannelOrderStoreTest {

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
    fun `buildKey separates server from library`() {
        // The pipe character is the chosen separator; library IDs are
        // UUIDs (no pipes) so collision is structurally impossible.
        val key = ChannelOrderStore.buildKey("https://host", "lib-1")
        assertThat(key).isEqualTo("https://host|lib-1")
    }

    @Test
    fun `isEmpty round-trip`() {
        assertThat(ChannelPrefs().isEmpty()).isTrue()
        assertThat(ChannelPrefs(order = listOf("a")).isEmpty()).isFalse()
        assertThat(ChannelPrefs(hidden = listOf("a")).isEmpty()).isFalse()
    }
}
