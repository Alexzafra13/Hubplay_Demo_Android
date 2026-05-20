package com.alex.hubplay.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.alex.hubplay.data.DeviceCodeStatus
import com.alex.hubplay.data.LanServer
import com.alex.hubplay.ui.components.CertTrustDialog
import com.alex.hubplay.ui.components.QrCode

/**
 * Login surface — two stages share one composable so the user's typed
 * URL is preserved if they cancel-and-retry pairing.
 *
 * Stage 1 — ServerUrl: URL input + a live list of LAN servers discovered
 *           via mDNS (NsdManager). Tap a discovered entry to fill the
 *           URL field with one tap.
 *
 * Stage 2 — Pairing: TWO equally-prominent options arranged side-by-side
 *           on wide layouts (TV, tablets, foldables) and stacked on
 *           phones — a QR encoding the `verification_uri_complete` (RFC
 *           8628 §3.3.1) for scanning from an already-paired device, and
 *           the manual user_code in big monospace for entry through the
 *           web's Settings → Devices panel. Both options reach the same
 *           server endpoint; the user picks whichever is closer to hand.
 */
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onAuthenticated: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsState()

    LaunchedEffect(ui.pollStatus) {
        if (ui.pollStatus is DeviceCodeStatus.Approved) onAuthenticated()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color    = MaterialTheme.colorScheme.background,
    ) {
        // Subtle radial gradient to lift the background away from pure
        // black — same trick the web client uses on Login.tsx.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background,
                        ),
                        radius = 1400f,
                    )
                ),
        ) {
            BoxWithConstraints(
                modifier         = Modifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val isWide = maxWidth >= 720.dp
                val contentMaxWidth = if (ui.stage == LoginStage.Pairing && isWide) 880.dp else 480.dp

                Column(
                    modifier = Modifier
                        .widthIn(max = contentMaxWidth)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Image(
                        painter            = painterResource(R.drawable.brand_wordmark),
                        contentDescription = stringResource(R.string.brand_hubplay),
                        modifier           = Modifier.height(64.dp),
                    )
                    Spacer(Modifier.height(32.dp))

                    when {
                        // Single LAN server got auto-picked → render the
                        // "Conectando…" overlay even while stage is still
                        // ServerUrl, so the user sees the auto-decision
                        // happen explicitly rather than a stage flicker.
                        ui.stage == LoginStage.ServerUrl && ui.autoConnected && ui.isStarting ->
                            AutoConnectingOverlay(ui)
                        ui.stage == LoginStage.ServerUrl ->
                            ServerUrlForm(ui, viewModel)
                        ui.stage == LoginStage.Pairing   ->
                            PairingForm(ui, viewModel, isWide)
                    }

                    ui.error?.let { error ->
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text      = error,
                            color     = MaterialTheme.colorScheme.error,
                            style     = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    // TOFU: trust-on-first-use dialog when PinnedCertTrustManager
    // rejects the server's cert and asks the user to review it. Lives
    // here (and not inside ServerUrlForm) so it survives the stage
    // transition if a redirect mid-pairing surfaces a new challenge.
    ui.certChallenge?.let { challenge ->
        CertTrustDialog(
            challenge = challenge,
            onTrust   = viewModel::acceptCertChallenge,
            onCancel  = viewModel::dismissCertChallenge,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stage 1 — Server URL / LAN picker
//
// Three visual states layered into one composable:
//
//  1. LAN servers discovered → big tappable cards on top (primary path,
//     Steam Link style). The URL input collapses into "¿Otro servidor?"
//     so the typed path stays available without competing for attention.
//  2. Still searching, nothing found yet → URL input is primary, with a
//     small "Buscando en tu red…" indicator above.
//  3. Search finished with no hits (router blocks multicast, remote
//     server) → URL input is primary, no spinner.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ServerUrlForm(ui: LoginUiState, viewModel: LoginViewModel) {
    Text(
        text      = stringResource(R.string.login_title),
        style     = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text      = stringResource(R.string.login_subtitle),
        style     = MaterialTheme.typography.bodyMedium,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(28.dp))

    val hasLanHits = ui.lanDiscovery.isNotEmpty()

    if (hasLanHits) {
        // LAN cards as the primary action — large, tappable, focusable.
        LanServerList(
            entries   = ui.lanDiscovery,
            searching = ui.lanSearching,
            onPick    = { url -> viewModel.pickServer(url) },
        )
        Spacer(Modifier.height(20.dp))
        // Subtle divider + the typed URL path as a secondary affordance.
        SecondaryUrlInput(ui, viewModel)
    } else {
        // No LAN hits → typed URL is the only path. We still want to
        // tell the user *something* about the search so they're not
        // guessing — either "we're looking" with the spinner, or
        // "we looked and found nothing, want to try again?" with a
        // retry. Either takes the same vertical real estate so the
        // form below doesn't jump when the state flips.
        if (ui.lanSearching) {
            LanSearchingPill()
        } else {
            LanNoResultsPill(onSearchAgain = viewModel::restartLanSearch)
        }
        Spacer(Modifier.height(14.dp))
        PrimaryUrlInput(ui, viewModel)
    }
}

@Composable
private fun LanServerList(
    entries:   List<LanServer>,
    searching: Boolean,
    onPick:    (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier              = Modifier.padding(bottom = 4.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Lan,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(18.dp),
            )
            Text(
                text  = stringResource(R.string.login_lan_discovered),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            if (searching) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier    = Modifier.size(12.dp),
                    color       = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        entries.forEach { entry ->
            LanServerCard(entry = entry, onPick = onPick)
        }
    }
}

@Composable
private fun LanServerCard(entry: LanServer, onPick: (String) -> Unit) {
    Surface(
        color          = MaterialTheme.colorScheme.surface,
        shape          = RoundedCornerShape(14.dp),
        tonalElevation = 3.dp,
        modifier       = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onPick(entry.url) }),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Round badge with the brand mark colour so a TV user sees a
            // clear hit target even at distance.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Outlined.Lan,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.primary,
                    modifier           = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text       = entry.displayName,
                    style      = MaterialTheme.typography.bodyLarge,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text  = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LanSearchingPill() {
    Surface(
        color    = MaterialTheme.colorScheme.surface,
        shape    = RoundedCornerShape(999.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier    = Modifier.size(14.dp),
                color       = MaterialTheme.colorScheme.primary,
            )
            Text(
                text  = stringResource(R.string.login_lan_searching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Shown when the discovery window closed with zero hits. Same visual
 * frame as [LanSearchingPill] (so the form doesn't jump when the
 * timeout fires) but swaps the spinner for a "Buscar de nuevo" button.
 * Common when the user is on the emulator (no multicast) or on a guest
 * VLAN that blocks mDNS — neither hopeless, just needs a nudge.
 */
@Composable
private fun LanNoResultsPill(onSearchAgain: () -> Unit) {
    Surface(
        color    = MaterialTheme.colorScheme.surface,
        shape    = RoundedCornerShape(999.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier              = Modifier.padding(start = 16.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector        = Icons.Outlined.Lan,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier           = Modifier.size(16.dp),
            )
            Text(
                text     = stringResource(R.string.login_lan_no_results),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onSearchAgain) {
                Text(
                    text       = stringResource(R.string.login_lan_search_again),
                    style      = MaterialTheme.typography.labelLarge,
                    color      = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun PrimaryUrlInput(ui: LoginUiState, viewModel: LoginViewModel) {
    OutlinedTextField(
        value           = ui.serverUrl,
        onValueChange   = viewModel::onServerUrlChange,
        label           = { Text(stringResource(R.string.login_server_label)) },
        placeholder     = { Text(stringResource(R.string.login_server_hint)) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(20.dp))
    Button(
        onClick        = viewModel::onContinueClicked,
        enabled        = !ui.isStarting && ui.serverUrl.isNotBlank(),
        modifier       = Modifier.fillMaxWidth().height(52.dp),
        shape          = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        if (ui.isStarting) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier    = Modifier.size(20.dp),
                color       = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Text(stringResource(R.string.login_continue))
        }
    }
}

/**
 * Compact variant of the URL input shown UNDER the LAN list. Same logic
 * but visually demoted — smaller heading, lighter button — so it reads
 * as "fallback for power users" not "the main thing you should do".
 */
@Composable
private fun SecondaryUrlInput(ui: LoginUiState, viewModel: LoginViewModel) {
    Text(
        text       = stringResource(R.string.login_other_server),
        style      = MaterialTheme.typography.labelLarge,
        color      = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))
    OutlinedTextField(
        value           = ui.serverUrl,
        onValueChange   = viewModel::onServerUrlChange,
        placeholder     = { Text(stringResource(R.string.login_server_hint)) },
        singleLine      = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier        = Modifier.fillMaxWidth(),
        shape           = RoundedCornerShape(12.dp),
    )
    Spacer(Modifier.height(12.dp))
    TextButton(
        onClick  = viewModel::onContinueClicked,
        enabled  = !ui.isStarting && ui.serverUrl.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.login_continue))
    }
}

/**
 * Full-screen "Conectando con HubPlay…" while the auto-skip path is
 * waiting for /auth/device/start to come back. Surfaces the server we
 * picked so the user sees the auto-decision explicitly — no naked
 * spinner, no mystery delay before the QR appears.
 */
@Composable
private fun AutoConnectingOverlay(ui: LoginUiState) {
    val picked = ui.lanDiscovery.firstOrNull { it.url == ui.serverUrl }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier            = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector        = Icons.Outlined.Lan,
                contentDescription = null,
                tint               = MaterialTheme.colorScheme.primary,
                modifier           = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text       = stringResource(R.string.login_auto_connecting),
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text      = picked?.displayName ?: ui.serverUrl,
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text  = ui.serverUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        CircularProgressIndicator(
            strokeWidth = 2.dp,
            modifier    = Modifier.size(22.dp),
            color       = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Stage 2 — Pairing (QR + manual code)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PairingForm(ui: LoginUiState, viewModel: LoginViewModel, isWide: Boolean) {
    val start = ui.pairingStart ?: return

    Text(
        text      = stringResource(R.string.login_pairing_title),
        style     = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center,
        fontWeight = FontWeight.SemiBold,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text      = stringResource(R.string.login_pairing_subtitle),
        style     = MaterialTheme.typography.bodyMedium,
        color     = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier  = Modifier.widthIn(max = 560.dp),
    )
    Spacer(Modifier.height(28.dp))

    if (isWide) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment     = Alignment.Top,
        ) {
            QrOptionCard(start.verifyUrlComplete, modifier = Modifier.weight(1f))
            CodeOptionCard(
                userCode  = start.userCode,
                verifyUrl = start.verifyUrl,
                modifier  = Modifier.weight(1f),
            )
        }
    } else {
        QrOptionCard(start.verifyUrlComplete, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        CodeOptionCard(
            userCode  = start.userCode,
            verifyUrl = start.verifyUrl,
            modifier  = Modifier.fillMaxWidth(),
        )
    }

    Spacer(Modifier.height(28.dp))
    PollStatusRow(ui.pollStatus)
    Spacer(Modifier.height(12.dp))

    TextButton(onClick = viewModel::onCancelPairing) {
        Text(stringResource(R.string.login_pairing_change_url))
    }
}

@Composable
private fun QrOptionCard(payload: String, modifier: Modifier = Modifier) {
    OptionCard(
        title    = stringResource(R.string.login_pairing_qr_title),
        icon     = Icons.Filled.QrCode2,
        help     = stringResource(R.string.login_pairing_qr_help),
        modifier = modifier,
    ) {
        Box(
            modifier         = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            QrCode(
                payload  = payload,
                size     = 220.dp,
                fgColor  = Color.Black,
                bgColor  = Color.White,
            )
        }
    }
}

@Composable
private fun CodeOptionCard(
    userCode:  String,
    verifyUrl: String,
    modifier:  Modifier = Modifier,
) {
    OptionCard(
        title    = stringResource(R.string.login_pairing_code_title),
        icon     = null,
        help     = stringResource(R.string.login_pairing_code_help),
        modifier = modifier,
    ) {
        Surface(
            color    = MaterialTheme.colorScheme.surfaceVariant,
            shape    = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text       = userCode,
                style      = MaterialTheme.typography.displayMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text      = verifyUrl,
            style     = MaterialTheme.typography.bodySmall,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier  = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Reusable shell for the two pairing options. Same paddings, same elevation,
 * same title typography so QR and code feel like equally-weighted choices
 * rather than primary/secondary.
 */
@Composable
private fun OptionCard(
    title:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector?,
    help:     String,
    modifier: Modifier = Modifier,
    content:  @Composable () -> Unit,
) {
    Surface(
        color          = MaterialTheme.colorScheme.surface,
        shape          = RoundedCornerShape(20.dp),
        tonalElevation = 3.dp,
        modifier       = modifier,
    ) {
        Column(
            modifier            = Modifier.padding(20.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = null,
                        tint               = MaterialTheme.colorScheme.primary,
                        modifier           = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text       = title,
                    style      = MaterialTheme.typography.titleMedium,
                    color      = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(14.dp))
            Text(
                text      = help,
                style     = MaterialTheme.typography.bodySmall,
                color     = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PollStatusRow(status: DeviceCodeStatus?) {
    val message = when (status) {
        is DeviceCodeStatus.Pending,
        null                         -> stringResource(R.string.login_pairing_waiting)
        is DeviceCodeStatus.Approved -> stringResource(R.string.login_pairing_approved)
        is DeviceCodeStatus.Expired  -> stringResource(R.string.login_pairing_expired)
        is DeviceCodeStatus.Failed   -> status.message
    }
    Row(
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier              = Modifier.fillMaxWidth(),
    ) {
        if (status is DeviceCodeStatus.Pending || status == null) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier    = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
    }
}
