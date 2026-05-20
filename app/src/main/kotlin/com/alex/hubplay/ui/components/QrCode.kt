package com.alex.hubplay.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alex.hubplay.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Self-contained QR code renderer used by the login pairing screen.
 *
 * Uses ZXing's pure-Java encoder to build a BitMatrix, then paints it into
 * an ARGB_8888 Bitmap. The Bitmap is wrapped with `.asImageBitmap()` and
 * remembered against `(payload, sizePx, fgColor, bgColor)` so recompositions
 * (e.g. the polling status ticker) don't re-encode every frame.
 *
 * Why ECC level M (not H)? Higher EC = more modules in the matrix =
 * smaller modules per painted dp. The display is a clean TV / monitor
 * (no occlusion, no print damage, no fingerprints), so the 15% redundancy
 * of M is plenty and we trade the 30%-ish extra modules of H for visibly
 * chunkier blocks — easier for a phone camera to grab from across the
 * room, which is the typical scanning distance to a TV.
 *
 * Default size bumped from 220 → 280 dp for the same reason: a TV is a
 * far-field display, the QR has to be physically large enough that each
 * module is several mm wide at the user's seating distance.
 */
@Composable
fun QrCode(
    payload:   String,
    modifier:  Modifier = Modifier,
    size:      Dp       = 280.dp,
    fgColor:   Color    = Color.Black,
    bgColor:   Color    = Color.White,
    padding:   Dp       = 12.dp,
) {
    // Convert dp → px once; the encoder needs an integer.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val sizePx  = with(density) { size.toPx() }.toInt().coerceAtLeast(96)

    val bitmap = remember(payload, sizePx, fgColor, bgColor) {
        encodeQr(payload, sizePx, fgColor.toArgb(), bgColor.toArgb())
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.cd_pairing_qr),
            modifier = Modifier.size(size),
        )
    }
}

private fun encodeQr(payload: String, sizePx: Int, fgArgb: Int, bgArgb: Int): Bitmap {
    val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN           to 0,        // we add our own padding via Box
        EncodeHintType.CHARACTER_SET    to "UTF-8",
    )
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (y in 0 until matrix.height) {
        for (x in 0 until matrix.width) {
            bmp.setPixel(x, y, if (matrix.get(x, y)) fgArgb else bgArgb)
        }
    }
    return bmp
}

