package com.alex.hubplay.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Tracks idle time across the whole app and flips a [State] when the
 * user has been silent long enough for the screensaver to take over.
 *
 * Why an app-global controller instead of a per-screen LaunchedEffect?
 * The screensaver needs to know about input that happens ANYWHERE — a
 * touch on the player chrome, a D-pad press on the home rails, a typed
 * letter in search. The cleanest place to capture all of that is
 * MainActivity.dispatchKeyEvent / dispatchTouchEvent, which then funnels
 * everything through [onInteraction] here. Compose-level effects can't
 * see events the focused composable consumes, so they'd miss the
 * majority of input on a TV.
 *
 * Suspended states:
 *   - The PlayerScreen sets [suspended] = true while it's mounted, so
 *     a 2h movie doesn't trip the screensaver every 3 minutes. ExoPlayer
 *     has its own keep-screen-on signal so the device won't sleep
 *     either way.
 *   - Login is also a candidate to suspend (no backdrops to show before
 *     the user even has a server), and HubplayNavGraph does that.
 */
class IdleController(
    private val scope:        CoroutineScope,
    private val idleAfterMs:  Long = DEFAULT_IDLE_MS,
    private val tickEveryMs:  Long = 30_000L,
) {
    private val _state = MutableStateFlow(IdleState(isIdle = false, suspended = false))
    val state: StateFlow<IdleState> = _state.asStateFlow()

    @Volatile private var lastInteractionMs: Long = System.currentTimeMillis()
    private var tickerJob: Job? = null

    init {
        startTicker()
    }

    /** Call from every key / touch handler. Cheap — just stamps a Long. */
    fun onInteraction() {
        lastInteractionMs = System.currentTimeMillis()
        // Coming out of idle on input should be instant, not on the next tick.
        if (_state.value.isIdle) {
            _state.update { it.copy(isIdle = false) }
        }
    }

    /**
     * Disable / re-enable the screensaver. Used by the player screen to
     * suspend the idle countdown while a video is in the foreground.
     */
    fun setSuspended(suspended: Boolean) {
        _state.update { it.copy(suspended = suspended, isIdle = false) }
        // Reset the timestamp on resume so the user gets a full idle
        // window after closing the player, not "you were idle 10 min
        // while watching, screensaver now."
        if (!suspended) lastInteractionMs = System.currentTimeMillis()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (true) {
                delay(tickEveryMs)
                val s = _state.value
                if (s.suspended || s.isIdle) continue
                val elapsed = System.currentTimeMillis() - lastInteractionMs
                if (elapsed >= idleAfterMs) {
                    _state.update { it.copy(isIdle = true) }
                }
            }
        }
    }

    companion object {
        /** Three minutes — matches the default Android TV daydream timeout
         *  so we activate JUST BEFORE the system tries to take over. */
        const val DEFAULT_IDLE_MS = 3L * 60_000L
    }
}

data class IdleState(
    val isIdle:    Boolean,
    /** True while the player screen is up. Screensaver stays off here. */
    val suspended: Boolean,
)
