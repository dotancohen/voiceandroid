package com.dotancohen.voiceandroid.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** The QR code of a setup text (Stage 9, PAIR-1), as ZXing draws it. */
object QrCode {
    /** The modules of the code: true where a square is dark. */
    fun modules(text: String): Array<BooleanArray> {
        val matrix = QRCodeWriter().encode(
            text, BarcodeFormat.QR_CODE, 0, 0,
            // UTF-8 named in the code: ZXing's default is Latin-1, which turns Hebrew into question marks
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1, EncodeHintType.CHARACTER_SET to "UTF-8")
        )
        return Array(matrix.height) { y -> BooleanArray(matrix.width) { x -> matrix.get(x, y) } }
    }

    /** A bitmap of the code, `pixels` wide and high, black on white. */
    fun bitmap(text: String, pixels: Int): Bitmap {
        val modules = modules(text)
        val side = modules.size
        val scale = maxOf(1, pixels / side)
        val size = side * scale
        val colours = IntArray(size * size)
        for (y in 0 until size) {
            val row = modules[y / scale]
            for (x in 0 until size) {
                colours[y * size + x] = if (row[x / scale]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(colours, size, size, Bitmap.Config.RGB_565)
    }
}

/** The code on screen, as large as the width given. */
@Composable
fun QrCodeImage(text: String, modifier: Modifier = Modifier, contentDescription: String = "The code to read with another device") {
    val bitmap = remember(text) { QrCode.bitmap(text, 640).asImageBitmap() }
    Image(bitmap = bitmap, contentDescription = contentDescription, modifier = modifier, contentScale = ContentScale.Fit)
}
