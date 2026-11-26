package com.alex.hubplay.ui.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.OnAccent
import java.util.Locale

/**
 * Menú Opciones del reproductor (como el de Jellyfin): velocidad, encuadre
 * de la imagen, capítulos e información de reproducción. Velocidad y
 * capítulos actúan directamente sobre ExoPlayer; el encuadre lo aplica la
 * pantalla en el PlayerView.
 */
@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerOptionsSheet(
    player:    ExoPlayer,
    state:     PlayerOptionsState,
    onFit:     (VideoFit) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var speed by remember { mutableFloatStateOf(player.playbackParameters.speed) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = sheet,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(stringResource(R.string.player_section_speed))
            ChipRow {
                PlayerExtras.speeds.forEach { value ->
                    OptionChip(label = speedLabel(value), selected = value == speed) {
                        player.setPlaybackSpeed(value)
                        speed = value
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader(stringResource(R.string.player_section_fit))
            ChipRow {
                VideoFit.entries.forEach { fit ->
                    OptionChip(label = stringResource(fit.labelRes), selected = fit == state.fit) { onFit(fit) }
                }
            }

            if (state.chapters.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                SectionHeader(stringResource(R.string.player_section_chapters))
                val current = PlayerExtras.currentChapterIndex(state.chapters, player.currentPosition)
                state.chapters.forEachIndexed { index, chapter ->
                    val name = chapter.title ?: stringResource(R.string.player_chapter_number, index + 1)
                    TrackRow(label = "${formatTime(chapter.startMs)}   $name", selected = index == current) {
                        player.seekTo(chapter.startMs)
                        onDismiss()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionHeader(stringResource(R.string.player_section_info))
            PlaybackInfo(player = player, method = state.method)
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Fila de opciones exclusivas (velocidad, encuadre): caben todas a la vista. */
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(vertical = 4.dp)) {
        content()
    }
}

/** Opción de la fila: rellena con el acento si está elegida, borde blanco con foco. */
@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(CHIP_RADIUS)
    Text(
        text       = label,
        style      = MaterialTheme.typography.bodyMedium,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color      = if (selected) OnAccent else MaterialTheme.colorScheme.onSurface,
        modifier   = Modifier
            .clip(shape)
            .background(if (selected) Accent else MaterialTheme.colorScheme.surfaceVariant)
            .border(CHIP_BORDER, if (focused) Color.White else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private val CHIP_RADIUS = 20.dp
private val CHIP_BORDER = 2.dp

@Composable
private fun speedLabel(value: Float): String =
    if (value == 1f) stringResource(R.string.player_speed_normal) else "${formatSpeed(value)}×"

private fun formatSpeed(value: Float): String =
    java.text.NumberFormat.getInstance().apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }.format(value)

/** Modo (directo / remux / transcode) y formatos que de verdad decodifica ExoPlayer. */
@OptIn(UnstableApi::class)
@Composable
private fun PlaybackInfo(player: ExoPlayer, method: String?) {
    val methodRes = when (method) {
        "direct_play"   -> R.string.player_method_direct_play
        "direct_stream" -> R.string.player_method_direct_stream
        "transcode"     -> R.string.player_method_transcode
        else            -> null
    }
    methodRes?.let { InfoLine(stringResource(R.string.player_info_method), stringResource(it)) }
    player.videoFormat?.let { f ->
        val parts = listOfNotNull(
            f.takeIf { it.width > 0 && it.height > 0 }?.let { "${it.width}×${it.height}" },
            codecName(f.sampleMimeType ?: f.codecs),
            f.frameRate.takeIf { it > 0 }?.let { String.format(Locale.getDefault(), "%.0f fps", it) },
        )
        if (parts.isNotEmpty()) InfoLine(stringResource(R.string.player_info_video), parts.joinToString(" · "))
    }
    player.audioFormat?.let { f ->
        val parts = listOfNotNull(
            codecName(f.sampleMimeType ?: f.codecs),
            f.channelCount.takeIf { it > 0 }?.let(::channelsLabel),
            f.language?.let { TrackOptions.languageName(it) ?: it },
        )
        if (parts.isNotEmpty()) InfoLine(stringResource(R.string.player_info_audio), parts.joinToString(" · "))
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodyMedium,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(INFO_LABEL_WIDTH),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

private val INFO_LABEL_WIDTH = 120.dp
private const val SURROUND_51 = 6
private const val SURROUND_71 = 8

private fun codecName(mime: String?): String? = when {
    mime == null                -> null
    mime.contains("avc")        -> "H.264"
    mime.contains("hevc")       -> "HEVC"
    mime.contains("av01")       -> "AV1"
    mime.contains("vp9")        -> "VP9"
    mime.contains("mp4a")       -> "AAC"
    mime.contains("eac3")       -> "E-AC3"
    mime.contains("ac3")        -> "AC3"
    mime.contains("opus")       -> "Opus"
    mime.contains("dts")        -> "DTS"
    else                        -> mime.substringAfter('/').uppercase(Locale.ROOT)
}

private fun channelsLabel(count: Int): String = when (count) {
    1    -> "Mono"
    2    -> "Estéreo"
    SURROUND_51 -> "5.1"
    SURROUND_71 -> "7.1"
    else -> "$count ch"
}
