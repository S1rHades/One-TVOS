package app.pill.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/** Packages of web shortcuts start with this, so they can sit among apps (favourites, names, search). */
const val WebPrefix = "web:"

/** A website kept as a tile on the home screen, opened full screen in the TV viewer. */
data class WebShortcut(
    val id: String,
    val name: String,
    val url: String,
    /** Tell the site it is a TV, for sites that have a TV layout. */
    val tvVersion: Boolean = false,
    /** Text size in percent: 100 to 175. */
    val textZoom: Int = 100,
    /** Steer a pointer with the arrows (true), or jump between links (false). */
    val pointer: Boolean = true,
    /** How big the page is drawn, in percent: under 100 fits more of a wide page on screen. */
    val pageZoom: Int = 100,
) {
    val pkg: String get() = WebPrefix + id
}

/** The tile's package names a web shortcut. */
val AppEntry.isWeb: Boolean get() = pkg.startsWith(WebPrefix)

/** What a link turned out to be. */
enum class LinkKind { Apk, PlayStore, Website }

/** A checked link: what it is, its name and icon, and for an app its package (Play Store) or size (APK). */
data class FoundLink(
    val kind: LinkKind,
    val url: String,
    val name: String,
    val icon: Bitmap? = null,
    val pkg: String? = null,
    val sizeBytes: Long = 0,
)

/** Where adding a link has got to. */
enum class LinkPhase { Idle, Checking, Found, Downloading, Installing, Done, Failed }

data class LinkState(
    val phase: LinkPhase = LinkPhase.Idle,
    /** The link as typed or sent. */
    val link: String = "",
    val found: FoundLink? = null,
    /** 0 to 1 while downloading. */
    val progress: Float = 0f,
    /** What happened, in plain words (Done, Failed). */
    val message: String? = null,
)

/**
 * A link as the launcher opens it: spaces trimmed, https:// added when no scheme was
 * typed, and only http and https web addresses with a real host. Plain http becomes
 * https, since the TV only loads secure pages. Null when it isn't a web link.
 */
fun normaliseLink(text: String): String? {
    val t = text.trim().trim('<', '>', '"', '\'')
    if (t.isEmpty() || t.any { it.isWhitespace() }) return null
    playStoreId(t)?.let { return "https://play.google.com/store/apps/details?id=$it" }
    val withScheme = when {
        t.startsWith("https://", ignoreCase = true) -> t
        t.startsWith("http://", ignoreCase = true) -> "https://" + t.substring(7)
        "://" in t -> return null
        else -> "https://$t"
    }
    val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    val ip = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)
    if (!ip && (!host.contains('.') || host.endsWith('.') || host.startsWith('.'))) return null
    if (!ip && !Regex("^[a-z0-9.-]+$").matches(host)) return null
    return withScheme
}

/** The app id in a Play Store link (play.google.com/store/apps/details?id=…, market://details?id=…); null otherwise. */
fun playStoreId(link: String): String? {
    val uri = runCatching { java.net.URI(link.trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    val fromPlay = (scheme == "https" || scheme == "http") && uri.host?.lowercase() == "play.google.com" &&
        uri.path.orEmpty().startsWith("/store/apps/details")
    val fromMarket = scheme == "market" && (uri.host == "details" || uri.schemeSpecificPart.orEmpty().startsWith("//details"))
    if (!fromPlay && !fromMarket) return null
    val id = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("id=") }?.substringAfter("id=")
        ?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: return null
    return id.takeIf { Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(it) }
}

/** An answer is an Android app: by its type, the file name it is sent as, or its address. */
fun looksLikeApk(url: String, contentType: String?, disposition: String?): Boolean {
    val type = contentType.orEmpty().lowercase()
    if (type.startsWith("application/vnd.android.package-archive")) return true
    if (Regex("filename\\*?=[^;]*\\.apk\\b", RegexOption.IGNORE_CASE).containsMatchIn(disposition.orEmpty())) return true
    val path = runCatching { java.net.URI(url).path }.getOrNull().orEmpty().lowercase()
    return path.endsWith(".apk") && !type.startsWith("text/html")
}

/** The name to give an APK before it is opened: its file name without .apk, from the answer or the address. */
fun apkName(url: String, disposition: String?): String {
    val fromHeader = Regex("filename\\*?=(?:UTF-8'')?\"?([^\";]+)", RegexOption.IGNORE_CASE).find(disposition.orEmpty())?.groupValues?.get(1)
    val raw = fromHeader ?: runCatching { java.net.URI(url).path }.getOrNull().orEmpty().substringAfterLast('/')
    return java.net.URLDecoder.decode(raw, "UTF-8").removeSuffix(".apk").removeSuffix(".APK").ifBlank { hostOf(url) }
}

/** The host of [url] without "www.". */
fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host }.getOrNull().orEmpty().lowercase().removePrefix("www.")

/** An icon a page offers: where it is and how big it says it is (0 when it doesn't say). */
data class IconRef(val url: String, val size: Int)

/** What a page says about itself: its name, its icons and its web app manifest. */
data class SiteInfo(val name: String?, val icons: List<IconRef>, val manifest: String?)

private val TagRe = Regex("<(link|meta|title)\\b([^>]*)>", RegexOption.IGNORE_CASE)
private val AttrRe = Regex("([a-zA-Z:-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))")

private fun attrs(text: String): Map<String, String> = AttrRe.findAll(text).associate {
    it.groupValues[1].lowercase() to htmlText(it.groupValues[3].ifEmpty { it.groupValues[4] }.ifEmpty { it.groupValues[5] })
}

/** The few entities page titles use, decoded. */
private fun htmlText(s: String): String = s
    .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value }
    .replace(Regex("&#x([0-9a-fA-F]+);")) { m -> m.groupValues[1].toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value }
    .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

/** Resolves [href] against [base]; null when it can't be. */
private fun resolve(base: String, href: String): String? = runCatching { URL(URL(base), href).toString() }.getOrNull()

/**
 * Reads a page's head: the app name it gives (application-name, og:site_name, then the
 * title), its icons (apple-touch-icon and icon links, with their sizes) and its manifest.
 */
fun parseSiteInfo(html: String, base: String): SiteInfo {
    val head = html.substringBefore("</head>").take(400_000)
    var appName: String? = null
    var siteName: String? = null
    var title: String? = null
    var manifest: String? = null
    val icons = mutableListOf<IconRef>()
    for (m in TagRe.findAll(head)) {
        val a = attrs(m.groupValues[2])
        when (m.groupValues[1].lowercase()) {
            "title" -> if (title == null) {
                val end = head.indexOf("</title>", m.range.last, ignoreCase = true)
                if (end > 0) title = htmlText(head.substring(m.range.last + 1, end)).trim().replace(Regex("\\s+"), " ")
            }
            "meta" -> {
                val key = (a["name"] ?: a["property"]).orEmpty().lowercase()
                val content = a["content"]?.trim().orEmpty()
                if (content.isEmpty()) continue
                if (key == "application-name" || key == "apple-mobile-web-app-title") appName = appName ?: content
                if (key == "og:site_name") siteName = siteName ?: content
            }
            "link" -> {
                val rel = a["rel"].orEmpty().lowercase().split(Regex("\\s+"))
                val href = a["href"]?.trim().orEmpty()
                if (href.isEmpty() || href.startsWith("data:")) continue
                val url = resolve(base, href) ?: continue
                when {
                    "manifest" in rel -> manifest = manifest ?: url
                    "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel ->
                        icons += IconRef(url, sizeOf(a["sizes"]).takeIf { it > 0 } ?: 180)
                    "icon" in rel -> icons += IconRef(url, sizeOf(a["sizes"]))
                }
            }
        }
    }
    return SiteInfo(listOfNotNull(appName, siteName, title).firstOrNull { it.isNotBlank() }?.take(60), icons, manifest)
}

/** The largest size in a sizes attribute ("32x32 192x192"); 0 when none, 512 for "any". */
private fun sizeOf(sizes: String?): Int {
    if (sizes.isNullOrBlank()) return 0
    if (sizes.trim().equals("any", ignoreCase = true)) return 512
    return sizes.split(Regex("\\s+")).mapNotNull { it.lowercase().substringBefore('x').toIntOrNull() }.maxOrNull() ?: 0
}

/** A web app manifest's name (short_name first: it fits under a tile) and icons. */
fun parseManifest(json: String, base: String): Pair<String?, List<IconRef>> = runCatching {
    val o = JSONObject(json)
    val name = o.optString("short_name").ifBlank { o.optString("name") }.trim().ifBlank { null }
    val icons = mutableListOf<IconRef>()
    val list = o.optJSONArray("icons") ?: JSONArray()
    for (i in 0 until list.length()) {
        val icon = list.optJSONObject(i) ?: continue
        val src = icon.optString("src").ifBlank { null } ?: continue
        if (icon.optString("purpose").contains("monochrome")) continue
        val url = resolve(base, src) ?: continue
        icons += IconRef(url, sizeOf(icon.optString("sizes")))
    }
    name to icons
}.getOrDefault(null to emptyList())

/**
 * The icons to try, best first: the smallest that is at least 144 pixels (sharp on a
 * tile without being huge), then larger ones, then smaller ones, then /favicon.ico.
 */
fun iconsToTry(icons: List<IconRef>, base: String): List<String> {
    val (big, small) = icons.distinctBy { it.url }.partition { it.size >= 144 }
    val ordered = big.sortedBy { it.size } + small.sortedByDescending { it.size }
    return (ordered.map { it.url } + listOfNotNull(resolve(base, "/favicon.ico"))).distinct()
}

/** Web shortcuts as kept in the settings. */
fun webShortcutsJson(list: List<WebShortcut>): String = JSONArray().apply {
    for (s in list) put(
        JSONObject()
            .put("id", s.id).put("name", s.name).put("url", s.url)
            .put("tv", s.tvVersion).put("zoom", s.textZoom).put("pointer", s.pointer)
            .put("page", s.pageZoom),
    )
}.toString()

fun parseWebShortcuts(json: String?): List<WebShortcut> = runCatching {
    val a = JSONArray(json ?: return emptyList())
    (0 until a.length()).mapNotNull { i ->
        val o = a.optJSONObject(i) ?: return@mapNotNull null
        val url = normaliseLink(o.optString("url")) ?: return@mapNotNull null
        WebShortcut(
            id = o.optString("id").ifBlank { return@mapNotNull null },
            name = o.optString("name").ifBlank { hostOf(url) },
            url = url,
            tvVersion = o.optBoolean("tv", false),
            textZoom = o.optInt("zoom", 100).coerceIn(100, 175),
            pointer = o.optBoolean("pointer", true),
            pageZoom = o.optInt("page", 100).coerceIn(PageZooms.last(), 100),
        )
    }
}.getOrDefault(emptyList())

/** The text sizes the viewer steps through. */
val TextZooms = listOf(100, 125, 150, 175)

/** The page sizes the viewer steps through: full size, then smaller to fit wide pages. */
val PageZooms = listOf(100, 90, 80, 67)

/** The next page size after [zoom], back to 100 after the smallest. */
fun nextPageZoom(zoom: Int): Int = PageZooms.firstOrNull { it < zoom } ?: PageZooms.first()

/**
 * The width, in web pixels, the viewer gives a page at [zoom]: a laptop screen's 1280 at
 * 100%, so pages lay out as they do on a computer and the whole width is scaled to the TV;
 * smaller sizes give a wider page (1920 at 67%), drawn smaller.
 */
fun fitWidth(zoom: Int): Int = 1280 * 100 / zoom.coerceIn(PageZooms.last(), 100)

/**
 * Fits a page to the TV: tells it the screen is [fitWidth] web pixels wide and draws it
 * scaled so that width just fills the viewer ([viewDp] wide; 0 while unknown, when the
 * page's own idea of its window is used), whatever the page's own viewport line says, and
 * again if it changes it. Anything still wider is zoomed out until it fits too.
 */
fun fitScript(zoom: Int, viewDp: Float = 0f): String {
    val w = fitWidth(zoom)
    val view = if (viewDp > 0f) String.format(Locale.ROOT, "%.1f", viewDp) else "Math.min(screen.width,window.outerWidth||screen.width)"
    return "(function(w,v){var d=document,h=d.documentElement;window.__pillW=w;window.__pillV=v;" +
        "function vp(){var m=d.querySelector('meta[name=viewport]');" +
        "if(!m){if(!d.head)return;m=d.createElement('meta');m.name='viewport';d.head.appendChild(m);}" +
        "var s=(window.__pillV/window.__pillW).toFixed(4);" +
        "var c='width='+window.__pillW+', initial-scale='+s+', minimum-scale='+s+', maximum-scale='+s;" +
        "if(m.getAttribute('content')!==c)m.setAttribute('content',c);}" +
        "function fit(){h.style.zoom='';var sw=Math.max(h.scrollWidth,d.body?d.body.scrollWidth:0);" +
        "if(sw>window.__pillW+16)h.style.zoom=(window.__pillW/sw).toFixed(3);}" +
        "vp();if(!window.__pillFit){window.__pillFit=1;" +
        "if(d.head)new MutationObserver(vp).observe(d.head,{childList:true,subtree:true,attributes:true});" +
        "window.addEventListener('load',function(){vp();setTimeout(fit,300);});}" +
        "setTimeout(fit,300);})($w,$view)"
}

/** The next text size after [zoom], back to 100 after the largest. */
fun nextTextZoom(zoom: Int): Int = TextZooms.firstOrNull { it > zoom } ?: TextZooms.first()

/** Pixel size of a tile's icon: as sharp as an app's. */
const val TilePx = 216

/**
 * A tile for a website: its icon on a white square (full size when it is a large, square,
 * solid icon, as touch icons are), or the site's first letter on a colour taken from its
 * name when it has none.
 */
fun webTile(icon: Bitmap?, name: String): Bitmap {
    val out = Bitmap.createBitmap(TilePx, TilePx, Bitmap.Config.ARGB_8888)
    val c = Canvas(out)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    if (icon != null && icon.width >= 8) {
        val solid = icon.width >= 120 && icon.width == icon.height && isOpaque(icon)
        if (solid) {
            c.drawBitmap(icon, null, RectF(0f, 0f, TilePx.toFloat(), TilePx.toFloat()), paint)
        } else {
            c.drawColor(AndroidColor.WHITE)
            val inset = TilePx * 0.2f
            c.drawBitmap(icon, null, RectF(inset, inset, TilePx - inset, TilePx - inset), paint)
        }
        return out
    }
    val hsv = floatArrayOf((name.lowercase().hashCode().toLong() and 0xFFFF).toFloat() % 360f, 0.55f, 0.78f)
    c.drawColor(AndroidColor.HSVToColor(hsv))
    paint.color = AndroidColor.WHITE
    paint.textSize = TilePx * 0.5f
    paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    paint.textAlign = Paint.Align.CENTER
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
    val y = TilePx / 2f - (paint.descent() + paint.ascent()) / 2f
    c.drawText(letter, TilePx / 2f, y, paint)
    return out
}

/** No see-through pixels at the edges (sampled): a full-bleed icon. */
private fun isOpaque(b: Bitmap): Boolean {
    val n = b.width - 1
    return listOf(0 to 0, n to 0, 0 to n, n to n, n / 2 to 0, 0 to n / 2).all { (x, y) -> AndroidColor.alpha(b.getPixel(x, y)) > 240 }
}

/** The web shortcuts, kept in the launcher's settings, and their tiles in the app's files. */
class WebShortcuts(private val context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val dir = File(context.filesDir, "web").apply { mkdirs() }

    fun read(): List<WebShortcut> = parseWebShortcuts(prefs.getString(Key, null))

    fun write(list: List<WebShortcut>) {
        prefs.edit().putString(Key, webShortcutsJson(list)).apply()
        val keep = list.map { "${it.id}.png" }.toSet()
        dir.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
    }

    fun find(id: String): WebShortcut? = read().firstOrNull { it.id == id }

    fun update(changed: WebShortcut) = write(read().map { if (it.id == changed.id) changed else it })

    fun saveTile(id: String, tile: Bitmap) {
        File(dir, "$id.png").outputStream().use { tile.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun tile(s: WebShortcut): Bitmap =
        runCatching { BitmapFactory.decodeFile(File(dir, "${s.id}.png").path) }.getOrNull() ?: webTile(null, s.name)

    /** Each shortcut as a tile among the apps. */
    fun entries(list: List<WebShortcut>): List<AppEntry> = list.map { s ->
        val tile = tile(s)
        AppEntry(
            label = s.name,
            pkg = s.pkg,
            component = android.content.ComponentName(context, WebAppActivity::class.java),
            icon = tile.asImageBitmap(),
            accent = runCatching { accentOf(tile) }.getOrDefault(DefaultAccent),
            category = categoryOf(-1, false, s.name, hostOf(s.url)),
        )
    }

    companion object {
        const val Key = "webShortcuts"
    }
}

/**
 * Adding an app or a website from a link: checks what the link is, then downloads and
 * installs an APK, opens the Play Store on an app's page, or makes a web shortcut. A new
 * app is pinned to the favourites once Android says it is installed ([pending]); a
 * website at once ([pins]).
 */
class LinkAdder(private val context: Context, private val shortcuts: WebShortcuts) {
    val state = MutableStateFlow(LinkState())

    /** A link sent from a phone, waiting on the page to be checked; null once taken. */
    val offered = MutableStateFlow<String?>(null)

    /** Tiles to pin to the favourites: a web shortcut just made, or an app just installed. */
    val pins = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** The web shortcuts changed (made or removed). */
    val changed = MutableStateFlow(0)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    init {
        // Android's answer to an install it couldn't do
        scope.launch {
            linkInstallProblems.collect { problem ->
                if (problem != null && state.value.phase == LinkPhase.Installing) {
                    state.value = state.value.copy(phase = LinkPhase.Failed, message = problem)
                    linkInstallProblems.value = null
                }
            }
        }
    }

    /** Packages to pin once installed, from an APK or the Play Store. */
    var pending: Set<String>
        get() = prefs.getStringSet("pendingPins", emptySet()).orEmpty()
        set(value) { prefs.edit().putStringSet("pendingPins", value).apply() }

    /** Android installed [pkg]: pinned if it came from a link. True if it did. */
    fun installed(pkg: String): Boolean {
        if (pkg !in pending) return false
        pending = pending - pkg
        pins.tryEmit(pkg)
        if (state.value.phase == LinkPhase.Installing) {
            state.value = state.value.copy(phase = LinkPhase.Done, message = tr("Installed · added to Favorites"))
        }
        return true
    }

    fun check(text: String) {
        val link = normaliseLink(text)
        if (link == null) {
            state.value = LinkState(LinkPhase.Failed, text, message = tr("That isn't a web link. Check it and try again"))
            return
        }
        job?.cancel()
        state.value = LinkState(LinkPhase.Checking, link)
        job = scope.launch {
            state.value = try {
                LinkState(LinkPhase.Found, link, withContext(Dispatchers.IO) { probe(link) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LinkState(LinkPhase.Failed, link, message = tr("Couldn't open the link: %1\$s", linkProblem(e)))
            }
        }
    }

    fun cancel() {
        job?.cancel()
        state.value = LinkState()
    }

    /** Does what the found link is for. */
    fun add() {
        val s = state.value
        val found = s.found ?: return
        when (found.kind) {
            LinkKind.Website -> {
                val id = UUID.randomUUID().toString().take(8)
                shortcuts.write(shortcuts.read() + WebShortcut(id, found.name, found.url))
                shortcuts.saveTile(id, webTile(found.icon, found.name))
                changed.value++
                pins.tryEmit(WebPrefix + id)
                state.value = s.copy(phase = LinkPhase.Done, message = tr("%1\$s is on the home screen, in Favorites", found.name))
            }
            LinkKind.PlayStore -> {
                val pkg = found.pkg ?: return
                val opened = listOf(Uri.parse("market://details?id=$pkg"), Uri.parse(found.url)).any { uri ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                }
                if (opened) {
                    pending = pending + pkg
                    state.value = s.copy(phase = LinkPhase.Done, message = tr("Opened in the Play Store · it joins Favorites once installed"))
                } else {
                    state.value = s.copy(phase = LinkPhase.Failed, message = tr("This TV has no Play Store"))
                }
            }
            LinkKind.Apk -> download(found)
        }
    }

    /** Whether Android lets Pill Launcher install apps. */
    fun canInstall(): Boolean = Updater(context).canInstall()

    private fun download(found: FoundLink) {
        val updater = Updater(context)
        if (!updater.canInstall()) {
            val opened = updater.askToInstall()
            state.value = state.value.copy(
                message = if (opened) tr("Allow Pill Launcher to install apps, then choose Download and install again")
                else tr("Allow it in the TV's Settings → Apps → Special app access → Install unknown apps"),
            )
            return
        }
        job?.cancel()
        job = scope.launch {
            state.value = state.value.copy(phase = LinkPhase.Downloading, progress = 0f, message = null)
            try {
                val dir = File(context.cacheDir, "links").apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "app.apk")
                var shown = 0f
                withContext(Dispatchers.IO) {
                    downloadFile(found.url, file, found.sizeBytes, onProgress = { p ->
                        if (p - shown >= 0.01f || p >= 1f) {
                            shown = p
                            scope.launch { state.value = state.value.copy(progress = p) }
                        }
                    })
                }
                @Suppress("DEPRECATION")
                val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
                    ?: error(tr("The download isn't an Android app"))
                val pkg = info.packageName
                if (pkg == context.packageName) error(tr("That is Pill Launcher itself: update it from Settings → TV & system → Updates"))
                val label = info.applicationInfo?.let { app ->
                    app.sourceDir = file.path
                    app.publicSourceDir = file.path
                    runCatching { app.loadLabel(context.packageManager).toString() }.getOrNull()
                } ?: found.name
                pending = pending + pkg
                state.value = state.value.copy(phase = LinkPhase.Installing, found = found.copy(name = label, pkg = pkg))
                withContext(Dispatchers.IO) { installApk(context, file, self = false) }
            } catch (e: CancellationException) {
                state.value = state.value.copy(phase = LinkPhase.Found, progress = 0f)
                throw e
            } catch (e: Exception) {
                state.value = state.value.copy(phase = LinkPhase.Failed, message = tr("Couldn't install it: %1\$s", linkProblem(e)))
            }
        }
    }

    fun stopDownload() {
        job?.cancel()
    }

    /** The web shortcuts made so far. */
    fun list(): List<WebShortcut> = shortcuts.read()

    /** Removes a web shortcut and its tile. */
    fun remove(id: String) {
        shortcuts.write(shortcuts.read().filter { it.id != id })
        changed.value++
    }

    /** What [link] is: follows redirects, then looks at the answer's type, and reads a page's name and icon. */
    private fun probe(link: String): FoundLink {
        playStoreId(link)?.let { pkg ->
            val installed = runCatching { context.packageManager.getPackageInfo(pkg, 0) }.isSuccess
            if (installed) error(tr("That app is already on the TV"))
            // The app's name from its store page ("Netflix - Apps on Google Play"), else its id
            val name = runCatching {
                val c = open(link, "text/html")
                try {
                    if (c.responseCode == 200) playTitle(c.inputStream.use { readUpTo(it, 256 * 1024) }) else null
                } finally {
                    c.disconnect()
                }
            }.getOrNull()?.let { cleanSiteName(it, link) } ?: pkg
            return FoundLink(LinkKind.PlayStore, link, name, pkg = pkg)
        }
        var url = link
        repeat(8) {
            val c = open(url, "text/html,application/xhtml+xml,*/*;q=0.8")
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    url = c.getHeaderField("Location")?.let { URL(URL(url), it).toString() } ?: error(tr("The link led nowhere"))
                    if (!url.startsWith("https://")) url = normaliseLink(url) ?: error(tr("The link led nowhere"))
                    playStoreId(url)?.let { return probe(url) }
                    return@repeat
                }
                if (code >= 400) error(tr("The site answered %1\$s", code))
                val type = c.contentType
                val disposition = c.getHeaderField("Content-Disposition")
                if (looksLikeApk(url, type, disposition)) {
                    return FoundLink(LinkKind.Apk, url, apkName(url, disposition), sizeBytes = c.contentLengthLong.coerceAtLeast(0))
                }
                if (type?.lowercase()?.contains("html") != true) return FoundLink(LinkKind.Website, url, hostOf(url))
                val html = c.inputStream.use { readUpTo(it, 512 * 1024) }
                return site(url, html)
            } finally {
                c.disconnect()
            }
        }
        error(tr("Too many redirects"))
    }

    /** A website's name and icon, from its page and its manifest. */
    private fun site(url: String, html: String): FoundLink {
        val info = parseSiteInfo(html, url)
        var name = info.name
        val icons = info.icons.toMutableList()
        info.manifest?.let { m ->
            runCatching {
                val c = open(m, "application/manifest+json,application/json,*/*")
                try {
                    if (c.responseCode == 200) {
                        val (mName, mIcons) = parseManifest(c.inputStream.use { readUpTo(it, 128 * 1024) }, m)
                        if (mName != null) name = mName
                        icons += mIcons
                    }
                } finally {
                    c.disconnect()
                }
            }
        }
        var icon: Bitmap? = null
        for (candidate in iconsToTry(icons, url).take(5)) {
            icon = runCatching { fetchImage(candidate) }.getOrNull()
            if (icon != null) break
        }
        return FoundLink(LinkKind.Website, url, cleanSiteName(name, url), icon)
    }

    private fun fetchImage(url: String): Bitmap? {
        val c = open(url, "image/*")
        try {
            if (c.responseCode != 200) return null
            val bytes = c.inputStream.use { it.readBytes().takeIf { b -> b.size < 3_000_000 } } ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String, accept: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", BrowserAgent)
            setRequestProperty("Accept", accept)
            setRequestProperty("Accept-Language", Lang.current.tag + ",en;q=0.8")
        }

    private fun readUpTo(input: java.io.InputStream, max: Int): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < max) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toString("UTF-8")
    }
}

/**
 * A site's name for under its tile: the part before " - " or " | " when a title carries a
 * slogan ("YouTube - Broadcast yourself"), and the host when the page gives none.
 */
fun cleanSiteName(name: String?, url: String): String {
    val n = name?.trim().orEmpty()
    if (n.isEmpty()) return hostOf(url).substringBeforeLast('.').replaceFirstChar { it.titlecase(Locale.ROOT) }.ifEmpty { hostOf(url) }
    val parts = n.split(Regex("\\s+[-|–—·:]\\s+"))
    return (if (parts.size > 1 && parts.first().length >= 2) parts.first() else n).take(40)
}

/** The app's name from a Play Store page's title ("Netflix - Apps on Google Play"); null when it has none. */
fun playTitle(html: String): String? =
    Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE).find(html)?.groupValues?.get(1)?.trim()
        ?.takeIf { it.isNotEmpty() && !it.startsWith("Google Play", ignoreCase = true) }

/** How the launcher introduces itself to websites when checking a link: as an ordinary browser. */
const val BrowserAgent = "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"

/** A short, plain reason a link couldn't be checked or downloaded. */
fun linkProblem(e: Throwable): String = when (e) {
    is java.net.UnknownHostException -> tr("no site at that address, or the TV is offline")
    is java.net.SocketTimeoutException -> tr("the connection timed out")
    is javax.net.ssl.SSLException -> tr("a secure connection to the site failed")
    is SecurityException -> tr("Android didn't allow it")
    else -> (e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName).take(80)
}
