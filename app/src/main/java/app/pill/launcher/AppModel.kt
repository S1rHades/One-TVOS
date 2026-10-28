package app.pill.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.Color as AndroidColor

data class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: ImageBitmap,
    /** The app's 16:9 TV banner, when it ships one. */
    val banner: ImageBitmap? = null,
    /** A vivid colour from the app's artwork (ARGB), for its glow and the ambient light. */
    val accent: Int = DefaultAccent,
    /**
     * The two layers of an adaptive icon, so the foreground can lift against the background
     * on focus. Drawables, not bitmaps, so this costs no extra memory; null for older icons.
     */
    val iconLayers: IconLayers? = null,
)

/** Background and foreground of an adaptive icon. Drawn on the main thread only. */
class IconLayers(val background: Drawable, val foreground: Drawable)

const val DefaultAccent = 0xFF5B7CFA.toInt()

/** Banner bitmap size: 2x the 320x180 TV banner, sharp on a 1080p screen. */
private const val BANNER_W = 640
private const val BANNER_H = 360

/**
 * The most vivid colour in [bitmap]: sampled on a 24x24 grid, each pixel weighted by its
 * saturation and brightness, then lifted so it glows rather than muddies.
 */
fun accentOf(bitmap: Bitmap): Int {
    val small = Bitmap.createScaledBitmap(bitmap.copy(Bitmap.Config.ARGB_8888, false), 24, 24, true)
    var r = 0f; var g = 0f; var b = 0f; var total = 0f
    val hsv = FloatArray(3)
    for (y in 0 until 24) for (x in 0 until 24) {
        val c = small.getPixel(x, y)
        if (AndroidColor.alpha(c) < 128) continue
        AndroidColor.colorToHSV(c, hsv)
        val weight = hsv[1] * hsv[1] * hsv[2]
        r += AndroidColor.red(c) * weight; g += AndroidColor.green(c) * weight; b += AndroidColor.blue(c) * weight
        total += weight
    }
    if (total < 0.5f) return DefaultAccent
    AndroidColor.RGBToHSV((r / total).toInt(), (g / total).toInt(), (b / total).toInt(), hsv)
    hsv[1] = hsv[1].coerceAtLeast(0.45f)
    hsv[2] = hsv[2].coerceIn(0.75f, 1f)
    return AndroidColor.HSVToColor(hsv)
}

/** Icon bitmap size: sharp at the largest tile (108dp) on a 1080p TV (xhdpi). */
private const val ICON_PX = 216

/** Lists launchable apps (TV apps first, then regular launcher apps), one entry per package. */
fun loadApps(pm: PackageManager, self: String): List<AppEntry> {
    val seen = LinkedHashMap<String, AppEntry>()
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(query, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == self || seen.containsKey(pkg)) continue
            val drawable = try {
                info.activityInfo.loadIcon(pm)
            } catch (e: Exception) {
                continue
            }
            val icon = runCatching { drawable.toBitmap(ICON_PX, ICON_PX) }.getOrNull() ?: continue
            val layers = (drawable as? AdaptiveIconDrawable)?.let { adaptive ->
                val bg = adaptive.background
                val fg = adaptive.foreground
                if (bg != null && fg != null) IconLayers(bg, fg) else null
            }
            // TV apps ship a 16:9 banner; null for phone-style apps
            val banner = runCatching { info.activityInfo.loadBanner(pm)?.toBitmap(BANNER_W, BANNER_H) }.getOrNull()
            seen[pkg] = AppEntry(
                label = info.loadLabel(pm).toString(),
                pkg = pkg,
                component = ComponentName(pkg, info.activityInfo.name),
                icon = icon.asImageBitmap(),
                banner = banner?.asImageBitmap(),
                accent = runCatching { accentOf(banner ?: icon) }.getOrDefault(DefaultAccent),
                iconLayers = layers,
            )
        }
    }
    return seen.values.sortedBy { it.label.lowercase() }
}
