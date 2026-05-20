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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Shield
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.CertPinStore
import com.alex.hubplay.ui.theme.BgBase
import java.text.DateFormat
import java.util.Date

/**
 * Settings → "Servidores de confianza". Lists the hosts whose certs
 * the user manually accepted from the TOFU dialog, with the fingerprint
 * shown verbatim (so they can verify against the server's `openssl`
 * output) and a "Forget" button to wipe a single pin. Deleting and
 * connecting again re-triggers the dialog — which is exactly the recovery
 * path if the user thinks a stored pin was hostile.
 */
@Composable
fun TrustedServersScreen(
    viewModel: TrustedServersViewModel,
    onBack:    () -> Unit,
) {
    val entries by viewModel.entries.collectAsState()

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                    text       = stringResource(R.string.trusted_screen_title),
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
                        text  = stringResource(R.string.settings_trusted_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (entries.isEmpty()) {
                        EmptyState()
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(entries, key = { it.host }) { pin ->
                                PinRow(pin = pin, onForget = { viewModel.forget(pin.host) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier         = Modifier.fillMaxWidth().padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector        = Icons.Outlined.Shield,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.padding(bottom = 8.dp),
            )
            Text(
                text      = stringResource(R.string.trusted_screen_empty),
                style     = MaterialTheme.typography.bodyMedium,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PinRow(pin: CertPinStore.Pin, onForget: () -> Unit) {
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Surface(
        color    = MaterialTheme.colorScheme.surface,
        shape    = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text       = pin.host,
                style      = MaterialTheme.typography.titleMedium,
                color      = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = pin.fingerprint,
                style      = MaterialTheme.typography.bodySmall,
                color      = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text  = stringResource(R.string.trusted_added_at, df.format(Date(pin.addedAt))),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onForget) {
                    Text(stringResource(R.string.trusted_action_forget))
                }
            }
        }
    }
}
