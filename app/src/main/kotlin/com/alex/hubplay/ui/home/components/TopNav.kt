package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.annotation.StringRes
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.AccentSoft

/**
 * Sticky-top navigation bar.
 *
 * Layout: [wordmark] [tabs in centre] [search] [avatar w/ dropdown].
 *
 * The dropdown carries the auth-related actions that previously lived
 * as ad-hoc buttons on the Home screen (log out, switch profile,
 * settings). A real settings page hasn't shipped yet — those entries
 * are placeholders that just toast for now; the only functional one
 * is "Cerrar sesión".
 */
@Composable
fun TopNav(
    selectedTab:   Tab,
    onTabSelected: (Tab) -> Unit,
    onLogOut:      () -> Unit,
    onSettings:    () -> Unit = {},
    modifier:      Modifier = Modifier,
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // ── Brand mark
        Image(
            painter            = painterResource(R.drawable.brand_wordmark),
            contentDescription = stringResource(R.string.brand_hubplay),
            modifier           = Modifier.height(28.dp),
        )

        // ── Tabs (centre)
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Tab.entries.forEach { tab ->
                NavTab(
                    label    = stringResource(tab.labelRes),
                    selected = tab == selectedTab,
                    onClick  = { onTabSelected(tab) },
                )
            }
        }

        // ── Avatar dropdown (right)
        // Settings now lives inside the avatar dropdown — the previous
        // standalone search icon disappeared when Search became a tab,
        // so the right-hand cluster is just identity-related actions.
        AvatarMenu(
            onSettings = onSettings,
            onLogOut   = onLogOut,
        )
    }
}

@Composable
private fun NavTab(label: String, selected: Boolean, onClick: () -> Unit) {
    // D-pad focus visual: subtle scale + Accent border so a TV user
    // can see which tab the remote is hovering over. The selected
    // background (AccentSoft) stays independent so the user can tell
    // "which tab is active" vs "which tab the cursor is on".
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue   = if (focused) 1.06f else 1.0f,
        animationSpec = tween(180),
        label         = "nav-tab-scale",
    )
    Box(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .background(if (selected) AccentSoft else Color.Transparent)
            .then(
                if (focused) Modifier.border(2.dp, Accent, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text       = label,
            style      = MaterialTheme.typography.bodyMedium,
            color      = if (selected) Accent else MaterialTheme.colorScheme.onBackground,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun AvatarMenu(
    onSettings: () -> Unit,
    onLogOut:   () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            // Placeholder avatar — round, brand-coloured, with a generic icon.
            // Wire to the real `/me` profile avatar in a follow-up.
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Default.AccountCircle,
                    contentDescription = stringResource(R.string.cd_account),
                    tint               = Accent,
                )
            }
        }
        DropdownMenu(
            expanded         = open,
            onDismissRequest = { open = false },
        ) {
            // "Cambiar perfil" was here as a non-functional placeholder.
            // Removed on purpose — better to ship no entry than one that
            // lies; we'll re-add it the day multi-profile actually works.
            DropdownMenuItem(
                text     = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                onClick  = { open = false; onSettings() },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text     = { Text(stringResource(R.string.settings_action_logout)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                onClick  = { open = false; onLogOut() },
            )
        }
    }
}

enum class Tab(@StringRes val labelRes: Int) {
    Home(R.string.nav_tab_home),
    Movies(R.string.nav_tab_movies),
    Series(R.string.nav_tab_series),
    LiveTv(R.string.nav_tab_livetv),
    Search(R.string.nav_tab_search),
}
