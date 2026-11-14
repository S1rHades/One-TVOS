package app.pill.launcher

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import app.pill.launcher.ui.SiteActions
import app.pill.launcher.ui.SiteScreen
import app.pill.launcher.ui.TvSite
import app.pill.launcher.ui.TvSiteScreen
import app.pill.launcher.ui.WebAppPage
import app.pill.launcher.ui.sameUrl
import app.pill.launcher.ui.WebPageControls
import app.pill.launcher.ui.WebViewer
import kotlin.math.roundToInt

/**
 * A web shortcut, open full screen as a TV app: the site runs in a WebView out of sight, and
 * the launcher draws its own screens from what the page holds (rows of cards, a details
 * screen, the player). A page it can't draw (signing in, a form) shows as itself, with the
 * remote steering a pointer over it. One site at a time, in its own task, so Home goes back
 * to the launcher and the site waits in Recents.
 */
class WebAppActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var viewer: WebViewer? = null
    private var fullscreenView: View? = null

    /** The videos pages ask for, to play one in the launcher's own player. */
    private val catch = MediaCatch()

    /** The page's address, for the request thread (WebView's own is only for the main thread). */
    @Volatile private var pageUrl: String? = null

    /** The launcher's own player, while it plays a page's video. */
    private var tv by androidx.compose.runtime.mutableStateOf<app.pill.launcher.ui.TvPlayer?>(null)
    private var native: NativeVideo? = null
    private var nativeMedia: FoundMedia? = null
    private var fullscreenDone: WebChromeClient.CustomViewCallback? = null
    private lateinit var frame: FrameLayout
    private val shortcuts by lazy { WebShortcuts(this) }

    /** The TV app view, and whether the page itself is shown instead (for a page it can't draw). */
    private var site: TvSite? = null
    private var pageMode by androidx.compose.runtime.mutableStateOf(false)
    /** Back from the page closes the screen it stood for; the page's history entry it was shown at. */
    private var pagePop = false
    private var pageEntry = 0
    private val scans by lazy { SiteScans(this) }
    private val scanner by lazy { scanScript(this) }
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    /** Bumped on each new page, so a reading planned for the last one is dropped. */
    private var scanGen = 0
    private var pageStarts = 0

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(Lang.wrap(base))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shortcut = intent.getStringExtra(ExtraId)?.let { shortcuts.find(it) }
        if (shortcut == null) {
            finish()
            return
        }
        web = makeWebView()
        frame = FitFrame()
        open(shortcut, savedInstanceState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val shortcut = intent.getStringExtra(ExtraId)?.let { shortcuts.find(it) } ?: return
        if (shortcut.id != viewer?.shortcut?.id) {
            web.clearHistory()
            open(shortcut, null)
        }
    }

    private fun open(shortcut: WebShortcut, saved: Bundle?) {
        val v = WebViewer(shortcut, controls, onLeave = { leavePage() }, onSave = { s -> apply(s, reloadFor = viewer?.shortcut); shortcuts.update(s) })
        viewer = v
        apply(shortcut, reloadFor = null)
        val tvSite = TvSite(shortcut.name, siteActions)
        site = tvSite
        pageMode = false
        tvSite.start(shortcut.url, scans.load(shortcut.id))
        if (saved == null || web.restoreState(saved) == null) web.loadUrl(shortcut.url)
        val icon = shortcuts.tile(shortcut).asImageBitmap()
        setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                // The page is always laid out and running, under the TV app view when that shows
                WebAppPage(v, remember { icon }) {
                    AndroidView(factory = { frame.apply { if (web.parent == null) addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } })
                }
                if (!pageMode) TvSiteScreen(tvSite, remember { icon })
                tv?.let { player ->
                    androidx.compose.runtime.key(player) {
                        app.pill.launcher.ui.TvPlayerScreen(player) {
                            AndroidView(factory = { c ->
                                androidx.media3.ui.PlayerView(c).apply {
                                    useController = false
                                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                                    setKeepContentOnPlayerReset(true)
                                    this.player = native?.player
                                }
                            }, modifier = androidx.compose.ui.Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }

    /** Puts a shortcut's settings on the page: text size, the TV version (reloading), and focus for jumping between links. */
    private fun apply(s: WebShortcut, reloadFor: WebShortcut?) {
        val adsChanged = blockingAds != s.blockAds
        blockingAds = s.blockAds
        web.settings.textZoom = s.textZoom
        // A new page width (Page size, the TV version): the page is laid out again at it
        frame.requestLayout()
        val agent = agentFor(WebSettings.getDefaultUserAgent(this), s.tvVersion)
        if (web.settings.userAgentString != agent) {
            web.settings.userAgentString = agent
            if (reloadFor != null) web.reload()
        } else if (adsChanged && reloadFor != null) {
            web.reload()
        }
        if (!s.pointer) web.requestFocus()
    }

    /**
     * Fits a page to the TV without touching the page: the WebView is laid out as a laptop's
     * screen ([fitWidth] web pixels wide, the TV's shape), so the site sees an ordinary
     * computer browser and lays itself out for it, and the whole view is then drawn shrunk
     * to the screen, as a picture would be. (Changing the page's own viewport line, as 4.1.2
     * to 4.1.6 did, made sites whose code checks their page stop working.)
     */
    private inner class FitFrame : FrameLayout(this@WebAppActivity) {
        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            super.onMeasure(widthSpec, heightSpec)
            if (!::web.isInitialized || web.parent !== this || measuredWidth == 0 || measuredHeight == 0) return
            val s = viewer?.shortcut ?: return
            val pageW = (fitWidth(s.pageZoom, s.tvVersion) * resources.displayMetrics.density).roundToInt().coerceAtLeast(measuredWidth)
            val scale = measuredWidth.toFloat() / pageW
            val pageH = (measuredHeight / scale).roundToInt()
            web.measure(MeasureSpec.makeMeasureSpec(pageW, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(pageH, MeasureSpec.EXACTLY))
            web.pivotX = 0f
            web.pivotY = 0f
            web.scaleX = scale
            web.scaleY = scale
        }
    }

    /** Where a point on the screen falls on the page view, which is drawn shrunk. */
    private fun onPage(x: Float, y: Float): Pair<Float, Float> {
        val k = web.scaleX.takeIf { it > 0f } ?: 1f
        return x / k to y / k
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        // New windows go through onCreateWindow (blocked, or opened here when on the same site);
        // a page can't open one without a click at all
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = false
        // As Chrome does: pictures and video a secure page fetches over plain http still show (scripts don't)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(android.graphics.Color.WHITE)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                val blocking = viewer?.shortcut?.blockAds != false
                return when (url.scheme?.lowercase()) {
                    "https", "http" -> {
                        // A page sent to an ad network (the redirects ads make on a click) stays put
                        if (blocking && isAdHost(url.host)) {
                            noteBlocked()
                            true
                        } else {
                            false
                        }
                    }
                    // Another app (the Play Store, say) opens only from a press of OK
                    "intent" -> {
                        if (request.hasGesture() || !blocking) runCatching { startActivity(Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        else noteBlocked()
                        true
                    }
                    else -> {
                        if (request.hasGesture() || !blocking) runCatching { startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        else noteBlocked()
                        true
                    }
                }
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (blockingAds && isAdHost(request.url.host)) {
                    noteBlocked()
                    return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
                }
                // A video, from any frame: noted with how it was asked for, for the launcher's player
                val url = request.url.toString()
                if (mediaKindOf(url) != null) {
                    catch.note(url, mediaHeaders(request.requestHeaders.orEmpty(), runCatching { CookieManager.getInstance().getCookie(url) }.getOrNull(), pageUrl))
                }
                return null
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageUrl = url
                catch.clear()
                scanGen++
                pageStarts++
                viewer?.blocked = 0
                viewer?.error = null
            }

            override fun onPageFinished(view: WebView, url: String?) {
                scheduleScan(600)
            }

            // A site that changes page without loading one (its own script moving on)
            override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                pageUrl = url
                if (!isReload) scheduleScan(1200)
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    val problem = pageProblem(error.errorCode, error.description?.toString())
                    viewer?.error = problem
                    // A screen waiting for this page shows the page, with what went wrong
                    site?.let { s -> if (!pageMode && s.top?.kind == null) showPage(pop = true) }
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            // A new window: none without a click; with one, its address opens here if it is on
            // the same site (a link meant for a new tab), else it is dropped (an ad's pop-up)
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                if (!isUserGesture && blockingAds) {
                    noteBlocked()
                    return false
                }
                val catcher = WebView(this@WebAppActivity)
                catcher.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if (!blockingAds || (sameSite(target, web.url) && !isAdHost(request.url.host))) web.loadUrl(target) else noteBlocked()
                        v.post { v.destroy() }
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = catcher
                resultMsg.sendToTarget()
                // A window that never goes anywhere is let go after a few seconds
                web.postDelayed({ runCatching { catcher.destroy() } }, 5000)
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                viewer?.progress = newProgress
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                viewer?.title = title
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                // A video going full screen plays in the launcher's own player, when it was found;
                // the site's player leaves its full screen
                val media = catch.pick()
                if (media != null) {
                    web.post { runCatching { callback.onCustomViewHidden() } }
                    playNatively(media)
                    return
                }
                fullscreenView?.let { frame.removeView(it) }
                fullscreenView = view
                fullscreenDone = callback
                frame.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                viewer?.fullscreen = true
                // The site's own full screen, for a video the launcher can't play: shown as it is
                site?.starting = false
                if (!pageMode) showPage(pop = false)
            }

            override fun onHideCustomView() {
                fullscreenView?.let { frame.removeView(it) }
                fullscreenView = null
                fullscreenDone = null
                viewer?.fullscreen = false
            }
        }
        // A file the page offers to download: an app goes to the launcher, to install from Settings
        setDownloadListener { url, _, disposition, mimetype, _ ->
            if (looksLikeApk(url, mimetype, disposition)) {
                startActivity(Intent(this@WebAppActivity, MainActivity::class.java).putExtra(MainActivity.ExtraAddLink, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Read off the main thread, by the request filter. */
    @Volatile private var blockingAds = true

    /** One more ad, pop-up or tracker stopped, counted for the menu. */
    private fun noteBlocked() {
        runOnUiThread { viewer?.let { it.blocked++ } }
    }

    /** A tap's finger is still down on the page. */
    private var tapping = false

    /** One touch event at [x], [y] (the page's pixels), made as a real finger's is: one pointer, a finger, from the touch screen. */
    private fun touch(action: Int, downTime: Long, x: Float, y: Float, target: View = web) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        target.dispatchTouchEvent(e)
        e.recycle()
    }

    /** No swipe before this time: one at a time while an arrow is held at the edge. */
    private var swipeUntil = 0L

    /**
     * A finger's swipe across the row under the pointer, for carousels that only move when
     * dragged: from near the side the pointer pushes against, across a third of the screen,
     * so the row's next cards come in from that side.
     */
    private fun swipe(y: Float, toRight: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (now < swipeUntil || tapping) return
        swipeUntil = now + SwipeGapMs
        val w = frame.width.toFloat()
        val fromX = if (toRight) w * 0.78f else w * 0.22f
        val toX = if (toRight) w * 0.42f else w * 0.58f
        val (sx, sy) = onPage(fromX, y)
        val (ex, _) = onPage(toX, y)
        touch(MotionEvent.ACTION_DOWN, now, sx, sy)
        val steps = 10
        for (i in 1..steps) {
            web.postDelayed({
                val f = i / steps.toFloat()
                val eased = 1f - (1f - f) * (1f - f)
                touch(if (i == steps) MotionEvent.ACTION_UP else MotionEvent.ACTION_MOVE, now, sx + (ex - sx) * eased, sy)
            }, i * 16L)
        }
    }

    /** A finger's tap at [x], [y] (the page view's pixels), as a touch screen sends it: down, then up a moment later. A tap still in progress isn't doubled. */
    private fun tap(x: Float, y: Float, target: View = web) {
        if (tapping) return
        tapping = true
        val down = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, down, x, y, target)
        web.postDelayed({
            touch(MotionEvent.ACTION_UP, down, x, y, target)
            tapping = false
        }, TapMs)
    }

    /**
     * Reads the page for the TV app view, a moment after it has loaded: first scrolled down
     * and back (so rows that load on scrolling are there), then read, and read again a few
     * seconds on, for sites that fill in late.
     */
    private fun scheduleScan(delay: Long) {
        val gen = ++scanGen
        main.postDelayed({
            if (gen != scanGen || pageMode) return@postDelayed
            web.evaluateJavascript(PrimeScript, null)
            main.postDelayed({ scan(gen, again = true) }, 1900)
        }, delay)
    }

    private fun scan(gen: Int, again: Boolean) {
        if (gen != scanGen || pageMode || isDestroyed) return
        web.evaluateJavascript(scanner) { raw ->
            if (gen != scanGen || pageMode) return@evaluateJavascript
            val result = parseSiteScan(raw) ?: return@evaluateJavascript
            val app = site ?: return@evaluateJavascript
            app.scanned(result)
            // The first page, kept to open at once next time
            val top = app.top
            if (top != null && top.first && app.tab == 0 && app.stack.size == 1 && result.cardCount >= 4) {
                viewer?.shortcut?.id?.let { scans.save(it, raw) }
            }
            if (again) main.postDelayed({ scan(gen, again = false) }, 3000)
        }
    }

    /** Clicks the element at [path] on the page (a card that is a button), trying again a few times while the page is still coming. */
    private fun clickPath(path: String, tries: Int = 8, done: (Boolean) -> Unit = {}) {
        web.evaluateJavascript(locateScript(path)) { raw ->
            val o = runCatching { org.json.JSONObject(org.json.JSONTokener(raw).nextValue() as String) }.getOrNull()
            if (o == null) {
                if (tries > 1) main.postDelayed({ clickPath(path, tries - 1, done) }, 700) else done(false)
                return@evaluateJavascript
            }
            val d = resources.displayMetrics.density
            // Scrolled into view first; then a finger's tap on its middle
            main.postDelayed({
                tap(o.optDouble("x").toFloat() * d, o.optDouble("y").toFloat() * d)
                done(true)
            }, 250)
        }
    }

    /** Shows the page itself, to steer with the pointer; Back from its first page comes back to the TV app view. */
    private fun showPage(pop: Boolean) {
        pageMode = true
        pagePop = pop
        pageEntry = web.copyBackForwardList().currentIndex
        viewer?.title = web.title
        if (viewer?.shortcut?.pointer == false) web.requestFocus()
    }

    /** Back from the page: to the TV app view (closing the screen the page stood for), or out when there is none. */
    private fun leavePage() {
        val app = site
        if (!pageMode || app == null) {
            finish()
            return
        }
        if (fullscreenView != null) controls.exitFullscreen()
        pageMode = false
        if (pagePop) app.pop() else app.top?.let(siteActions::backTo)
    }

    private val siteActions = object : SiteActions {
        override fun open(card: SiteCard) {
            val screen = site?.top ?: return
            val starts = pageStarts
            val before = web.url
            val url = card.url
            if (url != null) {
                web.loadUrl(url)
            } else {
                card.path?.let { p ->
                    clickPath(p) { clicked ->
                        // A button that opened nothing new: the page itself, to see what it did
                        main.postDelayed({
                            if (site?.top === screen && screen.kind == null && !pageMode && pageStarts == starts && web.url == before) showPage(pop = true)
                        }, if (clicked) 3500 else 0)
                    }
                }
            }
            // A page that never comes: shown as it is
            main.postDelayed({ if (site?.top === screen && screen.kind == null && !pageMode) showPage(pop = true) }, 20_000)
        }

        override fun tab(card: SiteCard) {
            card.url?.let(web::loadUrl)
        }

        override fun backTo(screen: SiteScreen) {
            val target = screen.scan?.url?.ifBlank { null } ?: screen.url ?: return
            val now = web.url
            if (now != null && sameUrl(now, target)) return
            val list = web.copyBackForwardList()
            val prev = list.currentIndex - 1
            if (prev >= 0 && sameUrl(list.getItemAtIndex(prev).url, target)) web.goBack() else web.loadUrl(target)
        }

        override fun play(screen: SiteScreen) {
            val app = site ?: return
            catch.pick()?.let {
                playNatively(it)
                return
            }
            // Press the page's play button (or start its video), then wait for the video it asks for
            app.starting = true
            val detail = screen.scan?.detail
            if (detail?.play != null) clickPath(detail.play) else web.evaluateJavascript(StartVideoScript, null)
            val from = SystemClock.uptimeMillis()
            var nudged = detail?.play == null
            fun wait() {
                main.postDelayed({
                    if (!app.starting || site !== app) return@postDelayed
                    val media = catch.pick()
                    val waited = SystemClock.uptimeMillis() - from
                    when {
                        media != null -> playNatively(media)
                        waited > 14_000 -> {
                            app.starting = false
                            app.notice = tr("No video found here, so here is the page")
                            showPage(pop = false)
                        }
                        else -> {
                            if (!nudged && waited > 5000) {
                                nudged = true
                                web.evaluateJavascript(StartVideoScript, null)
                            }
                            wait()
                        }
                    }
                }, 400)
            }
            wait()
        }

        override fun showPage(pop: Boolean) = this@WebAppActivity.showPage(pop)
        override fun reload() = web.reload()
        override fun leave() = finish()
    }

    private val controls = object : WebPageControls {
        override fun click(x: Float, y: Float) {
            // In a full-screen video the tap goes to the player, which fills the screen unshrunk
            val player = fullscreenView
            val (px, py) = if (player != null) x to y else onPage(x, y)
            tap(px, py, player ?: web)
        }

        override fun scroll(x: Float, y: Float, dx: Float, dy: Float) {
            if (fullscreenView != null) return
            val d = resources.displayMetrics.density
            val k = web.scaleX.takeIf { it > 0f } ?: 1f
            val (px, py) = onPage(x, y)
            // Nothing that scrolls sideways under the pointer: a carousel that moves when dragged
            web.evaluateJavascript(scrollScript(px / d, py / d, dx / k / d, dy / k / d)) { result ->
                if (dx != 0f && result?.contains("none") == true) swipe(y, toRight = dx > 0f)
            }
        }

        override fun pageKey(event: KeyEvent): Boolean {
            val target = fullscreenView ?: web
            if (!target.hasFocus()) target.requestFocus()
            return target.dispatchKeyEvent(event)
        }

        // Back on the page goes back through the pages since it was shown, then to the TV app view
        override fun back(): Boolean {
            if (!web.canGoBack() || web.copyBackForwardList().currentIndex <= pageEntry) return false
            web.goBack()
            return true
        }

        override fun reload() = web.reload()

        override fun playVideo(): Boolean {
            val media = catch.pick() ?: return false
            playNatively(media)
            return true
        }

        override fun exitFullscreen() {
            fullscreenDone?.onCustomViewHidden()
            (web.webChromeClient as? WebChromeClient)?.onHideCustomView()
        }
    }

    /**
     * Plays [media] in the launcher's own player, from where the page's own video had got to
     * (when the page holds it itself), pausing the page's video.
     */
    private fun playNatively(media: FoundMedia) {
        if (tv != null) return
        val js = "(function(){var b=null,a=0;document.querySelectorAll('video').forEach(function(v){var s=v.clientWidth*v.clientHeight;if(s>=a){a=s;b=v;}});" +
            "var t=b?b.currentTime:0;document.querySelectorAll('video').forEach(function(v){try{v.pause()}catch(e){}});return t;})()"
        web.evaluateJavascript(js) { result ->
            if (tv != null) return@evaluateJavascript
            val start = ((result?.trim('"')?.toDoubleOrNull() ?: 0.0) * 1000).toLong()
            site?.starting = false
            val nv = NativeVideo(this, media, web.settings.userAgentString, start)
            native = nv
            nativeMedia = media
            val shown = site?.top?.takeIf { !pageMode }?.let { it.scan?.detail?.title?.ifBlank { null } ?: it.preview?.title?.ifBlank { null } }
            val title = shown ?: viewer?.title?.takeIf { it.isNotBlank() } ?: viewer?.shortcut?.name.orEmpty()
            tv = app.pill.launcher.ui.TvPlayer(title, nv, onExit = { closeNative() })
        }
    }

    /** Leaves the launcher's player for the page, which takes up where it left off (when the page holds the video itself). */
    private fun closeNative() {
        val nv = native ?: return
        val pos = nv.position
        if (nv.error != null) nativeMedia?.let { catch.failed(it.url) }
        nv.release()
        native = null
        nativeMedia = null
        tv = null
        web.evaluateJavascript(
            "(function(){var b=null,a=0;document.querySelectorAll('video').forEach(function(v){var s=v.clientWidth*v.clientHeight;if(s>=a){a=s;b=v;}});if(b&&$pos>0)b.currentTime=${pos / 1000.0};})()",
            null,
        )
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        tv?.let { return it.key(event) || super.dispatchKeyEvent(event) }
        // The TV app view takes every key but the TV's own (volume, Home)
        site?.takeIf { !pageMode }?.let { return it.key(event) || if (event.isSystem) super.dispatchKeyEvent(event) else true }
        val v = viewer ?: return super.dispatchKeyEvent(event)
        return v.key(event) || super.dispatchKeyEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        // Leaving for another app or the home screen pauses the video, as a TV app does
        native?.pause()
        if (::web.isInitialized) web.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) {
            web.onResume()
            // Timers (a carousel's turns, a clock) are shared by every page in the app: keep them running
            web.resumeTimers()
        }
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        native?.release()
        native = null
        if (::web.isInitialized) {
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val ExtraId = "shortcut"

        /** How long a tap's finger stays down. */
        const val TapMs = 70L

        /** Swipes while an arrow is held at the edge come this far apart. */
        const val SwipeGapMs = 420L

        fun intent(context: Context, id: String): Intent =
            Intent(context, WebAppActivity::class.java).putExtra(ExtraId, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}

/**
 * The browser name a site sees. Usually a computer's Chrome (of the TV's own Chrome
 * version): sites then send their full version, made for a mouse, which the pointer
 * works, rather than a phone's, which waits for a touch screen. With the TV version asked
 * for, the TV's own name with a TV's mark, without the marks of a phone or of being inside
 * an app.
 */
fun agentFor(default: String, tv: Boolean): String {
    if (tv) return default.replace("; wv", "").replace(Regex("\\s*Version/\\d+(\\.\\d+)*"), "").replace(" Mobile", "") + " SMART-TV"
    val chrome = Regex("Chrome/([\\d.]+)").find(default)?.groupValues?.get(1) ?: "124.0.0.0"
    return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
}

/** Scrolls the scrollable box under (x, y), or else the page; x, y and the distances are the screen's dp, turned into the page's own pixels at its scale. */
fun scrollScript(x: Float, y: Float, dx: Float, dy: Float): String {
    fun n(f: Float) = String.format(java.util.Locale.ROOT, "%.1f", f)
    return "(function(x,y,dx,dy){var k=(window.visualViewport&&visualViewport.scale)||1;x/=k;y/=k;dx/=k;dy/=k;" +
        "var e=document.elementFromPoint(x,y);" +
        "while(e&&e!==document.body&&e!==document.documentElement){var s=getComputedStyle(e);" +
        "if((dy&&e.scrollHeight>e.clientHeight+1&&/(auto|scroll)/.test(s.overflowY))||(dx&&e.scrollWidth>e.clientWidth+1&&/(auto|scroll)/.test(s.overflowX))){e.scrollBy(dx,dy);return 'box';}" +
        "e=e.parentElement;}" +
        "var r=document.scrollingElement||document.documentElement;" +
        "if(dx&&!dy&&r.scrollWidth<=innerWidth+1)return 'none';" +
        "window.scrollBy(dx,dy);return 'page';})(${n(x)},${n(y)},${n(dx)},${n(dy)})"
}

/** Starts the page's biggest video, for a page with no play button to press. */
const val StartVideoScript = "(function(){var b=null,a=0;document.querySelectorAll('video').forEach(function(v){var s=v.clientWidth*v.clientHeight;if(s>=a){a=s;b=v;}});if(b){try{b.muted=false;b.play()}catch(e){}}})()"

/** Why a page didn't load, in plain words. */
fun pageProblem(code: Int, description: String?): String = when (code) {
    WebViewClient.ERROR_HOST_LOOKUP -> tr("No site at that address, or the TV is offline")
    WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> tr("The site didn't answer")
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> tr("A secure connection to the site failed")
    else -> description?.takeIf { it.isNotBlank() }?.removePrefix("net::")?.take(80) ?: tr("The page didn't load")
}
