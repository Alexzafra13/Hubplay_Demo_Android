package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.ContinueWatchingEntryDto
import com.alex.hubplay.data.api.dto.ItemSummaryDto
import com.alex.hubplay.data.api.dto.LiveNowChannelDto
import com.alex.hubplay.data.api.dto.TrendingItemDto

/**
 * Owns reads against the home / catalogue surface and maps them into
 * UI-shaped [MediaItem] / [HomeData] objects so screens never see wire
 * DTOs.
 *
 * Each rail is a separate suspend method. The ViewModel calls them in
 * parallel via async{} so a slow rail doesn't block the rest.
 */
class HomeRepository(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) {

    suspend fun fetchContinueWatching(): List<MediaItem> {
        val server = serverUrl()
        return api.getContinueWatching().data.orEmpty().map { it.toMedia(server) }
    }

    suspend fun fetchTrending(limit: Int = 12): List<MediaItem> {
        return api.getTrending(limit).data?.items.orEmpty().map { it.toMedia() }
    }

    suspend fun fetchLatest(limit: Int = 20): List<MediaItem> {
        val server = serverUrl()
        return api.getLatest(limit).data?.items.orEmpty().map { it.toMedia(server) }
    }

    suspend fun fetchLiveNow(limit: Int = 10): List<MediaItem> {
        val server = serverUrl()
        return api.getLiveNow(limit).data.orEmpty().map { it.toMedia(server) }
    }

    private suspend fun serverUrl(): String =
        tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()

    // ─── DTO → MediaItem mappers ─────────────────────────────────────────────

    private fun ContinueWatchingEntryDto.toMedia(server: String): MediaItem {
        val resumeSec = (positionSeconds ?: userData?.positionSeconds ?: 0f).toLong()
        val totalSec  = ((runtimeMinutes ?: 0) * 60).toLong()
        val progress  = if (totalSec > 0) (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        return MediaItem(
            id           = id,
            kind         = MediaKind.from(type),
            title        = title.orEmpty(),
            subtitle     = episodeSubtitle(),
            posterUrl    = posterImageId?.let { "$server/api/v1/images/file/$it" },
            backdropUrl  = backdropImageId?.let { "$server/api/v1/images/file/$it" },
            logoUrl      = null,
            overview     = null,
            genres       = emptyList(),
            rating       = null,
            year         = year,
            progressPct  = progress,
            resumePosSec = resumeSec,
        )
    }

    private fun ContinueWatchingEntryDto.episodeSubtitle(): String? {
        if (type != "episode") return null
        val s = seasonNumber; val e = episodeNumber
        return if (s != null && e != null) "S$s · E$e" else null
    }

    private fun ItemSummaryDto.toMedia(server: String): MediaItem = MediaItem(
        id           = id,
        kind         = MediaKind.from(type),
        title        = title.orEmpty(),
        subtitle     = year?.toString(),
        posterUrl    = posterImageId?.let { "$server/api/v1/images/file/$it" },
        backdropUrl  = backdropImageId?.let { "$server/api/v1/images/file/$it" },
        logoUrl      = null,
        overview     = null,
        genres       = emptyList(),
        rating       = rating,
        year         = year,
    )

    /** Trending items already carry absolute URLs + overview + genres. */
    private fun TrendingItemDto.toMedia(): MediaItem = MediaItem(
        id           = id,
        kind         = MediaKind.from(type),
        title        = title.orEmpty(),
        subtitle     = year?.toString(),
        posterUrl    = posterUrl,
        backdropUrl  = backdropUrl,
        logoUrl      = logoUrl,
        overview     = overview,
        genres       = genres,
        rating       = communityRating,
        year         = year,
    )

    private fun LiveNowChannelDto.toMedia(server: String): MediaItem = MediaItem(
        id           = id,
        kind         = MediaKind.LiveChannel,
        title        = name.orEmpty(),
        subtitle     = currentProgramTitle,
        posterUrl    = logoUrl,
        backdropUrl  = logoUrl,   // channels don't have backdrops; reuse the logo
        logoUrl      = logoUrl,
        overview     = currentProgramTitle,
        genres       = emptyList(),
        rating       = null,
        year         = null,
    )
}

// ─── Domain types ────────────────────────────────────────────────────────────

enum class MediaKind {
    Movie, Series, Season, Episode, LiveChannel, Unknown;
    companion object {
        fun from(s: String?): MediaKind = when (s) {
            "movie"   -> Movie
            "series"  -> Series
            "season"  -> Season
            "episode" -> Episode
            else      -> Unknown
        }
    }
}

/**
 * Single domain type every Card / Hero / Detail screen consumes. Lossy
 * by design — we only carry what the UI actually renders today; richer
 * fields can be added per-screen as needed.
 */
data class MediaItem(
    val id:           String,
    val kind:         MediaKind,
    val title:        String,
    val subtitle:     String?,
    val posterUrl:    String?,
    val backdropUrl:  String?,
    val logoUrl:      String?,
    val overview:     String?,
    val genres:       List<String>,
    val rating:       Float?,
    val year:         Int?,
    val progressPct:  Float = 0f,    // 0f..1f, only > 0 for Continue Watching
    val resumePosSec: Long  = 0L,
)

/** Snapshot of all home rails for the ViewModel to expose to the UI. */
data class HomeData(
    val hero:             List<MediaItem> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    val latest:           List<MediaItem> = emptyList(),
    val trending:         List<MediaItem> = emptyList(),
    val liveNow:          List<MediaItem> = emptyList(),
)
