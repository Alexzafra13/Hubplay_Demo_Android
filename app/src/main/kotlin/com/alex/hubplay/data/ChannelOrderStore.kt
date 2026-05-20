package com.alex.hubplay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Local write-through cache + refresh-signal bus for the per-user
 * channel overlay. The backend is the source of truth — see
 * `/api/v1/me/iptv/channels/order` and friends — and this store mirrors
 * the post-write state purely for two reasons:
 *
 *  1. **Signal channel**: `ChannelOrderViewModel` writes through here
 *     after each successful backend call. `LiveTvViewModel` observes
 *     `prefsFlow` and uses any emission as a "refetch" trigger — the
 *     contents don't matter, only the Flow tick does.
 *  2. **Offline mirror**: the persisted blob holds the last known good
 *     state per `serverUrl|libraryId`. We don't currently apply it
 *     locally (the backend list call already returns the personalised
 *     view), but keeping it written-through means a future offline-grace
 *     pass can fall back to it without a schema migration.
 *
 * Storage shape — one JSON blob keyed by `"$serverUrl|$libraryId"`:
 *   { "https://hubplay.local|abc": { "order": ["c2","c1"], "hidden": ["c3"] } }
 *
 * Logout keeps these prefs (per-token tokens drop, the user's channel
 * preferences belong to the server identity, not the auth session).
 * "Forget server" leaves stale entries behind because no caller looks
 * them up against a different `serverUrl`.
 */
class ChannelOrderStore(private val context: Context) {

    private val Context.dataStore by preferencesDataStore(name = DATASTORE_NAME)
    private val blobKey = stringPreferencesKey("channel_prefs_v1")

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter<Map<String, ChannelPrefs>>(
        Types.newParameterizedType(Map::class.java, String::class.java, ChannelPrefs::class.java),
    )

    val prefsFlow: Flow<Map<String, ChannelPrefs>> =
        context.dataStore.data.map { prefs -> prefs[blobKey]?.let(::parse) ?: emptyMap() }

    suspend fun snapshot(): Map<String, ChannelPrefs> =
        context.dataStore.data.first()[blobKey]?.let(::parse) ?: emptyMap()

    suspend fun snapshotFor(serverUrl: String, libraryId: String): ChannelPrefs =
        snapshot()[buildKey(serverUrl, libraryId)] ?: ChannelPrefs()

    suspend fun setOrder(serverUrl: String, libraryId: String, order: List<String>) {
        mutate(serverUrl, libraryId) { it.copy(order = order) }
    }

    suspend fun setHidden(
        serverUrl: String,
        libraryId: String,
        channelId: String,
        hidden:    Boolean,
    ) {
        mutate(serverUrl, libraryId) { current ->
            val next = if (hidden) current.hidden + channelId else current.hidden - channelId
            current.copy(hidden = next.distinct())
        }
    }

    /** Drops every saved order + hidden entry. Hook this in if "reset" lands in UI. */
    suspend fun clearAll() {
        context.dataStore.edit { prefs -> prefs.remove(blobKey) }
    }

    private suspend fun mutate(
        serverUrl: String,
        libraryId: String,
        block:     (ChannelPrefs) -> ChannelPrefs,
    ) {
        context.dataStore.edit { prefs ->
            val current = prefs[blobKey]?.let(::parse).orEmpty()
            val k       = buildKey(serverUrl, libraryId)
            val updated = block(current[k] ?: ChannelPrefs())
            // Drop the entry entirely when it has nothing to remember — keeps
            // the blob compact and means "reset" can be done by an empty mutation.
            val merged = if (updated.isEmpty()) current - k else current + (k to updated)
            prefs[blobKey] = serialize(merged)
        }
    }

    private fun parse(raw: String): Map<String, ChannelPrefs> =
        runCatching { adapter.fromJson(raw).orEmpty() }.getOrDefault(emptyMap())

    private fun serialize(map: Map<String, ChannelPrefs>): String = adapter.toJson(map)

    companion object {
        private const val DATASTORE_NAME = "hubplay_channel_prefs"

        internal fun buildKey(serverUrl: String, libraryId: String): String =
            "${serverUrl.trimEnd('/')}|$libraryId"
    }
}

data class ChannelPrefs(
    val order:  List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
) {
    /** True when nothing's customised — used to evict empty entries from the blob. */
    fun isEmpty(): Boolean = order.isEmpty() && hidden.isEmpty()
}
