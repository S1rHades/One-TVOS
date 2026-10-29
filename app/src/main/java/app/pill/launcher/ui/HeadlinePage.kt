package app.pill.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.PickedHeadline
import kotlinx.coroutines.delay

/**
 * A headline with its summary, opened with OK on the headlines card. Left / Right reads
 * the others; OK or Back closes it.
 */
@Composable
fun HeadlinePage(picked: List<PickedHeadline>, start: Int, onDismiss: () -> Unit) {
    if (picked.isEmpty()) return
    var index by remember { mutableIntStateOf(start.coerceIn(0, picked.lastIndex)) }
    val focus = remember { FocusRequester() }
    OverlayPage(onDismiss = onDismiss, width = 760.dp) { close ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { focus.requestFocus() }
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            label = "headlinePage",
            modifier = Modifier
                .focusRequester(focus)
                .focusable()
                .onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { index = (index - 1 + picked.size) % picked.size; true }
                        Key.DirectionRight -> { index = (index + 1) % picked.size; true }
                        Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { close(); true }
                        else -> false
                    }
                },
        ) { i ->
            val item = picked[i]
            val h = item.headline
            Column(Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                PageLabel(
                    listOfNotNull(item.topic?.title ?: "Top stories", h.source.title, h.published?.let { ageText(it) }).joinToString(" · "),
                    color = Color(0xFFFFA8A8),
                )
                Spacer(Modifier.height(10.dp))
                Text(h.title, color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, lineHeight = 34.sp)
                if (h.summary.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    Text(h.summary, color = Palette.TextSecondary, fontSize = 19.sp, lineHeight = 27.sp)
                }
            }
        }
        PageFooter(
            listOfNotNull(
                if (picked.size > 1) "${index + 1} of ${picked.size} · Left / Right for more" else null,
                "Back to close",
            ).joinToString(" · "),
            Modifier.padding(top = 18.dp),
        )
    }
}
