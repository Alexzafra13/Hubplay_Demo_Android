package com.alex.hubplay.data

import com.alex.hubplay.data.api.dto.PeerContinueDto
import com.alex.hubplay.data.api.dto.PeerDto
import com.alex.hubplay.data.api.dto.PeerItemDto
import com.alex.hubplay.data.api.dto.PeerProgressRequest
import com.alex.hubplay.data.api.dto.PeerStreamSessionDto
import com.alex.hubplay.data.api.dto.PeerSubtitleDto
import com.alex.hubplay.ui.player.TrackOptions
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * "Compartido contigo": del wire de `/me/peers/…` a [Content.Shared].
 * Lo que importa en la TV: URLs de póster absolutas y con `?w=`, de quién
 * es cada item, qué se puede reproducir y desde dónde reanudar.
 */
class PeersRepositoryTest {

    private val server = "https://hub.example"

    @Test
    fun `recent maps peer attribution, absolute poster with width and series as not playable`() = runTest {
        val api = FakePeersApi(
            recent = listOf(
                PeerItemDto(
                    id = "m1", type = "movie", title = "Dune", year = 2021,
                    posterUrl = "/api/v1/me/peers/p1/items/m1/poster",
                    peerId = "p1", peerName = "Pedro", libraryId = "lib1",
                ),
                PeerItemDto(id = "s1", type = "series", title = "Dark", peerId = "p1", peerName = "Pedro"),
                // Sin peer_id no se puede enrutar: se descarta.
                PeerItemDto(id = "x", title = "Huérfano"),
            ),
        )
        val repo = PeersRepository(api) { server }

        val items = repo.recent()

        assertThat(items.map { it.id }).containsExactly("m1", "s1").inOrder()
        val movie = items[0]
        assertThat(movie.peerId).isEqualTo("p1")
        assertThat(movie.peerName).isEqualTo("Pedro")
        assertThat(movie.libraryId).isEqualTo("lib1")
        assertThat(movie.posterUrl)
            .isEqualTo("$server/api/v1/me/peers/p1/items/m1/poster?w=${PeersRepository.IMG_W_SHARED_CARD}")
        assertThat(movie.isPlayable).isTrue()
        assertThat(items[1].remoteKind).isEqualTo(MediaKind.Series)
        assertThat(items[1].isPlayable).isFalse()
        assertThat(items[1].posterUrl).isNull()
    }

    @Test
    fun `continue watching carries progress in seconds and fraction`() = runTest {
        val api = FakePeersApi(
            continueWatching = listOf(
                PeerContinueDto(
                    id = "m1", peerId = "p1", peerName = "Pedro", type = "movie", title = "Dune",
                    positionTicks = 600L * TICKS_PER_SECOND, durationTicks = 6000L * TICKS_PER_SECOND,
                    percentage = 10.0,
                ),
            ),
        )
        val repo = PeersRepository(api) { server }

        val item = repo.continueWatching().single()

        assertThat(item.resumePosSec).isEqualTo(600L)
        assertThat(item.durationSec).isEqualTo(6000L)
        assertThat(item.progressPct).isWithin(0.0001f).of(0.10f)
    }

    @Test
    fun `items seen in a listing are cached for the detail screen and the player`() = runTest {
        val library = SharedLibrary("p1", "Pedro", "lib1", "Pelis", "movies", canPlay = true)
        val api = FakePeersApi(page = listOf(PeerItemDto(id = "m1", type = "movie", title = "Dune")))
        val repo = PeersRepository(api) { server }

        repo.libraryItems(library)

        val cached = repo.cached("p1", "m1")
        assertThat(cached?.title).isEqualTo("Dune")
        assertThat(cached?.peerName).isEqualTo("Pedro")
        assertThat(repo.cached("p2", "m1")).isNull()
    }

    @Test
    fun `find falls back to the library first page when the item is not cached`() = runTest {
        val api = FakePeersApi(
            peers = listOf(PeerDto(id = "p1", name = "Pedro", status = "paired")),
            page  = listOf(PeerItemDto(id = "m1", type = "movie", title = "Dune")),
        )
        val repo = PeersRepository(api) { server }

        val found = repo.find("p1", "lib1", "m1")

        assertThat(found?.title).isEqualTo("Dune")
        assertThat(found?.peerName).isEqualTo("Pedro")
        assertThat(repo.find("p1", "lib1", "missing")).isNull()
        // Sin biblioteca no hay dónde buscar.
        assertThat(repo.find("p1", null, "other")).isNull()
    }

    @Test
    fun `resume position follows the web rules`() {
        // Nada guardado, terminado o pasado el 90 %: desde el principio.
        assertThat(SharedProgress().resumeSec).isEqualTo(0L)
        assertThat(SharedProgress(positionSec = 300, durationSec = 1000, completed = true).resumeSec).isEqualTo(0L)
        assertThat(SharedProgress(positionSec = 950, durationSec = 1000).resumeSec).isEqualTo(0L)
        // A medias: se reanuda; sin duración conocida también.
        assertThat(SharedProgress(positionSec = 300, durationSec = 1000).resumeSec).isEqualTo(300L)
        assertThat(SharedProgress(positionSec = 300, durationSec = 0).resumeSec).isEqualTo(300L)
    }

    @Test
    fun `start stream returns the master on our server and null without url`() = runTest {
        val repo = PeersRepository(
            FakePeersApi(stream = PeerStreamSessionDto("direct_stream", "/api/v1/me/peers/p1/stream/session/s1/master.m3u8", "s1")),
        ) { server }
        val stream = repo.startStream("p1", "m1", "h264/mp4")
        assertThat(stream?.masterPath).isEqualTo("/api/v1/me/peers/p1/stream/session/s1/master.m3u8")
        assertThat(stream?.method).isEqualTo("direct_stream")

        val empty = PeersRepository(FakePeersApi(stream = PeerStreamSessionDto("transcode", null, null))) { server }
        assertThat(empty.startStream("p1", "m1", "")).isNull()
    }

    @Test
    fun `session subtitles map to media streams and sideload from the session path`() = runTest {
        val api = FakePeersApi().apply {
            subtitles = listOf(
                PeerSubtitleDto(index = 3, codec = "subrip", language = "spa", default = true),
                PeerSubtitleDto(index = 4, codec = "hdmv_pgs_subtitle", language = "eng"),
            )
        }
        val repo = PeersRepository(api) { server }
        val stream = SharedStream("/api/v1/me/peers/p1/stream/session/s1/master.m3u8", "direct_stream", "s1")

        val tracks = TrackOptions.subtitles(repo.subtitles("p1", stream))
        val sideloaded = TrackOptions.sideloadedFrom(stream.subtitlesPath, tracks)

        assertThat(tracks.map { it.streamIndex }).containsExactly(3, 4).inOrder()
        assertThat(tracks[1].burnIn).isTrue()
        // Los de imagen no se cargan aparte: la federación no los quema.
        assertThat(sideloaded.map { it.url })
            .containsExactly("/api/v1/me/peers/p1/stream/session/s1/subtitles/3")
        // Sin id de sesión no hay ruta de subtítulos.
        assertThat(repo.subtitles("p1", stream.copy(sessionId = null))).isEmpty()
    }

    @Test
    fun `progress sink writes position and duration in ticks and marks completion`() = runTest {
        val api = FakePeersApi()
        val sink = PeersRepository(api) { server }.progressSink("p1", "m1")

        sink.write(positionSec = 120, durationSec = 3600, completed = false)
        sink.markPlayed(durationSec = 3600)

        assertThat(api.saved).containsExactly(
            PeerProgressRequest(120L * TICKS_PER_SECOND, 3600L * TICKS_PER_SECOND, false),
            PeerProgressRequest(3600L * TICKS_PER_SECOND, 3600L * TICKS_PER_SECOND, true),
        ).inOrder()
    }
}
