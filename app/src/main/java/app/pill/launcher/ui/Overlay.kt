package app.pill.launcher.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val PageShape = RoundedCornerShape(30.dp)

/** How long a page's items wait for the softened photo to hide the home screen. */
private const val PageLead = 120

/**
 * A page opened from a widget or the rail, in glass motion: the photo racks focus (goes
 * soft) behind it, then the page dissolves in, its parts in a wave ([reveal] steps); Back
 * plays it out again before it is removed. [framed] pages sit on one glass card; unframed
 * ones lay out their own glass cards straight on the softened photo. [content] gets a
 * close function for its own keys.
 */
@Composable
fun OverlayPage(
    onDismiss: () -> Unit,
    width: Dp,
    framed: Boolean = true,
    /** The bar item this page belongs to (the Internet page's), marked in the bar at the top. */
    nav: NavItem? = null,
    /** A button for the top left, level with the bar and above the page's title. */
    topLeft: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var showing by remember { mutableStateOf(true) }
    val close = { showing = false }
    // The way in: the softened photo and the dim come first, and the page's wave starts
    // [PageLead] later, once the home screen is mostly hidden, so the page's text never
    // lies over it. The way out ([leaving], 0 to 1): the whole page fades quickly, then the
    // photo clears, so it is never left standing still and never shows through the home screen
    val clock = remember { Animatable(0f) }
    val leaving = remember { Animatable(0f) }
    LaunchedEffect(showing) {
        if (showing) {
            clock.animateTo((Motion.RevealMs + PageLead).toFloat(), tween(Motion.RevealMs + PageLead, easing = LinearEasing))
        } else {
            leaving.animateTo(1f, tween(Motion.Exit, easing = LinearEasing))
            onDismiss()
        }
    }
    val revealed = remember { derivedStateOf { (clock.value - PageLead).coerceAtLeast(0f) } }
    // Going out the page answers at once and is gone by half way; the photo holds, then clears
    val pageShown = { 1f - Motion.EnterEasing.transform((leaving.value / 0.5f).coerceIn(0f, 1f)) }
    val focusOut = {
        Motion.EnterEasing.transform((clock.value / (Motion.Enter * 0.6f)).coerceIn(0f, 1f)) *
            (1f - Motion.ExitEasing.transform(((leaving.value - 0.25f) / 0.75f).coerceIn(0f, 1f)))
    }
    val backdrop = LocalBackdrop.current
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalReveal provides revealed) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = focusOut() }) {
                    backdrop?.let { drawBackdrop(it, IntSize(size.width.toInt(), size.height.toInt())) }
                    drawRect(Palette.Scrim)
                }
                // Pages sit below the bar at the top right, which stays usable on every page
                TopNav(nav)
                topLeft?.let { button ->
                    Box(
                        Modifier
                            .graphicsLayer { alpha = pageShown() }
                            .align(Alignment.TopCenter)
                            .padding(top = NavTop)
                            .width(width)
                            .height(NavHeight)
                            .reveal(0),
                        contentAlignment = Alignment.CenterStart,
                    ) { button() }
                }
                Column(
                    Modifier
                        .graphicsLayer { alpha = pageShown() }
                        .align(Alignment.Center)
                        .padding(top = NavRoom - PageBottomRoom, bottom = 0.dp)
                        .width(width)
                        .then(
                            if (framed) {
                                Modifier
                                    .reveal(0)
                                    .shadow(32.dp, PageShape)
                                    .glass(PageShape)
                                    .padding(horizontal = 30.dp, vertical = 24.dp)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    content(close)
                }
            }
        }
    }
}

/** A small caps heading inside a page, like the cards' labels. */
@Composable
fun PageLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(
        text.uppercase(),
        style = TextStyle(fontFamily = Flex, color = color, fontSize = 12.sp, fontWeight = FontWeight(650), letterSpacing = 1.3.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** The grey line at the bottom of a page: where the data comes from, and how to leave. */
@Composable
fun PageFooter(text: String, modifier: Modifier = Modifier) {
    Text(text, color = Palette.TextTertiary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** Room kept under a page, matched at the top by the bar's room minus this, so pages sit centred below the bar. */
private val PageBottomRoom = 12.dp

/** Whether pages still say "Back to close": true for the first [app.pill.launcher.BackHintTimes] pages opened. */
val LocalBackHint = staticCompositionLocalOf { true }

/** "Back to close" while the hint is still shown, else null (for listOfNotNull). */
@Composable
fun backHint(): String? = if (LocalBackHint.current) "Back to close" else null
