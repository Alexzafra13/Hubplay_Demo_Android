package com.alex.hubplay.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.CertChallenge
import com.alex.hubplay.data.CertFailureReason
import java.text.DateFormat
import java.util.Date

/**
 * Modal dialog shown when [PinnedCertTrustManager] rejects a server's
 * cert and asks the user whether to pin it. The TV form factor wants
 * one screen of facts: hostname, what failed, leaf subject + issuer,
 * fingerprint + validity range, plus a warning that pinning a hostile
 * cert hands the session to an attacker.
 *
 * The Trust action is intentionally not the primary visual button —
 * D-pad focus lands on Cancel by default so a careless OK-mash doesn't
 * accept a bogus cert.
 */
@Composable
fun CertTrustDialog(
    challenge: CertChallenge,
    onTrust:   (CertChallenge) -> Unit,
    onCancel:  () -> Unit,
) {
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text       = stringResource(R.string.cert_dialog_title),
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text  = stringResource(R.string.cert_dialog_intro, challenge.host),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(4.dp))

                Field(
                    label = stringResource(R.string.cert_dialog_label_reason),
                    value = reasonLabel(challenge.reason),
                    color = MaterialTheme.colorScheme.error,
                )
                Field(
                    label = stringResource(R.string.cert_dialog_label_host),
                    value = challenge.host,
                )
                Field(
                    label = stringResource(R.string.cert_dialog_label_subject),
                    value = challenge.subject.ifBlank { "—" },
                )
                Field(
                    label = stringResource(R.string.cert_dialog_label_issuer),
                    value = challenge.issuer.ifBlank { "—" },
                )
                Field(
                    label = stringResource(R.string.cert_dialog_label_fingerprint),
                    value = challenge.fingerprint,
                    mono  = true,
                )
                Field(
                    label = stringResource(R.string.cert_dialog_label_validity),
                    value = stringResource(
                        R.string.cert_dialog_validity_range,
                        df.format(Date(challenge.notBefore)),
                        df.format(Date(challenge.notAfter)),
                    ),
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text  = stringResource(R.string.cert_dialog_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onTrust(challenge) }) {
                Text(
                    text  = stringResource(R.string.cert_dialog_action_trust),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cert_dialog_action_cancel))
            }
        },
    )
}

@Composable
private fun Field(
    label: String,
    value: String,
    mono:  Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Column {
        Text(
            text       = label,
            style      = MaterialTheme.typography.labelSmall,
            color      = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text       = value,
            style      = MaterialTheme.typography.bodySmall,
            color      = color,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

@Composable
private fun reasonLabel(reason: CertFailureReason): String = stringResource(
    when (reason) {
        CertFailureReason.UnknownIssuer    -> R.string.cert_dialog_reason_unknown_issuer
        CertFailureReason.Expired          -> R.string.cert_dialog_reason_expired
        CertFailureReason.NotYetValid      -> R.string.cert_dialog_reason_not_yet_valid
        CertFailureReason.HostnameMismatch -> R.string.cert_dialog_reason_hostname
        CertFailureReason.Other            -> R.string.cert_dialog_reason_other
    },
)

