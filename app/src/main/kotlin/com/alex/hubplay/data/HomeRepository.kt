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
        return api.getLiveNow(limit).data?.items.orEmpty().map { it.toMedia(server) }
    }

    suspend fun fetchItemDetail(itemId: String): MediaItem {
        val server = serverUrl()
        val data = api.getItem(itemId).data
            ?: error("items/$itemId returned no data envelope")
        val totalSec  = data.durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val resumeSec = data.userData?.progress?.positionTicks
            ?.let { ticksToSeconds(it) } ?: 0L
        val progress  = if (totalSec > 0 && resumeSec > 0)
            (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        return MediaItem(
            id           = data.id,
            kind         = MediaKind.from(data.type),
            title        = data.title.orEmpty(),
            subtitle     = data.tagline,
            posterUrl    = absolutize(data.posterUrl, server),
            backdropUrl  = absolutize(data.backdropUrl, server),
            logoUrl      = absolutize(data.logoUrl ?: data.studioLogoUrl, server),
            overview     = data.overview,
            genres       = data.genres,
            rating       = data.communityRating,
            year         = data.year,
            progressPct  = progress,
            resumePosSec = resumeSec,
        )
    }

    private suspend fun serverUrl(): String =
        tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()

    // ─── DTO → MediaItem mappers ─────────────────────────────────────────────

    private fun ContinueWatchingEntryDto.toMedia(server: String): MediaItem {
        val resumeSec = positionTicks?.let { ticksToSeconds(it) } ?: 0L
        val totalSec  = durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val progress  = userData?.progress?.percentage?.let { it / 100f }
            ?: if (totalSec > 0) (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        // Episodes are best displayed as "Series · S2 E4" — promote the
        // series title when the entry carries it; movies just use their
        // own title.
        val displayTitle = if (type == "episode" && !seriesTitle.isNullOrBlank())
            seriesTitle else title.orEmpty()
        val episodeSubtitle = if (type == "episode") {
            val s = seasonIndex; val e = episodeIndex
            if (s != null && e != null) "S$s · E$e · ${title.orEmpty()}".trimEnd(' ', '·')
            else                        title
        } else null

        return MediaItem(
            id           = id,
            kind         = MediaKind.from(type),
            title        = displayTitle,
            subtitle     = episodeSubtitle,
            // 16:9 thumb when present (movies); fall back to backdrop
            // (per-episode still on episodes); poster as a last resort.
            posterUrl    = absolutize(posterUrl, server),
            backdropUrl  = absolutize(thumbUrl ?: backdropUrl ?: posterUrl, server),
            logoUrl      = absolutize(logoUrl, server),
            overview     = null,
            genres       = emptyList(),
            rating       = null,
            year         = seriesYear,
            progressPct  = progress,
            resumePosSec = resumeSec,
        )
    }

    private fun ItemSummaryDto.toMedia(server: String): MediaItem = MediaItem(
        id           = id,
        kind         = MediaKind.from(type),
        title        = title.orEmpty(),
        subtitle     = year?.toString(),
        posterUrl    = absolutize(posterUrl, server),
        backdropUrl  = absolutize(backdropUrl ?: posterUrl, server),
        logoUrl      = absolutize(logoUrl, server),
        overview     = overview,
        genres       = genres,
        rating       = communityRating,
        year         = year,
    )

    /**
     * Trending items already carry absolute URLs from the server (they
     * pass through a separate enrichment path). The other rails get
     * relative paths and we glue the server URL on this side.
     */
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

    private fun LiveNowChannelDto.toMedia(server: String): MediaItem {
        val absoluteLogo = absolutize(channelLogo, server)
        return MediaItem(
            id           = channelId,
            kind         = MediaKind.LiveChannel,
            title        = channelName.orEmpty(),
            subtitle     = programTitle,
            posterUrl    = absoluteLogo,
            backdropUrl  = absoluteLogo,
            logoUrl      = absoluteLogo,
            overview     = programTitle,
            genres       = emptyList(),
            rating       = null,
            year         = null,
        )
    }

    /**
     * Glue a server-relative path ("/api/v1/images/file/abc") onto the
     * paired serverUrl. Returns absolute URLs unchanged. Returns null
     * for null input so callers can pass through directly.
     */
    private fun absolutize(path: String?, server: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$server$cleanPath"
    }

    /** .NET ticks → seconds. 10_000_000 ticks per second (100ns each). */
    private fun ticksToSeconds(ticks: Long): Long = ticks / 10_000_000L
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
    val progressPct:  Float = 0f,
    val resumePosSec: Long  = 0L,
)

data class HomeData(
    val hero:             List<MediaItem> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    val latest:           List<MediaItem> = emptyList(),
    val trending:         List<MediaItem> = emptyList(),
    val liveNow:          List<MediaItem> = emptyList(),
)
