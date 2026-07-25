package et.windows.ui

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.awt.image.BufferedImage

/** Renders [text] as a scannable QR code. */
@Composable
fun QrCodeImage(text: String, modifier: Modifier = Modifier) {
    val bitmap = remember(text) { renderQrCode(text, 480) }
    Image(bitmap = bitmap, contentDescription = "QR code", modifier = modifier)
}

private fun renderQrCode(text: String, sizePx: Int) = run {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
    val image = BufferedImage(sizePx, sizePx, BufferedImage.TYPE_INT_RGB)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            image.setRGB(x, y, if (matrix.get(x, y)) 0x000000 else 0xFFFFFF)
        }
    }
    image.toComposeImageBitmap()
}
