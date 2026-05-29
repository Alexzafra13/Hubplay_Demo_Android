package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.alex.hubplay.R
import com.alex.hubplay.data.Content
import com.alex.hubplay.ui.theme.Accent
import com.alex.hubplay.ui.theme.OnAccent
import com.alex.hubplay.ui.theme.TextSecondary

@Composable
fun HeroInfo(
    item:                Content?,
    onPlay:              (Content?) -> Unit,
    onDetails:           (Content?) -> Unit,
    showControls:        Boolean,
    /** Si false, HeroInfo NO pelea por el foco inicial — útil cuando hay
     *  un foco previo guardado en otro sitio (p.ej. un rail tras back-nav)
     *  que debe ganar la carrera. */
    requestInitialFocus: Boolean = true,
    /** Total de slots del carousel (5 trending). 0 = no mostrar dots. */
    carouselSize:        Int      = 0,
    /** Índice del slot que rinde actualmente. Solo se muestran dots si
     *  el carousel realmente está activo (focusedItem null upstream).
     *  Driven por HomeViewModel.heroSlideIndex. */
    carouselIndex:       Int      = 0,
    /** Callback al pulsar ←/→ con foco en el botón Play. ±1 normalmente. */
    onShiftSlide:        (Int) -> Unit = {},
    /** True cuando cualquier botón del hero (Play o Detalles) tiene foco
     *  — usado por HomeScreen para decidir si el hero ocupa toda la
     *  pantalla (true) o se reduce dejando ver los rails (false). */
    onHeroFocusedChange: (Boolean) -> Unit = {},
    /** FocusRequester del botón Play, hoisted al caller (HomeScreen)
     *  para que el primer rail pueda apuntarlo en `focusProperties.up`
     *  y la navegación ↑ desde un card vuelva al hero. Si es null
     *  HeroInfo crea uno interno y la navegación ↑ depende del focus
     *  engine por defecto. */
    playFocusRequester:  FocusRequester? = null,
    modifier:            Modifier = Modifier,
) {
    if (item == null) return

    val internalPlayRequester = remember { FocusRequester() }
    val playRequester = playFocusRequester ?: internalPlayRequester

    LaunchedEffect(showControls, requestInitialFocus) {
        if (showControls && requestInitialFocus) {
            runCatching { playRequester.requestFocus() }
        }
    }

    Box(
        modifier = modifier.padding(bottom = 4.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        AnimatedContent(
            targetState = item,
            label = "hero-info",
            transitionSpec = {
                (fadeIn(tween(400)) togetherWith fadeOut(tween(250)))
            },
        ) { displayItem ->
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .padding(start = 24.dp, end = 32.dp, bottom = 4.dp),
            ) {
                // Title — large and bold like Prime Video
                if (!displayItem.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = displayItem.logoUrl,
                        contentDescription = displayItem.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(min = 48.dp, max = 90.dp)
                            .widthIn(max = 400.dp),
                    )
                } else {
                    Text(
                        text = displayItem.title,
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 52.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))

                // Meta row: genre · duration · year · rating
                HeroMetaRow(displayItem)

                // El Home hero es minimalista — solo logo + meta + CTAs.
                // La descripción larga vive en Detail screen (donde el
                // usuario explícitamente pide "más info"). Esto reduce
                // ruido visual y deja respirar el backdrop / trailer.

                // CTA buttons
                if (showControls) {
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        var playFocused by remember { mutableStateOf(false) }
                        var detailsFocused by remember { mutableStateOf(false) }
                        val playScale by animateFloatAsState(
                            targetValue = if (playFocused) 1.06f else 1.0f,
                            animationSpec = tween(180),
                            label = "hero-play-scale",
                        )
                        val detailsScale by animateFloatAsState(
                            targetValue = if (detailsFocused) 1.06f else 1.0f,
                            animationSpec = tween(180),
                            label = "hero-details-scale",
                        )
                        Button(
                            onClick = { onPlay(displayItem) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = OnAccent,
                            ),
                            modifier = Modifier
                                .focusRequester(playRequester)
                                .onFocusChanged {
                                    playFocused = it.isFocused
                                    if (it.isFocused) {
                                        onHeroFocusedChange(true)
                                    } else if (!detailsFocused) {
                                        onHeroFocusedChange(false)
                                    }
                                }
                                .scale(playScale)
                                // Capturamos ←/→ ANTES de que llegue al focus
                                // engine para que el carousel del hero rote
                                // sin mover el foco lateralmente (a ningún
                                // sitio, porque el Play es el primer botón).
                                // El usuario percibe: foco en Play, ←→ cambia
                                // de slide y los datos del hero se refrescan.
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown || carouselSize <= 1) {
                                        false
                                    } else {
                                        when (ev.key) {
                                            Key.DirectionLeft -> {
                                                onShiftSlide(-1)
                                                true
                                            }
                                            Key.DirectionRight -> {
                                                onShiftSlide(+1)
                                                true
                                            }
                                            else -> false
                                        }
                                    }
                                }
                                .then(
                                    if (playFocused)
                                        Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                                    else Modifier,
                                ),
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_play), fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { onDetails(displayItem) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .onFocusChanged {
                                    detailsFocused = it.isFocused
                                    if (it.isFocused) {
                                        onHeroFocusedChange(true)
                                    } else if (!playFocused) {
                                        onHeroFocusedChange(false)
                                    }
                                }
                                .scale(detailsScale)
                                .then(
                                    if (detailsFocused)
                                        Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                                    else Modifier,
                                ),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_view_details))
                        }
                    }

                    // Dots indicator del carousel (solo se rinde si el hero
                    // está realmente en modo carousel — i.e. carouselSize >0).
                    // Inspirado en Prime Video: pequeños círculos discretos
                    // que indican posición sin robar atención al backdrop.
                    if (carouselSize > 1) {
                        Spacer(Modifier.height(12.dp))
                        HeroDots(
                            count = carouselSize,
                            activeIndex = carouselIndex.coerceIn(0, carouselSize - 1),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMetaRow(item: Content) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val parts = mutableListOf<@Composable () -> Unit>()

        item.genres.firstOrNull()?.let { genre ->
            parts.add {
                Text(
                    text = genre,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        // Only Movies and Episodes carry a runtime — Series store an
        // "episode average" elsewhere and Live channels are continuous.
        val durationSec = (item as? Content.Resumable)?.durationSec ?: 0L
        if (durationSec > 0) {
            val mins = durationSec / 60
            parts.add {
                Text(
                    text = "${mins} min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        item.year?.let { year ->
            parts.add {
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
        }

        item.rating?.let { rating ->
            parts.add {
                Text(
                    text = "★ ${"%.1f".format(rating)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Accent,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        parts.forEachIndexed { index, composable ->
            if (index > 0) {
                Text(
                    text = "·",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
            }
            composable()
        }
    }
}

/**
 * Discrete row of dots showing carousel position. Active dot is wider
 * + brand colour; inactive dots are subtle white-alpha circles.
 * Non-focusable — the active slide changes via ← / → on the Play
 * button, not via clicking the dots themselves.
 */
@Composable
private fun HeroDots(count: Int, activeIndex: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        repeat(count) { idx ->
            val active     = idx == activeIndex
            val dotShape   = if (active) RoundedCornerShape(3.dp) else CircleShape
            val dotWidth   = if (active) 18.dp else 6.dp
            val dotColor   = if (active) Accent else Color.White.copy(alpha = 0.35f)
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(dotWidth)
                    .clip(dotShape)
                    .background(dotColor),
            )
        }
    }
}
