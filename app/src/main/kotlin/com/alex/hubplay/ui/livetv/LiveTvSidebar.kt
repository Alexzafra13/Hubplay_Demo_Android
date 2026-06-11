package com.alex.hubplay.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.OnAccent
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary

/**
 * Vertical category rail on the left edge of LiveTv.
 *
 * Same background colour as the rest of the screen ([BgBase]) so
 * there's no visual seam between the sidebar and the EPG grid. State
 * styling:
 *
 *   - **Selected** → solid Accent fill, OnAccent text
 *   - **Focused**  → transparent fill + Accent border, white text
 *   - **Idle**     → transparent fill + muted text
 *
 * Focus discipline: D-pad → from sidebar enters the EPG grid; D-pad
 * ← from the grid jumps back here (Compose's default focus search,
 * because the sidebar is to the left of the grid).
 */
@Composable
fun LiveTvSidebar(
    groups:           List<String>,
    selectedFilter:   ChannelFilter,
    favoritesCount:   Int,
    /** Recently-watched channels available. 0 hides the auto filter. */
    recentCount:      Int = 0,
    onFilterChanged:  (ChannelFilter) -> Unit,
    onReorderChannels: () -> Unit = {},
    onOpenSettings:    () -> Unit = {},
    modifier:         Modifier = Modifier,
) {
    val allLabel        = stringResource(R.string.livetv_filter_all)
    val favoritesLabel  = stringResource(R.string.livetv_filter_favorites)
    val favoritesWithCt = stringResource(R.string.livetv_filter_favorites_count, favoritesCount)
    val recentLabel     = stringResource(R.string.livetv_filter_recent)
    val items = remember(groups, favoritesCount, recentCount, allLabel, favoritesLabel, favoritesWithCt, recentLabel) {
        buildList {
            add(SidebarItem.Builtin(allLabel, ChannelFilter.All))
            add(SidebarItem.Builtin(
                label  = if (favoritesCount > 0) favoritesWithCt else favoritesLabel,
                filter = ChannelFilter.Favorites,
                isStar = true,
            ))
            // Auto-generated: only shows up once there's watch history,
            // sitting between Favoritos and the M3U group names.
            if (recentCount > 0) add(SidebarItem.Builtin(recentLabel, ChannelFilter.Recent))
            for (g in groups) add(SidebarItem.Group(g))
        }
    }

    Column(
        modifier = modifier
            .width(SIDEBAR_WIDTH)
            .background(BgBase),
    ) {
        // Scrollable filter list takes the available height …
        LazyColumn(
            modifier              = Modifier.weight(1f),
            contentPadding        = PaddingValues(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement   = Arrangement.spacedBy(2.dp),
        ) {
            items(items, key = { it.key }) { item ->
                val (label, isSelected, isStar) = when (item) {
                    is SidebarItem.Builtin -> Triple(item.label, selectedFilter == item.filter, item.isStar)
                    is SidebarItem.Group   -> Triple(item.name, (selectedFilter as? ChannelFilter.Group)?.name == item.name, false)
                }
                SidebarRow(
                    label    = label,
                    isStar   = isStar,
                    selected = isSelected,
                    leadingIcon = null,
                    onClick  = {
                        onFilterChanged(when (item) {
                            is SidebarItem.Builtin -> item.filter
                            is SidebarItem.Group   -> ChannelFilter.Group(item.name)
                        })
                    },
                )
            }
        }
        // … and the bottom block lives outside the LazyColumn so the
        // Settings + Reorder entries stay anchored to the sidebar's foot
        // even when the filter list scrolls. TV-friendly: D-pad ↓ from
        // the last filter naturally lands here without leaving the
        // sidebar's focus column.
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            HorizontalDivider(
                color    = TextSecondary.copy(alpha = 0.18f),
                modifier = Modifier.padding(vertical = 6.dp),
            )
            SidebarRow(
                label       = stringResource(R.string.livetv_sidebar_reorder),
                isStar      = false,
                selected    = false,
                leadingIcon = Icons.Filled.SwapVert,
                onClick     = onReorderChannels,
            )
            SidebarRow(
                label       = stringResource(R.string.livetv_sidebar_settings),
                isStar      = false,
                selected    = false,
                leadingIcon = Icons.Filled.Settings,
                onClick     = onOpenSettings,
            )
        }
    }
}

@Composable
private fun SidebarRow(
    label:       String,
    isStar:      Boolean,
    selected:    Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector?,
    onClick:     () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    val bg = if (selected) Accent else Color.Transparent
    val fg = when {
        selected -> OnAccent
        focused  -> TextPrimary
        else     -> TextSecondary
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused && !selected) Modifier.border(1.5.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = onClick,
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isStar -> {
                Icon(
                    imageVector        = Icons.Filled.Star,
                    contentDescription = null,
                    tint               = if (selected) OnAccent else Accent,
                    modifier           = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            leadingIcon != null -> {
                Icon(
                    imageVector        = leadingIcon,
                    contentDescription = null,
                    tint               = fg,
                    modifier           = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
        }
        Text(
            text       = label,
            color      = fg,
            fontSize   = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis,
        )
    }
}

private sealed class SidebarItem {
    abstract val key: String

    data class Builtin(
        val label:  String,
        val filter: ChannelFilter,
        val isStar: Boolean = false,
    ) : SidebarItem() {
        override val key = "builtin:$label"
    }

    data class Group(val name: String) : SidebarItem() {
        override val key = "group:$name"
    }
}

internal val SIDEBAR_WIDTH = 220.dp
