package com.alex.hubplay.data

import android.util.Log
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * SSE client for `/api/v1/me/events` — the per-user event channel that
 * broadcasts cross-device watch state (`user.progress.updated`,
 * `user.played.toggled`, `user.favorite.toggled`) plus session lifecycle.
 *
 * The wire shape is:
 *
 *     event: user.progress.updated
 *     data: {"type":"user.progress.updated","data":{"user_id":"…","item_id":"…","position_ticks":12345,"completed":false}}
 *
 * okhttp-sse already parses the framing (event/data lines, multi-line
 * data, heartbeats), so the listener gives us the type + raw JSON
 * payload directly. We Moshi-parse the inner `data` envelope into a
 * sealed [MeEvent] so consumers can branch with a `when`.
 *
 * Reconnect strategy: a single auto-reconnect loop wraps the EventSource.
 * On disconnect we wait 2 s and retry; the OkHttp AuthInterceptor takes
 * care of token refresh on 401, so a reconnect after token expiry
 * doesn't need any auth logic here.
 */
class MeEventsStream(
    private val okHttp:    OkHttpClient,
    private val tokenStore: TokenStore,
    moshi:                 Moshi,
) {

    // Moshi adapter for the outer envelope. Inner `data` is parsed with
    // its own adapter so unknown fields are tolerated and unused ones
    // default to null thanks to Kotlin defaults.
    private val envelopeAdapter: JsonAdapter<EventEnvelope> =
        moshi.adapter(EventEnvelope::class.java)

    /**
     * Hot-style cold flow: each collector spins up its own EventSource
     * and reconnects on its own. Designed for screen-scope use (one
     * subscriber per HomeViewModel/DetailViewModel) — multiplexing across
     * many subscribers is not worth the complexity until we have proof
     * that two open sockets is actually a problem.
     */
    fun events(): Flow<MeEvent> = flow {
        while (true) {
            val base = tokenStore.serverUrlBlocking()
            if (base.isNullOrBlank()) {
                // Not paired yet — wait and re-check rather than crash.
                delay(2_000L)
                continue
            }
            val request = Request.Builder()
                .url("${base.trimEnd('/')}/api/v1/me/events")
                .header("Accept", "text/event-stream")
                .build()

            try {
                openSource(request).collect { emit(it) }
            } catch (t: Throwable) {
                Log.w(TAG, "stream broke, will retry", t)
            }
            // Backoff before reconnecting. Jittered slightly so several
            // screens reconnecting after the same Wi-Fi blip don't all
            // hit the server simultaneously.
            delay(2_000L + (System.currentTimeMillis() % 750L))
        }
    }.flowOn(Dispatchers.IO)

    private fun openSource(request: Request): Flow<MeEvent> = callbackFlow {
        val factory  = EventSources.createFactory(okHttp)
        val listener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                val parsed = parse(type, data) ?: return
                trySend(parsed)
            }
            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                close(t)
            }
            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }
        val source = factory.newEventSource(request, listener)
        awaitClose { source.cancel() }
    }

    /**
     * Returns null when the event isn't one we care about (e.g. notification
     * inbox) — keeping the parsing dispatch close to the listener avoids
     * leaking irrelevant types into HomeViewModel's logic.
     *
     * Uses Moshi for both layers (outer envelope + inner payload). SSE
     * events arrive at most a few times per second under any realistic
     * workload, so the extra allocations are well below noise.
     */
    private fun parse(eventType: String?, rawJson: String): MeEvent? {
        val envelope = runCatching { envelopeAdapter.fromJson(rawJson) }.getOrNull() ?: return null
        val payload  = envelope.data ?: return null
        return when (eventType) {
            "user.progress.updated" -> MeEvent.ProgressUpdated(
                itemId        = payload.itemId        ?: return null,
                positionTicks = payload.positionTicks ?: 0L,
                completed     = payload.completed     ?: false,
            )
            "user.played.toggled" -> MeEvent.PlayedToggled(
                itemId    = payload.itemId    ?: return null,
                played    = payload.played    ?: false,
                completed = payload.completed ?: false,
            )
            "user.favorite.toggled" -> MeEvent.FavoriteToggled(
                itemId     = payload.itemId     ?: return null,
                isFavorite = payload.isFavorite ?: false,
            )
            // Refresh signal for the Live TV personalisation overlay —
            // emitted by the backend when this user's order/hidden state
            // changes on any device. Payload has nothing actionable, the
            // tick itself is what counts.
            "user.channel.order.updated" -> MeEvent.ChannelOrderUpdated
            else -> null
        }
    }

    @JsonClass(generateAdapter = true)
    internal data class EventEnvelope(
        val type: String?       = null,
        val data: EventPayload? = null,
    )

    @JsonClass(generateAdapter = true)
    internal data class EventPayload(
        @com.squareup.moshi.Json(name = "item_id")        val itemId:        String? = null,
        @com.squareup.moshi.Json(name = "user_id")        val userId:        String? = null,
        @com.squareup.moshi.Json(name = "position_ticks") val positionTicks: Long?   = null,
        val completed:                                                       Boolean? = null,
        val played:                                                          Boolean? = null,
        @com.squareup.moshi.Json(name = "is_favorite")    val isFavorite:    Boolean? = null,
    )

    companion object {
        private const val TAG = "MeEventsStream"
    }
}

sealed interface MeEvent {
    data class ProgressUpdated(val itemId: String, val positionTicks: Long, val completed: Boolean) : MeEvent
    data class PlayedToggled  (val itemId: String, val played: Boolean, val completed: Boolean)     : MeEvent
    data class FavoriteToggled(val itemId: String, val isFavorite: Boolean)                          : MeEvent
    /** Marker event — another device of this user just changed channel order/visibility. */
    data object ChannelOrderUpdated                                                                  : MeEvent
}
