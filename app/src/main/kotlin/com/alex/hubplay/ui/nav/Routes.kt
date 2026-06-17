package com.alex.hubplay.ui.nav

/**
 * Type-safe route enum used by both the NavGraph definition and the
 * code that does `navController.navigate(...)`. Strings live in one
 * place so a typo can't desync them.
 *
 * Routes that take args expose typed `route(...)` builders so call
 * sites can't misformat them.
 */
sealed class Route(val path: String) {
    data object Login   : Route("login")
    data object WhoIsWatching : Route("who-is-watching")
    data object Home    : Route("home")
    data object Movies  : Route("movies")
    data object SeriesList : Route("series-list")
    data object LiveTv  : Route("live-tv")
    data object Search  : Route("search")
    data object Settings: Route("settings")
    data object ChannelOrder: Route("channel-order")
    data object TrustedServers: Route("trusted-servers")
    data object Collections : Route("collections")

    /** Collection detail — saga hero + member movies in release order. */
    data object CollectionDetail : Route("collections/{collectionId}") {
        const val ARG_COLLECTION_ID = "collectionId"
        fun route(collectionId: String): String =
            "collections/${java.net.URLEncoder.encode(collectionId, Charsets.UTF_8)}"
    }

    /** Item detail / browse-then-play surface (movies). */
    data object Detail : Route("detail/{itemId}?trailerResume={trailerResumeSec}") {
        const val ARG_ITEM_ID = "itemId"
        const val ARG_TRAILER_RESUME = "trailerResumeSec"
        fun route(itemId: String, trailerResumeSec: Long = 0L): String =
            "detail/$itemId?trailerResume=$trailerResumeSec"
    }

    /** Series detail — seasons + episodes + resume resolver. */
    data object Series : Route("series/{seriesId}") {
        const val ARG_SERIES_ID = "seriesId"
        fun route(seriesId: String): String = "series/$seriesId"
    }

    /** Person detail — profile + filmography (tap-through from cast row). */
    data object Person : Route("person/{personId}") {
        const val ARG_PERSON_ID = "personId"
        fun route(personId: String): String =
            "person/${java.net.URLEncoder.encode(personId, Charsets.UTF_8)}"
    }

    /** Studio / network detail — profile + catalogue (tap-through from chip). */
    data object Studio : Route("studio/{studioSlug}") {
        const val ARG_STUDIO_SLUG = "studioSlug"
        fun route(slug: String): String =
            "studio/${java.net.URLEncoder.encode(slug, Charsets.UTF_8)}"
    }

    /** Federation landing — "Servidores conectados": unified shared-library grid. */
    data object Peers : Route("peers")

    /** A peer library's catalogue (grid of that peer's items). */
    data object PeerLibrary : Route("peers/{peerId}/{peerName}/{libraryId}/{libraryName}") {
        const val ARG_PEER_ID = "peerId"
        const val ARG_PEER_NAME = "peerName"
        const val ARG_LIBRARY_ID = "libraryId"
        const val ARG_LIBRARY_NAME = "libraryName"
        fun route(peerId: String, peerName: String, libraryId: String, libraryName: String): String {
            fun enc(s: String) = java.net.URLEncoder.encode(s, Charsets.UTF_8)
            return "peers/${enc(peerId)}/${enc(peerName)}/${enc(libraryId)}/${enc(libraryName)}"
        }
    }

    data object Player : Route("player/{itemId}?resume={resumePosSec}") {
        const val ARG_ITEM_ID = "itemId"
        const val ARG_RESUME  = "resumePosSec"

        fun route(itemId: String, resumePosSec: Long = 0L): String =
            "player/$itemId?resume=$resumePosSec"
    }
}
