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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import app.pill.launcher.ui.WebAppPage
import app.pill.launcher.ui.WebPageControls
import app.pill.launcher.ui.WebViewer
import kotlin.math.roundToInt

/**
 * A web shortcut, open full screen: the site in a WebView, with the remote steering a
 * pointer over it (or jumping between links), and its own menu on a held OK. One site at a
 * time, in its own task, so Home goes back to the launcher and the site waits in Recents.
 */
class WebAppActivity : ComponentActivity() {
    private lateinit var web: WebView
    private var viewer: WebViewer? = null
    private var fullscreenView: View? = null
    private var fullscreenDone: WebChromeClient.CustomViewCallback? = null
    private lateinit var frame: FrameLayout
    private val shortcuts by lazy { WebShortcuts(this) }

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
        val v = WebViewer(shortcut, controls, onLeave = { finish() }, onSave = { s -> apply(s, reloadFor = viewer?.shortcut); shortcuts.update(s) })
        viewer = v
        apply(shortcut, reloadFor = null)
        if (saved == null || web.restoreState(saved) == null) web.loadUrl(shortcut.url)
        val icon = shortcuts.tile(shortcut).asImageBitmap()
        setContent {
            WebAppPage(v, remember { icon }) {
                AndroidView(factory = { frame.apply { if (web.parent == null) addView(web, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } })
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
                if (!blockingAds || !isAdHost(request.url.host)) return null
                noteBlocked()
                return WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                viewer?.blocked = 0
                viewer?.error = null
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) viewer?.error = pageProblem(error.errorCode, error.description?.toString())
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
                fullscreenView?.let { frame.removeView(it) }
                fullscreenView = view
                fullscreenDone = callback
                frame.addView(view, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                viewer?.fullscreen = true
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

    private val controls = object : WebPageControls {
        override fun click(x: Float, y: Float) {
            // A finger's tap, as a touch screen sends it: down, then up a moment later, both at
            // the time they happen. A tap still in progress isn't doubled.
            if (tapping) return
            tapping = true
            val down = SystemClock.uptimeMillis()
            // In a full-screen video the tap goes to the player, which fills the screen unshrunk
            val player = fullscreenView
            val (px, py) = if (player != null) x to y else onPage(x, y)
            val target = player ?: web
            touch(MotionEvent.ACTION_DOWN, down, px, py, target)
            web.postDelayed({
                touch(MotionEvent.ACTION_UP, down, px, py, target)
                tapping = false
            }, TapMs)
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

        override fun back(): Boolean {
            if (!web.canGoBack()) return false
            web.goBack()
            return true
        }

        override fun reload() = web.reload()

        override fun quietPlayer() {
            // The mouse leaves the video: players hide their controls when it does
            val target = fullscreenView ?: return
            val t = SystemClock.uptimeMillis()
            val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE })
            for (action in listOf(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_EXIT)) {
                val coords = arrayOf(MotionEvent.PointerCoords().apply { x = target.width / 2f; y = target.height - 1f })
                val e = MotionEvent.obtain(t, t, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_MOUSE, 0)
                target.dispatchGenericMotionEvent(e)
                e.recycle()
            }
        }

        override fun exitFullscreen() {
            fullscreenDone?.onCustomViewHidden()
            (web.webChromeClient as? WebChromeClient)?.onHideCustomView()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val v = viewer ?: return super.dispatchKeyEvent(event)
        return v.key(event) || super.dispatchKeyEvent(event)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::web.isInitialized) web.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
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

/** Why a page didn't load, in plain words. */
fun pageProblem(code: Int, description: String?): String = when (code) {
    WebViewClient.ERROR_HOST_LOOKUP -> tr("No site at that address, or the TV is offline")
    WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> tr("The site didn't answer")
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> tr("A secure connection to the site failed")
    else -> description?.takeIf { it.isNotBlank() }?.removePrefix("net::")?.take(80) ?: tr("The page didn't load")
}
