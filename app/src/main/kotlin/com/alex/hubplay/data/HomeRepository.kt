package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.ContinueWatchingEntryDto

/**
 * Owns reads against the home / catalogue surface.
 *
 * For now it just exposes Continue Watching — the rail the user sees
 * first after login. The other rails (Latest, Trending, Next Up,
 * LiveNow) follow the same shape and will be added as new methods on
 * this class without restructuring callers.
 */
class HomeRepository(
    private val api:        HubplayApi,
    private val tokenStore: TokenStore,
) {

    suspend fun fetchContinueWatching(): List<ContinueWatchingItem> {
        val resp = api.getContinueWatching()
        val server = tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()
        return resp.data.orEmpty().map { it.toDomain(server) }
    }

    private fun ContinueWatchingEntryDto.toDomain(server: String): ContinueWatchingItem {
        // Resume timestamp is on the entry directly (server convenience);
        // user_data is a fallback for edge cases where it's missing.
        val resumeSec = (positionSeconds ?: userData?.positionSeconds ?: 0f).toLong()
        val totalSec  = ((runtimeMinutes ?: 0) * 60).toLong()
        val progressPct = if (totalSec > 0) (resumeSec.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        // Backend exposes image *ids*, not URLs. Construct the URL
        // ourselves; it's authenticated so we send through OkHttp +
        // bearer (Coil's network-okhttp engine handles that for us).
        val imageId = backdropImageId ?: posterImageId
        val imageUrl = imageId?.let { "$server/api/v1/images/file/$it" }

        return ContinueWatchingItem(
            id            = id,
            title         = title.orEmpty(),
            subtitle      = displaySubtitle(),
            imageUrl      = imageUrl,
            progressPct   = progressPct,
            resumePosSec  = resumeSec,
        )
    }

    /** Episode → "S2 · E4". Movie/series → null. */
    private fun ContinueWatchingEntryDto.displaySubtitle(): String? {
        if (type != "episode") return null
        val s = seasonNumber
        val e = episodeNumber
        return when {
            s != null && e != null -> "S$s · E$e"
            else                   -> null
        }
    }
}

/**
 * UI-facing model. The reactive layer (ViewModel, Compose) only sees
 * this — never the wire DTOs. This means we can change the JSON shape
 * server-side without touching screens, only the `toDomain` mapper.
 */
data class ContinueWatchingItem(
    val id:            String,
    val title:         String,
    val subtitle:      String?,
    val imageUrl:      String?,
    val progressPct:   Float,    // 0f..1f
    val resumePosSec:  Long,
)
