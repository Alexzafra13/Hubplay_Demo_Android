package com.alex.hubplay.ui.home.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import com.alex.hubplay.ui.theme.BgCard
import com.alex.hubplay.ui.theme.BgElevated
import com.alex.hubplay.ui.theme.TextMuted
import com.alex.hubplay.ui.theme.TextPrimary
import com.alex.hubplay.ui.theme.TextSecondary

enum class CardStyle(val aspect: Float, val defaultWidth: Dp) {
    Landscape(16f / 9f, 240.dp),
    Portrait(2f / 3f, 150.dp),
}

/** Escala del artwork enfocado — el "pop" que hace viva la rejilla en
 *  D-pad. 1.07 realza sin invadir a las vecinas. */
private const val FOCUSED_SCALE = 1.07f

/** Alto reservado bajo el artwork para el título (2 líneas) + aire.
 *  Constante pública para que los rails de Home dimensionen sus filas. */
val CARD_CAPTION_HEIGHT = 40.dp

/**
 * Tarjeta de contenido (rails de Home, rejillas de catálogo, sagas).
 *
 * Diseño: artwork con esquinas suaves y, DEBAJO, el título siempre
 * visible (apagado en reposo, primario al enfocar). Una biblioteca sin
 * carátulas (TMDb aún sin configurar, ficheros sin identificar) sigue
 * siendo navegable y legible — antes las tarjetas eran rectángulos
 * negros sobre negro y solo la enfocada mostraba el nombre.
 *
 * Sin artwork, el hueco no queda vacío: degradado de superficie con la
 * inicial del título en grande, el mismo recurso que usa la web.
 */
@Composable
fun MediaCard(
    item:         Content,
    onFocused:    (Content) -> Unit,
    onClick:      (Content) -> Unit,
    style:        CardStyle = CardStyle.Landscape,
    slotWidth:    Dp        = style.defaultWidth,
    showCaption:  Boolean   = true,
    modifier:     Modifier  = Modifier,
) {
    // Solo Movie + Episode (Resumable) llevan progreso; series y canales no.
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

    // Spring sin rebote: realce inmediato y limpio (el rebote en TV se
    // siente barato). La card crece y proyecta sombra para separarse.
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

    Column(
        modifier = modifier
            // zIndex > vecinas para que realce + sombra pinten ENCIMA.
            .zIndex(if (focused) 1f else 0f)
            .width(slotWidth)
            .onFocusChanged { state ->
                focused = state.isFocused
                if (state.isFocused) onFocused(item)
            }
            .clickable(
                interactionSource = interactionSource,
                indication        = null,
                onClick           = { onClick(item) },
            ),
    ) {
        Box(
            modifier = Modifier
                .scale(scale)
                .fillMaxWidth()
                .height(cardHeight)
                .shadow(elevation = elevation.dp, shape = shape, clip = false)
                .clip(shape)
                .then(
                    if (focused) Modifier.border(
                        width = 3.dp,
                        color = Color.White,
                        shape = shape,
                    ) else Modifier,
                ),
        ) {
            ArtworkPlaceholder(title = item.title)
            if (imageUrl != null) {
                AsyncImage(
                    model              = imageUrl,
                    contentDescription = item.title,
                    contentScale       = ContentScale.Crop,
                    modifier           = Modifier.fillMaxSize(),
                )
            }
            if (progressPct > 0f) ProgressStripe(progressPct = progressPct)
        }

        if (showCaption) CardCaption(title = item.title, focused = focused)
    }
}

/** Título bajo el artwork: apagado en reposo, primario y SemiBold al enfocar. */
@Composable
private fun CardCaption(title: String, focused: Boolean) {
    Spacer(Modifier.height(8.dp))
    Text(
        text       = title,
        color      = if (focused) TextPrimary else TextSecondary,
        style      = MaterialTheme.typography.labelLarge,
        fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Medium,
        maxLines   = 2,
        overflow   = TextOverflow.Ellipsis,
        modifier   = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    )
}

/**
 * Fondo para tarjetas sin artwork: degradado de superficie + inicial
 * del título. Se pinta siempre debajo de la imagen, así también cubre
 * el instante de carga y los fallos de red sin parpadeo a negro.
 */
@Composable
private fun BoxScope.ArtworkPlaceholder(title: String) {
    val initial = title.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "·"
    Box(
        modifier = Modifier
            .matchParentSize()
            .background(
                Brush.verticalGradient(
                    0f to BgElevated,
                    1f to BgCard,
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text       = initial,
            color      = TextMuted,
            style      = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Barra fina de progreso de reproducción anclada al borde inferior. */
@Composable
private fun BoxScope.ProgressStripe(progressPct: Float) {
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
