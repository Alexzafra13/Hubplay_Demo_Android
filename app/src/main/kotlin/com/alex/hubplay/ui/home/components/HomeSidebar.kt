package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextMuted

val SIDEBAR_WIDTH = 52.dp

@Composable
fun HomeSidebar(
    onNavigateToTab: (Tab) -> Unit,
    onOpenSearch:    () -> Unit,
    onOpenSettings:  () -> Unit,
    visibleTabs:     Set<Tab>,
    modifier:        Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(SIDEBAR_WIDTH)
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SidebarIcon(
                icon = Icons.Default.Search,
                label = stringResource(R.string.home_sidebar_search),
                onClick = onOpenSearch,
            )
            SidebarIcon(
                icon = Icons.Default.Home,
                label = stringResource(R.string.home_sidebar_home),
                onClick = { onNavigateToTab(Tab.Home) },
            )
            if (Tab.Movies in visibleTabs) {
                SidebarIcon(
                    icon = Icons.Default.Movie,
                    label = stringResource(R.string.home_sidebar_movies),
                    onClick = { onNavigateToTab(Tab.Movies) },
                )
            }
            if (Tab.Series in visibleTabs) {
                SidebarIcon(
                    icon = Icons.Default.VideoLibrary,
                    label = stringResource(R.string.home_sidebar_series),
                    onClick = { onNavigateToTab(Tab.Series) },
                )
            }
            if (Tab.Collections in visibleTabs) {
                SidebarIcon(
                    icon = Icons.Outlined.CollectionsBookmark,
                    label = stringResource(R.string.home_sidebar_collections),
                    onClick = { onNavigateToTab(Tab.Collections) },
                )
            }
            if (Tab.LiveTv in visibleTabs) {
                SidebarIcon(
                    icon = Icons.Default.LiveTv,
                    label = stringResource(R.string.home_sidebar_livetv),
                    onClick = { onNavigateToTab(Tab.LiveTv) },
                )
            }
            SidebarIcon(
                icon = Icons.Default.Settings,
                label = stringResource(R.string.home_sidebar_settings),
                onClick = onOpenSettings,
            )
        }
    }
}

@Composable
private fun SidebarIcon(
    icon:    ImageVector,
    label:   String,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.12f else 1.0f,
        animationSpec = tween(150),
        label = "sidebar-icon-scale",
    )
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (focused) TextPrimary else TextMuted

    Box(
        modifier = Modifier
            .width(SIDEBAR_WIDTH)
            .height(38.dp)
            .scale(scale)
            .clip(RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .then(
                if (focused) Modifier.border(1.5.dp, TextPrimary.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}
