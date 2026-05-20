package com.alex.hubplay.ui.livetv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.ChannelOrderStore
import com.alex.hubplay.data.ChannelPrefs
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.LiveLibrary
import com.alex.hubplay.data.LiveTvRepository
import com.alex.hubplay.data.TokenStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Channel Order screen — lets the user reorder channels within
 * a single library and mark channels as hidden so they disappear from
 * Live TV.
 *
 * Storage model: persisted by [ChannelOrderStore] keyed by `serverUrl|libraryId`.
 * Hidden channels are remembered, not removed — they reappear if the user
 * unhides them, including for channels currently in the hidden state on
 * load (we ALWAYS show every channel returned by the backend in this
 * screen, regardless of the hidden flag).
 *
 * UI semantics:
 *  - Edits are optimistic: `_ui` updates immediately, the store write is
 *    fired-and-forgotten (errors are logged but not surfaced — DataStore
 *    on-disk failures are rare enough that the recovery story is "reload").
 *  - One library shown at a time when there's > 1; the picker is part of
 *    the screen (most users have one IPTV library so we skip the picker
 *    automatically).
 */
class ChannelOrderViewModel(
    private val repository: LiveTvRepository,
    private val store:      ChannelOrderStore,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChannelOrderUiState(isLoading = true))
    val ui: StateFlow<ChannelOrderUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching { fetch() }
                .onSuccess { snapshot ->
                    _ui.update {
                        it.copy(
                            isLoading        = false,
                            error            = null,
                            serverUrl        = snapshot.serverUrl,
                            libraries        = snapshot.libraries,
                            channelsByLib    = snapshot.channelsByLib,
                            prefsByLib       = snapshot.prefsByLib,
                            // Preserve the user's library selection across reloads
                            // when possible — otherwise default to the first library.
                            selectedLibraryId =
                                it.selectedLibraryId?.takeIf { id -> snapshot.libraries.any { lib -> lib.id == id } }
                                    ?: snapshot.libraries.firstOrNull()?.id,
                        )
                    }
                }
                .onFailure { err ->
                    Log.w(TAG, "load failed", err)
                    _ui.update {
                        it.copy(
                            isLoading = false,
                            error     = err.message ?: "No se pudo cargar la lista de canales",
                        )
                    }
                }
        }
    }

    fun selectLibrary(libraryId: String) {
        _ui.update { it.copy(selectedLibraryId = libraryId) }
    }

    /** Move the channel at [index] up by one position (no-op when already at top). */
    fun moveUp(libraryId: String, index: Int) {
        mutateOrder(libraryId) { ids ->
            if (index <= 0 || index >= ids.size) return@mutateOrder ids
            ids.toMutableList().apply {
                add(index - 1, removeAt(index))
            }
        }
    }

    /** Move the channel at [index] down by one position (no-op when already at bottom). */
    fun moveDown(libraryId: String, index: Int) {
        mutateOrder(libraryId) { ids ->
            if (index < 0 || index >= ids.lastIndex) return@mutateOrder ids
            ids.toMutableList().apply {
                add(index + 1, removeAt(index))
            }
        }
    }

    fun toggleHidden(libraryId: String, channelId: String) {
        val currentPrefs = _ui.value.prefsByLib[libraryId] ?: ChannelPrefs()
        val nowHidden = channelId !in currentPrefs.hidden
        val nextHidden =
            if (nowHidden) currentPrefs.hidden + channelId
            else           currentPrefs.hidden - channelId
        val nextPrefs = currentPrefs.copy(hidden = nextHidden.distinct())
        _ui.update { it.copy(prefsByLib = it.prefsByLib + (libraryId to nextPrefs)) }
        viewModelScope.launch {
            val server = _ui.value.serverUrl
            runCatching { store.setHidden(server, libraryId, channelId, nowHidden) }
                .onFailure { Log.w(TAG, "setHidden($channelId, $nowHidden) failed", it) }
        }
    }

    /** Reset both order and hidden for [libraryId] back to backend defaults. */
    fun resetLibrary(libraryId: String) {
        // Snapshot the currently-hidden IDs BEFORE clearing UI state — we
        // need them to undo each hidden flag in the persisted blob.
        val hiddenSnapshot = _ui.value.prefsByLib[libraryId]?.hidden.orEmpty()
        _ui.update { it.copy(prefsByLib = it.prefsByLib + (libraryId to ChannelPrefs())) }
        viewModelScope.launch {
            val server = _ui.value.serverUrl
            runCatching { store.setOrder(server, libraryId, emptyList()) }
                .onFailure { Log.w(TAG, "reset order failed", it) }
            for (id in hiddenSnapshot) {
                runCatching { store.setHidden(server, libraryId, id, hidden = false) }
                    .onFailure { Log.w(TAG, "reset hidden $id failed", it) }
            }
        }
    }

    /**
     * Applies [transform] to the current saved order. We compute the
     * "current order" by composing the stored order (if any) over the
     * default sequence — the result is a stable list of every channel
     * ID we know about, in the order the user currently sees them.
     */
    private fun mutateOrder(
        libraryId: String,
        transform: (List<String>) -> List<String>,
    ) {
        val current = orderedIds(libraryId)
        val next = transform(current)
        if (next == current) return
        val currentPrefs = _ui.value.prefsByLib[libraryId] ?: ChannelPrefs()
        val nextPrefs    = currentPrefs.copy(order = next)
        _ui.update { it.copy(prefsByLib = it.prefsByLib + (libraryId to nextPrefs)) }
        viewModelScope.launch {
            val server = _ui.value.serverUrl
            runCatching { store.setOrder(server, libraryId, next) }
                .onFailure { Log.w(TAG, "setOrder failed", it) }
        }
    }

    private fun orderedIds(libraryId: String): List<String> {
        val channels = _ui.value.channelsByLib[libraryId].orEmpty()
        val prefs    = _ui.value.prefsByLib[libraryId] ?: ChannelPrefs()
        val ordered  = ChannelOrderStore.applyPrefsForOrderView(channels, prefs)
        return ordered.map { it.id }
    }

    private suspend fun fetch(): Snapshot = coroutineScope {
        val serverUrl = tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()
        val libraries = repository.fetchLiveLibraries()
        val channelsByLib = libraries
            .map { lib -> async { lib.id to repository.fetchChannels(lib.id) } }
            .awaitAll()
            .associate { (id, list) ->
                id to list.sortedWith(
                    compareBy(
                        { ch -> ch.number.takeIf { n -> n > 0 } ?: Int.MAX_VALUE },
                        { ch -> ch.name.lowercase() },
                    ),
                )
            }
        val prefsByLib = store.snapshot()
            .mapNotNull { (key, prefs) ->
                // Keys are "serverUrl|libraryId"; only surface entries that
                // belong to the current server. Stray entries from previously
                // paired servers stay in the blob untouched.
                val parts = key.split('|', limit = 2)
                if (parts.size != 2 || parts[0] != serverUrl) null
                else parts[1] to prefs
            }
            .toMap()
        Snapshot(serverUrl, libraries, channelsByLib, prefsByLib)
    }

    private data class Snapshot(
        val serverUrl:     String,
        val libraries:     List<LiveLibrary>,
        val channelsByLib: Map<String, List<LiveChannel>>,
        val prefsByLib:    Map<String, ChannelPrefs>,
    )

    companion object {
        private const val TAG = "ChannelOrderVM"

        fun factory(
            repository: LiveTvRepository,
            store:      ChannelOrderStore,
            tokenStore: TokenStore,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChannelOrderViewModel(repository, store, tokenStore) as T
            }
        }
    }
}

@androidx.compose.runtime.Immutable
data class ChannelOrderUiState(
    val isLoading:          Boolean = false,
    val error:              String? = null,
    val serverUrl:          String = "",
    val libraries:          List<LiveLibrary> = emptyList(),
    val channelsByLib:      Map<String, List<LiveChannel>> = emptyMap(),
    val prefsByLib:         Map<String, ChannelPrefs> = emptyMap(),
    val selectedLibraryId:  String? = null,
) {
    /**
     * The full list of channels for the focused library, sorted by the
     * user's saved order (and showing hidden ones — hidden is a flag, not
     * removal, in this screen).
     */
    val displayChannels: List<LiveChannel> get() {
        val libId = selectedLibraryId ?: return emptyList()
        val channels = channelsByLib[libId].orEmpty()
        val prefs    = prefsByLib[libId] ?: ChannelPrefs()
        return ChannelOrderStore.applyPrefsForOrderView(channels, prefs)
    }

    fun isHidden(channelId: String): Boolean {
        val libId = selectedLibraryId ?: return false
        return (prefsByLib[libId]?.hidden ?: emptyList()).contains(channelId)
    }
}
