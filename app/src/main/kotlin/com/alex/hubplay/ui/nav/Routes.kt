package com.alex.hubplay.ui.nav

/**
 * Type-safe route enum used by both the NavGraph definition and the
 * code that does `navController.navigate(...)`. Strings live in one
 * place so a typo can't desync them.
 *
 * Player has typed arg helpers because it takes parameters; the others
 * are static destinations.
 */
sealed class Route(val path: String) {
    data object Login : Route("login")
    data object Home  : Route("home")

    /**
     * Player route — needs an itemId and an optional resumePosSec
     * passed via the URL. Two helpers:
     *   - [path]: the registration string with placeholders
     *     ("player/{itemId}?resume={resumePosSec}")
     *   - [route]: builds a concrete URL from values
     *     (Player.route("abc", 120) → "player/abc?resume=120")
     */
    data object Player : Route("player/{itemId}?resume={resumePosSec}") {
        const val ARG_ITEM_ID      = "itemId"
        const val ARG_RESUME       = "resumePosSec"

        fun route(itemId: String, resumePosSec: Long = 0L): String =
            "player/$itemId?resume=$resumePosSec"
    }
}
