package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.CollectionListEntryDto
import com.alex.hubplay.data.api.dto.ContinueWatchingEntryDto
import com.alex.hubplay.data.api.dto.ItemSummaryDto
import com.alex.hubplay.data.api.dto.LiveNowChannelDto
import com.alex.hubplay.data.api.dto.NextUpItemDto
import com.alex.hubplay.data.api.dto.TrendingItemDto

/**
 * Contract for the home / catalogue data surface. Screens and ViewModels
 * depend on this interface; the concrete [HomeRepositoryImpl] wires up
 * Retrofit + TokenStore. Tests swap in a [FakeHomeRepository].
 */
interface HomeRepository {
    suspend fun fetchContinueWatching(): List<MediaItem>
    suspend fun fetchTrending(limit: Int = 12): List<MediaItem>
    suspend fun fetchLatest(libraryId: String? = null, type: String? = null, limit: Int = 20): List<MediaItem>
    suspend fun fetchLibraries(): Map<String, String>
    suspend fun fetchLiveNow(limit: Int = 10): List<MediaItem>
    suspend fun fetchHomeLayout(): List<HomeRailConfig>
    suspend fun fetchChildren(parentId: String): List<MediaItem>
    suspend fun fetchNextUp(): List<MediaItem>
    suspend fun fetchCatalogue(type: String, limit: Int = 60, offset: Int = 0, sortBy: String = "added_at", sortOrder: String = "desc"): List<MediaItem>
    suspend fun fetchCollections(): List<CollectionSummary>
    suspend fun fetchCollectionDetail(id: String): CollectionDetail
    suspend fun fetchItemDetail(itemId: String): MediaItem
    suspend fun toggleItemFavorite(itemId: String): Boolean
    suspend fun searchItems(query: String, limit: Int = 60): List<MediaItem>
}

/**
 * Production implementation. Maps wire DTOs into UI-shaped domain types
 * so screens never see the API surface directly.
 */
class HomeRepositoryImpl(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) : HomeRepository {

    override suspend fun fetchContinueWatching(): List<MediaItem> {
        val server = serverUrl()
        return api.getContinueWatching().data.orEmpty().map { it.toMedia(server) }
    }

    override suspend fun fetchTrending(limit: Int): List<MediaItem> {
        val server = serverUrl()
        return api.getTrending(limit).data?.items.orEmpty().map { it.toMedia(server) }
    }

    /**
     * Latest items in a specific library, filtered by content type.
     *
     * Why both: passing `library_id` alone returns whatever was added
     * most recently — including individual episodes when a series
     * library got new content. The home rail wants the *series* card
     * with a "+N nuevos episodios" hint instead, which the server
     * synthesises when `type=series` + a single library_id are both
     * set. Same idea for movie libraries: `type=movie` strips out
     * orphan episodes that might exist in a mixed library.
     */
    override suspend fun fetchLatest(
        libraryId: String?,
        type:      String?,
        limit:     Int,
    ): List<MediaItem> {
        val server = serverUrl()
        return api.getLatest(limit, libraryId, type)
            .data?.items.orEmpty()
            .map { it.toMedia(server) }
    }

    /**
     * Library id → content_type ("movies" / "shows" / "livetv" / "mixed").
     * The home layout rails reference libraries by id; we need the type
     * to pick the right `type=` filter for `/items/latest`.
     */
    override suspend fun fetchLibraries(): Map<String, String> {
        return api.getLibraries().data.orEmpty().associate { lib ->
            lib.id to (lib.contentType ?: "mixed")
        }
    }

    override suspend fun fetchLiveNow(limit: Int): List<MediaItem> {
        val server = serverUrl()
        return api.getLiveNow(limit).data?.items.orEmpty().map { it.toMedia(server) }
    }

    /**
     * Per-user home layout. Returns the rail order + visibility the
     * user configured on the web ("Personalizar inicio"). When no
     * layout is stored the server synthesises a sensible default;
     * either way we get a list of [HomeRailConfig] in display order.
     */
    override suspend fun fetchHomeLayout(): List<HomeRailConfig> {
        val data = api.getHomeLayout().data ?: return defaultHomeLayout()
        return data.sections
            .filter { it.visible }
            .mapNotNull { section ->
                HomeRailType.from(section.type)?.let { type ->
                    HomeRailConfig(
                        id        = section.id,
                        type      = type,
                        libraryId = section.libraryId,
                        title     = railTitle(type, section.libraryName),
                    )
                }
            }
    }

    private fun railTitle(type: HomeRailType, libraryName: String?): String = when (type) {
        HomeRailType.ContinueWatching  -> "Continuar viendo"
        HomeRailType.NextUp             -> "A continuación"
        HomeRailType.Trending           -> "Tendencias"
        HomeRailType.LiveNow            -> "En directo ahora"
        HomeRailType.LatestInLibrary    -> libraryName?.let { "Lo último en $it" } ?: "Lo último"
    }

    /**
     * Server-side fallback default — used when /me/home/layout returns
     * a null or empty payload (cold-start user, network blip).
     */
    private fun defaultHomeLayout(): List<HomeRailConfig> = listOf(
        HomeRailConfig("default-cw",       HomeRailType.ContinueWatching,   null, "Continuar viendo"),
        HomeRailConfig("default-latest",   HomeRailType.LatestInLibrary,    null, "Lo último"),
        HomeRailConfig("default-trending", HomeRailType.Trending,           null, "Tendencias"),
        HomeRailConfig("default-live",     HomeRailType.LiveNow,            null, "En directo ahora"),
    )

    /**
     * /items/{id}/children — seasons of a series, or episodes of a season.
     * The list comes back in scanner order; SeriesScreen filters by type
     * and sorts by season_number / episode_number itself.
     */
    override suspend fun fetchChildren(parentId: String): List<MediaItem> {
        val server = serverUrl()
        return api.getChildren(parentId).data.orEmpty().map { it.toMedia(server) }
    }

    /** /me/next-up — queued episodes across every series. */
    override suspend fun fetchNextUp(): List<MediaItem> {
        return api.getNextUp().data.orEmpty().map { it.toMedia() }
    }

    /**
     * /items?type=movie|series — full catalogue listing for the
     * dedicated Movies / Series screens. Defaults: 60 items per page
     * ordered by added_at desc (newest first), which matches the
     * default the web client uses on /movies and /series.
     */
    override suspend fun fetchCatalogue(
        type:      String,
        limit:     Int,
        offset:    Int,
        sortBy:    String,
        sortOrder: String,
    ): List<MediaItem> {
        val server = serverUrl()
        return api.listItems(
            type      = type,
            limit     = limit,
            offset    = offset,
            sortBy    = sortBy,
            sortOrder = sortOrder,
        ).data?.items.orEmpty().map { it.toMedia(server) }
    }

    /**
     * GET /collections — the index of TMDb sagas matched by the
     * scanner. Repository absolutizes poster / backdrop URLs against
     * the paired server so Coil hits them directly without re-applying
     * BaseUrlInterceptor on every Image() composable.
     *
     * Empty list = backend resolved but has zero collections (legit on
     * a fresh deployment); failure throws. Caller distinguishes both
     * via runCatching.
     */
    override suspend fun fetchCollections(): List<CollectionSummary> {
        val server = serverUrl()
        return api.listCollections().data?.collections.orEmpty().map { it.toDomain(server) }
    }

    /**
     * GET /collections/{id} — saga hero + member movies in release
     * order. Wraps both in [CollectionDetail] for the screen.
     */
    override suspend fun fetchCollectionDetail(id: String): CollectionDetail {
        val server = serverUrl()
        val data = api.getCollection(id).data
            ?: error("collections/$id returned no data envelope")
        return CollectionDetail(
            id          = data.id,
            tmdbId      = data.tmdbId,
            name        = data.name,
            overview    = data.overview,
            posterUrl   = absolutize(data.posterUrl, server),
            backdropUrl = absolutize(data.backdropUrl, server),
            items       = data.items.map { it.toMedia(server) },
        )
    }

    private fun CollectionListEntryDto.toDomain(server: String) = CollectionSummary(
        id          = id,
        name        = name,
        itemCount   = itemCount,
        posterUrl   = absolutize(posterUrl, server),
        backdropUrl = absolutize(backdropUrl, server),
    )

    override suspend fun fetchItemDetail(itemId: String): MediaItem {
        val server = serverUrl()
        val data = api.getItem(itemId).data
            ?: error("items/$itemId returned no data envelope")
        val totalSec  = data.durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val resumeSec = data.userData?.progress?.positionTicks
            ?.let { ticksToSeconds(it) } ?: 0L
        val progress  = if (totalSec > 0 && resumeSec > 0)
            (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        android.util.Log.d(
            "HomeRepository",
            "fetchItemDetail($itemId) type=${data.type} trailer=${data.trailer} title=${data.title}",
        )

        return MediaItem(
            id                = data.id,
            kind              = MediaKind.from(data.type),
            title             = data.title.orEmpty(),
            subtitle          = data.tagline,
            posterUrl         = absolutize(data.posterUrl, server),
            backdropUrl       = absolutize(data.backdropUrl, server),
            logoUrl           = absolutize(data.logoUrl ?: data.studioLogoUrl, server),
            overview          = data.overview,
            genres            = data.genres,
            rating            = data.communityRating,
            year              = data.year,
            progressPct       = progress,
            resumePosSec      = resumeSec,
            trailerKey        = data.trailer?.key,
            trailerSite       = data.trailer?.site,
            isFavorite        = data.userData?.isFavorite == true,
            collectionId      = data.collection?.id,
            collectionName    = data.collection?.name,
        )
    }

    /**
     * Toggle the favourite flag on a movie / series / episode. Returns
     * the new state (true = now favourite). Doesn't try to be clever
     * about concurrent toggles — the server is the source of truth and
     * a stale optimistic update gets corrected on the next /me/events
     * tick.
     */
    override suspend fun toggleItemFavorite(itemId: String): Boolean {
        val resp = api.toggleItemFavorite(itemId)
        return resp.data?.isFavorite == true
    }

    /**
     * Full-text search across the catalogue. Returns the same MediaItem
     * shape Home rails use so the result grid can reuse MediaCard +
     * navigate to Detail/Series with the existing rules. Empty query
     * short-circuits without hitting the network.
     */
    override suspend fun searchItems(query: String, limit: Int): List<MediaItem> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val server = serverUrl()
        return api.searchItems(query = q, limit = limit).data.map { it.toMedia(server) }
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
            seriesId     = seriesId,
            parentId     = parentId,
            seasonNumber = seasonIndex,
            episodeNumber = episodeIndex,
            durationSec  = totalSec,
        )
    }

    /**
     * /me/next-up entries are sparse — no overview, no images — but they
     * carry the episode id + series_id we need to play and to match in
     * the resume resolver. SeriesScreen tops up the missing fields via
     * children when it needs to render rich episode cards.
     */
    private fun NextUpItemDto.toMedia(): MediaItem {
        val totalSec  = durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val episodeLabel = buildString {
            val s = seasonNumber; val e = episodeNumber
            if (s != null && e != null) append("S$s · E$e")
            if (!episodeTitle.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(episodeTitle)
            }
        }.ifBlank { null }

        return MediaItem(
            id           = id,
            kind         = MediaKind.Episode,
            title        = seriesTitle.orEmpty(),
            subtitle     = episodeLabel,
            posterUrl    = null,
            backdropUrl  = null,
            logoUrl      = null,
            overview     = null,
            genres       = emptyList(),
            rating       = null,
            year         = null,
            progressPct  = 0f,
            resumePosSec = 0L,
            seriesId     = seriesId,
            parentId     = null,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            durationSec  = totalSec,
        )
    }

    private fun ItemSummaryDto.toMedia(server: String): MediaItem {
        val kindFromType = MediaKind.from(type)
        // For episodes inside a season's children, the more useful subtitle
        // is "S2 · E4" rather than the year (which is the series year).
        val sub = when {
            kindFromType == MediaKind.Episode && episodeNumber != null ->
                buildString {
                    seasonNumber?.let { append("S$it · ") }
                    append("E$episodeNumber")
                }
            kindFromType == MediaKind.Season && seasonNumber != null ->
                "Temporada $seasonNumber"
            else -> year?.toString()
        }
        val totalSec = durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val resumeSec = userData?.progress?.positionTicks?.let { ticksToSeconds(it) } ?: 0L
        val progressPct = if (totalSec > 0 && resumeSec > 0)
            (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        return MediaItem(
            id            = id,
            kind          = kindFromType,
            title         = title.orEmpty(),
            subtitle      = sub,
            posterUrl     = absolutize(posterUrl, server),
            backdropUrl   = absolutize(backdropUrl ?: posterUrl, server),
            logoUrl       = absolutize(logoUrl, server),
            overview      = overview,
            genres        = genres,
            rating        = communityRating,
            year          = year,
            progressPct   = progressPct,
            resumePosSec  = resumeSec,
            parentId      = parentId,
            seasonNumber  = seasonNumber,
            episodeNumber = episodeNumber,
            durationSec   = totalSec,
            isFavorite    = userData?.isFavorite == true,
        )
    }

    /**
     * Trending items also come back with relative URLs — verified
     * against me_home.go::Trending(). The earlier assumption that
     * trending was "already absolute" was wrong; pass them through
     * absolutize() like every other rail.
     */
    private fun TrendingItemDto.toMedia(server: String): MediaItem = MediaItem(
        id           = id,
        kind         = MediaKind.from(type),
        title        = title.orEmpty(),
        subtitle     = year?.toString(),
        posterUrl    = absolutize(posterUrl, server),
        backdropUrl  = absolutize(backdropUrl, server),
        logoUrl      = absolutize(logoUrl, server),
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
            logoInitials = logoInitials,
            logoBg       = logoBg,
            logoFg       = logoFg,
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

/**
 * Marked `@Immutable` so the Compose compiler treats `MediaItem` as a
 * stable parameter and skips recomposition for unchanged instances.
 * Every field below is a read-only `val` of a stable type (String,
 * primitive, immutable List<String>), so the marker is honest — Compose
 * would have inferred stability for most fields but `List<String>` is
 * conservatively treated as unstable without the explicit annotation.
 */
@androidx.compose.runtime.Immutable
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
    /**
     * Series / season / episode hierarchy. Set on items that participate
     * in a series. The Series resume resolver matches by these.
     */
    val seriesId:     String? = null,
    val parentId:     String? = null,
    val seasonNumber: Int?    = null,
    val episodeNumber: Int?   = null,
    val durationSec:  Long    = 0L,
    /**
     * YouTube/Vimeo trailer pair when the server has picked one for
     * this item. Set on /items/{id} responses only (Home / Trending
     * rails do not include it to keep payloads small).
     */
    val trailerKey:   String? = null,
    val trailerSite:  String? = null,
    /**
     * Channel placeholder. Set on LiveNow rail items when the channel
     * has no logo (or as a fallback for when the logo fetch fails) so
     * the card can render an initials-on-coloured-circle avatar à la
     * Plex / Jellyfin instead of an empty grey rectangle.
     */
    val logoInitials: String? = null,
    val logoBg:       String? = null,
    val logoFg:       String? = null,
    /**
     * Whether the authenticated user has marked this item as a favourite.
     * Driven by user_data.is_favorite from the server; toggled via
     * POST /me/progress/{id}/favorite. Defaults to false so rails that
     * don't bother fetching user_data (Trending, LiveNow) keep working.
     */
    val isFavorite:   Boolean = false,
    /**
     * TMDb saga membership. Set only on /items/{id} responses for movies
     * that belong to a collection — drives the "Parte de: <name>" chip
     * on the Detail screen.
     */
    val collectionId:    String? = null,
    val collectionName:  String? = null,
)

/**
 * Row in the Collections tab grid. Trimmed to what the tile renders:
 * name + poster + member count badge. Detail is fetched on demand.
 */
@androidx.compose.runtime.Immutable
data class CollectionSummary(
    val id:          String,
    val name:        String,
    val itemCount:   Int,
    val posterUrl:   String?,
    val backdropUrl: String?,
)

/**
 * Full collection detail with member movies in release order. Members
 * reuse [MediaItem] so the same grid + card code as Movies/Series
 * works without translation.
 */
@androidx.compose.runtime.Immutable
data class CollectionDetail(
    val id:          String,
    val tmdbId:      Int?,
    val name:        String,
    val overview:    String?,
    val posterUrl:   String?,
    val backdropUrl: String?,
    val items:       List<MediaItem>,
)

@androidx.compose.runtime.Immutable
data class HomeData(
    val hero:             List<MediaItem> = emptyList(),
    val continueWatching: List<MediaItem> = emptyList(),
    val nextUp:           List<MediaItem> = emptyList(),
    val trending:         List<MediaItem> = emptyList(),
    val liveNow:          List<MediaItem> = emptyList(),
    val rails:            List<HomeRailConfig> = emptyList(),
    /**
     * Per-rail latest items, keyed by HomeRailConfig.id. Each
     * `latest_in_library` rail in the layout gets its own entry,
     * fetched with the library_id + type=movie|series filter so the
     * card shape matches what the user expects (no episode pollution).
     */
    val latestByRailId:   Map<String, List<MediaItem>> = emptyMap(),
)

/** A single rail's place in the home layout. */
@androidx.compose.runtime.Immutable
data class HomeRailConfig(
    val id:        String,
    val type:      HomeRailType,
    val libraryId: String?,
    val title:     String,
)

/**
 * The kinds of rails the home page knows how to render. Mirrors the
 * server's `HomeSection.type` enum but strips out the unknown values
 * (anything new the server adds shows up as `null` in HomeRailType.from
 * and the repo skips it — frontend-tolerant by design).
 */
enum class HomeRailType {
    ContinueWatching, NextUp, Trending, LiveNow, LatestInLibrary;

    companion object {
        fun from(s: String): HomeRailType? = when (s) {
            "continue_watching"  -> ContinueWatching
            "next_up"            -> NextUp
            "trending"           -> Trending
            "live_now"           -> LiveNow
            "latest_in_library"  -> LatestInLibrary
            else                 -> null
        }
    }
}
