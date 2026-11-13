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
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.asAndroidPath
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
import app.pill.launcher.nextPageZoom
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
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

/**
 * The pointer's speed, in screen widths a second: it sets off slowly enough to land on a
 * small link, and over [RampS] of holding glides up to crossing the screen in about a second.
 */
private const val StartSpeed = 0.16f
private const val TopSpeed = 1.05f
private const val RampS = 0.9f

/** A quick tap still moves the pointer this far (a share of the screen's width), gliding there. */
private const val NudgeShare = 0.012f

/** The page scrolls this much faster than the pointer moves, once the pointer is at an edge. */
private const val ScrollBoost = 1.6f

/** An arrow with no repeat for this long is taken as let go (its release was lost). */
private const val LostKeyMs = 700L

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

    /** Bumped on each click, for the pointer's press. */
    var clicks by mutableIntStateOf(0)
        private set

    private var okDown = false
    private var okHeld = false

    /** Arrows held now: when each started (frame time, -1 until the next frame) and how far it has moved the pointer. */
    private class Held(var since: Long = -1L, var moved: Float = 0f, var seen: Long = 0L)
    private val held = LinkedHashMap<Int, Held>()
    /** Still to glide after a quick tap. */
    private var nudge = Offset.Zero
    private var lastFrame = 0L
    /** Scrolling owed to the page, sent a few times a second rather than every frame. */
    private var scrollOwed = Offset.Zero
    private var lastScroll = 0L

    /** True while the pointer is gliding: the screen then asks for [tick] each frame. */
    var moving by mutableStateOf(false)
        private set

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
                    shortcut.pointer -> here().let {
                        clicks++
                        controls.click(it.x, it.y)
                    }
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
                if (down) {
                    val h = held.getOrPut(e.keyCode) { Held() }
                    h.seen = e.eventTime
                } else {
                    // Let go: a tap too quick to have moved it far still moves it a little
                    val h = held.remove(e.keyCode)
                    if (h != null && size != IntSize.Zero) {
                        val short = size.width * NudgeShare - h.moved
                        if (short > 0f) nudge += direction(e.keyCode) * short
                    }
                }
                moving = held.isNotEmpty() || nudge != Offset.Zero
                return true
            }
        }
        return false
    }

    private fun direction(code: Int): Offset = when (code) {
        KeyEvent.KEYCODE_DPAD_LEFT -> Offset(-1f, 0f)
        KeyEvent.KEYCODE_DPAD_RIGHT -> Offset(1f, 0f)
        KeyEvent.KEYCODE_DPAD_UP -> Offset(0f, -1f)
        else -> Offset(0f, 1f)
    }

    /** How fast a held arrow moves the pointer after [heldS] seconds, in pixels a second: slow, then smoothly faster. */
    private fun speed(heldS: Float): Float {
        val t = (heldS / RampS).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)
        return size.width * (StartSpeed + (TopSpeed - StartSpeed) * eased)
    }

    /**
     * One frame of the pointer's glide, at [now] (nanoseconds): held arrows move it at their
     * speed, a tap's nudge eases out, and at an edge the page scrolls under it instead.
     * Moving by time rather than by key repeats is what keeps it smooth.
     */
    fun tick(now: Long, uptimeMs: Long = now / 1_000_000) {
        if (size == IntSize.Zero || !shortcut.pointer) {
            held.clear()
            nudge = Offset.Zero
            moving = false
            lastFrame = 0L
            return
        }
        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1e9f).coerceIn(0f, 0.05f)
        lastFrame = now
        // A release that never came (the remote, or another window took it): stop
        held.entries.removeAll { uptimeMs - it.value.seen > LostKeyMs }
        var move = Offset.Zero
        for ((code, h) in held) {
            if (h.since < 0) h.since = now
            val step = speed((now - h.since) / 1e9f) * dt
            h.moved += step
            move += direction(code) * step
        }
        val pushed = move
        if (nudge != Offset.Zero) {
            // The rest of a tap's nudge, eased: most of what is left each frame
            val part = if (nudge.getDistance() < 0.5f) nudge else nudge * (1f - kotlin.math.exp(-dt * 22f))
            move += part
            nudge -= part
        }
        val at = here()
        val edge = size.width * 0.008f
        val x = (at.x + move.x).coerceIn(edge, size.width - edge)
        val y = (at.y + move.y).coerceIn(edge, size.height - edge)
        cursor = Offset(x, y)
        // Past the edge, a held arrow scrolls the page instead
        val overX = if (pushed.x != 0f && (x == edge || x == size.width - edge)) pushed.x else 0f
        val overY = if (pushed.y != 0f && (y == edge || y == size.height - edge)) pushed.y else 0f
        scrollOwed += Offset(overX, overY) * ScrollBoost
        if (scrollOwed != Offset.Zero && (now - lastScroll >= 33_000_000L || held.isEmpty())) {
            controls.scroll(x, y, scrollOwed.x, scrollOwed.y)
            scrollOwed = Offset.Zero
            lastScroll = now
        }
        moving = held.isNotEmpty() || nudge != Offset.Zero
        if (!moving) lastFrame = 0L
    }

    fun reloadPage() {
        error = null
        controls.reload()
    }

    fun leave() = onLeave()

    /** The menu went: the OK press that opened it no longer counts. */
    fun menuClosed() {
        held.clear()
        nudge = Offset.Zero
        moving = false
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
    val s = viewer.shortcut
    // The pointer glides a little each frame while an arrow is held, or a tap's nudge eases out
    LaunchedEffect(viewer.moving) {
        if (viewer.moving) while (isActive) withFrameNanos { viewer.tick(it) }
    }
    Box(modifier.fillMaxSize().background(Palette.Ink)) {
        // The page fills the screen, fitted to it edge to edge
        Box(Modifier.fillMaxSize().background(Color.White).onSizeChanged { viewer.size = it }) {
            page()
            if (viewer.progress in 1..99 && !viewer.fullscreen) {
                Box(Modifier.fillMaxWidth(viewer.progress / 100f).height(4.dp).background(WebAccent))
            }
            if (s.pointer && !viewer.fullscreen && viewer.error == null && viewer.size != IntSize.Zero) {
                Pointer(viewer)
            }
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
                    MenuChoice(tr("Page size · %1\$s%%", s.pageZoom), tr("Smaller fits more of a wide page")) { viewer.change(s.copy(pageZoom = nextPageZoom(s.pageZoom))) },
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

/**
 * The arrow's outline, tip at (0, 0), in units of [PointerUnit]: the classic pointer, a
 * little slimmer, so the tip (where OK clicks) is plain to see.
 */
private val ArrowPoints = listOf(
    0f to 0f, 0f to 17.2f, 4.1f to 13.4f, 6.9f to 19.8f, 9.6f to 18.6f, 6.9f to 12.4f, 12.4f to 12.4f,
)
private val PointerUnit = 1.7.dp

/**
 * The pointer: a dark arrow with a white edge and a soft shadow, drawn as a smooth shape at
 * any size, so it stays sharp on a 4K screen. Its tip is where OK clicks. A click presses it
 * in for a moment, with a ring spreading from the tip.
 */
@Composable
private fun Pointer(viewer: WebViewer) {
    val press = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(viewer.clicks) {
        if (viewer.clicks == 0) return@LaunchedEffect
        press.snapTo(1f)
        press.animateTo(0f, androidx.compose.animation.core.tween(320, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    val shadow = remember {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.TRANSPARENT
            style = android.graphics.Paint.Style.FILL
        }
    }
    // Read while drawing, so each frame of the glide only redraws the pointer
    Canvas(Modifier.fillMaxSize()) {
        val at = viewer.cursor.takeIf { it != Offset.Unspecified } ?: Offset(size.width / 2f, size.height / 2f)
        val p = press.value
        val unit = PointerUnit.toPx() * (1f - 0.12f * p)
        val arrow = androidx.compose.ui.graphics.Path().apply {
            ArrowPoints.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(at.x + x * unit, at.y + y * unit) else lineTo(at.x + x * unit, at.y + y * unit) }
            close()
        }
        // A ring from the tip on a click
        if (p > 0f) {
            drawCircle(Color.White.copy(alpha = 0.55f * p), radius = (6.dp.toPx() + 16.dp.toPx() * (1f - p)), center = at, style = Stroke(2.dp.toPx()))
            drawCircle(WebAccent.copy(alpha = 0.35f * p), radius = (4.dp.toPx() + 12.dp.toPx() * (1f - p)), center = at)
        }
        // The soft shadow under it, a little below and right, as a light from above casts
        drawIntoCanvas { canvas ->
            shadow.setShadowLayer(5.dp.toPx(), 0f, 2.dp.toPx(), android.graphics.Color.argb(110, 0, 0, 0))
            shadow.color = android.graphics.Color.argb(1, 0, 0, 0)
            canvas.nativeCanvas.drawPath(arrow.asAndroidPath(), shadow)
        }
        val round = Stroke(width = 2.6.dp.toPx() * (unit / PointerUnit.toPx()), join = androidx.compose.ui.graphics.StrokeJoin.Round)
        drawPath(arrow, Color.White, style = round)
        drawPath(arrow, Color(0xFF14161C))
        // A faint light along its top edge, for a little depth
        drawPath(arrow, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color.Transparent), startY = at.y, endY = at.y + 14f * unit))
    }
}
