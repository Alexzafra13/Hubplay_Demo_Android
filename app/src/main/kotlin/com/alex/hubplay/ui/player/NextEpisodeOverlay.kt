package com.alex.hubplay.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.hubplay.R
import com.alex.hubplay.ui.components.HeroCtaButton
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Netflix-style "Siguiente episodio" card shown over the bottom-right
 * corner when a VOD episode reaches STATE_ENDED and the ViewModel
 * resolved a follow-up.
 *
 *  - Counts down from [COUNTDOWN_SECONDS]; at 0 fires [onPlayNow].
 *  - "Reproducir ya" (focused by default — OK on the remote starts
 *    immediately) and "Cancelar" (dismisses, playback stays parked on
 *    the end screen).
 *
 * The countdown restarts per episode id, so back-to-back finales each
 * get their full window.
 */
@Composable
fun NextEpisodeOverlay(
    next:      NextEpisodeInfo,
    onPlayNow: () -> Unit,
    onCancel:  () -> Unit,
    modifier:  Modifier = Modifier,
) {
    var secondsLeft by remember(next.id) { mutableIntStateOf(COUNTDOWN_SECONDS) }
    // The auto-fire happens seconds after composition — read the latest
    // lambda instead of the one captured when the effect launched.
    val currentOnPlayNow by rememberUpdatedState(onPlayNow)
    LaunchedEffect(next.id) {
        while (secondsLeft > 0) {
            delay(ONE_SECOND_MS)
            secondsLeft -= 1
        }
        currentOnPlayNow()
    }

    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = CARD_ALPHA))
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text       = stringResource(R.string.player_next_episode_label),
            color      = Accent,
            fontSize   = 13.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = listOfNotNull(next.label.takeIf { it.isNotEmpty() }, next.title)
                .joinToString(" — ")
                .ifEmpty { stringResource(R.string.player_next_episode_label) },
            color      = TextPrimary,
            fontSize   = 17.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines   = 2,
            overflow   = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text     = stringResource(R.string.player_next_episode_countdown, secondsLeft),
            color    = TextSecondary,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroCtaButton(
                label          = stringResource(R.string.player_next_episode_play_now),
                icon           = Icons.Filled.PlayArrow,
                primary        = true,
                focusRequester = playFocus,
                onClick        = onPlayNow,
            )
            HeroCtaButton(
                label   = stringResource(R.string.player_next_episode_cancel),
                icon    = Icons.Filled.Close,
                primary = false,
                onClick = onCancel,
            )
        }
    }
}

private const val COUNTDOWN_SECONDS = 8
private const val CARD_ALPHA = 0.85f
private const val ONE_SECOND_MS = 1_000L
