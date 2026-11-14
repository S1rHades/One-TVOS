package app.pill.launcher

import java.util.Locale

/** What kind of video an address is, for the player to read it right. */
enum class MediaKind { Hls, Dash, File }

/** A video a page asked for: where, what kind, the headers it was asked with, and when. */
data class FoundMedia(val url: String, val kind: MediaKind, val headers: Map<String, String>, val at: Long)

/**
 * The kind of video at [url], from its address: an HLS playlist (.m3u8), a DASH manifest
 * (.mpd) or a whole file (.mp4, .webm, .m4v, .mov, .mkv); null for anything else, a video's
 * pieces (.ts, .m4s) and pictures included.
 */
fun mediaKindOf(url: String): MediaKind? {
    val lower = url.lowercase(Locale.ROOT)
    if (!lower.startsWith("http")) return null
    val path = lower.substringBefore('#').substringBefore('?')
    val query = lower.substringAfter('?', "")
    return when {
        path.endsWith(".m3u8") || path.contains(".m3u8/") || query.contains(".m3u8") || query.contains("format=m3u8") -> MediaKind.Hls
        path.endsWith(".mpd") || query.contains(".mpd") -> MediaKind.Dash
        Regex("\\.(mp4|webm|m4v|mov|mkv)$").containsMatchIn(path) -> MediaKind.File
        else -> null
    }
}

/**
 * The videos the pages in the viewer have asked for, from any frame (a player embedded
 * from another site included), so a full-screen video can be played by the launcher's own
 * player. Safe to call from the WebView's request thread.
 */
class MediaCatch(private val now: () -> Long = { System.currentTimeMillis() }) {
    private val seen = ArrayList<FoundMedia>()

    /** Addresses the launcher's player couldn't play: those go to the site's own full screen. */
    private val failed = HashSet<String>()

    @Synchronized
    fun note(url: String, headers: Map<String, String>) {
        val kind = mediaKindOf(url) ?: return
        if (seen.any { it.url == url }) return
        seen += FoundMedia(url, kind, headers, now())
        if (seen.size > 40) seen.removeAt(0)
    }

    @Synchronized
    fun clear() = seen.clear()

    @Synchronized
    fun failed(url: String) {
        failed += url
    }

    /**
     * The video to play: of those asked for in the last ten minutes and not failed, a stream
     * (an HLS or DASH manifest, the first of its kind asked for since the latest one began,
     * which is the master list rather than one quality's) before a whole file, newest first.
     */
    @Synchronized
    fun pick(): FoundMedia? {
        val recent = seen.filter { now() - it.at < 10 * 60_000L && it.url !in failed }
        val streams = recent.filter { it.kind != MediaKind.File }
        if (streams.isNotEmpty()) {
            // A master playlist comes before its variants: the earliest of the last burst
            val last = streams.last()
            val burst = streams.filter { last.at - it.at < 15_000L && it.kind == last.kind }
            return burst.first()
        }
        return recent.lastOrNull()
    }
}

/** The headers worth passing on to the video's server: who asked (Referer, Origin) and the cookies. */
fun mediaHeaders(request: Map<String, String>, cookies: String?, pageUrl: String?): Map<String, String> {
    val out = LinkedHashMap<String, String>()
    val referer = request.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: pageUrl
    if (!referer.isNullOrBlank()) {
        out["Referer"] = referer
        runCatching { java.net.URI(referer) }.getOrNull()?.let { u -> if (u.scheme != null && u.host != null) out["Origin"] = "${u.scheme}://${u.host}" + (if (u.port > 0) ":${u.port}" else "") }
    }
    request.entries.firstOrNull { it.key.equals("Origin", true) }?.let { out["Origin"] = it.value }
    if (!cookies.isNullOrBlank()) out["Cookie"] = cookies
    return out
}

/** A time as a player shows it: "4:05", "1:02:33". */
fun playerTime(ms: Long): String {
    val s = (ms.coerceAtLeast(0) / 1000)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}
