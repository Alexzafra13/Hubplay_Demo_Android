package com.alex.hubplay.ui.player

import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer

/** Una calidad elegible: la variante `trackIndex` del grupo de vídeo. */
@Immutable
data class QualityOption(val trackIndex: Int, val height: Int, val bitrate: Int)

/**
 * Calidad del vídeo en HLS con variantes (el master del servidor anuncia
 * 1080p/720p/480p/360p sin escalar por encima de la fuente). "Auto" deja
 * que ExoPlayer elija por ancho de banda; una calidad fija la variante (en
 * el servidor es otro transcode y para la sesión de la anterior). En direct
 * play no hay variantes y la sección no sale.
 */
object QualityOptions {

    /** Variantes del grupo de vídeo, de mayor a menor; vacío si hay una o ninguna. */
    fun of(tracks: Tracks): List<QualityOption> {
        val group = videoGroup(tracks) ?: return emptyList()
        if (group.length < 2) return emptyList()
        return (0 until group.length)
            .filter { group.isTrackSupported(it) }
            .map { i -> group.getTrackFormat(i).let { QualityOption(i, it.height, it.bitrate) } }
            .sortedWith(compareByDescending<QualityOption> { it.height }.thenByDescending { it.bitrate })
    }

    /** Variante fijada ahora mismo; null = Auto. */
    fun selected(player: ExoPlayer): Int? {
        val group = videoGroup(player.currentTracks) ?: return null
        return player.trackSelectionParameters.overrides[group.mediaTrackGroup]?.trackIndices?.firstOrNull()
    }

    /** Fija la variante [option] o vuelve a Auto con null. */
    fun select(player: ExoPlayer, option: QualityOption?) {
        val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        val group = videoGroup(player.currentTracks)
        if (option != null && group != null) {
            builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        }
        player.trackSelectionParameters = builder.build()
    }

    private fun videoGroup(tracks: Tracks): Tracks.Group? =
        tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
}
