package com.alex.hubplay.ui.livetv

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.ChannelOrderStore
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.data.LiveLibrary
import com.alex.hubplay.data.LiveTvRepository
import com.alex.hubplay.data.TokenStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the Channel Order screen.
 *
 * Source of truth: the **backend**. We load the personalisation view
 * (`GET /libraries/{id}/channels?include_hidden=true`) which already
 * returns channels in the user's saved order with the `hidden` flag set
 * per row. Edits push to the backend via:
 *
 *   - `PUT /me/iptv/channels/{channelId}/visibility` for hide/show (small body)
 *   - `PUT /me/iptv/channels/order` for reorder (full overlay replace, debounced)
 *   - `DELETE /me/iptv/channels/order` for "Restablecer"
 *
 * Sync to other devices: the list endpoint already applies the overlay
 * server-side so a fresh nav to Live TV is enough. For live propagation
 * the backend also publishes `user.channel.order.updated` on /me/events
 * after each personalisation write — `LiveTvViewModel` listens and
 * refetches without waiting for the user to renavigate.
 *
 * UI semantics:
 *  - Edits are **optimistic**: local state flips immediately, then the
 *    backend call runs. On HTTP failure we roll back so the UI matches
 *    what the server actually persisted. Errors land in [ChannelOrderUiState.error]
 *    so the screen can surface a banner instead of going silently wrong.
 *  - Reorder writes are **debounced** (300ms). Holding ↓ on a row to
 *    move it down 20 places fires one PUT, not twenty.
 *  - After each successful backend write we touch [ChannelOrderStore]
 *    to signal `LiveTvViewModel` to refetch its inventory.
 */
class ChannelOrderViewModel(
    private val repository: LiveTvRepository,
    private val store:      ChannelOrderStore,
    private val tokenStore: TokenStore,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChannelOrderUiState(isLoading = true))
    val ui: StateFlow<ChannelOrderUiState> = _ui.asStateFlow()

    /** Coalesces rapid reorder taps into one PUT — see [scheduleOrderPersist]. */
    private val pendingPersists = mutableMapOf<String, Job>()

    /** Auto-commit timer for the type-a-number move buffer. */
    private var pendingCommitJob: Job? = null

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
        mutateOrder(libraryId) { channels ->
            if (index <= 0 || index >= channels.size) channels
            else channels.toMutableList().apply { add(index - 1, removeAt(index)) }
        }
    }

    /** Move the channel at [index] down by one position (no-op when already at bottom). */
    fun moveDown(libraryId: String, index: Int) {
        mutateOrder(libraryId) { channels ->
            if (index < 0 || index >= channels.lastIndex) channels
            else channels.toMutableList().apply { add(index + 1, removeAt(index)) }
        }
    }

    /**
     * TV-remote style "type a number to jump" entry. Each digit the user
     * presses while a row is focused accumulates in
     * [ChannelOrderUiState.pendingMove]. After [PENDING_TIMEOUT_MS] of
     * idle (or an explicit [commitPendingMove]), the focused channel
     * moves to that 1-based position. Buffer caps at [MAX_DIGITS] to
     * keep absurdly long sequences from latching forever.
     */
    fun appendMoveDigit(channelId: String, digit: Char) {
        if (!digit.isDigit()) return
        val current = _ui.value.pendingMove
        val nextDigits = (if (current?.channelId == channelId) current.digits else "") + digit
        val capped = nextDigits.take(MAX_DIGITS)
        _ui.update { it.copy(pendingMove = PendingMove(channelId, capped)) }
        // Restart the auto-commit timer on every keystroke so a fast
        // typer composing "127" doesn't fire mid-input.
        pendingCommitJob?.cancel()
        pendingCommitJob = viewModelScope.launch {
            delay(PENDING_TIMEOUT_MS)
            commitPendingMoveInternal()
        }
    }

    /** Delete the last digit of the in-flight move buffer (Backspace from the row). */
    fun popMoveDigit() {
        val current = _ui.value.pendingMove ?: return
        val nextDigits = current.digits.dropLast(1)
        if (nextDigits.isEmpty()) {
            cancelPendingMove()
            return
        }
        _ui.update { it.copy(pendingMove = current.copy(digits = nextDigits)) }
        pendingCommitJob?.cancel()
        pendingCommitJob = viewModelScope.launch {
            delay(PENDING_TIMEOUT_MS)
            commitPendingMoveInternal()
        }
    }

    /** Fired by the screen on Enter / OK — flush the pending move immediately. */
    fun commitPendingMove() {
        pendingCommitJob?.cancel()
        viewModelScope.launch { commitPendingMoveInternal() }
    }

    fun cancelPendingMove() {
        pendingCommitJob?.cancel()
        pendingCommitJob = null
        _ui.update { it.copy(pendingMove = null) }
    }

    private fun commitPendingMoveInternal() {
        val pending  = _ui.value.pendingMove ?: return
        val targetPosition = pending.digits.toIntOrNull()
        _ui.update { it.copy(pendingMove = null) }
        if (targetPosition == null || targetPosition <= 0) return
        val libraryId = _ui.value.selectedLibraryId ?: return
        val channels  = _ui.value.channelsByLib[libraryId] ?: return
        val srcIndex  = channels.indexOfFirst { it.id == pending.channelId }
        if (srcIndex < 0) return
        // Clamp the requested position to the list bounds so typing
        // "9999" simply moves the channel to the bottom.
        val targetIdx = (targetPosition - 1).coerceIn(0, channels.lastIndex)
        if (srcIndex == targetIdx) return
        val next = channels.toMutableList().apply { add(targetIdx, removeAt(srcIndex)) }
        _ui.update { it.copy(channelsByLib = it.channelsByLib + (libraryId to next)) }
        scheduleOrderPersist(libraryId)
    }

    fun toggleHidden(libraryId: String, channelId: String) {
        val channels   = _ui.value.channelsByLib[libraryId] ?: return
        val target     = channels.firstOrNull { it.id == channelId } ?: return
        val nextHidden = !target.hidden
        // Optimistic flip.
        val updatedList = channels.map { ch ->
            if (ch.id == channelId) ch.copy(hidden = nextHidden) else ch
        }
        _ui.update { it.copy(channelsByLib = it.channelsByLib + (libraryId to updatedList)) }
        viewModelScope.launch {
            runCatching { repository.setChannelVisibility(channelId, nextHidden) }
                .onSuccess { signalLiveTvRefresh(libraryId) }
                .onFailure { err ->
                    Log.w(TAG, "setChannelVisibility($channelId, $nextHidden) failed", err)
                    // Roll back the optimistic flip.
                    _ui.update {
                        val current = it.channelsByLib[libraryId].orEmpty()
                        val reverted = current.map { ch ->
                            if (ch.id == channelId) ch.copy(hidden = !nextHidden) else ch
                        }
                        it.copy(
                            channelsByLib = it.channelsByLib + (libraryId to reverted),
                            error         = err.message ?: "No se pudo cambiar la visibilidad del canal",
                        )
                    }
                }
        }
    }

    /** Reset both order and hidden for [libraryId] back to backend defaults. */
    fun resetLibrary(libraryId: String) {
        viewModelScope.launch {
            runCatching { repository.resetChannelOrder() }
                .onSuccess {
                    signalLiveTvRefresh(libraryId)
                    // Refetch so the screen mirrors the post-reset server state
                    // exactly (admin defaults). Cheaper than rebuilding it locally.
                    load()
                }
                .onFailure { err ->
                    Log.w(TAG, "resetChannelOrder failed", err)
                    _ui.update {
                        it.copy(error = err.message ?: "No se pudo restablecer el orden")
                    }
                }
        }
    }

    private fun mutateOrder(
        libraryId: String,
        transform: (List<LiveChannel>) -> List<LiveChannel>,
    ) {
        val current = _ui.value.channelsByLib[libraryId] ?: return
        val next    = transform(current)
        if (next === current || next == current) return
        _ui.update { it.copy(channelsByLib = it.channelsByLib + (libraryId to next)) }
        scheduleOrderPersist(libraryId)
    }

    /**
     * Coalesce rapid edits within [ORDER_DEBOUNCE_MS] of each other into
     * one backend write. We cancel the in-flight job for the library and
     * start a new one each time; only the last one runs to completion
     * and PUTs the latest snapshot.
     */
    private fun scheduleOrderPersist(libraryId: String) {
        pendingPersists.remove(libraryId)?.cancel()
        pendingPersists[libraryId] = viewModelScope.launch {
            delay(ORDER_DEBOUNCE_MS)
            val current = _ui.value.channelsByLib[libraryId].orEmpty()
            val ordered = current.map { it.id }
            val hidden  = current.filter { it.hidden }.map { it.id }
            runCatching { repository.replaceChannelOrder(ordered, hidden) }
                .onSuccess { signalLiveTvRefresh(libraryId) }
                .onFailure { err ->
                    Log.w(TAG, "replaceChannelOrder failed", err)
                    _ui.update {
                        it.copy(error = err.message ?: "No se pudo guardar el orden")
                    }
                }
            pendingPersists.remove(libraryId)
        }
    }

    /**
     * Bump the [ChannelOrderStore] so the main LiveTv VM observes a
     * change and refetches. We persist an empty marker entry keyed by
     * the library — the contents don't matter, only the flow emission.
     */
    private suspend fun signalLiveTvRefresh(libraryId: String) {
        val server = _ui.value.serverUrl
        runCatching { store.setOrder(server, libraryId, _ui.value.channelsByLib[libraryId].orEmpty().map { it.id }) }
            .onFailure { Log.w(TAG, "store signal write failed", it) }
    }

    private suspend fun fetch(): Snapshot = coroutineScope {
        val serverUrl = tokenStore.snapshot().serverUrl?.trimEnd('/').orEmpty()
        val libraries = repository.fetchLiveLibraries()
        val channelsByLib = libraries
            .map { lib -> async { lib.id to repository.fetchChannelsForPersonalisation(lib.id) } }
            .awaitAll()
            .toMap()
        Snapshot(serverUrl, libraries, channelsByLib)
    }

    fun clearError() {
        _ui.update { it.copy(error = null) }
    }

    private data class Snapshot(
        val serverUrl:     String,
        val libraries:     List<LiveLibrary>,
        val channelsByLib: Map<String, List<LiveChannel>>,
    )

    companion object {
        private const val TAG                = "ChannelOrderVM"
        private const val ORDER_DEBOUNCE_MS  = 300L
        private const val PENDING_TIMEOUT_MS = 1200L
        private const val MAX_DIGITS         = 4

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
data class PendingMove(
    val channelId: String,
    /** 1..MAX_DIGITS digits typed so far; numeric only. */
    val digits:    String,
)

@androidx.compose.runtime.Immutable
data class ChannelOrderUiState(
    val isLoading:          Boolean = false,
    val error:              String? = null,
    val serverUrl:          String = "",
    val libraries:          List<LiveLibrary> = emptyList(),
    val channelsByLib:      Map<String, List<LiveChannel>> = emptyMap(),
    val selectedLibraryId:  String? = null,
    /** Active type-a-number-to-move buffer, scoped to a single channel. */
    val pendingMove:        PendingMove? = null,
) {
    /**
     * Channels for the focused library, exactly as the backend returned
     * them (ordered by the user's overlay; hidden channels included so
     * the user can unhide them).
     */
    val displayChannels: List<LiveChannel> get() {
        val libId = selectedLibraryId ?: return emptyList()
        return channelsByLib[libId].orEmpty()
    }
}
