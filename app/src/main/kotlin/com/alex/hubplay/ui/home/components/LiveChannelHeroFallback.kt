package com.alex.hubplay.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.alex.hubplay.data.Content
import com.alex.hubplay.ui.livetv.parseHex
import com.alex.hubplay.ui.theme.BgBase

/**
 * Fondo del hero de Inicio mientras arranca la vista previa de un canal en
 * directo (1,2 s de debounce más la conexión: antes quedaba negro todo ese
 * tiempo). Un tinte suave con el color del canal y su logo a la derecha,
 * donde luego aparece el vídeo; a la izquierda siguen los degradados y el
 * texto del hero. El vídeo se funde encima cuando llega.
 */
@Composable
fun LiveChannelHeroFallback(item: Content.LiveChannel, modifier: Modifier = Modifier) {
    val tint = parseHex(item.logoBg)?.let { lerp(BgBase, it, TINT_FRACTION) } ?: BgBase
    // Muchos logos IPTV dan 404 en el servidor: si falla, iniciales.
    var logoFailed by remember(item.id) { mutableStateOf(false) }
    Box(modifier = modifier.fillMaxSize().background(tint)) {
        // Arriba a la derecha: el hero ocupa el tercio superior y los
        // rails empiezan a media pantalla.
        val logoModifier = Modifier
            .align(Alignment.TopEnd)
            .padding(top = LOGO_TOP_PADDING, end = LOGO_END_PADDING)
            .fillMaxWidth(LOGO_WIDTH_FRACTION)
            .fillMaxHeight(LOGO_HEIGHT_FRACTION)
        if (!item.logoUrl.isNullOrBlank() && !logoFailed) {
            AsyncImage(
                model              = item.logoUrl,
                contentDescription = item.title,
                contentScale       = ContentScale.Fit,
                alpha              = LOGO_ALPHA,
                onState            = { if (it is AsyncImagePainter.State.Error) logoFailed = true },
                modifier           = logoModifier,
            )
        } else {
            Box(modifier = logoModifier, contentAlignment = Alignment.Center) {
                Text(
                    text       = (item.logoInitials ?: item.title).take(INITIALS_MAX),
                    style      = MaterialTheme.typography.displayLarge,
                    color      = parseHex(item.logoFg) ?: Color.White,
                    fontSize   = INITIALS_SIZE,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private const val TINT_FRACTION = 0.25f
private const val LOGO_WIDTH_FRACTION = 0.28f
private const val LOGO_HEIGHT_FRACTION = 0.28f
private const val LOGO_ALPHA = 0.9f
private const val INITIALS_MAX = 3
private val LOGO_END_PADDING = 120.dp
private val LOGO_TOP_PADDING = 80.dp
private val INITIALS_SIZE = 96.sp
