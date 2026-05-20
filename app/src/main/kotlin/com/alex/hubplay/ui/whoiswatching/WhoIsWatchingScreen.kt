package com.alex.hubplay.ui.whoiswatching

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.Profile
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.BgBase
import com.alex.hubplay.ui.theme.TextSecondary

/**
 * "Who's watching?" picker.
 *
 * Shown after device pairing when the account has multiple profiles
 * (siblings under the same parent). One row of round avatars + names
 * — picking a non-PIN profile switches the session token immediately,
 * picking a PIN-protected one opens a 4-digit input dialog.
 *
 * Auto-skip cases (handled by [WhoIsWatchingViewModel]): zero profiles
 * (server unreachable, treated as "go to Home anyway") and exactly one
 * profile (solo account — pin and forward). The screen never visibly
 * renders in those branches.
 */
@Composable
fun WhoIsWatchingScreen(
    viewModel:      WhoIsWatchingViewModel,
    onNavigateHome: () -> Unit,
    onSignOut:      () -> Unit,
) {
    val ui by viewModel.ui.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                Effect.NavigateHome,
                Effect.SkipToHome -> onNavigateHome()
                Effect.SignOut    -> onSignOut()
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = BgBase) {
        Box(
            modifier         = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                ui.isLoading -> LoadingState()
                ui.error != null && ui.profiles.isEmpty() -> ErrorState(
                    onRetry   = viewModel::load,
                    onSignOut = viewModel::signOut,
                )
                else -> ProfileGrid(
                    profiles  = ui.profiles,
                    switching = ui.switching,
                    onSelect  = viewModel::select,
                )
            }
        }
    }

    ui.pendingProfile?.let { target ->
        PinDialog(
            target       = target,
            isSubmitting = ui.switching,
            isError      = ui.pinError,
            onSubmit     = viewModel::submitPin,
            onDismiss    = viewModel::dismissPinDialog,
        )
    }
}

@Composable
private fun LoadingState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = Accent)
        Spacer(Modifier.height(16.dp))
        Text(
            text  = stringResource(R.string.who_loading),
            color = TextSecondary,
        )
    }
}

@Composable
private fun ErrorState(onRetry: () -> Unit, onSignOut: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text       = stringResource(R.string.who_error),
            style      = MaterialTheme.typography.bodyLarge,
            color      = MaterialTheme.colorScheme.onBackground,
            textAlign  = TextAlign.Center,
            modifier   = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.action_retry))
            }
            TextButton(onClick = onSignOut) {
                Text(stringResource(R.string.settings_action_logout))
            }
        }
    }
}

@Composable
private fun ProfileGrid(
    profiles:  List<Profile>,
    switching: Boolean,
    onSelect:  (Profile) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier.widthIn(max = 960.dp).fillMaxWidth(),
    ) {
        Text(
            text       = stringResource(R.string.who_title),
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text  = stringResource(R.string.who_subtitle),
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(40.dp))

        // 4 columns when there's room, fewer when narrower — Adaptive
        // keeps the tiles big enough that D-pad focus reads cleanly on
        // a 10-foot UI without us hand-rolling a breakpoint.
        LazyVerticalGrid(
            columns               = GridCells.Adaptive(minSize = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            verticalArrangement   = Arrangement.spacedBy(32.dp),
            modifier              = Modifier.fillMaxWidth(),
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileTile(
                    profile  = profile,
                    enabled  = !switching,
                    onClick  = { onSelect(profile) },
                )
            }
        }
    }
}

@Composable
private fun ProfileTile(
    profile: Profile,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier            = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
    ) {
        AvatarCircle(profile = profile, size = 132.dp)
        Spacer(Modifier.height(14.dp))
        Text(
            text       = profile.displayName.ifBlank { "—" },
            style      = MaterialTheme.typography.titleMedium,
            color      = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Medium,
            textAlign  = TextAlign.Center,
        )
        if (profile.hasPin) {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector        = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint               = TextSecondary,
                    modifier           = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text  = stringResource(R.string.who_locked),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
            }
        }
    }
}

@Composable
private fun AvatarCircle(profile: Profile, size: Dp) {
    val bgColor = remember(profile.avatarColor, profile.displayName) {
        avatarBackgroundColor(profile)
    }
    Box(
        modifier         = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor)
            .border(width = 2.dp, color = Color.White.copy(alpha = 0.06f), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val avatarUrl = profile.avatarUrl
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model              = avatarUrl,
                contentDescription = stringResource(R.string.who_avatar_cd, profile.displayName),
                modifier           = Modifier.size(size).clip(CircleShape),
            )
        } else {
            Text(
                text       = initialsOf(profile.displayName),
                color      = Color.White,
                fontSize   = (size.value * 0.34f).sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PinDialog(
    target:       Profile,
    isSubmitting: Boolean,
    isError:      Boolean,
    onSubmit:     (String) -> Unit,
    onDismiss:    () -> Unit,
) {
    var pin by remember(target.id) { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.who_pin_dialog_title))
        },
        text = {
            Column {
                Text(
                    text  = stringResource(R.string.who_pin_dialog_subtitle, target.displayName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value           = pin,
                    onValueChange   = { value -> pin = value.filter(Char::isDigit).take(MAX_PIN_LEN) },
                    label           = { Text(stringResource(R.string.who_pin_input_hint)) },
                    singleLine      = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    isError         = isError,
                    enabled         = !isSubmitting,
                    modifier        = Modifier.fillMaxWidth(),
                )
                if (isError) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text  = stringResource(R.string.who_pin_error_invalid),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pin) },
                enabled = pin.length == MAX_PIN_LEN && !isSubmitting,
            ) {
                Text(stringResource(R.string.who_pin_action_unlock))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSubmitting) {
                Text(stringResource(R.string.who_pin_action_cancel))
            }
        },
    )
}

private const val MAX_PIN_LEN = 4

/**
 * Mirrors `avatarColorFor(seed)` in web/src/utils/avatarColor.ts so the
 * same username paints the same colour across web + Android. FNV-1a
 * 32-bit hash of the seed string, modulo the palette size.
 */
private fun avatarBackgroundColor(profile: Profile): Color {
    profile.avatarColor?.let { hex ->
        val parsed = parseHexColor(hex)
        if (parsed != null) return parsed
    }
    val seed = profile.displayName.ifBlank { profile.id }
    if (seed.isEmpty()) return AVATAR_PALETTE[0]
    var h = 0x811c9dc5.toInt()
    for (ch in seed) {
        h = h xor ch.code
        h *= 0x01000193
    }
    val idx = (h.toLong() and 0xFFFFFFFFL).toInt() % AVATAR_PALETTE.size
    return AVATAR_PALETTE[idx]
}

private fun parseHexColor(hex: String): Color? {
    val clean = hex.trim().removePrefix("#")
    return runCatching {
        when (clean.length) {
            6 -> Color(("FF$clean").toLong(16))
            8 -> Color(clean.toLong(16))
            else -> null
        }
    }.getOrNull()
}

private fun initialsOf(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val parts = trimmed.split(' ', '.', '-').filter { it.isNotBlank() }
    return when {
        parts.size >= 2 -> "${parts[0].first().uppercaseChar()}${parts[1].first().uppercaseChar()}"
        else            -> parts[0].first().uppercaseChar().toString()
    }
}

/**
 * Same hex values as `AVATAR_PALETTE` in web/src/utils/avatarColor.ts.
 * Keep ordering aligned so the deterministic hash maps to the same
 * colour on both clients.
 */
private val AVATAR_PALETTE: List<Color> = listOf(
    Color(0xFFB91C1C), // rojo
    Color(0xFFC2410C), // naranja
    Color(0xFFA16207), // ámbar
    Color(0xFF15803D), // verde
    Color(0xFF0F766E), // turquesa
    Color(0xFF1D4ED8), // azul
    Color(0xFF6D28D9), // violeta
    Color(0xFFBE185D), // rosa
)

