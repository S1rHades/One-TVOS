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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    content: @Composable ColumnScope.(close: () -> Unit) -> Unit,
) {
    var showing by remember { mutableStateOf(true) }
    val close = { showing = false }
    // The wave's clock: up to its end on the way in, quickly back to 0 on the way out
    val clock = remember { Animatable(0f) }
    LaunchedEffect(showing) {
        if (showing) {
            clock.animateTo(Motion.RevealMs.toFloat(), tween(Motion.RevealMs, easing = LinearEasing))
        } else {
            clock.animateTo(0f, tween(Motion.Exit + Motion.Step * 4, easing = LinearEasing))
            onDismiss()
        }
    }
    val revealed = remember { derivedStateOf { clock.value } }
    // The softened photo and the dim behind the page, in step with the wave
    val focusOut = { (clock.value / Motion.Enter).coerceIn(0f, 1f) }
    val backdrop = LocalBackdrop.current
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalReveal provides revealed) {
            Box(Modifier.fillMaxSize()) {
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = focusOut() }) {
                    backdrop?.let { drawBackdrop(it, IntSize(size.width.toInt(), size.height.toInt())) }
                    drawRect(Palette.Scrim)
                }
                Column(
                    Modifier
                        .align(Alignment.Center)
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
