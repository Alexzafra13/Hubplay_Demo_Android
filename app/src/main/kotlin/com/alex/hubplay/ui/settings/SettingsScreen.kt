package com.alex.hubplay.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.ui.theme.BgBase

/**
 * Settings / account info screen.
 *
 * Two sections today — Servidor (which HubPlay instance we're paired
 * with) and Acerca de (app build info). The "Cambiar servidor" action is
 * `onLogOut` underneath: clearing tokens forces the login flow which
 * re-asks for the URL. Future work splits this into "log out" (clear
 * tokens, keep URL) vs "switch server" (clear both) — the current
 * `clearBlocking` keeps the serverUrl, so the login screen starts with
 * the URL pre-filled, which matches what most users will want.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack:    () -> Unit,
    onLogOut:  () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar: back + title — deliberately plain (not the TopNav
            // tab bar) so Settings reads as a modal subscreen rather than
            // another tab destination.
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Volver",
                        tint               = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text       = "Ajustes",
                    style      = MaterialTheme.typography.headlineSmall,
                    color      = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier         = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(
                    modifier            = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Spacer(Modifier.height(8.dp))

                    SectionCard(title = "Servidor", icon = Icons.Outlined.Dns) {
                        InfoRow(label = "URL", value = ui.serverUrl ?: "—")
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick  = onLogOut,
                            colors   = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            shape    = RoundedCornerShape(10.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.Logout,
                                contentDescription = null,
                                modifier           = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Cerrar sesión y cambiar servidor")
                        }
                    }

                    SectionCard(title = "Acerca de", icon = Icons.Outlined.Info) {
                        InfoRow(label = "Versión",   value = ui.appVersion)
                        InfoRow(label = "Build",     value = ui.buildFlavor)
                        InfoRow(label = "Paquete",   value = "com.alex.hubplay")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title:   String,
    icon:    ImageVector,
    content: @Composable () -> Unit,
) {
    Surface(
        color          = MaterialTheme.colorScheme.surface,
        shape          = RoundedCornerShape(18.dp),
        tonalElevation = 2.dp,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = icon,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text  = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodyMedium,
            color      = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}
