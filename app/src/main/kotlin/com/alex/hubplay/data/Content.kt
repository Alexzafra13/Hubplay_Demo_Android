package com.alex.hubplay.data

import androidx.compose.runtime.Immutable

/**
 * Sealed domain type for any catalog item displayed in the UI: movies,
 * series, seasons, episodes, live channels. Replaces the old MediaItem
 * god-class (27 nullable fields) so each variant only carries the
 * fields that actually make sense for it.
 *
 * Common fields live on this interface so polymorphic consumers (rails,
 * grid cards, hero) keep working without `when` blocks. Variant-specific
 * fields (trailer pair, episode hierarchy, channel avatar) live on each
 * data class and require either pattern matching with `when (item)` or
 * a smart cast (`as? Content.Resumable`, `as? Content.Episode`, …).
 *
 * Forward-compat: unknown server-side `type` values become
 * [Content.Unknown] — the repo doesn't drop them silently so a future
 * `type=podcast` would surface (without crashing) until we add a real
 * variant for it.
 */
@Immutable
sealed interface Content {
    val id:          String
    val kind:        MediaKind
    val title:       String
    val subtitle:    String?
    val posterUrl:   String?
    val backdropUrl: String?
    val logoUrl:     String?
    val overview:    String?
    val genres:      List<String>
    val rating:      Float?
    val year:        Int?

    /**
     * Variants whose UI shows a playback progress bar or resume button:
     * movies and episodes. Series / seasons / channels never do. This
     * intermediate interface lets generic consumers (MediaCard, HeroInfo)
     * read progress info via one smart cast instead of branching on
     * every concrete subtype.
     */
    sealed interface Resumable : Content {
        val progressPct:  Float
        val resumePosSec: Long
        val durationSec:  Long
    }

    @Immutable
    data class Movie(
        override val id:           String,
        override val title:        String,
        override val subtitle:     String?      = null,
        override val posterUrl:    String?      = null,
        override val backdropUrl:  String?      = null,
        override val logoUrl:      String?      = null,
        override val overview:     String?      = null,
        override val genres:       List<String> = emptyList(),
        override val rating:       Float?       = null,
        override val year:         Int?         = null,
        override val progressPct:  Float        = 0f,
        override val resumePosSec: Long         = 0L,
        override val durationSec:  Long         = 0L,
        val trailerKey:            String?      = null,
        val trailerSite:           String?      = null,
        val isFavorite:            Boolean      = false,
        val watched:               Boolean      = false,
        val collectionId:          String?      = null,
        val collectionName:        String?      = null,
        val people:                List<Person> = emptyList(),
    ) : Resumable {
        override val kind: MediaKind get() = MediaKind.Movie
    }

    @Immutable
    data class Series(
        override val id:          String,
        override val title:       String,
        override val subtitle:    String?      = null,
        override val posterUrl:   String?      = null,
        override val backdropUrl: String?      = null,
        override val logoUrl:     String?      = null,
        override val overview:    String?      = null,
        override val genres:      List<String> = emptyList(),
        override val rating:      Float?       = null,
        override val year:        Int?         = null,
        val trailerKey:           String?      = null,
        val trailerSite:          String?      = null,
        val isFavorite:           Boolean      = false,
        val watched:              Boolean      = false,
        val people:               List<Person> = emptyList(),
    ) : Content {
        override val kind: MediaKind get() = MediaKind.Series
    }

    @Immutable
    data class Season(
        override val id:          String,
        override val title:       String,
        override val subtitle:    String?      = null,
        override val posterUrl:   String?      = null,
        override val backdropUrl: String?      = null,
        override val logoUrl:     String?      = null,
        override val overview:    String?      = null,
        override val genres:      List<String> = emptyList(),
        override val rating:      Float?       = null,
        override val year:        Int?         = null,
        val parentId:             String?      = null,
        val seasonNumber:         Int?         = null,
    ) : Content {
        override val kind: MediaKind get() = MediaKind.Season
    }

    @Immutable
    data class Episode(
        override val id:           String,
        override val title:        String,
        override val subtitle:     String?      = null,
        override val posterUrl:    String?      = null,
        override val backdropUrl:  String?      = null,
        override val logoUrl:      String?      = null,
        override val overview:     String?      = null,
        override val genres:       List<String> = emptyList(),
        override val rating:       Float?       = null,
        override val year:         Int?         = null,
        override val progressPct:  Float        = 0f,
        override val resumePosSec: Long         = 0L,
        override val durationSec:  Long         = 0L,
        val seriesId:              String?      = null,
        val parentId:              String?      = null,
        val seasonNumber:          Int?         = null,
        val episodeNumber:         Int?         = null,
        val isFavorite:            Boolean      = false,
        val watched:               Boolean      = false,
        val people:                List<Person> = emptyList(),
    ) : Resumable {
        override val kind: MediaKind get() = MediaKind.Episode
    }

    @Immutable
    data class LiveChannel(
        override val id:          String,
        override val title:       String,
        override val subtitle:    String?      = null,
        override val posterUrl:   String?      = null,
        override val backdropUrl: String?      = null,
        override val logoUrl:     String?      = null,
        override val overview:    String?      = null,
        override val genres:      List<String> = emptyList(),
        override val rating:      Float?       = null,
        override val year:        Int?         = null,
        val logoInitials:         String?      = null,
        val logoBg:               String?      = null,
        val logoFg:               String?      = null,
    ) : Content {
        override val kind: MediaKind get() = MediaKind.LiveChannel
    }

    /**
     * Fallback for `type` values the client doesn't recognise yet
     * (forward-compat with new server enums). Rails render them with
     * the common fields; deeper screens that depend on variant-specific
     * data should filter these out.
     */
    @Immutable
    data class Unknown(
        override val id:          String,
        override val title:       String,
        override val subtitle:    String?      = null,
        override val posterUrl:   String?      = null,
        override val backdropUrl: String?      = null,
        override val logoUrl:     String?      = null,
        override val overview:    String?      = null,
        override val genres:      List<String> = emptyList(),
        override val rating:      Float?       = null,
        override val year:        Int?         = null,
    ) : Content {
        override val kind: MediaKind get() = MediaKind.Unknown
    }
}

/**
 * Wire-level type discriminator that survives serialisation (nav args,
 * cache keys, server JSON). Useful when we have just a kind string and
 * no Content instance yet — e.g. on a deep link or a saved nav back stack.
 */
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
 * A cast or crew credit on an item. `role` is the lowercase server value
 * ("actor" / "director" / "writer"); `character` is set only for actors.
 * Carried on [Content.Movie] / [Content.Series] / [Content.Episode] so
 * the Detail screen can render a "Reparto y equipo" rail.
 */
@Immutable
data class Person(
    val id:        String,
    val name:      String,
    val role:      String? = null,
    val character: String? = null,
    val imageUrl:  String? = null,
)

/**
 * A person profile + their filmography. Filmography entries reuse
 * [Content] so the PersonDetail screen renders them with the same
 * MediaCard + navigation rules as every other grid.
 */
@Immutable
data class PersonDetail(
    val id:          String,
    val name:        String,
    val type:        String?       = null,
    val imageUrl:    String?       = null,
    val filmography: List<Content> = emptyList(),
)
