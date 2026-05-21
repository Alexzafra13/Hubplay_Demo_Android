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
    data object Detail : Route("detail/{itemId}") {
        const val ARG_ITEM_ID = "itemId"
        fun route(itemId: String): String = "detail/$itemId"
    }

    /** Series detail — seasons + episodes + resume resolver. */
    data object Series : Route("series/{seriesId}") {
        const val ARG_SERIES_ID = "seriesId"
        fun route(seriesId: String): String = "series/$seriesId"
    }

    data object Player : Route("player/{itemId}?resume={resumePosSec}") {
        const val ARG_ITEM_ID = "itemId"
        const val ARG_RESUME  = "resumePosSec"

        fun route(itemId: String, resumePosSec: Long = 0L): String =
            "player/$itemId?resume=$resumePosSec"
    }
}
