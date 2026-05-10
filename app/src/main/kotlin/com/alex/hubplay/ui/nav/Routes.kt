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
    data object Login : Route("login")
    data object Home  : Route("home")

    /** Item detail / browse-then-play surface. */
    data object Detail : Route("detail/{itemId}") {
        const val ARG_ITEM_ID = "itemId"
        fun route(itemId: String): String = "detail/$itemId"
    }

    data object Player : Route("player/{itemId}?resume={resumePosSec}") {
        const val ARG_ITEM_ID = "itemId"
        const val ARG_RESUME  = "resumePosSec"

        fun route(itemId: String, resumePosSec: Long = 0L): String =
            "player/$itemId?resume=$resumePosSec"
    }
}
