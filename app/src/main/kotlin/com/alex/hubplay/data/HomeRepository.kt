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
    suspend fun fetchContinueWatching(): List<Content.Resumable>
    suspend fun fetchTrending(limit: Int = 12): List<Content>
    suspend fun fetchLatest(libraryId: String? = null, type: String? = null, limit: Int = 20): List<Content>
    suspend fun fetchLibraries(): Map<String, String>
    suspend fun fetchLiveNow(limit: Int = 10): List<Content.LiveChannel>
    suspend fun fetchHomeLayout(): List<HomeRailConfig>
    suspend fun fetchChildren(parentId: String): List<Content>
    suspend fun fetchNextUp(): List<Content.Episode>
    suspend fun fetchCatalogue(type: String, limit: Int = 60, offset: Int = 0, sortBy: String = "added_at", sortOrder: String = "desc"): List<Content>
    suspend fun fetchCollections(): List<CollectionSummary>
    suspend fun fetchCollectionDetail(id: String): CollectionDetail
    suspend fun fetchItemDetail(itemId: String): Content
    suspend fun toggleItemFavorite(itemId: String): Boolean
    suspend fun searchItems(query: String, limit: Int = 60): List<Content>
}

/**
 * Production implementation. Maps wire DTOs into UI-shaped domain types
 * so screens never see the API surface directly.
 */
class HomeRepositoryImpl(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) : HomeRepository {

    override suspend fun fetchContinueWatching(): List<Content.Resumable> {
        val server = serverUrl()
        return api.getContinueWatching().data.orEmpty().mapNotNull { it.toResumable(server) }
    }

    override suspend fun fetchTrending(limit: Int): List<Content> {
        val server = serverUrl()
        return api.getTrending(limit).data?.items.orEmpty().map { it.toContent(server) }
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
    ): List<Content> {
        val server = serverUrl()
        return api.getLatest(limit, libraryId, type)
            .data?.items.orEmpty()
            .map { it.toContent(server) }
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

    override suspend fun fetchLiveNow(limit: Int): List<Content.LiveChannel> {
        val server = serverUrl()
        return api.getLiveNow(limit).data?.items.orEmpty().map { it.toContent(server) }
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
    override suspend fun fetchChildren(parentId: String): List<Content> {
        val server = serverUrl()
        return api.getChildren(parentId).data.orEmpty().map { it.toContent(server) }
    }

    /** /me/next-up — queued episodes across every series. */
    override suspend fun fetchNextUp(): List<Content.Episode> {
        return api.getNextUp().data.orEmpty().map { it.toEpisode() }
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
    ): List<Content> {
        val server = serverUrl()
        return api.listItems(
            type      = type,
            limit     = limit,
            offset    = offset,
            sortBy    = sortBy,
            sortOrder = sortOrder,
        ).data?.items.orEmpty().map { it.toContent(server) }
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
            items       = data.items.map { it.toContent(server) },
        )
    }

    private fun CollectionListEntryDto.toDomain(server: String) = CollectionSummary(
        id          = id,
        name        = name,
        itemCount   = itemCount,
        posterUrl   = absolutize(posterUrl, server),
        backdropUrl = absolutize(backdropUrl, server),
    )

    override suspend fun fetchItemDetail(itemId: String): Content {
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

        // /items/{id} returns the rich detail payload. Only movies and
        // series carry trailer + collection metadata; the wire `type`
        // tells us which variant to emit. Episodes / seasons fall through
        // to the generic mapping (they're rarely surfaced by this endpoint
        // but the server can return them when deep-linked).
        return when (MediaKind.from(data.type)) {
            MediaKind.Movie -> Content.Movie(
                id             = data.id,
                title          = data.title.orEmpty(),
                subtitle       = data.tagline,
                posterUrl      = absolutize(data.posterUrl, server),
                backdropUrl    = absolutize(data.backdropUrl, server),
                logoUrl        = absolutize(data.logoUrl ?: data.studioLogoUrl, server),
                overview       = data.overview,
                genres         = data.genres,
                rating         = data.communityRating,
                year           = data.year,
                progressPct    = progress,
                resumePosSec   = resumeSec,
                durationSec    = totalSec,
                trailerKey     = data.trailer?.key,
                trailerSite    = data.trailer?.site,
                isFavorite     = data.userData?.isFavorite == true,
                collectionId   = data.collection?.id,
                collectionName = data.collection?.name,
            )
            MediaKind.Series -> Content.Series(
                id          = data.id,
                title       = data.title.orEmpty(),
                subtitle    = data.tagline,
                posterUrl   = absolutize(data.posterUrl, server),
                backdropUrl = absolutize(data.backdropUrl, server),
                logoUrl     = absolutize(data.logoUrl ?: data.studioLogoUrl, server),
                overview    = data.overview,
                genres      = data.genres,
                rating      = data.communityRating,
                year        = data.year,
                trailerKey  = data.trailer?.key,
                trailerSite = data.trailer?.site,
                isFavorite  = data.userData?.isFavorite == true,
            )
            MediaKind.Episode -> Content.Episode(
                id           = data.id,
                title        = data.title.orEmpty(),
                subtitle     = data.tagline,
                posterUrl    = absolutize(data.posterUrl, server),
                backdropUrl  = absolutize(data.backdropUrl, server),
                logoUrl      = absolutize(data.logoUrl, server),
                overview     = data.overview,
                genres       = data.genres,
                rating       = data.communityRating,
                year         = data.year,
                progressPct  = progress,
                resumePosSec = resumeSec,
                durationSec  = totalSec,
                isFavorite   = data.userData?.isFavorite == true,
            )
            MediaKind.Season -> Content.Season(
                id          = data.id,
                title       = data.title.orEmpty(),
                subtitle    = data.tagline,
                posterUrl   = absolutize(data.posterUrl, server),
                backdropUrl = absolutize(data.backdropUrl, server),
                logoUrl     = absolutize(data.logoUrl, server),
                overview    = data.overview,
                genres      = data.genres,
                rating      = data.communityRating,
                year        = data.year,
            )
            MediaKind.LiveChannel,
            MediaKind.Unknown -> Content.Unknown(
                id          = data.id,
                title       = data.title.orEmpty(),
                subtitle    = data.tagline,
                posterUrl   = absolutize(data.posterUrl, server),
                backdropUrl = absolutize(data.backdropUrl, server),
                logoUrl     = absolutize(data.logoUrl, server),
                overview    = data.overview,
                genres      = data.genres,
                rating      = data.communityRating,
                year        = data.year,
            )
        }
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
     * Full-text search across the catalogue. Returns the same [Content]
     * shape Home rails use so the result grid can reuse MediaCard +
     * navigate to Detail/Series with the existing rules. Empty query
     * short-circuits without hitting the network.
     */
    override suspend fun searchItems(query: String, limit: Int): List<Content> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val server = serverUrl()
        return api.searchItems(query = q, limit = limit).data.map { it.toContent(server) }
    }

    private suspend fun serverUrl(): String =
        tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()

    // ─── DTO → Content mappers ───────────────────────────────────────────────

    /**
     * Continue-watching only ever contains movies and episodes — the
     * server filters out anything else before publishing this rail.
     * Returns `null` for the rare malformed entry (unknown type) so
     * the caller can `mapNotNull` and skip it without a runtime cast.
     */
    private fun ContinueWatchingEntryDto.toResumable(server: String): Content.Resumable? {
        val resumeSec = positionTicks?.let { ticksToSeconds(it) } ?: 0L
        val totalSec  = durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val progress  = userData?.progress?.percentage?.let { it / 100f }
            ?: if (totalSec > 0) (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        return when (MediaKind.from(type)) {
            MediaKind.Movie -> Content.Movie(
                id           = id,
                title        = title.orEmpty(),
                posterUrl    = absolutize(posterUrl, server),
                backdropUrl  = absolutize(thumbUrl ?: backdropUrl ?: posterUrl, server),
                logoUrl      = absolutize(logoUrl, server),
                year         = seriesYear,
                progressPct  = progress,
                resumePosSec = resumeSec,
                durationSec  = totalSec,
            )
            MediaKind.Episode -> {
                // Episodes display as "Series · S2 E4 · Title" — promote
                // the series title when present so the card reads naturally.
                val displayTitle = if (!seriesTitle.isNullOrBlank()) seriesTitle else title.orEmpty()
                val episodeSubtitle = run {
                    val s = seasonIndex; val e = episodeIndex
                    if (s != null && e != null)
                        "S$s · E$e · ${title.orEmpty()}".trimEnd(' ', '·')
                    else
                        title
                }
                Content.Episode(
                    id            = id,
                    title         = displayTitle,
                    subtitle      = episodeSubtitle,
                    posterUrl     = absolutize(posterUrl, server),
                    backdropUrl   = absolutize(thumbUrl ?: backdropUrl ?: posterUrl, server),
                    logoUrl       = absolutize(logoUrl, server),
                    year          = seriesYear,
                    progressPct   = progress,
                    resumePosSec  = resumeSec,
                    durationSec   = totalSec,
                    seriesId      = seriesId,
                    parentId      = parentId,
                    seasonNumber  = seasonIndex,
                    episodeNumber = episodeIndex,
                )
            }
            else -> null
        }
    }

    /**
     * /me/next-up entries are sparse — no overview, no images — but they
     * carry the episode id + series_id we need to play and to match in
     * the resume resolver. SeriesScreen tops up the missing fields via
     * children when it needs to render rich episode cards.
     */
    private fun NextUpItemDto.toEpisode(): Content.Episode {
        val totalSec = durationTicks?.let { ticksToSeconds(it) } ?: 0L
        val episodeLabel = buildString {
            val s = seasonNumber; val e = episodeNumber
            if (s != null && e != null) append("S$s · E$e")
            if (!episodeTitle.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(episodeTitle)
            }
        }.ifBlank { null }

        return Content.Episode(
            id            = id,
            title         = seriesTitle.orEmpty(),
            subtitle      = episodeLabel,
            seriesId      = seriesId,
            seasonNumber  = seasonNumber,
            episodeNumber = episodeNumber,
            durationSec   = totalSec,
        )
    }

    private fun ItemSummaryDto.toContent(server: String): Content {
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
        val progress = if (totalSec > 0 && resumeSec > 0)
            (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f
        val poster = absolutize(posterUrl, server)
        val backdrop = absolutize(backdropUrl ?: posterUrl, server)
        val logo = absolutize(logoUrl, server)
        val favorite = userData?.isFavorite == true

        return when (kindFromType) {
            MediaKind.Movie -> Content.Movie(
                id           = id,
                title        = title.orEmpty(),
                subtitle     = sub,
                posterUrl    = poster,
                backdropUrl  = backdrop,
                logoUrl      = logo,
                overview     = overview,
                genres       = genres,
                rating       = communityRating,
                year         = year,
                progressPct  = progress,
                resumePosSec = resumeSec,
                durationSec  = totalSec,
                isFavorite   = favorite,
            )
            MediaKind.Series -> Content.Series(
                id          = id,
                title       = title.orEmpty(),
                subtitle    = sub,
                posterUrl   = poster,
                backdropUrl = backdrop,
                logoUrl     = logo,
                overview    = overview,
                genres      = genres,
                rating      = communityRating,
                year        = year,
                isFavorite  = favorite,
            )
            MediaKind.Season -> Content.Season(
                id           = id,
                title        = title.orEmpty(),
                subtitle     = sub,
                posterUrl    = poster,
                backdropUrl  = backdrop,
                logoUrl      = logo,
                overview     = overview,
                genres       = genres,
                rating       = communityRating,
                year         = year,
                parentId     = parentId,
                seasonNumber = seasonNumber,
            )
            MediaKind.Episode -> Content.Episode(
                id            = id,
                title         = title.orEmpty(),
                subtitle      = sub,
                posterUrl     = poster,
                backdropUrl   = backdrop,
                logoUrl       = logo,
                overview      = overview,
                genres        = genres,
                rating        = communityRating,
                year          = year,
                progressPct   = progress,
                resumePosSec  = resumeSec,
                durationSec   = totalSec,
                parentId      = parentId,
                seasonNumber  = seasonNumber,
                episodeNumber = episodeNumber,
                isFavorite    = favorite,
            )
            MediaKind.LiveChannel,
            MediaKind.Unknown -> Content.Unknown(
                id          = id,
                title       = title.orEmpty(),
                subtitle    = sub,
                posterUrl   = poster,
                backdropUrl = backdrop,
                logoUrl     = logo,
                overview    = overview,
                genres      = genres,
                rating      = communityRating,
                year        = year,
            )
        }
    }

    /**
     * Trending items also come back with relative URLs — verified
     * against me_home.go::Trending(). The earlier assumption that
     * trending was "already absolute" was wrong; pass them through
     * absolutize() like every other rail.
     */
    /**
     * Trending only carries movies and series — that's enforced on the
     * server. Anything else (episodes via mismatched libraries) becomes
     * [Content.Unknown] so the rail still renders the poster but Detail
     * navigation safely falls back.
     */
    private fun TrendingItemDto.toContent(server: String): Content {
        val poster = absolutize(posterUrl, server)
        val backdrop = absolutize(backdropUrl, server)
        val logo = absolutize(logoUrl, server)
        return when (MediaKind.from(type)) {
            MediaKind.Movie -> Content.Movie(
                id          = id,
                title       = title.orEmpty(),
                subtitle    = year?.toString(),
                posterUrl   = poster,
                backdropUrl = backdrop,
                logoUrl     = logo,
                overview    = overview,
                genres      = genres,
                rating      = communityRating,
                year        = year,
            )
            MediaKind.Series -> Content.Series(
                id          = id,
                title       = title.orEmpty(),
                subtitle    = year?.toString(),
                posterUrl   = poster,
                backdropUrl = backdrop,
                logoUrl     = logo,
                overview    = overview,
                genres      = genres,
                rating      = communityRating,
                year        = year,
            )
            else -> Content.Unknown(
                id          = id,
                title       = title.orEmpty(),
                subtitle    = year?.toString(),
                posterUrl   = poster,
                backdropUrl = backdrop,
                logoUrl     = logo,
                overview    = overview,
                genres      = genres,
                rating      = communityRating,
                year        = year,
            )
        }
    }

    private fun LiveNowChannelDto.toContent(server: String): Content.LiveChannel {
        val absoluteLogo = absolutize(channelLogo, server)
        return Content.LiveChannel(
            id           = channelId,
            title        = channelName.orEmpty(),
            subtitle     = programTitle,
            posterUrl    = absoluteLogo,
            backdropUrl  = absoluteLogo,
            logoUrl      = absoluteLogo,
            overview     = programTitle,
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
//
// MediaKind + sealed Content hierarchy live in Content.kt. This file keeps
// only the home-screen-specific aggregates (HomeData, HomeRailConfig, …) and
// the Collections types that re-use Content for their member lists.

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
 * reuse [Content] so the same grid + card code as Movies/Series works
 * without translation.
 */
@androidx.compose.runtime.Immutable
data class CollectionDetail(
    val id:          String,
    val tmdbId:      Int?,
    val name:        String,
    val overview:    String?,
    val posterUrl:   String?,
    val backdropUrl: String?,
    val items:       List<Content>,
)

@androidx.compose.runtime.Immutable
data class HomeData(
    val hero:             List<Content>             = emptyList(),
    val continueWatching: List<Content.Resumable>   = emptyList(),
    val nextUp:           List<Content.Episode>     = emptyList(),
    val trending:         List<Content>             = emptyList(),
    val liveNow:          List<Content.LiveChannel> = emptyList(),
    val rails:            List<HomeRailConfig>      = emptyList(),
    /**
     * Per-rail latest items, keyed by HomeRailConfig.id. Each
     * `latest_in_library` rail in the layout gets its own entry,
     * fetched with the library_id + type=movie|series filter so the
     * card shape matches what the user expects (no episode pollution).
     */
    val latestByRailId:   Map<String, List<Content>> = emptyMap(),
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
