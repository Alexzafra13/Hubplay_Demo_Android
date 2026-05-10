package com.alex.hubplay.player

import android.media.MediaCodecList
import android.media.MediaCodecList.REGULAR_CODECS

/**
 * Builds the `X-Hubplay-Client-Capabilities` header value the server uses
 * to decide direct-play vs. transcode.
 *
 * Format (mirrors what the web client sends): comma-separated
 * `codec/container` pairs, e.g. `h264/mp4,h264/ts,hevc/mp4`.
 *
 * We probe the device's MediaCodecList for hardware decoders (the
 * `REGULAR_CODECS` filter excludes software-only codecs ExoPlayer would
 * skip anyway) and emit the union with the standard HLS containers.
 *
 * This isn't perfect — some devices report codecs they can't actually
 * decode at high resolutions — but it's the right starting point. A
 * future enhancement is to filter further on max profile/level.
 */
object ClientCapabilities {

    fun probe(): String {
        val codecs = mutableSetOf<String>()
        val list = MediaCodecList(REGULAR_CODECS)
        for (info in list.codecInfos) {
            if (info.isEncoder) continue
            for (mime in info.supportedTypes) {
                shortName(mime)?.let { codecs += it }
            }
        }

        // Containers ExoPlayer + HLS handle out of the box on Android.
        val containers = listOf("mp4", "ts", "mkv", "m3u8")

        return codecs.flatMap { codec ->
            containers.map { container -> "$codec/$container" }
        }.joinToString(",")
    }

    private fun shortName(mime: String): String? = when (mime) {
        "video/avc"          -> "h264"
        "video/hevc"         -> "hevc"
        "video/x-vnd.on2.vp9", "video/vp9" -> "vp9"
        "video/av01"         -> "av1"
        "audio/mp4a-latm"    -> "aac"
        "audio/ac3"          -> "ac3"
        "audio/eac3"         -> "eac3"
        "audio/opus"         -> "opus"
        "audio/flac"         -> "flac"
        "audio/vorbis"       -> "vorbis"
        else                 -> null
    }
}
