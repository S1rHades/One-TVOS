package app.pill.launcher

import androidx.compose.ui.graphics.Color
import app.pill.launcher.ui.GlassTint
import app.pill.launcher.ui.Palette
import app.pill.launcher.ui.ScrimStops
import org.junit.Assert.assertTrue
import org.junit.Test
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.pow

/**
 * WCAG AA contrast for every text colour, measured against every bundled photo: the
 * header text over the photo and its scrim, and panel text over frosted glass. The
 * background is the brightest 5% of the area the text can sit on, so a single glint
 * doesn't decide it. Text shadows are ignored; they help, but WCAG doesn't count them.
 * Runs with every build (build.yml) and fails it if any pair drops below the minimum.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ContrastTest {

    private val photos = File("src/main/assets/photos").listFiles { f -> f.extension == "jpg" }.orEmpty().sortedBy { it.name }

    private data class Role(val name: String, val color: Color, val minimum: Double)

    private val roles = listOf(
        Role("clock (large text)", Palette.TextPrimary, 3.0),
        Role("primary", Palette.TextPrimary, 4.5),
        Role("secondary", Palette.TextSecondary, 4.5),
        Role("tertiary", Palette.TextTertiary, 4.5),
    )

    @Test
    fun headerTextOverPhotos() {
        // Clock, date and weather: 4-62% across, 5-22% down the screen
        check("header") { photo -> brightest(photo, 0.04f, 0.05f, 0.62f, 0.22f) { y -> scrimAlpha(y) to Color.Black } }
    }

    @Test
    fun appLabelsOverPhotos() {
        // Labels under the favourites and the hint text near the bottom: 72-94% down
        check("labels") { photo -> brightest(photo, 0.04f, 0.72f, 0.96f, 0.94f) { y -> scrimAlpha(y) to Color.Black } }
    }

    @Test
    fun textOnGlass() {
        // Glass can sit anywhere, over the blurred photo with no scrim
        check("glass") { photo -> brightest(photo, 0f, 0f, 1f, 1f, grid = 24) { GlassTint.alpha to GlassTint.copy(alpha = 1f) } }
    }

    private fun check(surface: String, background: (Bitmap) -> FloatArray) {
        assertTrue("no photos found in src/main/assets/photos", photos.isNotEmpty())
        val failures = mutableListOf<String>()
        val worst = mutableMapOf<String, Double>()
        for (file in photos) {
            val bg = background(BitmapFactory.decodeFile(file.path))
            for (role in roles) {
                val ratio = contrast(over(role.color, bg), bg)
                worst[role.name] = minOf(worst[role.name] ?: Double.MAX_VALUE, ratio)
                if (ratio < role.minimum) {
                    failures += "%s text on %s, photo %s: %.2f : 1 (needs %.1f)".format(role.name, surface, file.name, ratio, role.minimum)
                }
            }
        }
        println("Contrast on $surface, worst over ${photos.size} photos: " + worst.entries.joinToString { "%s %.2f".format(it.key, it.value) })
        assertTrue("Contrast below WCAG AA:\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    /** Height fraction -> black alpha, interpolated between the scrim's stops. */
    private fun scrimAlpha(y: Float): Float {
        val stops = ScrimStops
        for ((a, b) in stops.zipWithNext()) {
            if (y in a.first..b.first) return a.second + (b.second - a.second) * (y - a.first) / (b.first - a.first)
        }
        return stops.last().second
    }

    /**
     * The 95th-percentile background in a region (fractions of the screen), after
     * [overlay] (alpha, colour at a given height) is laid over the photo. The photo is
     * averaged down to a coarse [grid] first, as the eye (and the blur) would.
     */
    private fun brightest(
        photo: Bitmap,
        x0: Float, y0: Float, x1: Float, y1: Float,
        grid: Int = 192,
        overlay: (Float) -> Pair<Float, Color>,
    ): FloatArray {
        val cols = grid
        val rows = grid * photo.height / photo.width
        val samples = mutableListOf<FloatArray>()
        for (r in (y0 * rows).toInt() until (y1 * rows).toInt().coerceAtMost(rows)) {
            val (alpha, tint) = overlay(r.toFloat() / rows)
            for (c in (x0 * cols).toInt() until (x1 * cols).toInt().coerceAtMost(cols)) {
                val px = average(photo, c, r, cols, rows)
                samples += FloatArray(3) { i ->
                    val t = when (i) { 0 -> tint.red; 1 -> tint.green; else -> tint.blue }
                    px[i] * (1 - alpha) + t * alpha
                }
            }
        }
        samples.sortBy { luminance(it) }
        return samples[(samples.size * 0.95).toInt().coerceAtMost(samples.size - 1)]
    }

    /** Mean sRGB colour (0..1) of one cell of a cols x rows grid over the photo. */
    private fun average(photo: Bitmap, c: Int, r: Int, cols: Int, rows: Int): FloatArray {
        val xa = c * photo.width / cols; val xb = ((c + 1) * photo.width / cols).coerceAtLeast(xa + 1)
        val ya = r * photo.height / rows; val yb = ((r + 1) * photo.height / rows).coerceAtLeast(ya + 1)
        var red = 0.0; var green = 0.0; var blue = 0.0; var n = 0
        var y = ya
        while (y < yb) {
            var x = xa
            while (x < xb) {
                val rgb = photo.getPixel(x, y)
                val r8 = (rgb shr 16) and 0xFF
                val g8 = (rgb shr 8) and 0xFF
                val b8 = rgb and 0xFF
                red += r8
                green += g8
                blue += b8
                n++
                x += 2
            }
            y += 2
        }
        return floatArrayOf((red / n / 255).toFloat(), (green / n / 255).toFloat(), (blue / n / 255).toFloat())
    }

    /** [text] (with its alpha) composited over [bg]. */
    private fun over(text: Color, bg: FloatArray) = floatArrayOf(
        text.red * text.alpha + bg[0] * (1 - text.alpha),
        text.green * text.alpha + bg[1] * (1 - text.alpha),
        text.blue * text.alpha + bg[2] * (1 - text.alpha),
    )

    private fun luminance(c: FloatArray): Double {
        fun lin(v: Float) = if (v <= 0.04045f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * lin(c[0]) + 0.7152 * lin(c[1]) + 0.0722 * lin(c[2])
    }

    private fun contrast(a: FloatArray, b: FloatArray): Double {
        val la = luminance(a); val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }
}
