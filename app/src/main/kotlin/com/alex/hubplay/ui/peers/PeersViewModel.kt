package com.alex.hubplay.ui.peers

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.ConnectedPeer
import com.alex.hubplay.data.FederationRepository
import com.alex.hubplay.data.PeerLibrary
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Backs the "Servidores conectados" landing — the unified grid of shared
 * libraries across every paired peer, grouped by content type (the web's
 * `PeersPage` doctrine: one flat grid, not nested peer→library nav).
 *
 * Best-effort throughout: a slow/offline peer never blocks the screen — the
 * backend fans out with a per-peer timeout, and we fall back to empty.
 */
class PeersViewModel(
    private val repository: FederationRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(PeersUiState(isLoading = true))
    val ui: StateFlow<PeersUiState> = _ui.asStateFlow()

    init { load() }

    fun load() {
        _ui.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val peers = async { repository.listPeers() }
                    val libs = async { repository.listAllLibraries() }
                    peers.await() to libs.await()
                }
            }.onSuccess { (peers, libs) ->
                _ui.update {
                    PeersUiState(
                        isLoading = false,
                        peers     = peers,
                        groups    = groupLibrariesByType(libs),
                    )
                }
            }.onFailure { err ->
                _ui.update {
                    it.copy(isLoading = false, error = friendlyError(err, "No se pudieron cargar los servidores"))
                }
            }
        }
    }

    companion object {
        fun factory(repository: FederationRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PeersViewModel(repository) as T
        }
    }
}

@Immutable
data class LibraryGroup(
    val contentType: String,
    val libraries:   List<PeerLibrary>,
)

@Immutable
data class PeersUiState(
    val isLoading: Boolean             = false,
    val peers:     List<ConnectedPeer> = emptyList(),
    val groups:    List<LibraryGroup>  = emptyList(),
    val error:     String?             = null,
)

/**
 * Bins libraries by content type in a deterministic display order
 * (movies → series → live TV → anything else, in insertion order). Pure +
 * JVM-testable — mirrors the web's `groupByContentType`.
 */
internal fun groupLibrariesByType(libs: List<PeerLibrary>): List<LibraryGroup> {
    val priority = listOf("movies", "series", "shows", "livetv")
    val buckets = LinkedHashMap<String, MutableList<PeerLibrary>>()
    for (lib in libs) {
        val key = lib.contentType.ifBlank { "other" }
        buckets.getOrPut(key) { mutableListOf() }.add(lib)
    }
    val ordered = mutableListOf<LibraryGroup>()
    for (k in priority) buckets.remove(k)?.let { ordered.add(LibraryGroup(k, it)) }
    for ((k, v) in buckets) ordered.add(LibraryGroup(k, v))
    return ordered
}
