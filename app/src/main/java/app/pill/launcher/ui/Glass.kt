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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.State
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A blurred copy of the photo on screen. Glass surfaces draw the part of it that lies
 * behind them, which looks exactly like a live backdrop blur (only the photo is ever
 * behind a panel) but costs nothing per frame and works on every Android version.
 */
val LocalBackdrop = compositionLocalOf<ImageBitmap?> { null }

/**
 * The photo's own colour, softened (see [tintFromPixels]), fading slowly to each new
 * photo's. Glass, the widgets and the focus glow take a little of it. A State, read only
 * while drawing, so a fade redraws those surfaces without recomposing them.
 */
val LocalWallpaperTint = staticCompositionLocalOf<State<Color>> { mutableStateOf(NeutralTint) }

/** The tint for a photo with no colour to speak of (snow, fog, night). */
val NeutralTint = Color(0xFFB8C4DE)

/** How much of the wallpaper's colour glass takes on, and its edges. */
const val GlassTintAlpha = 0.08f
const val EdgeTintAlpha = 0.36f

/** [tintFromPixels] for a (blurred) backdrop. */
fun wallpaperTint(image: ImageBitmap): Color {
    val px = IntArray(image.width * image.height)
    image.readPixels(px)
    return tintFromPixels(px)
}

/**
 * The colour a photo is most about, as a soft pastel: the hue that carries the most
 * colour (24 bins, each pixel weighted by how colourful it is, neighbouring bins
 * together), at a fixed lightness so text over it reads the same on every photo.
 * Greys, near-blacks and near-whites hardly count; a photo made only of them gets
 * [NeutralTint].
 */
fun tintFromPixels(px: IntArray): Color {
    val bins = 24
    val weight = FloatArray(bins)
    val sumX = FloatArray(bins)
    val sumY = FloatArray(bins)
    for (c in px) {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val chroma = max - min
        if (chroma < 0.04f) continue
        val hue = when (max) {
            r -> 60f * (((g - b) / chroma) % 6f)
            g -> 60f * ((b - r) / chroma + 2f)
            else -> 60f * ((r - g) / chroma + 4f)
        }.let { if (it < 0f) it + 360f else it }
        val bin = (hue / (360f / bins)).toInt().coerceIn(0, bins - 1)
        val rad = Math.toRadians(hue.toDouble())
        weight[bin] += chroma
        sumX[bin] += chroma * cos(rad).toFloat()
        sumY[bin] += chroma * sin(rad).toFloat()
    }
    val best = (0 until bins).maxByOrNull { b -> weight[(b + bins - 1) % bins] + weight[b] + weight[(b + 1) % bins] } ?: return NeutralTint
    val near = listOf((best + bins - 1) % bins, best, (best + 1) % bins)
    // Average colourfulness of the whole photo in that hue
    val strength = near.sumOf { weight[it].toDouble() }.toFloat() / px.size.coerceAtLeast(1)
    if (strength < 0.02f) return NeutralTint
    val hue = Math.toDegrees(atan2(near.sumOf { sumY[it].toDouble() }, near.sumOf { sumX[it].toDouble() })).toFloat()
        .let { if (it < 0f) it + 360f else it }
    return Color.hsl(hue, (0.32f + strength * 2.2f).coerceAtMost(0.62f), 0.74f)
}

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

/**
 * Frosted glass: the blurred photo behind this element, a dark tint, a hairline edge and
 * a faint top highlight. Falls back to the plain tint until the first photo has loaded.
 */
@Composable
fun Modifier.glass(shape: Shape, tint: Color = GlassTint): Modifier {
    val backdrop = LocalBackdrop.current
    val wallpaper = LocalWallpaperTint.current
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
            drawRect(wallpaper.value.copy(alpha = GlassTintAlpha))
            // Light catching the top edge
            drawRect(Color(0x1FFFFFFF), size = size.copy(height = 1.dp.toPx()))
        }
        // A hairline edge in the wallpaper's colour (half of it falls outside the clip)
        .drawWithContent {
            drawContent()
            drawOutline(shape.createOutline(size, layoutDirection, this), wallpaper.value.copy(alpha = EdgeTintAlpha), style = Stroke(2.dp.toPx()))
        }
}
