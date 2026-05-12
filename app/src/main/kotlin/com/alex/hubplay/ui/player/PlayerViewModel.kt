package com.alex.hubplay.ui.player

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.alex.hubplay.data.api.HubplayApi
import com.alex.hubplay.player.ClientCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
        Log.d(TAG, "resolve itemId=$itemId resume=$resumePosSec caps=$capabilities")

        // Fetch the title-bar metadata. Non-fatal: 404 here is expected
        // when the id is actually a TV channel (channels live in a
        // different table than items).
        val itemResult = runCatching { api.getItem(itemId).data }
        val item = itemResult.getOrNull()

        // Try the item path first. Channel ids 404 here; we use the
        // HTTP code below to decide whether to fall back to channel
        // playback or surface an error.
        val infoResult = runCatching { api.getStreamInfo(itemId, capabilities).data }
        val info       = infoResult.getOrNull()
        val infoErr    = infoResult.exceptionOrNull()
        val infoCode   = (infoErr as? HttpException)?.code()

        if (info != null) {
            // ── Item path (movies / episodes) ─────────────────────────────
            // The server returns only the playback method — the client
            // builds the URLs itself (mirrors web's usePlayback.ts):
            //   direct_play   → /stream/{id}/direct       (progressive, Range)
            //   direct_stream → /stream/{id}/master.m3u8  (HLS remux)
            //   transcode     → /stream/{id}/master.m3u8  (HLS transcoded)
            val isDirectPlay = info.method == "direct_play"
            val streamUrl    = if (isDirectPlay) "/api/v1/stream/$itemId/direct"
                               else              "/api/v1/stream/$itemId/master.m3u8"
            Log.d(TAG, "item path method=${info.method} container=${info.container} → $streamUrl")

            _ui.value = _ui.value.copy(
                title       = item?.title ?: "Reproduciendo…",
                startParams = PlayerStartParams(
                    streamUrl    = streamUrl,
                    resumePosSec = resumePosSec,
                    isHls        = !isDirectPlay,
                ),
                error       = null,
            )
            return
        }

        // /stream/{id}/info returned 404 → the id is most likely a
        // TV channel. Channels expose their stream via
        // `/api/v1/channels/{id}/stream` (HLS transmuxed by the server,
        // ground truth in iptv_channels.go::toChannelDTO). Same fallback
        // the web client effectively does — it never hits /stream/info
        // for channels because the LiveTV view holds the stream_url on
        // the channel object.
        if (infoCode == 404) {
            Log.d(TAG, "channel fallback for $itemId (item 404 + stream-info 404)")
            _ui.value = _ui.value.copy(
                title       = item?.title ?: "Canal en vivo",
                startParams = PlayerStartParams(
                    streamUrl    = "/api/v1/channels/$itemId/stream",
                    resumePosSec = 0L,           // live can't resume
                    isHls        = true,
                ),
                error       = null,
            )
            return
        }

        // Any other failure — surface the real reason rather than the
        // old "could not get the URL" black box.
        val msg = when {
            infoErr != null -> "Error pidiendo /stream/$itemId/info: ${describe(infoErr)}"
            else            -> "El server devolvió /stream/$itemId/info sin envelope `data`."
        }
        Log.e(TAG, "resolve failed: $msg", infoErr)
        _ui.value = _ui.value.copy(title = item?.title, error = msg)
    }

    private fun describe(t: Throwable): String = when (t) {
        is HttpException -> "HTTP ${t.code()} ${t.message()}"
        else             -> "${t.javaClass.simpleName}: ${t.message ?: "sin mensaje"}"
    }

    companion object {
        private const val TAG = "PlayerViewModel"

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
