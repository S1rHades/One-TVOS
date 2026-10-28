package app.pill.launcher.ui

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A blurred copy of the photo on screen. Glass surfaces draw the part of it that lies
 * behind them, which looks exactly like a live backdrop blur (only the photo is ever
 * behind a panel) but costs nothing per frame and works on every Android version.
 */
val LocalBackdrop = compositionLocalOf<ImageBitmap?> { null }

/** Downscales [source] to a thumbnail and box-blurs it: about 10 ms, once per photo. */
fun blurredBackdrop(source: Bitmap): ImageBitmap {
    val w = 192
    val h = max(1, (w * source.height.toFloat() / source.width).roundToInt())
    val small = Bitmap.createScaledBitmap(source.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
    val px = IntArray(w * h)
    small.getPixels(px, 0, w, 0, 0, w, h)
    // Three box passes approximate a Gaussian
    repeat(3) {
        boxBlur(px, w, h, radius = 5, horizontal = true)
        boxBlur(px, w, h, radius = 5, horizontal = false)
    }
    small.setPixels(px, 0, w, 0, 0, w, h)
    return small.asImageBitmap()
}

private fun boxBlur(px: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
    val lines = if (horizontal) h else w
    val len = if (horizontal) w else h
    val out = IntArray(len)
    for (line in 0 until lines) {
        fun at(i: Int) = if (horizontal) line * w + i else i * w + line
        var r = 0; var g = 0; var b = 0
        val window = radius * 2 + 1
        for (k in -radius..radius) {
            val c = px[at(k.coerceIn(0, len - 1))]
            r += (c shr 16) and 0xFF; g += (c shr 8) and 0xFF; b += c and 0xFF
        }
        for (i in 0 until len) {
            out[i] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
            val add = px[at((i + radius + 1).coerceAtMost(len - 1))]
            val drop = px[at((i - radius).coerceAtLeast(0))]
            r += ((add shr 16) and 0xFF) - ((drop shr 16) and 0xFF)
            g += ((add shr 8) and 0xFF) - ((drop shr 8) and 0xFF)
            b += (add and 0xFF) - (drop and 0xFF)
        }
        for (i in 0 until len) px[at(i)] = out[i]
    }
}

/** Draws [image] the way the photo is shown: cropped to fill [screen], offset by [origin]. */
fun DrawScope.drawBackdrop(image: ImageBitmap, screen: IntSize, origin: Offset = Offset.Zero) {
    if (screen.width == 0 || screen.height == 0) return
    val scale = max(screen.width.toFloat() / image.width, screen.height.toFloat() / image.height)
    val srcW = (screen.width / scale).roundToInt().coerceAtMost(image.width)
    val srcH = (screen.height / scale).roundToInt().coerceAtMost(image.height)
    drawImage(
        image,
        srcOffset = IntOffset((image.width - srcW) / 2, (image.height - srcH) / 2),
        srcSize = IntSize(srcW, srcH),
        dstOffset = IntOffset(-origin.x.roundToInt(), -origin.y.roundToInt()),
        dstSize = screen,
        filterQuality = FilterQuality.Low,
    )
}

/** Tint over the blurred photo: dark enough for white text at WCAG AA everywhere. */
private val GlassTint = Color(0x9E121826)

/**
 * Frosted glass: the blurred photo behind this element, a dark tint, a hairline edge and
 * a faint top highlight. Falls back to the plain tint until the first photo has loaded.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color = GlassTint): Modifier {
    val backdrop = LocalBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    return this
        .onGloballyPositioned {
            origin = it.positionInRoot()
            screen = it.findRootCoordinates().size
        }
        .clip(shape)
        .drawBehind {
            backdrop?.let { drawBackdrop(it, screen, origin) }
            drawRect(tint)
            // Light catching the top edge
            drawRect(Color(0x1FFFFFFF), size = size.copy(height = 1.dp.toPx()))
        }
        .border(1.dp, Color(0x29FFFFFF), shape)
}
