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
 * Per-library channel preferences — custom order + hidden set.
 *
 * Lives in its own DataStore (separate from [TokenStore]) so logout
 * doesn't wipe channel ordering — users who switch tokens against the
 * same server expect their reorder to survive. "Forget server" doesn't
 * clear this either today; a stale entry just goes unread (its
 * `serverUrl` key never matches the new session).
 *
 * Storage shape — one JSON blob keyed by `"$serverUrl|$libraryId"`:
 *   { "https://hubplay.local|abc": { "order": ["c2","c1"], "hidden": ["c3"] } }
 *
 * Why a single blob (and not dynamic preference keys per library):
 * a) the dataset is tiny (tens of libraries × ~hundreds of IDs);
 * b) one read/write covers the whole UI atomically;
 * c) when the backend ships `/me/channels/order` (see backend's
 *    `docs/memory/per-user-channel-order-pending.md`) this store
 *    becomes the local cache and a single blob is easier to mirror.
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

        /**
         * Apply [prefs] to a freshly fetched channel list. Pure function so
         * the policy is unit-testable without DataStore plumbing.
         *
         *  - Hidden IDs drop out.
         *  - Known IDs (those in `prefs.order`) move to the front in saved
         *    order. Unknown IDs (e.g. new channels added server-side after
         *    the last reorder) keep their incoming relative position at the
         *    tail, so a freshly-added channel doesn't silently rocket to
         *    the top because its lookup index is `Int.MAX_VALUE` while
         *    everything else is already pinned.
         */
        fun applyPrefs(channels: List<LiveChannel>, prefs: ChannelPrefs): List<LiveChannel> {
            val hidden  = prefs.hidden.toSet()
            val visible = channels.filter { it.id !in hidden }
            return sortByOrder(visible, prefs.order)
        }

        /**
         * Like [applyPrefs] but DOES NOT drop hidden channels — used by the
         * reorder screen, which must keep them in view so the user can
         * unhide them. Sort is identical: stored order wins, unknowns at
         * the tail in their incoming relative order.
         */
        fun applyPrefsForOrderView(channels: List<LiveChannel>, prefs: ChannelPrefs): List<LiveChannel> =
            sortByOrder(channels, prefs.order)

        private fun sortByOrder(channels: List<LiveChannel>, order: List<String>): List<LiveChannel> {
            if (order.isEmpty()) return channels
            val orderIndex = order.withIndex().associate { (i, id) -> id to i }
            return channels.withIndex()
                .sortedWith(
                    compareBy(
                        { orderIndex[it.value.id] ?: Int.MAX_VALUE },
                        { it.index },
                    ),
                )
                .map { it.value }
        }
    }
}

data class ChannelPrefs(
    val order:  List<String> = emptyList(),
    val hidden: List<String> = emptyList(),
) {
    /** True when nothing's customised — used to evict empty entries from the blob. */
    fun isEmpty(): Boolean = order.isEmpty() && hidden.isEmpty()
}
