package com.alex.hubplay.data

import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.ContinueWatchingItemDto

/**
 * Owns reads against the home / catalogue surface.
 *
 * For now it just exposes Continue Watching — the rail the user sees
 * first after login. The other rails (Latest, Trending, Next Up,
 * LiveNow) follow the same shape and will be added as new methods on
 * this class without restructuring callers.
 */
class HomeRepository(private val api: HubplayApi) {

    suspend fun fetchContinueWatching(): List<ContinueWatchingItem> {
        return api.getContinueWatching().map { it.toDomain() }
    }

    private fun ContinueWatchingItemDto.toDomain(): ContinueWatchingItem {
        val playbackPos = userData?.playbackPosition ?: 0L
        val totalSec    = runtime ?: 0L
        val progressPct = if (totalSec > 0) (playbackPos.toFloat() / totalSec).coerceIn(0f, 1f) else 0f

        return ContinueWatchingItem(
            id            = id,
            title         = displayTitle(),
            subtitle      = displaySubtitle(),
            // Prefer the 16:9 thumb when available (movies + episodes both
            // expose it now); fall back to backdrop, then to poster as a
            // last resort so the card never renders empty.
            imageUrl      = thumbUrl ?: backdropUrl ?: posterUrl,
            progressPct   = progressPct,
            resumePosSec  = playbackPos,
        )
    }

    /** Series episode → "Severance · S2 E4". Movie → just the name. */
    private fun ContinueWatchingItemDto.displayTitle(): String {
        return when {
            type == "episode" && !seriesName.isNullOrBlank() -> seriesName
            else                                              -> name ?: "—"
        }
    }

    private fun ContinueWatchingItemDto.displaySubtitle(): String? {
        if (type != "episode") return null
        val s = seasonIndex
        val e = episodeIndex
        return when {
            s != null && e != null -> "S$s · E$e · ${name ?: ""}".trim('·', ' ')
            else                   -> name
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
