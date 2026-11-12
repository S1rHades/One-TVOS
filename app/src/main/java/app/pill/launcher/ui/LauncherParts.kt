package app.pill.launcher.ui

import app.pill.launcher.tr
import androidx.compose.animation.core.Animatable
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.State
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.composed
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppEntry
import app.pill.launcher.News
import app.pill.launcher.WidgetKind
import app.pill.launcher.Weather
import kotlinx.coroutines.delay

// The home screen's smaller pieces: scrolling between its pages, the idle presentation, titles, the toast, and the menus' requests.

/**
 * Page-like D-pad scrolling for the home screen.
 *
 * Anything on the first page scrolls back to the top. In the grid nothing moves while the
 * focused row is inside the current page; moving below it brings the new row up to
 * [PAGE_EDGE] from the top, so the grid turns over a page at a time, and moving above it
 * puts the row [PAGE_EDGE] above the bottom.
 *
 * The chosen scroll target is remembered: Compose asks again on every animation frame
 * with the item's new offset, and answering from the target (not from whether the item is
 * already visible) keeps the animation going all the way to the page.
 */
@OptIn(ExperimentalFoundationApi::class)
internal class PageScrollSpec(
    private val scroll: ScrollState,
    /** Where the first page ends, in px from the top of the content. */
    private val firstPageEnd: Float,
) : BringIntoViewSpec {
    private var target = 0f

    // Between the first page and All apps the move is a page turn: longer, easing out gently.
    // Inside the grid, a row at a time, it stays quick.
    private var pageTurn = false
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = if (pageTurn) tween(Motion.Page + 100, easing = Motion.EnterEasing) else tween(300, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val current = scroll.value.toFloat()
        // Follow scrolling done some other way (touch, or a jump) before this request
        if (!scroll.isScrollInProgress) target = current
        val top = current + offset
        val bottom = top + size
        val edge = containerSize * PAGE_EDGE
        val from = target
        target = when {
            top < firstPageEnd -> 0f
            // Coming down from the first page into All apps: its title goes to the top
            current < firstPageEnd - 1f && bottom <= firstPageEnd + containerSize -> firstPageEnd
            top >= target && bottom <= target + containerSize -> target
            top > target -> top - edge
            else -> bottom - (containerSize - edge)
        }.coerceIn(0f, scroll.maxValue.toFloat())
        // Crossing the fold either way is a page turn
        pageTurn = (from < firstPageEnd - 1f) != (target < firstPageEnd - 1f)
        return target - current
    }
}

/**
 * Fades a grid row as it reaches the bottom of the screen while more rows continue below,
 * so the grid dissolves into the edge like the favourites row. Never the focused row.
 * Position is read in layout and applied in the draw phase, so scrolling only redraws.
 */
@Composable
internal fun Modifier.bottomEdgeFade(scroll: ScrollState, zone: Dp): Modifier {
    val zonePx = with(LocalDensity.current) { zone.toPx() }
    var bottom by remember { mutableFloatStateOf(0f) }
    var screen by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var holdsFocus by remember { mutableStateOf(false) }
    val barPx = with(LocalDensity.current) { NavRoom.toPx() }
    return this
        .onGloballyPositioned {
            bottom = it.positionInRoot().y + it.size.height
            height = it.size.height.toFloat()
            screen = it.findRootCoordinates().size.height.toFloat()
        }
        .onFocusChanged { holdsFocus = it.hasFocus }
        .graphicsLayer {
            val t = if (holdsFocus || !scroll.canScrollForward || screen == 0f) 0f
            else ((bottom - (screen - zonePx)) / zonePx).coerceIn(0f, 1f)
            // A row going up under the bar at the top fades away, so the bar always has its space
            val top = bottom - height
            val u = if (holdsFocus || height == 0f) 0f else ((barPx - top) / (height * 0.6f)).coerceIn(0f, 1f)
            alpha = (1f - 0.75f * t * t * (3 - 2 * t)) * (1f - u * u * (3 - 2 * u))
        }
}

/** A short confirmation message. A new instance restarts the hide timer even for the same text. */
internal class Toast(val message: String)

internal class Refocus(val pkg: String?)

/**
 * Idle presentation of the widget row: at [focus] 1 it sits centred on the screen,
 * enlarged to about 86% of the screen's width (at most 1.4x), offset by [shift]. Read in
 * the draw phase; the row's place is measured before the transform, so it never feeds back.
 */
internal fun Modifier.idlePresentation(focus: () -> Float, shift: () -> Offset): Modifier = composed {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    this
        .onGloballyPositioned {
            bounds = it.boundsInRoot()
            screen = it.findRootCoordinates().size
        }
        .graphicsLayer {
            val p = focus()
            if (p == 0f || bounds.width == 0f || screen.width == 0) return@graphicsLayer
            val grow = (screen.width * 0.86f / bounds.width).coerceIn(1f, 1.4f)
            val scale = 1f + (grow - 1f) * p
            scaleX = scale
            scaleY = scale
            val s = shift()
            translationX = p * (screen.width / 2f - bounds.center.x + s.x)
            translationY = p * (screen.height * 0.44f - bounds.center.y + s.y)
        }
}

/**
 * Guards plasma and OLED screens while idle without visible motion: every 3 minutes the
 * widgets glide a few dp to a new spot over 4 seconds, then stay perfectly still.
 */
@Composable
internal fun rememberBurnInShift(active: Boolean): State<Offset> {
    val density = LocalDensity.current
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(active) {
        if (!active) {
            // Back home with the widgets, rather than jumping as they leave
            shift.animateTo(Offset.Zero, tween(1300, easing = WakeEasing))
            return@LaunchedEffect
        }
        val range = with(density) { 14.dp.toPx() }
        val spots = listOf(Offset(-1f, -0.6f), Offset(1f, 0.4f), Offset(-0.4f, 1f), Offset(0.6f, -1f), Offset(0f, 0f))
        var i = 0
        while (true) {
            delay(3 * 60_000L)
            shift.animateTo(spots[i++ % spots.size] * range, tween(4000, easing = FastOutSlowInEasing))
        }
    }
    return shift.asState()
}

@Composable
internal fun SectionTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.Tile),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = Flex,
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight(650),
                shadow = TextShadow,
            ),
        )
        if (hint != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                hint,
                style = TextStyle(fontFamily = Flex, color = Palette.TextTertiary, fontSize = 15.sp, shadow = TextShadow),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
internal fun EmptyFavorites(loading: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(horizontal = Spacing.Tile)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Palette.Hairline, shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Text(
            if (loading) tr("Loading apps…") else tr("Go down to All apps and hold OK on an app to pin it here"),
            style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = 17.sp, shadow = TextShadow),
        )
    }
}

@Composable
internal fun ToastChip(message: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .shadow(24.dp, shape)
            .glass(shape)
            .padding(horizontal = 24.dp, vertical = 13.dp),
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight(560))
    }
}

/** An open app menu: for [app], whether OK is still held, and whether it came from the favourites row. */
internal class MenuRequest(val app: AppEntry, val okHeld: Boolean, val fromFavorites: Boolean)

internal class WidgetMenuRequest(val kind: WidgetKind, val okHeld: Boolean)

/** Where "Widget settings" in a widget's menu goes: its own page where it has one. */
internal fun widgetSettingsPage(kind: WidgetKind): SettingsPage = when (kind) {
    WidgetKind.Weather -> SettingsPage.WeatherPlace
    WidgetKind.Fennecs -> SettingsPage.Football
    WidgetKind.Headlines -> SettingsPage.News
    WidgetKind.Prayers -> SettingsPage.Prayers
    WidgetKind.Stack -> SettingsPage.Stack
    else -> SettingsPage.Widgets
}

internal fun widgetSettingsHint(kind: WidgetKind): String = when (kind) {
    WidgetKind.Weather -> tr("Location and unit")
    WidgetKind.Fennecs -> tr("Teams and match day")
    WidgetKind.Headlines -> tr("Topics")
    WidgetKind.Prayers -> tr("Mosque, refresh")
    WidgetKind.Stack -> tr("Its widgets and their order")
    else -> tr("Turn widgets on, off and reorder them")
}

/** Room the settings panel takes at the right of the screen, and the margin around the preview. */
private val PanelRoom = 488.dp
internal val PreviewMargin = 28.dp

/** How small the first page gets so it fits left of the settings panel, as a live preview. */
internal fun previewScale(contentWidth: Dp, screenWidth: Dp): Float =
    ((screenWidth - PanelRoom - PreviewMargin * 2) / contentWidth).coerceIn(0.3f, 1f)

/**
 * Safe mode, where the notification bar would be: the launcher crashed twice in a row,
 * so it started with only the photo and Favorites. Try normally starts it as usual.
 */
@Composable
internal fun SafeModeCard(reason: String, onTryNormally: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tr("Safe mode: Pill Launcher stopped unexpectedly"), color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(tr("Widgets, notifications and Files are off for now · %1\$s", reason), color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        PillButton(tr("Try normally"), primary = true, onClick = onTryNormally)
    }
}
