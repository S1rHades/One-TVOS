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
        frame = FrameLayout(this)
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
        web.settings.textZoom = s.textZoom
        web.evaluateJavascript(fitScript(s.pageZoom, viewDp()), null)
        val agent = agentFor(WebSettings.getDefaultUserAgent(this), s.tvVersion)
        if (web.settings.userAgentString != agent) {
            web.settings.userAgentString = agent
            if (reloadFor != null) web.reload()
        }
        if (!s.pointer) web.requestFocus()
    }

    /** The viewer's width in dp (web pixels at 100%); 0 before it is laid out. */
    private fun viewDp(): Float = if (::web.isInitialized) web.width / resources.displayMetrics.density else 0f

    @SuppressLint("SetJavaScriptEnabled")
    private fun makeWebView(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.builtInZoomControls = false
        settings.setSupportMultipleWindows(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
        isFocusable = true
        isFocusableInTouchMode = true
        // A new width (the margin turned on or off): fit the page to it again
        addOnLayoutChangeListener { v, l, _, r, _, ol, _, or2, _ ->
            if (r - l != or2 - ol) viewer?.shortcut?.let { (v as WebView).evaluateJavascript(fitScript(it.pageZoom, viewDp()), null) }
        }
        setBackgroundColor(android.graphics.Color.WHITE)
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return when (url.scheme?.lowercase()) {
                    "https", "http" -> false
                    "intent" -> {
                        runCatching { startActivity(Intent.parseUri(url.toString(), Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        true
                    }
                    else -> {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        true
                    }
                }
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                viewer?.error = null
            }

            override fun onPageCommitVisible(view: WebView, url: String?) {
                viewer?.shortcut?.let { view.evaluateJavascript(fitScript(it.pageZoom, viewDp()), null) }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                viewer?.shortcut?.let { view.evaluateJavascript(fitScript(it.pageZoom, viewDp()), null) }
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) viewer?.error = pageProblem(error.errorCode, error.description?.toString())
            }
        }
        webChromeClient = object : WebChromeClient() {
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

    /** A tap's finger is still down on the page. */
    private var tapping = false

    /** One touch event at [x], [y] (the page's pixels), made as a real finger's is: one pointer, a finger, from the touch screen. */
    private fun touch(action: Int, downTime: Long, x: Float, y: Float) {
        val props = arrayOf(MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_FINGER })
        val coords = arrayOf(MotionEvent.PointerCoords().apply { this.x = x; this.y = y; pressure = 1f; size = 1f })
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, 1, props, coords, 0, 0, 1f, 1f, 0, 0, android.view.InputDevice.SOURCE_TOUCHSCREEN, 0)
        web.dispatchTouchEvent(e)
        e.recycle()
    }

    private val controls = object : WebPageControls {
        override fun click(x: Float, y: Float) {
            // A finger's tap, as a touch screen sends it: down, then up a moment later, both at
            // the time they happen. A tap still in progress isn't doubled.
            if (tapping) return
            tapping = true
            val down = SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN, down, x, y)
            web.postDelayed({
                touch(MotionEvent.ACTION_UP, down, x, y)
                tapping = false
            }, TapMs)
        }

        override fun scroll(x: Float, y: Float, dx: Float, dy: Float) {
            val d = resources.displayMetrics.density
            web.evaluateJavascript(scrollScript(x / d, y / d, dx / d, dy / d), null)
        }

        override fun pageKey(event: KeyEvent): Boolean {
            if (!web.hasFocus()) web.requestFocus()
            return web.dispatchKeyEvent(event)
        }

        override fun back(): Boolean {
            if (!web.canGoBack()) return false
            web.goBack()
            return true
        }

        override fun reload() = web.reload()

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
        if (::web.isInitialized) web.onResume()
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

        fun intent(context: Context, id: String): Intent =
            Intent(context, WebAppActivity::class.java).putExtra(ExtraId, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    }
}

/**
 * The browser name a site sees: the TV's own, without the mark that says it is inside an
 * app (some sites refuse those), and with a TV's mark when the TV version is asked for.
 */
fun agentFor(default: String, tv: Boolean): String {
    val plain = default.replace("; wv", "").replace(Regex("\\s*Version/\\d+(\\.\\d+)*"), "")
    return if (tv) plain.replace(" Mobile", "") + " SMART-TV" else plain
}

/** Scrolls the scrollable box under (x, y), or else the page; x, y and the distances are the screen's dp, turned into the page's own pixels at its scale. */
fun scrollScript(x: Float, y: Float, dx: Float, dy: Float): String {
    fun n(f: Float) = String.format(java.util.Locale.ROOT, "%.1f", f)
    return "(function(x,y,dx,dy){var k=(window.visualViewport&&visualViewport.scale)||1;x/=k;y/=k;dx/=k;dy/=k;" +
        "var e=document.elementFromPoint(x,y);" +
        "while(e&&e!==document.body&&e!==document.documentElement){var s=getComputedStyle(e);" +
        "if((dy&&e.scrollHeight>e.clientHeight+1&&/(auto|scroll)/.test(s.overflowY))||(dx&&e.scrollWidth>e.clientWidth+1&&/(auto|scroll)/.test(s.overflowX))){e.scrollBy(dx,dy);return;}" +
        "e=e.parentElement;}window.scrollBy(dx,dy);})(${n(x)},${n(y)},${n(dx)},${n(dy)})"
}

/** Why a page didn't load, in plain words. */
fun pageProblem(code: Int, description: String?): String = when (code) {
    WebViewClient.ERROR_HOST_LOOKUP -> tr("No site at that address, or the TV is offline")
    WebViewClient.ERROR_CONNECT, WebViewClient.ERROR_TIMEOUT -> tr("The site didn't answer")
    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> tr("A secure connection to the site failed")
    else -> description?.takeIf { it.isNotBlank() }?.removePrefix("net::")?.take(80) ?: tr("The page didn't load")
}
