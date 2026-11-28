package com.alex.hubplay.data

import com.alex.hubplay.data.api.PeersApi
import com.alex.hubplay.data.api.dto.PeerContinueDto
import com.alex.hubplay.data.api.dto.PeerContinueResponse
import com.alex.hubplay.data.api.dto.PeerDto
import com.alex.hubplay.data.api.dto.PeerHits
import com.alex.hubplay.data.api.dto.PeerHitsResponse
import com.alex.hubplay.data.api.dto.PeerItemDto
import com.alex.hubplay.data.api.dto.PeerItemsPage
import com.alex.hubplay.data.api.dto.PeerItemsResponse
import com.alex.hubplay.data.api.dto.PeerLibrariesResponse
import com.alex.hubplay.data.api.dto.PeerLibraryDto
import com.alex.hubplay.data.api.dto.PeerProgressDto
import com.alex.hubplay.data.api.dto.PeerProgressRequest
import com.alex.hubplay.data.api.dto.PeerProgressResponse
import com.alex.hubplay.data.api.dto.PeerStreamSessionDto
import com.alex.hubplay.data.api.dto.PeerStreamSessionResponse
import com.alex.hubplay.data.api.dto.PeerSubtitleDto
import com.alex.hubplay.data.api.dto.PeerSubtitlesResponse
import com.alex.hubplay.data.api.dto.PeersResponse

/** `PeersApi` en memoria para los tests de federación. */
internal class FakePeersApi(
    private val peers:            List<PeerDto> = emptyList(),
    private val recent:           List<PeerItemDto> = emptyList(),
    private val continueWatching: List<PeerContinueDto> = emptyList(),
    private val page:             List<PeerItemDto> = emptyList(),
    private val stream:           PeerStreamSessionDto? = null,
) : PeersApi {
    val saved = mutableListOf<PeerProgressRequest>()
    var libraries: List<PeerLibraryDto> = emptyList()

    /** Items por biblioteca (`libraryId` → items); si falta se usa `page`. */
    var pages: Map<String, List<PeerItemDto>> = emptyMap()
    var subtitles: List<PeerSubtitleDto> = emptyList()

    /** Hijos por item (`itemId` → temporadas o episodios); null = el amigo falla. */
    var children: Map<String, List<PeerItemDto>>? = emptyMap()

    /** Progreso guardado por item. */
    var progress: Map<String, PeerProgressDto> = emptyMap()

    override suspend fun listPeers() = PeersResponse(peers)
    override suspend fun listLibraries() = PeerLibrariesResponse(libraries)
    override suspend fun listItems(peerId: String, libraryId: String, offset: Int, limit: Int): PeerItemsResponse {
        val items = pages[libraryId] ?: page
        return PeerItemsResponse(PeerItemsPage(items = items, total = items.size))
    }
    override suspend fun recent(perPeer: Int) = PeerHitsResponse(PeerHits(recent))
    override suspend fun search(query: String, perPeer: Int) = PeerHitsResponse(PeerHits(emptyList()))
    override suspend fun continueWatching(limit: Int) = PeerContinueResponse(continueWatching)
    override suspend fun startStream(peerId: String, itemId: String, capabilities: String) =
        PeerStreamSessionResponse(stream)
    override suspend fun subtitles(peerId: String, sessionId: String) = PeerSubtitlesResponse(subtitles)
    override suspend fun children(peerId: String, itemId: String): PeerItemsResponse {
        val all = children ?: error("502 PEER_UNREACHABLE")
        val items = all[itemId].orEmpty()
        return PeerItemsResponse(PeerItemsPage(items = items, total = items.size))
    }
    override suspend fun getProgress(peerId: String, itemId: String) =
        PeerProgressResponse(progress[itemId] ?: PeerProgressDto())
    override suspend fun saveProgress(peerId: String, itemId: String, body: PeerProgressRequest) {
        saved += body
    }
}
