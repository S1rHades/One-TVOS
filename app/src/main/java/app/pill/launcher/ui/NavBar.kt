package app.pill.launcher.ui

import app.pill.launcher.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime

/** The places the bar leads to, left to right. All apps is below Favorites, so it needs no button. */
enum class NavItem(private val labelEn: String) {
    Home("Home"), Search("Search"), Internet("Internet"), Settings("Settings");

    /** In the language on screen. */
    val label: String get() = tr(labelEn)
}

private val ItemSize = 40.dp
private val ItemGap = 4.dp
private val BarPad = 5.dp
/** The divider between finding things (Home, Search) and the TV's own (Wi-Fi, Settings). */
private val DividerRoom = 17.dp
private val OfflineRed = Color(0xFFFF8787)

/** How far the bar sits from the top of the screen. */
val NavTop = 20.dp

/** The bar's height. */
val NavHeight = ItemSize + BarPad * 2

/** Room the bar takes at the top of the screen, margins included: pages and panels start below it. */
val NavRoom = NavTop + NavHeight + 8.dp

/** Where item [i] starts along the bar, from its inner edge. */
private fun itemX(i: Int) = (ItemSize + ItemGap) * i + if (i >= NavItem.Internet.ordinal) DividerRoom else 0.dp

/** How long a new bar waits before it can take focus, so the page it opens with takes it first. */
private const val ReadyMs = 400L

/**
 * The last press on a bar, handed to the bar of the page it opens: [from] is the page the
 * press was made on and [pressed] the item. Only a fresh press counts, and only once.
 */
private object NavPress {
    class Handoff(val from: Int, val pressed: Int)
    private var last: Handoff? = null
    private var at = 0L
    fun press(from: Int, pressed: Int) {
        last = Handoff(from, pressed)
        at = SystemClock.uptimeMillis()
    }
    fun take(): Handoff? {
        val h = last?.takeIf { SystemClock.uptimeMillis() - at < 700 }
        last = null
        return h
    }
}

/** Provided by the home screen, so every page and panel shows the same bar in the same place. */
val LocalNavBar = compositionLocalOf<(@Composable (current: NavItem?) -> Unit)?> { null }

/** The bar, as the home screen provides it, at the top right; nothing outside the home screen. */
@Composable
fun BoxScope.TopNav(current: NavItem?) {
    LocalNavBar.current?.let { bar ->
        Box(Modifier.align(Alignment.TopEnd).padding(top = NavTop, end = Spacing.ScreenH)) { bar(current) }
    }
}

/**
 * The bar at the top right of every screen: Home and Search, a divider, then Wi-Fi (the
 * Internet page) and Settings. A disc marks where you are; while the bar has focus it
 * turns white and glides to the focused item. Nothing shows under the bar.
 */
@Composable
fun NavBar(
    current: NavItem?,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
    settingsFocus: FocusRequester? = null,
    /** Where Down from the bar goes; without it, to whatever is below. */
    onDown: (() -> Unit)? = null,
    /** True for the bar a page opens with: it can't take focus until the page has. */
    waitForPage: Boolean = false,
) {
    val network by rememberNetworkStatus()
    // The focused item, or -1 while focus is elsewhere
    var focused by remember { mutableIntStateOf(-1) }
    val active = focused >= 0
    // Two discs: a grey one that stays on the page you're on, and a white one that follows
    // focus while the bar has it (setting off from the current page, so it glides out of it).
    // A page opens with a bar of its own; when a press in another bar opened it, the grey
    // disc sets off from the page you pressed it on and glides into the item you pressed,
    // where the white disc fades.
    val here = current?.ordinal ?: -1
    val handoff = remember { NavPress.take() }
    val from = handoff?.from ?: -1
    val hereX = remember { Animatable(BarPad + itemX(if (from >= 0) from else here.coerceAtLeast(0)), Dp.VectorConverter) }
    val hereAlpha = remember { Animatable(if (here >= 0 && (from >= 0 || handoff == null)) 1f else 0f) }
    LaunchedEffect(here) {
        launch { hereAlpha.animateTo(if (here >= 0) 1f else 0f, tween(Motion.Quick)) }
        if (here >= 0) {
            if (hereAlpha.value == 0f) hereX.snapTo(BarPad + itemX(here))
            hereX.animateTo(BarPad + itemX(here), Motion.glide())
        }
    }
    // Coming into the bar it sets off from the page you're on; leaving, it fades where it is
    // (gliding back while fading swept a ghost disc across the bar and left the item dark)
    val pressed = handoff?.pressed ?: -1
    val focusX = remember { Animatable(BarPad + itemX(if (pressed >= 0) pressed else here.coerceAtLeast(0)), Dp.VectorConverter) }
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused < 0) {
            wasActive = false
            return@LaunchedEffect
        }
        if (!wasActive) focusX.snapTo(BarPad + itemX(here.coerceAtLeast(0)))
        wasActive = true
        focusX.animateTo(BarPad + itemX(focused), Motion.glide())
    }
    val focusAlpha = remember { Animatable(if (pressed >= 0) 1f else 0f) }
    var handing by remember { mutableStateOf(pressed >= 0) }
    LaunchedEffect(active) {
        // A handed-over white disc lingers while the grey one glides in under it
        if (!active && handing) {
            focusAlpha.animateTo(0f, tween(Motion.Page))
            handing = false
            return@LaunchedEffect
        }
        handing = false
        focusAlpha.animateTo(if (active) 1f else 0f, tween(Motion.Quick))
    }
    // A new window gives focus to the first thing it finds, which is this bar's Home; the
    // page then takes it. Until the page has, the bar can't take focus, so the white disc
    // doesn't dart to Home and back as a page opens.
    var ready by remember { mutableStateOf(!waitForPage) }
    LaunchedEffect(Unit) {
        if (ready) return@LaunchedEffect
        delay(ReadyMs)
        ready = true
    }
    Box(
        modifier.onPreviewKeyEvent { event ->
            if (onDown == null || event.key != Key.DirectionDown) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyDown) onDown()
            true
        },
    ) {
        Box(Modifier.glass(RoundedCornerShape(50)).padding(BarPad)) {
            Box(
                Modifier
                    .offset { IntOffset((hereX.value - BarPad).roundToPx(), 0) }
                    .size(ItemSize)
                    .graphicsLayer { alpha = hereAlpha.value }
                    .background(Color(0x38FFFFFF), CircleShape),
            )
            Box(
                Modifier
                    .offset { IntOffset((focusX.value - BarPad).roundToPx(), 0) }
                    .size(ItemSize)
                    // No glow: the bar's glass clips it, which cut it flat at the bar's edges
                    .graphicsLayer { alpha = focusAlpha.value }
                    .background(Palette.Focus, CircleShape),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                for (item in NavItem.entries) {
                    if (item == NavItem.Internet) {
                        Box(
                            Modifier
                                .padding(horizontal = (DividerRoom - 1.dp) / 2 + ItemGap / 2)
                                .size(width = 1.dp, height = 22.dp)
                                .background(Color(0x40FFFFFF)),
                        )
                    }
                    val on = focused == item.ordinal || (handing && !active && item.ordinal == pressed)
                    val tint by animateColorAsState(if (on) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "navTint")
                    Box(
                        Modifier
                            .padding(start = if (item.ordinal == 0 || item == NavItem.Internet) 0.dp else ItemGap)
                            .size(ItemSize)
                            .clip50()
                            .semantics { contentDescription = if (item == NavItem.Internet) tr("Internet: ") + network.label else item.label }
                            .onFocusChanged { if (it.isFocused) focused = item.ordinal else if (focused == item.ordinal) focused = -1 }
                            .then(if (item == NavItem.Settings && settingsFocus != null) Modifier.focusRequester(settingsFocus) else Modifier)
                            .then(if (ready) Modifier else Modifier.focusProperties { canFocus = false })
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                NavPress.press(from = here, pressed = item.ordinal)
                                onSelect(item)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (item) {
                            NavItem.Home -> HomeIcon(tint, 22.dp)
                            NavItem.Search -> SearchIcon(tint, 22.dp)
                            // Always the plain Wi-Fi symbol (wired or not), crossed out and red when offline
                            NavItem.Internet -> WifiIcon(
                                if (network.link == Link.Offline) network else NetworkStatus(Link.WiFi, 4, false),
                                if (network.link == Link.Offline && !on) OfflineRed else tint,
                                22.dp,
                            )
                            NavItem.Settings -> GearIcon(tint, 23.dp)
                        }
                    }
                }
            }
        }

    }
}

private fun Modifier.clip50(): Modifier = this.then(Modifier.graphicsLayer { shape = CircleShape; clip = true })

/** A house outline, in the rail's stroke weight. */
@Composable
private fun HomeIcon(tint: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(s * 0.14f, s * 0.46f)
            lineTo(s * 0.5f, s * 0.15f)
            lineTo(s * 0.86f, s * 0.46f)
            moveTo(s * 0.24f, s * 0.38f)
            lineTo(s * 0.24f, s * 0.86f)
            lineTo(s * 0.76f, s * 0.86f)
            lineTo(s * 0.76f, s * 0.38f)
            moveTo(s * 0.42f, s * 0.86f)
            lineTo(s * 0.42f, s * 0.62f)
            lineTo(s * 0.58f, s * 0.62f)
            lineTo(s * 0.58f, s * 0.86f)
        }
        drawPath(path, tint, style = stroke)
    }
}

/** "Good evening" and so on, by the hour. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> tr("Good morning")
    in 12..17 -> tr("Good afternoon")
    in 18..22 -> tr("Good evening")
    else -> tr("Good night")
}

/** The greeting above the widgets. The clock and weather cards already say the date and place. */
@Composable
fun Greeting(modifier: Modifier = Modifier) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    Text(
        greetingFor(now.hour),
        style = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight(400), shadow = TextShadow),
        maxLines = 1,
        modifier = modifier,
    )
}
