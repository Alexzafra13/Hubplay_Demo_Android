package com.alex.hubplay.data

import androidx.compose.runtime.Immutable
import com.alex.hubplay.data.api.dto.FederationHitDto
import com.alex.hubplay.data.api.dto.PeerContinueDto
import com.alex.hubplay.data.api.dto.RemoteItemDto
import com.alex.hubplay.data.api.dto.UnifiedLibraryDto

// ─── Federation domain types ──────────────────────────────────────────────────
//
// Peer-specific shapes kept separate from the local sealed [Content] hierarchy.
// The UI layer (Phase 2) maps these into MediaCard-friendly rows with a peer
// attribution badge; keeping the domain peer-aware here means routing info
// (peerId / libraryId) survives all the way to "open detail" / "play".

/** A paired peer server visible to the current user. */
@Immutable
data class ConnectedPeer(
    val id:          String,
    val name:        String,
    val fingerprint: String,
)

/** One shared library, flattened with its owning peer (the unified grid row). */
@Immutable
data class PeerLibrary(
    val peerId:      String,
    val peerName:    String,
    val libraryId:   String,
    val name:        String,
    val contentType: String,
    val canPlay:     Boolean,
    val canLivetv:   Boolean,
)

/**
 * A peer catalogue item / search hit / recent entry — one shape for all three
 * since they render identically (card with a "de {peer}" badge). [peerId] +
 * [libraryId] are what a click needs to open the federated detail / play path.
 */
@Immutable
data class PeerItem(
    val id:        String,
    val peerId:    String,
    val peerName:  String,
    val libraryId: String,
    val type:      String,
    val title:     String,
    val year:      Int?,
    val overview:  String?,
    val posterUrl: String?,
    // Resume metadata — only populated for continue-watching rows; 0 otherwise.
    val progressPct:  Float = 0f,
    val resumePosSec: Long = 0L,
)

/** Outcome of opening a playback session against a peer item. */
@Immutable
data class PeerStreamSession(
    val strategy:          String,
    val masterPlaylistUrl: String,
    val peerSessionId:     String,
)

/**
 * Pure DTO → domain mapping. No Android, no repository state — takes the paired
 * `server` URL so poster proxy paths absolutize, mirroring the testable-pure
 * style of [com.alex.hubplay.ui.player.NextEpisodeResolver].
 */
object FederationMapper {

    private const val TICKS_PER_SECOND = 10_000_000L
    private const val IMG_W_CARD = 400
    private const val PERCENT_DIVISOR = 100f

    fun library(dto: UnifiedLibraryDto): PeerLibrary = PeerLibrary(
        peerId      = dto.peerId,
        peerName    = dto.peerName.orEmpty(),
        libraryId   = dto.libraryId,
        name        = dto.libraryName.orEmpty(),
        contentType = dto.contentType.orEmpty(),
        canPlay     = dto.canPlay,
        canLivetv   = dto.canLivetv,
    )

    fun item(dto: RemoteItemDto, peerId: String, peerName: String, libraryId: String, server: String): PeerItem =
        PeerItem(
            id        = dto.id,
            peerId    = peerId,
            peerName  = peerName,
            libraryId = libraryId,
            type      = dto.type.orEmpty(),
            title     = dto.title.orEmpty(),
            year      = dto.year,
            overview  = dto.overview,
            posterUrl = absolutize(dto.posterUrl, server),
        )

    fun hit(dto: FederationHitDto, server: String): PeerItem = PeerItem(
        id        = dto.id,
        peerId    = dto.peerId,
        peerName  = dto.peerName.orEmpty(),
        libraryId = dto.libraryId.orEmpty(),
        type      = dto.type.orEmpty(),
        title     = dto.title.orEmpty(),
        year      = dto.year,
        overview  = dto.overview,
        posterUrl = absolutize(dto.posterUrl, server),
    )

    fun continueItem(dto: PeerContinueDto, server: String): PeerItem {
        val resumeSec = dto.positionTicks?.let { it / TICKS_PER_SECOND } ?: 0L
        val totalSec  = dto.durationTicks?.let { it / TICKS_PER_SECOND } ?: 0L
        val pct = dto.percentage?.let { it / PERCENT_DIVISOR }
            ?: if (totalSec > 0) (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f
        return PeerItem(
            id           = dto.id,
            peerId       = dto.peerId,
            peerName     = dto.peerName.orEmpty(),
            libraryId    = dto.libraryId.orEmpty(),
            type         = dto.type.orEmpty(),
            title        = dto.title.orEmpty(),
            year         = dto.year,
            overview     = null,
            posterUrl    = absolutize(dto.posterUrl, server),
            progressPct  = pct.coerceIn(0f, 1f),
            resumePosSec = resumeSec,
        )
    }

    /**
     * Peer poster URLs are same-origin proxy paths served by OUR backend, so
     * the `?w=` thumbnail trick applies (saves bandwidth/decode on TV boxes).
     * Remote absolute URLs (shouldn't happen for federation, but be safe) pass
     * through untouched.
     */
    fun absolutize(path: String?, server: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        val sep = if (cleanPath.contains('?')) '&' else '?'
        return "$server$cleanPath${sep}w=$IMG_W_CARD"
    }
}
