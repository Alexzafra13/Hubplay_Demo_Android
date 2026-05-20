package com.alex.hubplay.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R
import com.alex.hubplay.data.LiveChannel
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.OnAccent
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary

/**
 * Channel reorder + hide screen.
 *
 * Layout: top bar (back + title) → optional library tab row → vertical
 * list of channels with action buttons per row. Each row exposes three
 * focusable actions: move up, move down, toggle hidden. D-pad ↑/↓
 * traverses rows; ←/→ traverses the action buttons inside a row.
 *
 * UX notes:
 *  - The "Move up" / "Move down" buttons are deliberately explicit (vs.
 *    a long-press / "OK enters move mode" affordance) because they're
 *    self-discoverable on first use, just as usable with a mouse, and
 *    a Compose-on-TV "modal move mode" needs more focus management
 *    than its UX value warrants for a settings screen.
 *  - Hidden channels stay visible here (greyed out + Visibility-off icon)
 *    so the user can unhide them. The main Live TV screen excludes them.
 *  - Each edit writes to [ChannelOrderStore]; [LiveTvViewModel] observes
 *    that flow and re-applies prefs to its in-memory channel list, so
 *    the user sees the new order/hidden state the moment they nav back.
 *    No explicit "save" button; back-press is the exit.
 */
@Composable
fun ChannelOrderScreen(
    viewModel: ChannelOrderViewModel,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(
                title  = stringResource(R.string.channel_order_title),
                onBack = onBack,
                onReset = ui.selectedLibraryId?.let { id -> { viewModel.resetLibrary(id) } },
            )

            when {
                ui.isLoading && ui.libraries.isEmpty() -> CenteredSpinner()
                ui.error != null && ui.libraries.isEmpty() ->
                    ErrorState(message = ui.error!!, onRetry = viewModel::load)
                ui.libraries.isEmpty() ->
                    EmptyState(
                        title    = stringResource(R.string.livetv_empty_title),
                        subtitle = stringResource(R.string.livetv_empty_subtitle),
                    )
                else -> Content(
                    ui              = ui,
                    onSelectLibrary = viewModel::selectLibrary,
                    onMoveUp        = { libId, index -> viewModel.moveUp(libId, index) },
                    onMoveDown      = { libId, index -> viewModel.moveDown(libId, index) },
                    onToggleHidden  = { libId, channelId -> viewModel.toggleHidden(libId, channelId) },
                )
            }
        }
    }
}

@Composable
private fun Content(
    ui:              ChannelOrderUiState,
    onSelectLibrary: (String) -> Unit,
    onMoveUp:        (String, Int) -> Unit,
    onMoveDown:      (String, Int) -> Unit,
    onToggleHidden:  (String, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (ui.libraries.size > 1) {
            LibraryTabs(
                libraries       = ui.libraries.map { it.id to it.name.ifBlank { it.id } },
                selectedId      = ui.selectedLibraryId,
                onSelect        = onSelectLibrary,
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val libId    = ui.selectedLibraryId
            val channels = ui.displayChannels
            if (libId == null || channels.isEmpty()) {
                EmptyState(
                    title    = stringResource(R.string.channel_order_empty_title),
                    subtitle = stringResource(R.string.channel_order_empty_subtitle),
                )
            } else {
                LazyColumn(
                    modifier            = Modifier.fillMaxSize(),
                    contentPadding      = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "header_hint") {
                        Text(
                            text  = stringResource(R.string.channel_order_help),
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    itemsIndexed(channels, key = { _, ch -> ch.id }) { index, channel ->
                        ChannelOrderRow(
                            channel    = channel,
                            position   = index + 1,
                            isFirst    = index == 0,
                            isLast     = index == channels.lastIndex,
                            isHidden   = ui.isHidden(channel.id),
                            onMoveUp   = { onMoveUp(libId, index) },
                            onMoveDown = { onMoveDown(libId, index) },
                            onToggleHidden = { onToggleHidden(libId, channel.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    title:   String,
    onBack:  () -> Unit,
    onReset: (() -> Unit)?,
) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint               = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text       = title,
            style      = MaterialTheme.typography.headlineSmall,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier   = Modifier.weight(1f),
        )
        if (onReset != null) {
            TextButton(onClick = onReset) {
                Icon(
                    imageVector        = Icons.Outlined.Refresh,
                    contentDescription = null,
                    modifier           = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.channel_order_reset))
            }
        }
    }
}

@Composable
private fun LibraryTabs(
    libraries:  List<Pair<String, String>>,
    selectedId: String?,
    onSelect:   (String) -> Unit,
) {
    // Horizontal scroll handles the rare case where the user has many
    // IPTV libraries; LazyRow would be overkill for a handful of chips.
    val scroll = rememberScrollState()
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        libraries.forEach { (id, name) ->
            LibraryChip(
                label    = name,
                selected = id == selectedId,
                onClick  = { onSelect(id) },
            )
        }
    }
}

@Composable
private fun LibraryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bg = if (selected) Accent else Color.Transparent
    val fg = when {
        selected -> OnAccent
        focused  -> TextPrimary
        else     -> TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(
                if (focused && !selected) Modifier.border(1.5.dp, Accent, RoundedCornerShape(20.dp))
                else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = label,
            color      = fg,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ChannelOrderRow(
    channel:        LiveChannel,
    position:       Int,
    isFirst:        Boolean,
    isLast:         Boolean,
    isHidden:       Boolean,
    onMoveUp:       () -> Unit,
    onMoveDown:     () -> Unit,
    onToggleHidden: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.surface
    val nameColor = if (isHidden) TextSecondary else TextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text       = "$position",
            color      = TextSecondary,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier   = Modifier.width(36.dp),
        )
        // Pre-resolve composable string lookups before composing the
        // subtitle — @Composable can't be called from inside buildString.
        val hiddenBadge = stringResource(R.string.channel_order_hidden_badge)
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text       = channel.name,
                color      = nameColor,
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines   = 1,
                overflow   = TextOverflow.Ellipsis,
            )
            val parts = buildList {
                if (channel.number > 0) add("LCN ${channel.number}")
                if (channel.groupName.isNotBlank()) add(channel.groupName)
                if (isHidden) add(hiddenBadge)
            }
            if (parts.isNotEmpty()) {
                Text(
                    text     = parts.joinToString(" · "),
                    color    = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        ActionButton(
            icon               = Icons.Filled.ArrowUpward,
            contentDescription = stringResource(R.string.channel_order_move_up),
            enabled            = !isFirst,
            onClick            = onMoveUp,
        )
        Spacer(Modifier.width(6.dp))
        ActionButton(
            icon               = Icons.Filled.ArrowDownward,
            contentDescription = stringResource(R.string.channel_order_move_down),
            enabled            = !isLast,
            onClick            = onMoveDown,
        )
        Spacer(Modifier.width(6.dp))
        ActionButton(
            icon               = if (isHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription =
                if (isHidden) stringResource(R.string.channel_order_show)
                else          stringResource(R.string.channel_order_hide),
            enabled            = true,
            onClick            = onToggleHidden,
        )
    }
}

@Composable
private fun ActionButton(
    icon:               androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled:            Boolean,
    onClick:            () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> TextSecondary.copy(alpha = 0.35f)
        focused  -> OnAccent
        else     -> TextPrimary
    }
    val bg = if (focused && enabled) Accent else Color.Transparent
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .onFocusChanged { focused = it.isFocused && enabled }
            .clickable(
                enabled           = enabled,
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector        = icon,
            contentDescription = contentDescription,
            tint               = tint,
            modifier           = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun CenteredSpinner() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text      = message,
            color     = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String) {
    Column(
        modifier              = Modifier.fillMaxSize().padding(40.dp),
        verticalArrangement   = Arrangement.Center,
        horizontalAlignment   = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = title,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize   = 18.sp,
            textAlign  = TextAlign.Center,
        )
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text      = subtitle,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize  = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

