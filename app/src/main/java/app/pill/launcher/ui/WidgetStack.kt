package app.pill.launcher.ui

import app.pill.launcher.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.WidgetKind
import app.pill.launcher.StackTurnSec
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay

// The widget stack: several widgets in one card's place, taking turns.

/**
 * Inside a widget stack a card doesn't take focus itself: the stack does, shows the
 * card's focus, and passes OK on to it.
 */
internal class StackSlot {
    var action by mutableStateOf<(() -> Unit)?>(null)
    var focused by mutableStateOf(false)
    /** Left / Right for the card on show (the football card's teams). */
    var keys by mutableStateOf<((KeyEvent) -> Boolean)?>(null)
}

internal val LocalStackSlot = compositionLocalOf<StackSlot?> { null }

/**
 * Several widgets in one place, taking turns: every [StackTurnSec] seconds the next
 * slides up from below, with dots down the right edge saying which is showing. While it
 * has focus it stays put, Up / Down flips through by hand (Down from the last goes on to
 * Favorites), and OK opens the page of the card on show. [preview] holds one in view
 * while it is set up in Settings; a goal brings the football card forward.
 */
@Composable
internal fun StackCard(stack: List<WidgetKind>, preview: WidgetKind?, content: WidgetContent, compact: Boolean, modifier: Modifier) {
    var index by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf(false) }
    val slots = remember { mutableStateMapOf<WidgetKind, StackSlot>() }
    val count = stack.size
    val shown = stack.getOrNull(index.coerceIn(0, (count - 1).coerceAtLeast(0)))
    fun go(to: Int) {
        if (to == index || to !in 0 until count) return
        forward = to > index
        index = to
    }
    // Takes turns while nobody is looking at it closely
    LaunchedEffect(stack, focused, preview, content.moment, content.animate) {
        while (count > 1 && !focused && preview == null && content.moment == null) {
            delay(StackTurnSec * 1000L)
            forward = true
            index = (index + 1) % count
        }
    }
    LaunchedEffect(preview, stack) { preview?.let { stack.indexOf(it) }?.takeIf { it >= 0 }?.let(::go) }
    LaunchedEffect(content.moment, stack) { if (content.moment != null) stack.indexOf(WidgetKind.Fennecs).takeIf { it >= 0 }?.let(::go) }
    if (index >= count && count > 0) index = 0

    val lift by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "stackFocus")
    Box(
        modifier
            .height(WidgetHeight)
            .graphicsLayer {
                val s = 1f + 0.045f * lift
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                // Left / Right belong to the card on show when it uses them (the football card's teams)
                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                    return@onPreviewKeyEvent shown?.let { slots[it]?.keys?.invoke(event) } == true
                }
                // Up / Down flip through the stack; past either end focus moves on as usual
                val step = when (event.key) {
                    Key.DirectionUp -> -1
                    Key.DirectionDown -> 1
                    else -> return@onPreviewKeyEvent false
                }
                val to = index + step
                if (to !in 0 until count) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) go(to)
                true
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                shown?.let { slots[it]?.action?.invoke() }
            }
            .semantics { contentDescription = tr("Widget stack") + (shown?.let { ": ${it.title}" } ?: "") }
            .clip(WidgetCardShape),
    ) {
        if (shown == null) {
            WidgetCard(accent = NeutralTint, modifier = Modifier.fillMaxSize()) {
                FitText(
                    tr("Choose its widgets in Settings → Widgets → Widget stack"),
                    style = lineStyle(15.sp, Palette.TextPrimary),
                    maxLines = 3,
                    shorter = listOf(tr("Choose its widgets in Settings"), tr("No widgets yet")),
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        } else {
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    val enter = tween<IntOffset>(Motion.Page, easing = Motion.EnterEasing)
                    val exit = tween<IntOffset>(Motion.Exit + 120, easing = Motion.ExitEasing)
                    (slideInVertically(enter) { if (forward) it else -it } + fadeIn(tween(Motion.Enter, delayMillis = 60))) togetherWith
                        (slideOutVertically(exit) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(Motion.Exit))) using
                        SizeTransform(clip = true)
                },
                label = "stack",
                modifier = Modifier.fillMaxSize(),
            ) { kind ->
                val slot = slots.getOrPut(kind) { StackSlot() }
                SideEffect { slot.focused = focused }
                CompositionLocalProvider(LocalStackSlot provides slot) {
                    content.Card(kind, compact, Modifier.fillMaxSize())
                }
            }
        }
        if (count > 1) {
            StackDots(count, index.coerceIn(0, count - 1), Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
        }
    }
}

/** Which card of the stack is showing: a column of dots, the current one longer and brighter. */
@Composable
private fun StackDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(count) { i ->
            val on = i == current
            val h by animateDpAsState(if (on) 14.dp else 5.dp, Motion.glide(), label = "stackDot")
            val a by animateFloatAsState(if (on) 0.9f else 0.32f, tween(Motion.Quick), label = "stackDotAlpha")
            Box(Modifier.width(5.dp).height(h).background(Color.White.copy(alpha = a), CircleShape))
        }
    }
}
