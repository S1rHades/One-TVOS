package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** The places the bar leads to, left to right. All apps is below Favorites, so it needs no button. */
enum class NavItem(val label: String) { Home("Home"), Search("Search"), Internet("Internet"), Settings("Settings") }

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
) {
    val network by rememberNetworkStatus()
    // The focused item, or -1 while focus is elsewhere
    var focused by remember { mutableIntStateOf(-1) }
    val active = focused >= 0
    val at = if (active) focused else current?.ordinal ?: -1
    var lastAt by remember { mutableIntStateOf(at.coerceAtLeast(0)) }
    if (at >= 0) lastAt = at
    val markerX by animateDpAsState(BarPad + itemX(lastAt), Motion.glide(), label = "navMarker")
    val markerColor by animateColorAsState(
        when {
            active -> Palette.Focus
            at >= 0 -> Color(0x38FFFFFF)
            else -> Color.Transparent
        },
        tween(Motion.Quick),
        label = "navMarkerColor",
    )
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
                    .offset(x = markerX - BarPad)
                    .size(ItemSize)
                    .shadow(if (active) 12.dp else 0.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
                    .background(markerColor, CircleShape),
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
                    val on = focused == item.ordinal
                    val tint by animateColorAsState(if (on) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "navTint")
                    Box(
                        Modifier
                            .padding(start = if (item.ordinal == 0 || item == NavItem.Internet) 0.dp else ItemGap)
                            .size(ItemSize)
                            .clip50()
                            .semantics { contentDescription = if (item == NavItem.Internet) "Internet: " + network.label else item.label }
                            .onFocusChanged { if (it.isFocused) focused = item.ordinal else if (focused == item.ordinal) focused = -1 }
                            .then(if (item == NavItem.Settings && settingsFocus != null) Modifier.focusRequester(settingsFocus) else Modifier)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(item) },
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
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
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
