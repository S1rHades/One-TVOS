package app.pill.launcher

import android.content.Context
import org.json.JSONObject

/**
 * A slideshow photo, and who to credit for it (null for the built-in set): the full
 * [credit] with its licence for Settings, and a short [caption] (place and photographer)
 * for the idle screen.
 */
data class Photo(val url: String, val credit: String? = null, val caption: String? = null)

private const val FeedBase = "https://raw.githubusercontent.com/S1rHades/One-TVOS/photos-data/"

/**
 * Today's online nature photos, published by .github/workflows/photo-feed.yml from
 * Wikimedia Commons' Featured Pictures. The list is kept, so the last set (which
 * Coil also keeps on disk) still shows when the TV is offline. Call off the main thread.
 */
class PhotoFeed(context: Context) {
    private val prefs = context.getSharedPreferences("photos", Context.MODE_PRIVATE)

    fun cached(): List<Photo> = runCatching { parsePhotoFeed(prefs.getString("feed", null) ?: return emptyList()) }.getOrDefault(emptyList())

    fun fetch(): Result<List<Photo>> = runCatching {
        val json = httpGet(FeedBase + "photos.json")
        parsePhotoFeed(json).also {
            check(it.isNotEmpty()) { "The photo list is empty" }
            prefs.edit().putString("feed", json).apply()
        }
    }
}

fun parsePhotoFeed(json: String): List<Photo> {
    val list = JSONObject(json).getJSONArray("photos")
    return (0 until list.length()).map { i ->
        val p = list.getJSONObject(i)
        val credit = listOf(p.optString("title"), p.optString("artist"), p.optString("license"))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        val caption = listOf(placeOf(p.optString("title")), p.optString("artist"))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        Photo(FeedBase + p.getString("file"), "$credit · Wikimedia Commons".removePrefix(" · "), caption.ifEmpty { null })
    }
}

/**
 * The place in a Commons title, without the dates, numbers and camera codes around it:
 * "Iceland - 2017-02-22 - Gullfoss - 3684" is "Iceland, Gullfoss".
 */
fun placeOf(title: String): String {
    val parts = title.replace('_', ' ')
        .split(Regex("""\s+[-–—]\s+|,\s*|\s*[()]\s*"""))
        .map { it.trim() }
        .filter { it.isNotEmpty() && it.none(Char::isDigit) }
        .distinct()
    return parts.take(3).joinToString(", ")
}
