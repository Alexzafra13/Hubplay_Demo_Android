package com.alex.hubplay.ui.player

import androidx.media3.common.PlaybackException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Fallos de ExoPlayer → grupos de `POST /channels/{id}/playback-failure`. */
class LiveFailureTest {

    @Test
    fun `network, timeout, manifest and decoder errors land in their bucket`() {
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS)).isEqualTo("network")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)).isEqualTo("network")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)).isEqualTo("timeout")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED)).isEqualTo("manifest")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED)).isEqualTo("media")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)).isEqualTo("media")
    }

    @Test
    fun `anything else is unknown, which the server always accepts`() {
        assertThat(LiveFailure.kindOf(null)).isEqualTo("unknown")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_UNSPECIFIED)).isEqualTo("unknown")
        assertThat(LiveFailure.kindOf(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED)).isEqualTo("unknown")
    }
}
