package com.alex.hubplay.ui.peers

import com.alex.hubplay.data.PeerLibrary
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PeersGroupingTest {

    private fun lib(name: String, type: String) =
        PeerLibrary(
            peerId = "p", peerName = "Peer", libraryId = name, name = name,
            contentType = type, canPlay = true, canLivetv = type == "livetv",
        )

    @Test
    fun `groups in deterministic order movies series livetv then other`() {
        val groups = groupLibrariesByType(
            listOf(
                lib("Docs", "documentary"),
                lib("Pelis", "movies"),
                lib("Canales", "livetv"),
                lib("Series", "series"),
            ),
        )
        assertThat(groups.map { it.contentType })
            .containsExactly("movies", "series", "livetv", "documentary")
            .inOrder()
    }

    @Test
    fun `blank content type falls into the other bucket`() {
        val groups = groupLibrariesByType(listOf(lib("X", "")))
        assertThat(groups).hasSize(1)
        assertThat(groups.first().contentType).isEqualTo("other")
    }

    @Test
    fun `libraries of the same type stay together preserving order`() {
        val groups = groupLibrariesByType(
            listOf(lib("A", "movies"), lib("B", "movies")),
        )
        assertThat(groups).hasSize(1)
        assertThat(groups.first().libraries.map { it.name }).containsExactly("A", "B").inOrder()
    }
}
