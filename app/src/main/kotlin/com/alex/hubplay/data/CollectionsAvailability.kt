package com.alex.hubplay.data

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Tracks "does this server have any movie collections to show?" so the
 * TopNav can hide the Collections tab on libraries that don't carry
 * any sagas yet (a brand-new install or a library that's TV-only).
 * Without this the user would tap a tab and land on an empty grid.
 *
 * Lazy + reactive:
 *   - The first paint of any TopNav-bearing screen kicks off [refresh]
 *     via [ensureLoaded] — we don't burn the call on first login if
 *     the user heads straight into Player from Continue Watching.
 *   - Re-fetches whenever the paired server URL or the auth flag
 *     changes (login, switch profile, forget server). A previous
 *     server's pool can't bleed into a new one.
 *
 * Optimistic default: we start at `true` so the tab is visible during
 * the first request — flickering "tab appears 200 ms after Home loads"
 * looks worse than a slightly-wrong tab visible while we settle.
 */
class CollectionsAvailability(
    private val repository: HomeRepository,
    authStateFlow:          StateFlow<AuthState>,
    private val scope:      CoroutineScope,
) {
    private val _hasAny  = MutableStateFlow(true)
    val hasAny: StateFlow<Boolean> = _hasAny.asStateFlow()

    private var loadedOnce = false

    init {
        // Reset + refetch whenever the paired identity changes. We key
        // on (serverUrl, isAuthenticated) — token refresh alone doesn't
        // count because the catalogue is the same.
        authStateFlow
            .distinctUntilChangedBy { it.serverUrl to it.isAuthenticated }
            .onEach { state ->
                if (state.isAuthenticated) {
                    loadedOnce = false
                    ensureLoaded()
                } else {
                    // Logged out — let the tab stay visible for the
                    // login flow; it'll re-resolve on next auth.
                    _hasAny.value = true
                }
            }
            .launchIn(scope)
    }

    fun ensureLoaded() {
        if (loadedOnce) return
        loadedOnce = true
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        runCatching { repository.fetchCollections() }
            .onSuccess { _hasAny.value = it.isNotEmpty() }
            .onFailure {
                Log.w(TAG, "fetch collections for availability failed", it)
                // Keep last value — better to leave the tab as it was
                // than flicker on a transient network blip.
            }
    }

    companion object { private const val TAG = "CollectionsAvail" }
}
