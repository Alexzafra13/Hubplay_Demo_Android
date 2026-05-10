package com.alex.hubplay.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.player.ClientCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Resolves the playback decision for an item: hits /items/{id} for the
 * title bar metadata, then /stream/{id}/info to learn whether the server
 * wants us to direct-play or pull HLS, and emits a [PlayerStartParams]
 * the screen passes to [HubplayPlayer].
 */
class PlayerViewModel(
    private val api: HubplayApi,
    private val itemId: String,
    private val resumePosSec: Long,
) : ViewModel() {

    private val _ui = MutableStateFlow(PlayerUiState(itemId = itemId))
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { resolve() }
    }

    private suspend fun resolve() {
        val capabilities = ClientCapabilities.probe()

        // Fire both calls; if either fails we surface a friendly error.
        val item = runCatching { api.getItem(itemId) }.getOrNull()?.data
        val info = runCatching { api.getStreamInfo(itemId, capabilities) }.getOrNull()?.data

        // Pick the right URL for the strategy:
        //  - direct_play  → original file with Range; ExoPlayer extractors handle it
        //  - direct_stream / transcode → HLS .m3u8 master playlist
        val isDirectPlay = info?.strategy == "direct_play"
        val streamUrl = if (isDirectPlay) info?.directUrl ?: info?.masterPlaylistUrl
                        else              info?.masterPlaylistUrl

        if (streamUrl.isNullOrBlank()) {
            _ui.value = _ui.value.copy(error = "No se pudo obtener la URL de reproducción.")
            return
        }

        _ui.value = _ui.value.copy(
            title         = item?.title ?: "Reproduciendo…",
            startParams   = PlayerStartParams(
                streamUrl     = streamUrl,
                resumePosSec  = resumePosSec,
                isHls         = !isDirectPlay,
            ),
            error         = null,
        )
    }

    companion object {
        fun factory(api: HubplayApi, itemId: String, resumePosSec: Long) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PlayerViewModel(api, itemId, resumePosSec) as T
                }
            }
    }
}

data class PlayerUiState(
    val itemId:      String,
    val title:       String? = null,
    val startParams: PlayerStartParams? = null,
    val error:       String? = null,
)

data class PlayerStartParams(
    val streamUrl:    String,
    val resumePosSec: Long,
    val isHls:        Boolean,
)
