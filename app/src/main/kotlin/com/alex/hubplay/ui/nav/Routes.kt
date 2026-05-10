package com.alex.hubplay.ui.nav

/**
 * Type-safe route enum used by both the NavGraph definition and the
 * code that does `navController.navigate(...)`. Strings live in one
 * place so a typo can't desync them.
 */
sealed class Route(val path: String) {
    data object Login : Route("login")
    data object Home  : Route("home")
}
