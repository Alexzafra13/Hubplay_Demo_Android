package com.alex.hubplay.ui.player

import androidx.compose.runtime.Immutable
import com.alex.hubplay.data.api.dto.MediaStreamDto
import com.alex.hubplay.player.SideloadedSubtitle
import java.util.Locale

/** Una pista de audio del fichero para el selector: `ordinal` es el `N` de `?audio=N`. */
@Immutable
data class AudioTrackOption(
    val ordinal:   Int,
    val label:     String,
    val isDefault: Boolean,
)

/**
 * Un subtítulo del fichero para el selector. `ordinal` es el `N` de
 * `?subtitle=N` (burn-in, cuenta solo subtítulos) y `streamIndex` el de
 * `/stream/{id}/subtitles/{streamIndex}` (WebVTT, índice absoluto del fichero).
 */
@Immutable
data class SubtitleTrackOption(
    val ordinal:     Int,
    val streamIndex: Int,
    val label:       String,
    val language:    String?,
    val isDefault:   Boolean,
    val isForced:    Boolean,
    /**
     * PGS/DVD/DVB/ASS: el servidor lo quema en el vídeo (fuerza transcode).
     * El resto lo extrae como WebVTT y ExoPlayer lo pinta encima.
     */
    val burnIn:      Boolean,
)

/**
 * Pistas del fichero (`media_streams` de la ficha) → opciones del selector
 * y URLs del backend. Puro, sin Android, para poder testearlo.
 */
object TrackOptions {

    /** Pistas de audio en el orden que ffmpeg entiende como `0:a:N`. */
    fun audio(streams: List<MediaStreamDto>): List<AudioTrackOption> =
        streams
            .filter { it.streamType == "audio" }
            .sortedBy { it.streamIndex }
            .mapIndexed { ordinal, s ->
                val channels = when (s.channels) {
                    null, 0              -> null
                    1                    -> "Mono"
                    2                    -> "Estéreo"
                    CHANNELS_SURROUND    -> "5.1"
                    CHANNELS_SURROUND_71 -> "7.1"
                    else                 -> "${s.channels} canales"
                }
                val name = languageName(s.language) ?: s.title?.takeIf { it.isNotBlank() } ?: "Audio ${ordinal + 1}"
                AudioTrackOption(
                    ordinal   = ordinal,
                    label     = listOfNotNull(name, channels, s.codec?.uppercase()).joinToString(" · "),
                    isDefault = s.isDefault,
                )
            }

    /**
     * Subtítulos en el orden que ffmpeg entiende como `0:s:N`. Las
     * etiquetas repetidas (tres "Español · SRT" sin título) se numeran
     * para que se distingan en el selector.
     */
    fun subtitles(streams: List<MediaStreamDto>): List<SubtitleTrackOption> {
        val tracks = streams
            .filter { it.streamType == "subtitle" }
            .sortedBy { it.streamIndex }
            .mapIndexed { ordinal, s ->
                val lang  = languageName(s.language)
                val title = s.title?.takeIf { it.isNotBlank() }
                val name  = lang ?: title ?: "Subtítulo ${ordinal + 1}"
                SubtitleTrackOption(
                    ordinal     = ordinal,
                    streamIndex = s.streamIndex,
                    label       = listOfNotNull(
                        name,
                        title.takeIf { lang != null },
                        "forzados".takeIf { s.isForced },
                        codecLabel(s.codec),
                    ).joinToString(" · "),
                    language    = s.language?.takeIf { it.isNotBlank() && it != "und" },
                    isDefault   = s.isDefault,
                    isForced    = s.isForced,
                    burnIn      = isBurnIn(s.codec),
                )
            }
        val repeats = tracks.groupingBy { it.label }.eachCount().filterValues { it > 1 }
        val seen = mutableMapOf<String, Int>()
        return tracks.map { track ->
            if (track.label !in repeats) return@map track
            val n = (seen[track.label] ?: 0) + 1
            seen[track.label] = n
            track.copy(label = "${track.label} ($n)")
        }
    }

    /**
     * Subtítulos de texto que ExoPlayer carga aparte del vídeo. Los de
     * burn-in no van: los pinta el servidor dentro de los frames.
     */
    fun sideloaded(itemId: String, tracks: List<SubtitleTrackOption>): List<SideloadedSubtitle> =
        tracks
            .filter { !it.burnIn }
            .map {
                SideloadedSubtitle(
                    id       = sideloadId(it.streamIndex),
                    url      = "/api/v1/stream/$itemId/subtitles/${it.streamIndex}",
                    language = it.language,
                    label    = it.label,
                )
            }

    /** Id con el que ExoPlayer anuncia la pista de texto cargada aparte (`Format.id`). */
    fun sideloadId(streamIndex: Int): String = "hubplay-sub-$streamIndex"

    /**
     * Master HLS con la pista de audio (`?audio=N`) y el subtítulo quemado
     * (`?subtitle=N`) elegidos; -1 = ninguno. Mismo orden de parámetros que
     * usa el backend en las URLs de las variantes.
     */
    fun masterUrl(itemId: String, audio: Int, burnSub: Int): String {
        val params = listOfNotNull(
            audio.takeIf { it >= 0 }?.let { "audio=$it" },
            burnSub.takeIf { it >= 0 }?.let { "subtitle=$it" },
        )
        val query = if (params.isEmpty()) "" else "?" + params.joinToString("&")
        return "/api/v1/stream/$itemId/master.m3u8$query"
    }

    /** Misma lista que `IsBurnableSubtitleCodec` del backend. */
    fun isBurnIn(codec: String?): Boolean = when (codec?.trim()?.lowercase()) {
        "hdmv_pgs_subtitle", "pgs",
        "dvd_subtitle", "dvdsub",
        "dvb_subtitle", "dvbsub",
        "xsub",
        "ass", "ssa" -> true
        else -> false
    }

    private fun codecLabel(codec: String?): String? = when (codec?.trim()?.lowercase()) {
        null, ""                    -> null
        "subrip", "srt"             -> "SRT"
        "hdmv_pgs_subtitle", "pgs"  -> "PGS"
        "dvd_subtitle", "dvdsub"    -> "DVD"
        "dvb_subtitle", "dvbsub"    -> "DVB"
        "mov_text"                  -> "MP4"
        "webvtt"                    -> "VTT"
        else                        -> codec.uppercase()
    }

    /**
     * "es" o "spa" → "Español"; null si no hay idioma o es `und`. ffprobe
     * etiqueta con ISO 639-2 (tres letras, a menudo el código bibliográfico:
     * `ger`, `fre`, `rum`…) que ni Java ni ICU traducen, así que se pasa
     * antes por [ISO_639_2_TO_1]. Sin nombre conocido, el tag en mayúsculas.
     */
    fun languageName(tag: String?): String? {
        if (tag.isNullOrBlank() || tag == "und") return null
        val normalized = ISO_639_2_TO_1[tag.lowercase()] ?: tag
        val name = runCatching { Locale.forLanguageTag(normalized).getDisplayLanguage(Locale("es")) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() && !it.equals(normalized, ignoreCase = true) }
        return name?.replaceFirstChar { it.uppercase() } ?: tag.uppercase()
    }

    private const val CHANNELS_SURROUND = 6
    private const val CHANNELS_SURROUND_71 = 8

    /** Códigos ISO 639-2 (bibliográficos y terminológicos) → ISO 639-1 de los idiomas habituales. */
    private val ISO_639_2_TO_1 = mapOf(
        "spa" to "es", "eng" to "en", "fre" to "fr", "fra" to "fr", "ger" to "de", "deu" to "de",
        "ita" to "it", "por" to "pt", "cat" to "ca", "eus" to "eu", "baq" to "eu", "glg" to "gl",
        "rum" to "ro", "ron" to "ro", "dut" to "nl", "nld" to "nl", "cze" to "cs", "ces" to "cs",
        "dan" to "da", "gre" to "el", "ell" to "el", "fin" to "fi", "hun" to "hu", "nor" to "no",
        "nob" to "nb", "pol" to "pl", "slo" to "sk", "slk" to "sk", "swe" to "sv", "tur" to "tr",
        "rus" to "ru", "ukr" to "uk", "jpn" to "ja", "kor" to "ko", "chi" to "zh", "zho" to "zh",
        "ara" to "ar", "heb" to "he", "hin" to "hi", "tha" to "th", "vie" to "vi", "ind" to "id",
        "may" to "ms", "msa" to "ms", "bul" to "bg", "hrv" to "hr", "srp" to "sr", "slv" to "sl",
        "lit" to "lt", "lav" to "lv", "est" to "et", "ice" to "is", "isl" to "is", "per" to "fa",
        "fas" to "fa", "tgl" to "tl", "fil" to "tl",
    )
}
