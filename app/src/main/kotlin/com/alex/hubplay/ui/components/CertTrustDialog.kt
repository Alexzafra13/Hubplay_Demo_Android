package com.alex.hubplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * "This cert isn't in your device's trust list — want to trust it?"
 * dialog, designed for the 10-foot UI:
 *
 *   - Single friendly paragraph (no JDK / X.509 jargon).
 *   - Hostname surfaced as the only number-one piece of identifying
 *     information — that's what the user can act on.
 *   - Technical details (issuer / fingerprint / validity) hidden
 *     behind a "Ver detalles" toggle, off by default. The savvy user
 *     who wants to compare against `openssl s_client` still has the
 *     fingerprint one tap away.
 *   - Cancel is the visually neutral action with default focus so a
 *     careless OK-mash on the remote doesn't accept a hostile cert.
 *     Trust is primary-coloured but second-tab.
 */
@Composable
fun CertTrustDialog(
    challenge: CertChallenge,
    onTrust:   (CertChallenge) -> Unit,
    onCancel:  () -> Unit,
) {
    var showDetails by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            // Centred lock badge + title — reads as a security prompt
            // at a glance rather than a generic alert.
            Column(
                modifier              = Modifier.fillMaxWidth(),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(24.dp),
                    )
                }
                Text(
                    text       = stringResource(R.string.cert_dialog_title),
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                // Intro: explain in plain language. No "X.509", no
                // "Chain validation". Just "this is new, do you know it?".
                Text(
                    text  = stringResource(R.string.cert_dialog_intro),
                    style = MaterialTheme.typography.bodyMedium,
                )

                // Hostname as a card-ish callout so it visually anchors
                // the decision: "you are trusting THIS host, not anything
                // else." Acts as both info and verification anchor.
                HostCallout(host = challenge.host)

                Text(
                    text  = stringResource(R.string.cert_dialog_caveat),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                // ── Toggle: technical details ────────────────────────
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier              = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showDetails = !showDetails }
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector        = if (showDetails) Icons.Default.KeyboardArrowUp
                                              else            Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(18.dp),
                    )
                    Text(
                        text       = stringResource(
                            if (showDetails) R.string.cert_dialog_hide_details
                            else             R.string.cert_dialog_show_details,
                        ),
                        style      = MaterialTheme.typography.labelLarge,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }

                AnimatedVisibility(visible = showDetails) {
                    TechnicalDetails(challenge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onTrust(challenge) }) {
                Text(
                    text       = stringResource(R.string.cert_dialog_action_trust),
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
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
private fun HostCallout(host: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text       = host,
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun TechnicalDetails(challenge: CertChallenge) {
    val df = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DetailRow(
            label = stringResource(R.string.cert_dialog_label_reason),
            value = reasonLabel(challenge.reason),
            color = MaterialTheme.colorScheme.error,
        )
        DetailRow(
            label = stringResource(R.string.cert_dialog_label_issuer),
            value = friendlyIssuer(challenge.issuer),
        )
        DetailRow(
            label = stringResource(R.string.cert_dialog_label_validity),
            value = stringResource(
                R.string.cert_dialog_validity_until,
                df.format(Date(challenge.notAfter)),
            ),
        )
        DetailRow(
            label = stringResource(R.string.cert_dialog_label_fingerprint),
            value = challenge.fingerprint,
            mono  = true,
        )
    }
}

@Composable
private fun DetailRow(
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
        Spacer(Modifier.height(2.dp))
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

/**
 * Pull a short, human-readable name out of the X.500 issuer string.
 * Most certs encode the issuer as `C=US, O=Let's Encrypt, CN=E7` — for
 * a TV dialog the user cares about the org, not the country code or
 * the CN. Falls back to the raw string when we can't parse.
 */
private fun friendlyIssuer(raw: String): String {
    if (raw.isBlank()) return "—"
    val org = raw.split(",").firstOrNull { it.trim().startsWith("O=") }
        ?.substringAfter("=")?.trim()?.trim('"')
    return org?.takeIf { it.isNotBlank() } ?: raw
}

