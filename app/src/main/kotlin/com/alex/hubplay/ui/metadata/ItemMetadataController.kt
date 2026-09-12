package com.alex.hubplay.ui.metadata

import com.alex.hubplay.data.HomeRepository
import com.alex.hubplay.data.IdentifyCandidate
import com.alex.hubplay.ui.friendlyError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Diálogo "Identificar" abierto, con su búsqueda. */
@androidx.compose.runtime.Immutable
data class IdentifyState(
    val query:      String                  = "",
    val year:       Int?                    = null,
    val candidates: List<IdentifyCandidate> = emptyList(),
    val loading:    Boolean                 = false,
    val applying:   Boolean                 = false,
    val error:      String?                 = null,
)

/** Estado de las herramientas de metadatos de una ficha (Detalle / Series). */
@androidx.compose.runtime.Immutable
data class MetadataToolsState(
    /** El usuario puede editar metadatos (admin o `can_edit_metadata`). */
    val canEditMetadata: Boolean        = false,
    /** Diálogo "Identificar" abierto. `null` = cerrado. */
    val identify:        IdentifyState? = null,
    /** Aviso transitorio (Toast). La pantalla lo limpia al mostrarlo. */
    val notice:          String?        = null,
)

/**
 * Permiso de metadatos + "Actualizar metadatos" + "Identificar" contra
 * TMDb para un item. Lo comparten los ViewModels de Detalle y Series
 * (mismo diálogo, mismos endpoints); [onItemChanged] recarga la ficha
 * cuando el servidor ha reescrito los metadatos.
 */
class ItemMetadataController(
    private val scope:         CoroutineScope,
    private val repository:    HomeRepository,
    private val itemId:        String,
    private val onItemChanged: () -> Unit,
) {
    private val _state = MutableStateFlow(MetadataToolsState())
    val state: StateFlow<MetadataToolsState> = _state.asStateFlow()

    /** Falla en silencio: sin permiso confirmado, el lápiz no se enseña. */
    fun loadPermissions() {
        scope.launch {
            runCatching { repository.fetchCanEditMetadata() }
                .onSuccess { can -> _state.update { it.copy(canEditMetadata = can) } }
        }
    }

    fun refreshMetadata() {
        scope.launch {
            runCatching { repository.refreshItemMetadata(itemId) }
                .onSuccess {
                    _state.update { it.copy(notice = "Metadatos actualizados") }
                    onItemChanged()
                }
                .onFailure { err ->
                    _state.update { it.copy(notice = friendlyError(err, "No se pudieron actualizar los metadatos")) }
                }
        }
    }

    /** Abre el diálogo sembrado con el título y año actuales del item. */
    fun openIdentify(title: String, year: Int?) = searchCandidates(query = title, year = year)

    fun searchCandidates(query: String, year: Int?) {
        _state.update { it.copy(identify = IdentifyState(query = query, year = year, loading = true)) }
        scope.launch {
            runCatching { repository.fetchIdentifyCandidates(itemId, query, year) }
                .onSuccess { list -> updateIdentify { it.copy(candidates = list, loading = false) } }
                .onFailure { err ->
                    updateIdentify { it.copy(loading = false, error = friendlyError(err, "No se pudo buscar en TMDb")) }
                }
        }
    }

    fun applyIdentify(externalId: String) {
        updateIdentify { it.copy(applying = true, error = null) }
        scope.launch {
            runCatching { repository.identifyItem(itemId, externalId) }
                .onSuccess {
                    _state.update { it.copy(identify = null, notice = "Ficha actualizada") }
                    onItemChanged()
                }
                .onFailure { err ->
                    updateIdentify { it.copy(applying = false, error = friendlyError(err, "No se pudo aplicar")) }
                }
        }
    }

    fun closeIdentify() { _state.update { it.copy(identify = null) } }

    fun clearNotice() { _state.update { it.copy(notice = null) } }

    private fun updateIdentify(transform: (IdentifyState) -> IdentifyState) {
        _state.update { st -> st.identify?.let { st.copy(identify = transform(it)) } ?: st }
    }
}
