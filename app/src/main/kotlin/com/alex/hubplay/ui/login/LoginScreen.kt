package com.alex.hubplay.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.DeviceCodeStatus

/**
 * Login surface. Two stages share this single composable so the user's
 * typed URL is preserved if they cancel-and-retry pairing.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onAuthenticated: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsState()

    // Approved? Tell the nav host to swap to Home.
    LaunchedEffect(ui.pollStatus) {
        if (ui.pollStatus is DeviceCodeStatus.Approved) onAuthenticated()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier            = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text       = "HubPlay",
                    style      = MaterialTheme.typography.displayMedium,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))

                when (ui.stage) {
                    LoginStage.ServerUrl -> ServerUrlForm(ui, viewModel)
                    LoginStage.Pairing   -> PairingForm(ui, viewModel)
                }

                ui.error?.let { error ->
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text  = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerUrlForm(ui: LoginUiState, viewModel: LoginViewModel) {
    Text(
        text = stringResource(R.string.login_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.login_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))

    OutlinedTextField(
        value         = ui.serverUrl,
        onValueChange = viewModel::onServerUrlChange,
        label         = { Text(stringResource(R.string.login_server_label)) },
        placeholder   = { Text(stringResource(R.string.login_server_hint)) },
        singleLine    = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier      = Modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(20.dp))

    Button(
        onClick  = viewModel::onContinueClicked,
        enabled  = !ui.isStarting && ui.serverUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape    = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (ui.isStarting) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier    = Modifier.height(20.dp),
                color       = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.login_continue))
        }
    }
}

@Composable
private fun PairingForm(ui: LoginUiState, viewModel: LoginViewModel) {
    val start = ui.pairingStart ?: return

    Text(
        text  = stringResource(R.string.login_pairing_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text  = stringResource(R.string.login_pairing_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))

    // Big monospace user_code, prominent.
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text       = start.userCode,
            style      = MaterialTheme.typography.displayLarge,
            color      = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        )
    }
    Spacer(Modifier.height(20.dp))

    Text(
        text = start.verifyUrl,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))

    val pollText = when (ui.pollStatus) {
        is DeviceCodeStatus.Pending,
        null,                                -> stringResource(R.string.login_pairing_waiting)
        is DeviceCodeStatus.Approved         -> "Aprobado, abriendo HubPlay…"
        is DeviceCodeStatus.Expired          -> "El código expiró. Inténtalo de nuevo."
        is DeviceCodeStatus.Failed           -> ui.pollStatus.message
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (ui.pollStatus is DeviceCodeStatus.Pending || ui.pollStatus == null) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.height(16.dp))
            Spacer(Modifier.height(0.dp).also { /* no-op layout */ })
            Text(text = "  $pollText", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(text = pollText, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(16.dp))

    TextButton(onClick = viewModel::onCancelPairing) {
        Text("Cancelar y cambiar URL")
    }
}

// ─── tiny Row shim so we don't pull foundation.layout.Row above for
//     a single use — keeps the imports list minimal.
@Composable
private fun Row(
    verticalAlignment:     Alignment.Vertical    = Alignment.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    modifier:              Modifier              = Modifier,
    content:               @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment     = verticalAlignment,
        horizontalArrangement = horizontalArrangement,
        modifier              = modifier,
        content               = { content() },
    )
}
