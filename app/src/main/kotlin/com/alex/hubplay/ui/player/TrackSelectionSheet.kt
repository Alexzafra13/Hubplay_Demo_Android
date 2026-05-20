package com.alex.hubplay.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.alex.hubplay.R

/**
 * Bottom sheet that lets the user pick the active audio + subtitle
 * track. Driven entirely by what the player currently advertises in
 * [ExoPlayer.currentTracks] — HLS variants surface audio renditions and
 * EXT-X-MEDIA subtitle groups here.
 *
 * Off-state is included for subtitles (since most users will want to
 * toggle them off, not just switch language). Audio always picks ONE
 * track; the "Disable" option is hidden because muting via track
 * selection mid-stream usually breaks decoding pipelines.
 *
 * Why a Material3 ModalBottomSheet rather than a Dialog? On TV / large
 * screens the sheet feels more cinematic and slides in from the edge
 * without obscuring the centre of the video. Compose handles the
 * focus traversal automatically.
 */
@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackSelectionSheet(
    player:    ExoPlayer,
    onDismiss: () -> Unit,
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The Tracks snapshot is captured into Compose state so changing
    // tracks via the sheet re-reads the active selection and re-renders.
    var tracks by remember { mutableStateOf(player.currentTracks) }

    val audioGroups    = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = state,
        containerColor   = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(stringResource(R.string.player_section_audio))
            if (audioGroups.isEmpty()) {
                EmptyRow(stringResource(R.string.player_no_extra_audio))
            } else {
                audioGroups.forEach { group ->
                    repeat(group.length) { idx ->
                        val format = group.getTrackFormat(idx)
                        val label  = formatAudioLabel(format)
                        TrackRow(
                            label    = label,
                            selected = group.isTrackSelected(idx),
                            onClick  = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .setOverrideForType(
                                        TrackSelectionOverride(group.mediaTrackGroup, idx),
                                    )
                                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                                    .build()
                                tracks = player.currentTracks
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(R.string.player_section_subtitles))
            TrackRow(
                label    = stringResource(R.string.player_subtitles_disabled),
                selected = subtitleGroups.none { group ->
                    (0 until group.length).any { group.isTrackSelected(it) }
                },
                onClick  = {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                    tracks = player.currentTracks
                },
            )
            subtitleGroups.forEach { group ->
                repeat(group.length) { idx ->
                    val format = group.getTrackFormat(idx)
                    TrackRow(
                        label    = formatSubtitleLabel(format),
                        selected = group.isTrackSelected(idx),
                        onClick  = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(
                                    TrackSelectionOverride(group.mediaTrackGroup, idx),
                                )
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .build()
                            tracks = player.currentTracks
                        },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Format-to-label helpers ─────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
private fun formatAudioLabel(format: androidx.media3.common.Format): String {
    val lang     = languageDisplay(format.language)
    val channels = when (format.channelCount) {
        1    -> stringResource(R.string.player_audio_mono)
        2    -> stringResource(R.string.player_audio_stereo)
        6    -> "5.1"
        8    -> "7.1"
        else -> if (format.channelCount > 0) stringResource(R.string.player_audio_channels_format, format.channelCount) else null
    }
    val codec = format.codecs?.substringBefore('.')?.uppercase()
    val defaultAudio = stringResource(R.string.player_audio_default_label)
    return listOfNotNull(lang ?: defaultAudio, channels, codec).joinToString(" · ")
}

@OptIn(UnstableApi::class)
@androidx.compose.runtime.Composable
private fun formatSubtitleLabel(format: androidx.media3.common.Format): String {
    val lang  = languageDisplay(format.language) ?: format.label ?: stringResource(R.string.player_subtitles_default_label)
    val flags = mutableListOf<String>()
    val selFlags = format.selectionFlags
    if (selFlags and C.SELECTION_FLAG_FORCED  != 0) flags += stringResource(R.string.player_subtitle_flag_forced)
    if (selFlags and C.SELECTION_FLAG_DEFAULT != 0) flags += stringResource(R.string.player_subtitle_flag_default)
    return if (flags.isEmpty()) lang else "$lang (${flags.joinToString(", ")})"
}

private fun languageDisplay(tag: String?): String? {
    if (tag.isNullOrBlank() || tag == "und") return null
    return runCatching { java.util.Locale.forLanguageTag(tag).getDisplayLanguage(java.util.Locale("es")) }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?.replaceFirstChar { it.uppercase() }
        ?: tag.uppercase()
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text       = text,
        style      = MaterialTheme.typography.titleSmall,
        color      = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun EmptyRow(text: String) {
    Text(
        text     = text,
        style    = MaterialTheme.typography.bodyMedium,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun TrackRow(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Text(
                text       = label,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        trailingContent = {
            if (selected) {
                Icon(
                    imageVector        = Icons.Outlined.Check,
                    contentDescription = stringResource(R.string.cd_selected),
                    tint               = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors   = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
    )
}
