package com.alex.hubplay.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.alex.hubplay.data.Content
import com.alex.hubplay.ui.theme.Accent

enum class CardStyle(val aspect: Float, val defaultWidth: Dp) {
    Landscape(16f / 9f, 240.dp),
    Portrait(2f / 3f, 150.dp),
}

/** Escala de la card enfocada — el "pop" estilo Prime/Netflix que hace
 *  que la rejilla se sienta viva en D-pad. 1.07 da el realce sin invadir
 *  de más a las vecinas. */
private const val FOCUSED_SCALE = 1.07f

@Composable
fun MediaCard(
    item:         Content,
    onFocused:    (Content) -> Unit,
    onClick:      (Content) -> Unit,
    style:        CardStyle = CardStyle.Landscape,
    slotWidth:    Dp        = style.defaultWidth,
    modifier:     Modifier  = Modifier,
) {
    // Only Resumable variants (Movie + Episode) ever show a progress bar
    // — series posters and live channels don't carry per-user progress.
    val progressPct = (item as? Content.Resumable)?.progressPct ?: 0f

    var focused by remember { mutableStateOf(false) }

    val cardHeight = when (style) {
        CardStyle.Portrait  -> style.defaultWidth * 1.5f
        CardStyle.Landscape -> style.defaultWidth * (9f / 16f)
    }

    val imageUrl = when (style) {
        CardStyle.Portrait  -> item.posterUrl ?: item.backdropUrl
        CardStyle.Landscape -> item.backdropUrl ?: item.posterUrl
    }

    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(8.dp)

    // Spring NoBouncy: realce inmediato y limpio, sin rebote (que en TV
    // se siente "barato"). La card crece y proyecta una sombra para
    // separarse del fondo y de las vecinas.
    val scale by animateFloatAsState(
        targetValue   = if (focused) FOCUSED_SCALE else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness    = Spring.StiffnessMedium,
        ),
        label = "card-scale",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 16f else 0f,
        label       = "card-elevation",
    )

    Box(
        modifier = modifier
            // zIndex > vecinas para que el realce + sombra se dibuje
            // ENCIMA de las cards adyacentes, no por debajo.
            .zIndex(if (focused) 1f else 0f)
            .scale(scale)
            .width(slotWidth)
            .height(cardHeight)
            .shadow(elevation = elevation.dp, shape = shape, clip = false)
            .clip(shape)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = { onClick(item) },
            )
            .then(
                if (focused) Modifier.border(
                    width = 3.dp,
                    color = Color.White,
                    shape = shape,
                ) else Modifier,
            ),
    ) {
        AsyncImage(
            model              = imageUrl,
            contentDescription = item.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize(),
        )

        // Scrim + título: aparecen sólo al enfocar (estilo Prime Video).
        // El degradado oscuro de abajo garantiza legibilidad del título
        // sobre cualquier artwork claro y da el acabado "editorial".
        AnimatedVisibility(
            visible  = focused,
            enter    = fadeIn(),
            exit     = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            ) {
                Text(
                    text       = item.title,
                    color      = Color.White,
                    style      = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        if (progressPct > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                LinearProgressIndicator(
                    progress   = { progressPct },
                    modifier   = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color      = Accent,
                    trackColor = Color.Transparent,
                )
            }
        }
    }
}
