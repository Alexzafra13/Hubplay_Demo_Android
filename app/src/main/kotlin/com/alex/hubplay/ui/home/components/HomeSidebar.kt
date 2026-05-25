package com.alex.hubplay.ui.home.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary

val SIDEBAR_COLLAPSED_WIDTH = 56.dp
val SIDEBAR_EXPANDED_WIDTH = 200.dp

@Composable
fun HomeSidebar(
    profileName:    String?,
    expanded:       Boolean,
    onExpandChange: (Boolean) -> Unit,
    onNavigateToTab: (Tab) -> Unit,
    onOpenSearch:   () -> Unit,
    onOpenSettings: () -> Unit,
    visibleTabs:    Set<Tab>,
    modifier:       Modifier = Modifier,
) {
    val width = if (expanded) SIDEBAR_EXPANDED_WIDTH else SIDEBAR_COLLAPSED_WIDTH

    Box(
        modifier = modifier
            .width(width)
            .fillMaxHeight()
            .background(
                if (expanded) BgBase.copy(alpha = 0.75f)
                else Color.Transparent,
            )
            .animateContentSize(animationSpec = tween(250))
            .onFocusChanged { state ->
                onExpandChange(state.hasFocus)
            }
            .padding(vertical = 16.dp),
    ) {
        // Nav items — centered vertically
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (expanded && !profileName.isNullOrBlank()) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = profileName.first().uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            SidebarItem(
                icon = Icons.Default.Search,
                label = stringResource(R.string.home_sidebar_search),
                expanded = expanded,
                onClick = onOpenSearch,
            )

            SidebarItem(
                icon = Icons.Default.Home,
                label = stringResource(R.string.home_sidebar_home),
                expanded = expanded,
                onClick = { onNavigateToTab(Tab.Home) },
            )

            if (Tab.Movies in visibleTabs) {
                SidebarItem(
                    icon = Icons.Default.Movie,
                    label = stringResource(R.string.home_sidebar_movies),
                    expanded = expanded,
                    onClick = { onNavigateToTab(Tab.Movies) },
                )
            }

            if (Tab.Series in visibleTabs) {
                SidebarItem(
                    icon = Icons.Default.VideoLibrary,
                    label = stringResource(R.string.home_sidebar_series),
                    expanded = expanded,
                    onClick = { onNavigateToTab(Tab.Series) },
                )
            }

            if (Tab.Collections in visibleTabs) {
                SidebarItem(
                    icon = Icons.Outlined.CollectionsBookmark,
                    label = stringResource(R.string.home_sidebar_collections),
                    expanded = expanded,
                    onClick = { onNavigateToTab(Tab.Collections) },
                )
            }

            if (Tab.LiveTv in visibleTabs) {
                SidebarItem(
                    icon = Icons.Default.LiveTv,
                    label = stringResource(R.string.home_sidebar_livetv),
                    expanded = expanded,
                    onClick = { onNavigateToTab(Tab.LiveTv) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SidebarItem(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.home_sidebar_settings),
                expanded = expanded,
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun SidebarItem(
    icon:     ImageVector,
    label:    String,
    expanded: Boolean,
    onClick:  () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1.0f,
        animationSpec = tween(180),
        label = "sidebar-item-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val fg = if (focused) TextPrimary else TextSecondary

    Row(
        modifier = Modifier
            .then(
                if (expanded) Modifier.width(SIDEBAR_EXPANDED_WIDTH - 8.dp)
                else Modifier.width(SIDEBAR_COLLAPSED_WIDTH),
            )
            .height(42.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(
                if (focused) Modifier.border(1.5.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .padding(horizontal = if (expanded) 12.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (expanded)
            Arrangement.spacedBy(12.dp) else Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(22.dp),
        )
        if (expanded) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
