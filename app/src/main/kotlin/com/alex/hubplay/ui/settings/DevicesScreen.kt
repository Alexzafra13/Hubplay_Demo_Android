package com.alex.hubplay.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Devices
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Ajustes → "Dispositivos conectados": dónde está abierta la cuenta (la
 * tele, el móvil, el navegador…). Sirve para echar a un aparato perdido
 * o prestado sin tener que ir a la web.
 */
@Composable
fun DevicesScreen(
    viewModel: DevicesViewModel,
    onBack:    () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier          = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                    text       = stringResource(R.string.devices_screen_title),
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
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = stringResource(R.string.devices_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DevicesBody(ui = ui, onRetry = viewModel::load, onRevoke = viewModel::revoke)
                }
            }
        }
    }
}

@Composable
private fun DevicesBody(ui: DevicesUiState, onRetry: () -> Unit, onRevoke: (Device) -> Unit) {
    when {
        ui.isLoading -> Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        ui.error -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text      = stringResource(R.string.devices_error),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
        else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(ui.devices, key = { it.id }) { device ->
                DeviceRow(device = device, onRevoke = { onRevoke(device) })
            }
        }
    }
}

@Composable
private fun DeviceRow(device: Device, onRevoke: () -> Unit) {
    val formatter = remember {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault())
    }
    Surface(
        color          = MaterialTheme.colorScheme.surface,
        shape          = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier       = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector        = Icons.Outlined.Devices,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text       = device.name ?: stringResource(R.string.devices_unnamed),
                        style      = MaterialTheme.typography.titleMedium,
                        color      = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (device.current) CurrentBadge()
                }
                Spacer(Modifier.height(4.dp))
                val details = listOfNotNull(
                    stringResource(if (device.linked) R.string.devices_method_linked else R.string.devices_method_password),
                    device.ip,
                    device.lastActive?.let { stringResource(R.string.devices_last_active, formatter.format(it)) },
                ).joinToString(" · ")
                Text(
                    text  = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!device.current) {
                TextButton(onClick = onRevoke) { Text(stringResource(R.string.devices_action_revoke)) }
            }
        }
    }
}

@Composable
private fun CurrentBadge() {
    Spacer(Modifier.width(10.dp))
    Text(
        text       = stringResource(R.string.devices_current),
        style      = MaterialTheme.typography.labelSmall,
        color      = MaterialTheme.colorScheme.onPrimary,
        fontWeight = FontWeight.SemiBold,
        modifier   = Modifier
            .background(Accent, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
