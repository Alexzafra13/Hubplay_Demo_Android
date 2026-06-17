package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.PeerProgressRequest

/**
 * Reads/plays content shared by paired peer servers (federation). Pairing
 * itself is admin-only (done from the web) — the TV client only consumes
 * already-paired peers.
 *
 * Mirrors [HomeRepository]: thin wrapper over [HubplayApi] that peels the
 * `{ data: ... }` envelope and maps DTOs → domain via the pure
 * [FederationMapper]. All calls return safe defaults the ViewModels treat as
 * "no peers / best-effort" — a slow or offline peer never blocks the UI.
 */
interface FederationRepository {
    suspend fun listPeers(): List<ConnectedPeer>
    suspend fun listAllLibraries(): List<PeerLibrary>
    suspend fun listLibraryItems(peerId: String, peerName: String, libraryId: String, offset: Int = 0, limit: Int = 50): List<PeerItem>
    suspend fun searchPeers(query: String): List<PeerItem>
    suspend fun recent(limit: Int = 12): List<PeerItem>
    suspend fun continueWatching(): List<PeerItem>
    suspend fun openStreamSession(peerId: String, itemId: String): PeerStreamSession?
    suspend fun reportProgress(peerId: String, itemId: String, positionSec: Long, completed: Boolean)
}

class FederationRepositoryImpl(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) : FederationRepository {

    override suspend fun listPeers(): List<ConnectedPeer> =
        api.listPeers().data.orEmpty().map {
            ConnectedPeer(id = it.id, name = it.name.orEmpty(), fingerprint = it.fingerprint.orEmpty())
        }

    override suspend fun listAllLibraries(): List<PeerLibrary> =
        api.listAllPeerLibraries().data.orEmpty().map { FederationMapper.library(it) }

    override suspend fun listLibraryItems(
        peerId: String,
        peerName: String,
        libraryId: String,
        offset: Int,
        limit: Int,
    ): List<PeerItem> {
        val server = serverUrl()
        return api.browsePeerItems(peerId, libraryId, limit = limit, offset = offset)
            .data?.items.orEmpty()
            .map { FederationMapper.item(it, peerId, peerName, libraryId, server) }
    }

    override suspend fun searchPeers(query: String): List<PeerItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val server = serverUrl()
        return api.searchPeers(q).data?.hits.orEmpty().map { FederationMapper.hit(it, server) }
    }

    override suspend fun recent(limit: Int): List<PeerItem> {
        val server = serverUrl()
        return api.peerRecent(limit).data?.hits.orEmpty().map { FederationMapper.hit(it, server) }
    }

    override suspend fun continueWatching(): List<PeerItem> {
        val server = serverUrl()
        return api.peerContinueWatching().data.orEmpty().map { FederationMapper.continueItem(it, server) }
    }

    override suspend fun openStreamSession(peerId: String, itemId: String): PeerStreamSession? {
        val dto = api.openPeerStreamSession(peerId, itemId).data ?: return null
        val url = dto.masterPlaylistUrl ?: return null
        return PeerStreamSession(
            strategy          = dto.strategy.orEmpty(),
            masterPlaylistUrl = url,
            peerSessionId     = dto.peerSessionId.orEmpty(),
        )
    }

    override suspend fun reportProgress(peerId: String, itemId: String, positionSec: Long, completed: Boolean) {
        api.reportPeerProgress(
            peerId,
            itemId,
            PeerProgressRequest(positionTicks = positionSec * TICKS_PER_SECOND, completed = completed),
        )
    }

    private suspend fun serverUrl(): String =
        tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()

    private companion object {
        private const val TICKS_PER_SECOND = 10_000_000L
    }
}
