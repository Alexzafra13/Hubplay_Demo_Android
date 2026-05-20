package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.BulkScheduleRequest
import com.alex.hubplay.data.api.dto.ChannelDto
import com.alex.hubplay.data.api.dto.EPGProgramDto
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * IPTV-flavoured surface: live libraries, channels per library, EPG
 * schedules, favourites and the watch beacon.
 *
 * Kept separate from [HomeRepository] because Live TV has its own
 * domain types (channels are not items, programs are not items) and
 * its own refresh cadence (now-playing rolls over every ~30 min, so
 * the ViewModel will repoll bulk schedule on a timer).
 */
class LiveTvRepository(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) {

    /**
     * Returns IPTV libraries only. The home repository already fetches
     * /libraries — exposing a filtered helper here keeps the LiveTv
     * ViewModel honest about "the only thing I care about is IPTV".
     */
    suspend fun fetchLiveLibraries(): List<LiveLibrary> {
        return api.getLibraries().data.orEmpty()
            .filter { (it.contentType ?: "") == "livetv" }
            .map { LiveLibrary(id = it.id, name = it.name.orEmpty()) }
    }

    suspend fun fetchChannels(libraryId: String): List<LiveChannel> {
        val server = serverUrl()
        return api.listChannels(libraryId).data.orEmpty()
            .filter { it.isActive }
            .map { it.toDomain(server) }
    }

    /**
     * Raw `group_name` strings (the chips). Order comes from the server
     * (currently insertion order of the M3U); UI may sort if it wants.
     */
    suspend fun fetchGroups(libraryId: String): List<String> {
        return api.listChannelGroups(libraryId).data.orEmpty()
    }

    suspend fun fetchFavoriteIds(): Set<String> {
        return api.listFavoriteChannelIds().data.orEmpty().toSet()
    }

    /** Idempotent on the server side. Returns the new state. */
    suspend fun addFavorite(channelId: String): Boolean {
        return api.addFavoriteChannel(channelId).data?.isFavorite ?: true
    }

    suspend fun removeFavorite(channelId: String): Boolean {
        return api.removeFavoriteChannel(channelId).data?.isFavorite ?: false
    }

    /**
     * Bulk EPG fetch. Backend defaults to `now-2h .. now+24h` when the
     * window params are blank; we tighten that to `now-1h .. now+6h`
     * because the UI only renders now + next today.
     *
     * Returns a map keyed by channel id. Channels with no EPG data come
     * back with an empty list (or are missing from the map entirely —
     * the caller treats both as "no programs").
     */
    suspend fun fetchBulkSchedule(
        channelIds: List<String>,
        fromHours:  Int = -1,
        toHours:    Int = 6,
    ): Map<String, List<EpgProgram>> {
        if (channelIds.isEmpty()) return emptyMap()
        // The backend caps at 5_000 channels per request; we never get
        // close to that in practice (a fat M3U is ~1_500), so a single
        // round-trip is fine. If it ever does explode, chunk here.
        val resp = api.bulkSchedule(
            BulkScheduleRequest(
                channels = channelIds,
                from     = fromHours.toString(),
                to       = toHours.toString(),
            ),
        )
        return resp.data.orEmpty().mapValues { (_, progs) ->
            progs.mapNotNull { it.toDomain() }
                .sortedBy { it.startTime }
        }
    }

    suspend fun recordWatch(channelId: String) {
        // Fire-and-forget for the UI; the player calls this when the
        // user actually opens a channel. Errors are swallowed by the
        // ViewModel — a missed beacon is not user-visible.
        api.recordChannelWatch(channelId)
    }

    private suspend fun serverUrl(): String =
        tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()

    // ─── DTO → domain mappers ────────────────────────────────────────────

    private fun ChannelDto.toDomain(server: String): LiveChannel = LiveChannel(
        id            = id,
        name          = name,
        number        = number,
        groupName     = groupName.orEmpty(),
        category      = category.orEmpty(),
        logoUrl       = absolutize(logoUrl, server),
        logoInitials  = logoInitials,
        logoBg        = logoBg,
        logoFg        = logoFg,
        libraryId     = libraryId.orEmpty(),
        isActive      = isActive,
        healthStatus  = healthStatus.orEmpty(),
    )

    private fun EPGProgramDto.toDomain(): EpgProgram? {
        val start = parseInstant(startTime) ?: return null
        val end   = parseInstant(endTime)   ?: return null
        return EpgProgram(
            id          = id.orEmpty(),
            title       = title.orEmpty().ifBlank { "Programa sin título" },
            description = description.orEmpty(),
            category    = category.orEmpty(),
            startTime   = start,
            endTime     = end,
            iconUrl     = iconUrl?.takeIf { it.isNotBlank() },
        )
    }

    /** Server emits RFC3339; parse, tolerate trailing micro/nano precision. */
    private fun parseInstant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return try {
            Instant.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    private fun absolutize(path: String?, server: String): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return "$server$cleanPath"
    }
}

// ─── Domain types ────────────────────────────────────────────────────────────

@androidx.compose.runtime.Immutable
data class LiveLibrary(
    val id:   String,
    val name: String,
)

@androidx.compose.runtime.Immutable
data class LiveChannel(
    val id:            String,
    val name:          String,
    val number:        Int,
    val groupName:     String,
    /** Canonical bucket the server derives from groupName ("sports"/"news"/...). */
    val category:      String,
    /** Absolute URL to the same-origin logo proxy; null when no logo. */
    val logoUrl:       String?,
    /** Fallback placeholder triple — always populated by the backend. */
    val logoInitials:  String?,
    val logoBg:        String?,
    val logoFg:        String?,
    val libraryId:     String,
    val isActive:      Boolean,
    /** "ok" | "degraded" | "dead" — UI may hide dead channels. */
    val healthStatus:  String,
)

@androidx.compose.runtime.Immutable
data class EpgProgram(
    val id:          String,
    val title:       String,
    val description: String,
    val category:    String,
    val startTime:   Instant,
    val endTime:     Instant,
    /** XMLTV programme icon — usually a 16:9 poster, may be null. */
    val iconUrl:     String? = null,
) {
    fun isLiveAt(now: Instant): Boolean =
        !now.isBefore(startTime) && now.isBefore(endTime)

    /** 0f → just started, 1f → ending now. */
    fun progressAt(now: Instant): Float {
        val total = endTime.epochSecond - startTime.epochSecond
        if (total <= 0L) return 0f
        val elapsed = (now.epochSecond - startTime.epochSecond).coerceAtLeast(0L)
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }
}
