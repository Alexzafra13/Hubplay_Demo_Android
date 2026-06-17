package com.alex.hubplay.data

import com.alex.hubplay.data.api.dto.FederationHitDto
import com.alex.hubplay.data.api.dto.PeerContinueDto
import com.alex.hubplay.data.api.dto.RemoteItemDto
import com.alex.hubplay.data.api.dto.UnifiedLibraryDto
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure mapping tests for [FederationMapper] — the JVM-verifiable core of the
 * federation feature (poster absolutization, ticks→seconds, peer attribution).
 */
class FederationMapperTest {

    private val server = "https://hub.example"
    private val ticksPerSecond = 10_000_000L

    @Test
    fun `library maps fields and peer attribution`() {
        val lib = FederationMapper.library(
            UnifiedLibraryDto(
                peerId = "p1", peerName = "Pedro", libraryId = "L1",
                libraryName = "Pelis de Pedro", contentType = "movies",
                canPlay = true, canDownload = false, canLivetv = false,
            ),
        )
        assertThat(lib.peerName).isEqualTo("Pedro")
        assertThat(lib.name).isEqualTo("Pelis de Pedro")
        assertThat(lib.contentType).isEqualTo("movies")
        assertThat(lib.canPlay).isTrue()
    }

    @Test
    fun `item absolutizes a relative proxy poster with the thumbnail width`() {
        val item = FederationMapper.item(
            RemoteItemDto(id = "i1", type = "movie", title = "Dune", posterUrl = "/api/v1/me/peers/p1/items/i1/poster"),
            peerId = "p1", peerName = "Pedro", libraryId = "L1", server = server,
        )
        assertThat(item.posterUrl).isEqualTo("$server/api/v1/me/peers/p1/items/i1/poster?w=400")
        assertThat(item.peerId).isEqualTo("p1")
        assertThat(item.peerName).isEqualTo("Pedro")
        assertThat(item.libraryId).isEqualTo("L1")
    }

    @Test
    fun `null poster stays null and remote poster is untouched`() {
        val noPoster = FederationMapper.item(
            RemoteItemDto(id = "i1", posterUrl = null), "p", "P", "L", server,
        )
        assertThat(noPoster.posterUrl).isNull()

        val remote = FederationMapper.item(
            RemoteItemDto(id = "i2", posterUrl = "https://cdn.example/x.jpg"), "p", "P", "L", server,
        )
        assertThat(remote.posterUrl).isEqualTo("https://cdn.example/x.jpg")
    }

    @Test
    fun `continue derives resume seconds and progress from ticks`() {
        val dto = PeerContinueDto(
            id = "i1", peerId = "p1", peerName = "Pedro",
            positionTicks = 600L * ticksPerSecond,
            durationTicks = 1200L * ticksPerSecond,
        )
        val item = FederationMapper.continueItem(dto, server)
        assertThat(item.resumePosSec).isEqualTo(600L)
        assertThat(item.progressPct).isWithin(0.01f).of(0.5f)
    }

    @Test
    fun `continue prefers the explicit percentage when present`() {
        val dto = PeerContinueDto(
            id = "i1", peerId = "p1", percentage = 25f,
            positionTicks = 100L * ticksPerSecond,
            durationTicks = 1000L * ticksPerSecond,
        )
        assertThat(FederationMapper.continueItem(dto, server).progressPct).isWithin(0.01f).of(0.25f)
    }

    @Test
    fun `hit carries peer routing fields`() {
        val hit = FederationMapper.hit(
            FederationHitDto(peerId = "p2", peerName = "Ana", libraryId = "L9", id = "i7", title = "Heat"),
            server,
        )
        assertThat(hit.peerId).isEqualTo("p2")
        assertThat(hit.peerName).isEqualTo("Ana")
        assertThat(hit.libraryId).isEqualTo("L9")
        assertThat(hit.title).isEqualTo("Heat")
    }
}
