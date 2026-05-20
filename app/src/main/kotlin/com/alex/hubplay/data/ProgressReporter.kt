package com.alex.hubplay.data

import android.util.Log
import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.data.api.dto.UpdateProgressRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Reports playback progress back to the server.
 *
 *  - `reportPosition(itemId, positionSec, durationSec, isPlaying)` is meant
 *    to be called every few seconds from a polling loop in the screen.
 *    It throttles to one HTTP call per [WRITE_INTERVAL_MS] and skips
 *    writes when the position hasn't moved meaningfully (paused video).
 *  - When the position crosses [COMPLETION_THRESHOLD] of duration, it
 *    fires `markPlayed` once (idempotent + guarded so it doesn't repeat
 *    if the user seeks back and replays the last 5 %).
 *  - `flush()` forces a final write — call it from the screen's
 *    DisposableEffect onDispose so the last position survives the user
 *    backing out before the throttle window elapses.
 *
 * Why fire-and-forget? Network blips during playback are common; failing
 * a progress write should NEVER stop playback or surface UI errors. Each
 * call is wrapped in `runCatching` and the failure is logged at WARN.
 *
 * Lifecycle: one Reporter per playback session. When the user surfs to
 * another VOD item, build a new Reporter — internal state (last position,
 * markPlayed-sent flag) MUST reset.
 */
class ProgressReporter(
    private val api:    HubplayApi,
    private val scope:  CoroutineScope,
    private val itemId: String,
) {

    private val mutex = Mutex()

    /** -1 = never written. Tracked in seconds; backend stores ticks but
     *  the rounding noise from sub-second positions adds zero value. */
    @Volatile private var lastWrittenPositionSec: Long = -1L
    @Volatile private var lastWrittenAtMs:        Long = 0L
    @Volatile private var markedPlayed:           Boolean = false
    @Volatile private var inflight:               Job? = null

    /**
     * Push the latest known position. No-op when:
     *   - the player isn't actually playing (paused / buffering / ended),
     *   - position has barely moved since last write (<1 s),
     *   - the previous write was <[WRITE_INTERVAL_MS] ago.
     *
     * Always evaluates the completion threshold so a one-shot markPlayed
     * fires the moment the user crosses 95 %, even if the throttle window
     * would otherwise suppress the write.
     */
    fun reportPosition(positionSec: Long, durationSec: Long, isPlaying: Boolean) {
        if (positionSec < 0) return

        if (durationSec > 0 && !markedPlayed) {
            val ratio = positionSec.toDouble() / durationSec.toDouble()
            if (ratio >= COMPLETION_THRESHOLD) {
                markedPlayed = true
                launchUnique {
                    runCatching { api.markPlayed(itemId) }
                        .onFailure { Log.w(TAG, "markPlayed($itemId) failed", it) }
                }
                return
            }
        }

        if (!isPlaying) return

        val now      = System.currentTimeMillis()
        val moved    = lastWrittenPositionSec < 0 ||
                       kotlin.math.abs(positionSec - lastWrittenPositionSec) >= 1
        val cooled   = now - lastWrittenAtMs >= WRITE_INTERVAL_MS
        if (!moved || !cooled) return

        lastWrittenPositionSec = positionSec
        lastWrittenAtMs        = now
        launchUnique { writePosition(positionSec, completed = false) }
    }

    /**
     * Force a write of the supplied position, regardless of throttle.
     * Use it from the screen's DisposableEffect onDispose so we capture
     * "where the user left it" even if the cleanup runs <10 s after the
     * last throttled write.
     */
    fun flush(positionSec: Long, completed: Boolean = false) {
        if (positionSec < 0) return
        if (positionSec == lastWrittenPositionSec && !completed) return
        lastWrittenPositionSec = positionSec
        lastWrittenAtMs        = System.currentTimeMillis()
        launchUnique { writePosition(positionSec, completed) }
    }

    private suspend fun writePosition(positionSec: Long, completed: Boolean) {
        val ticks = positionSec * TICKS_PER_SECOND
        runCatching {
            api.updateProgress(
                itemId = itemId,
                body   = UpdateProgressRequest(positionTicks = ticks, completed = completed),
            )
        }.onFailure { Log.w(TAG, "updateProgress($itemId, ${positionSec}s) failed", it) }
    }

    /** Serialise outgoing HTTP so we never have two writes racing for the same itemId. */
    private fun launchUnique(block: suspend () -> Unit) {
        scope.launch {
            mutex.withLock {
                val prior = inflight
                if (prior != null && prior.isActive) prior.join()
                inflight = scope.launch { block() }
            }
        }
    }

    companion object {
        private const val TAG               = "ProgressReporter"
        private const val WRITE_INTERVAL_MS = 10_000L
        private const val COMPLETION_THRESHOLD = 0.95
        private const val TICKS_PER_SECOND  = 10_000_000L  // .NET ticks unit the backend uses
    }
}
