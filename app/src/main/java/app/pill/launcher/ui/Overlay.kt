package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
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

private const val PAGE_MS = 220
private val PageShape = RoundedCornerShape(30.dp)

/**
 * A page opened from a widget: a glass card over a dimmed screen that scales in, and out
 * again on Back before it is removed. [content] gets a close function for its own keys.
 */
@Composable
fun OverlayPage(onDismiss: () -> Unit, width: Dp, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onDismiss()
    }
    val scrimAlpha by animateFloatAsState(if (visible.targetState) 1f else 0f, tween(PAGE_MS), label = "pageScrim")
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(PAGE_MS)) + scaleIn(tween(PAGE_MS, easing = FastOutSlowInEasing), initialScale = 0.94f),
                exit = fadeOut(tween(PAGE_MS)) + scaleOut(tween(PAGE_MS), targetScale = 0.96f),
            ) {
                Column(
                    Modifier
                        .width(width)
                        .shadow(32.dp, PageShape)
                        .glass(PageShape)
                        .padding(horizontal = 30.dp, vertical = 24.dp),
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
