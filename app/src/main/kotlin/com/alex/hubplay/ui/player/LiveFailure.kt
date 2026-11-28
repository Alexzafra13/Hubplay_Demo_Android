package com.alex.hubplay.ui.player

import androidx.media3.common.PlaybackException

/**
 * Traduce un fallo de ExoPlayer a los grupos que entiende
 * `POST /channels/{id}/playback-failure` (manifest / network / media /
 * timeout / unknown). Con ese aviso el servidor suma el fallo al contador
 * de salud del canal (el mismo que alimenta su sonda): los canales que no
 * arrancan en la TV acaban marcados y el admin los ve en el panel.
 */
object LiveFailure {

    fun kindOf(errorCode: Int?): String = when (errorCode) {
        null -> KIND_UNKNOWN
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_TIMEOUT -> KIND_TIMEOUT
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> KIND_MANIFEST
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> KIND_MEDIA
        else -> when (errorCode) {
            in IO_RANGE       -> KIND_NETWORK
            in DECODING_RANGE -> KIND_MEDIA
            in AUDIO_RANGE    -> KIND_MEDIA
            else              -> KIND_UNKNOWN
        }
    }

    const val KIND_MANIFEST = "manifest"
    const val KIND_NETWORK = "network"
    const val KIND_MEDIA = "media"
    const val KIND_TIMEOUT = "timeout"
    const val KIND_UNKNOWN = "unknown"

    /** Familias de `PlaybackException.errorCode` (2xxx red, 4xxx decodificación, 5xxx audio). */
    private val IO_RANGE = 2000..2999
    private val DECODING_RANGE = 4000..4999
    private val AUDIO_RANGE = 5000..5999
}
