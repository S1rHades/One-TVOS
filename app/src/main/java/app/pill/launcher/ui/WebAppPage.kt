package app.pill.launcher.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.WebShortcut
import app.pill.launcher.hostOf
import app.pill.launcher.nextTextZoom
import app.pill.launcher.tr
import kotlinx.coroutines.delay

/** What the viewer asks of the page under it (a WebView on the TV, a stand-in in tests). */
interface WebPageControls {
    /** A tap at [x], [y] pixels on the page. */
    fun click(x: Float, y: Float)

    /** Scrolls what is under [x], [y] (or the page) by [dx], [dy] pixels. */
    fun scroll(x: Float, y: Float, dx: Float, dy: Float)

    /** Hands a key to the page itself (jumping between links); true if it used it. */
    fun pageKey(event: KeyEvent): Boolean

    /** One page back; false when there is none. */
    fun back(): Boolean

    fun reload()

    /** Leaves a video playing full screen. */
    fun exitFullscreen()
}

/** How far the pointer goes on one press, as a share of the screen's width, and how much faster it gets when held. */
private const val StepShare = 0.014f
private const val MaxSpeedUp = 6f

/** The loading bar and the pointer's centre: blue, seen on light and dark pages. */
private val WebAccent = Color(0xFF3B82F6)

/** Held this long, OK opens the menu rather than clicking. */
private const val HoldMs = 450L

/**
 * The remote's side of a website shown full screen: a pointer the arrows move (it scrolls
 * the page at an edge) and OK clicks with, or the page's own jumping between links; Back
 * goes back a page and leaves the viewer from the first; holding OK (or Menu) opens the
 * viewer's menu. Keys reach it from the activity before the page sees them.
 */
class WebViewer(
    shortcut: WebShortcut,
    private val controls: WebPageControls,
    private val onLeave: () -> Unit,
    private val onSave: (WebShortcut) -> Unit,
) {
    var shortcut by mutableStateOf(shortcut)
        private set
    var size by mutableStateOf(IntSize.Zero)
    var cursor by mutableStateOf(Offset.Unspecified)
        private set
    var menuOpen by mutableStateOf(false)
    /** The OK press that opened the menu is still down. */
    var menuByHold by mutableStateOf(false)
        private set
    var fullscreen by mutableStateOf(false)
    var progress by mutableIntStateOf(0)
    var title by mutableStateOf<String?>(null)
    /** Why the page didn't load; null when it did. */
    var error by mutableStateOf<String?>(null)
    /** Bumped on each key, so the hint goes once the remote is in use. */
    var presses by mutableIntStateOf(0)
        private set

    private var okDown = false
    private var okHeld = false

    /** Applies a change made in the menu, and keeps it for next time. */
    fun change(s: WebShortcut) {
        shortcut = s
        onSave(s)
    }

    /** The pointer, put in the middle the first time the page has a size. */
    private fun here(): Offset {
        if (cursor == Offset.Unspecified && size != IntSize.Zero) cursor = Offset(size.width / 2f, size.height / 2f)
        return cursor
    }

    /** Handles a key; false to let it go on to the page (or the menu, when open). */
    fun key(e: KeyEvent): Boolean {
        if (menuOpen) return false
        if (e.action == KeyEvent.ACTION_DOWN) presses++
        val down = e.action == KeyEvent.ACTION_DOWN
        when (e.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (down) return true
                when {
                    fullscreen -> controls.exitFullscreen()
                    !controls.back() -> onLeave()
                }
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                if (down && e.repeatCount == 0) {
                    menuByHold = false
                    menuOpen = true
                }
                return true
            }
        }
        if (fullscreen) return false
        when (e.keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BUTTON_A -> {
                if (down) {
                    if (e.repeatCount == 0) {
                        okDown = true
                        okHeld = false
                    } else if (okDown && !okHeld && e.eventTime - e.downTime >= HoldMs) {
                        okHeld = true
                        menuByHold = true
                        menuOpen = true
                    }
                    return true
                }
                if (!okDown) return true
                okDown = false
                if (okHeld) return true
                when {
                    error != null -> {
                        error = null
                        controls.reload()
                    }
                    shortcut.pointer -> here().let { controls.click(it.x, it.y) }
                    else -> {
                        val t = e.eventTime
                        controls.pageKey(KeyEvent(t, t, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                        controls.pageKey(KeyEvent(t, t, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (!shortcut.pointer) return controls.pageKey(e)
                if (!down || size == IntSize.Zero) return true
                move(e.keyCode, e.repeatCount)
                return true
            }
        }
        return false
    }

    /** One step of the pointer, faster while held; at an edge the page scrolls instead. */
    private fun move(code: Int, repeat: Int) {
        val at = here()
        val step = size.width * StepShare * (1f + (repeat * 0.35f).coerceAtMost(MaxSpeedUp - 1f))
        val (dx, dy) = when (code) {
            KeyEvent.KEYCODE_DPAD_LEFT -> -step to 0f
            KeyEvent.KEYCODE_DPAD_RIGHT -> step to 0f
            KeyEvent.KEYCODE_DPAD_UP -> 0f to -step
            else -> 0f to step
        }
        val margin = size.height * 0.04f
        val x = (at.x + dx).coerceIn(0f, size.width - 1f)
        val y = (at.y + dy).coerceIn(margin, size.height - margin)
        val blockedX = dx != 0f && x == at.x
        val blockedY = dy != 0f && y == at.y
        if (blockedX || blockedY) {
            // At the edge: the page moves under the pointer, a screen's tenth a press
            val scrollBy = if (blockedY) size.height * 0.1f * (1f + (repeat * 0.25f).coerceAtMost(3f)) else size.width * 0.1f
            controls.scroll(at.x, at.y, if (blockedX) kotlin.math.sign(dx) * scrollBy else 0f, if (blockedY) kotlin.math.sign(dy) * scrollBy else 0f)
        }
        cursor = Offset(x, y)
    }

    fun reloadPage() {
        error = null
        controls.reload()
    }

    fun leave() = onLeave()

    /** The menu went: the OK press that opened it no longer counts. */
    fun menuClosed() {
        menuOpen = false
        menuByHold = false
        okDown = false
        okHeld = false
    }
}

/**
 * Everything the viewer draws over the page: a thin bar while it loads, the pointer, a
 * note on the remote's keys at the start, why a page didn't load, and the menu.
 */
@Composable
fun WebAppPage(viewer: WebViewer, icon: ImageBitmap?, modifier: Modifier = Modifier, page: @Composable () -> Unit) {
    Box(modifier.fillMaxSize().background(Color.White).onSizeChanged { viewer.size = it }) {
        page()
        val s = viewer.shortcut
        if (viewer.progress in 1..99 && !viewer.fullscreen) {
            Box(Modifier.fillMaxWidth(viewer.progress / 100f).height(4.dp).background(WebAccent))
        }
        viewer.error?.let { problem ->
            Box(Modifier.fillMaxSize().background(Palette.Ink), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(620.dp)) {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(84.dp).clip(Squircle))
                        Spacer(Modifier.height(22.dp))
                    }
                    Text(tr("Can't open %1\$s", hostOf(s.url)), color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(10.dp))
                    Text(problem, color = Palette.TextSecondary, fontSize = 19.sp, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(26.dp))
                    Text(tr("OK to try again · Back to leave"), color = Palette.TextTertiary, fontSize = 17.sp)
                }
            }
        }
        if (s.pointer && !viewer.fullscreen && viewer.error == null && viewer.size != IntSize.Zero) {
            val at = viewer.cursor.takeIf { it != Offset.Unspecified } ?: Offset(viewer.size.width / 2f, viewer.size.height / 2f)
            Canvas(Modifier.fillMaxSize()) {
                val r = 15.dp.toPx()
                drawCircle(Color(0x33000000), radius = r + 4.dp.toPx(), center = at + Offset(0f, 2.dp.toPx()))
                drawCircle(Color(0xE6FFFFFF), radius = r, center = at)
                drawCircle(Color(0xFF0B0F1A), radius = r, center = at, style = Stroke(2.5.dp.toPx()))
                drawCircle(WebAccent, radius = 4.dp.toPx(), center = at)
            }
        }
        // How the remote works here, until it has been used a few times
        var hint by remember(s.pointer) { mutableStateOf(true) }
        LaunchedEffect(s.pointer) {
            delay(6000)
            hint = false
        }
        LaunchedEffect(viewer.presses) { if (viewer.presses >= 4) hint = false }
        AnimatedVisibility(
            visible = hint && !viewer.fullscreen && viewer.error == null && !viewer.menuOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        ) {
            Text(
                if (s.pointer) tr("Arrows move the pointer · OK clicks · Hold OK for options")
                else tr("Arrows jump between links · OK opens · Hold OK for options"),
                color = Palette.TextPrimary,
                fontSize = 18.sp,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0xE60B0F1A)).padding(horizontal = 26.dp, vertical = 13.dp),
            )
        }
        if (viewer.menuOpen) {
            MenuCard(
                okHeld = viewer.menuByHold,
                header = {
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(Squircle))
                        Spacer(Modifier.width(14.dp))
                    }
                    Column {
                        Text(s.name, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(viewer.title?.takeIf { it.isNotBlank() && it != s.name } ?: hostOf(s.url), color = Palette.TextTertiary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                choices = listOf(
                    MenuChoice(tr("Reload")) { viewer.reloadPage() },
                    MenuChoice(tr("Text size · %1\$s%%", s.textZoom), tr("OK for the next size")) { viewer.change(s.copy(textZoom = nextTextZoom(s.textZoom))) },
                    if (s.pointer) MenuChoice(tr("Jump between links"), tr("For pages made for a remote")) { viewer.change(s.copy(pointer = false)) }
                    else MenuChoice(tr("Use the pointer"), tr("Arrows move it, OK clicks")) { viewer.change(s.copy(pointer = true)) },
                    MenuChoice(
                        if (s.tvVersion) tr("Ask for the usual version") else tr("Ask for the TV version"),
                        if (s.tvVersion) tr("Now asking for the TV version") else tr("Some sites have one made for a remote"),
                    ) { viewer.change(s.copy(tvVersion = !s.tvVersion)) },
                    MenuChoice(tr("Back to the home screen")) { viewer.leave() },
                ),
                onDismiss = { viewer.menuClosed() },
            )
        }
    }
}
