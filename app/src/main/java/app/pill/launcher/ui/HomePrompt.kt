package app.pill.launcher.ui

import app.pill.launcher.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PromptShape = RoundedCornerShape(22.dp)

/**
 * Shown on the home screen while Pill Launcher is neither the TV's home screen nor
 * helped by the Home button helper, because until then Home and closing an app lead
 * to the TV's own launcher. [onSetUp] tries both ways; [onDismiss] hides it for good.
 */
@Composable
fun HomePrompt(onSetUp: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(PromptShape)
            .padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text(tr("Closing an app takes you to the TV's own home screen"), color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                tr("Make Pill Launcher your home screen, so Home and closed apps come back here."),
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(18.dp))
        PillButton(tr("Set up"), primary = true, onClick = onSetUp)
        Spacer(Modifier.width(8.dp))
        PillButton(tr("Not now"), primary = false, onClick = onDismiss)
    }
}

/**
 * A button for prompts and dialogs. It is the pages' [PageButton], so every action in the
 * launcher has one shape: alike things look alike.
 */
@Composable
fun PillButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    PageButton(label, onClick, modifier, primary = primary, enabled = enabled)

/** One step of getting started, and whether it's done. */
class SetupStep(val title: String, val done: Boolean)

/**
 * Getting started, for a new install: a ring that fills as the steps are done, the next
 * step and its button, and **Not now**. It starts with one step already done (installing
 * it), because a goal that's under way gets finished more often than one that isn't.
 * [next] acts on the first step not done; null when it has no button (it's done by hand).
 */
@Composable
fun SetupCard(
    steps: List<SetupStep>,
    nextAction: String?,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val done = steps.count { it.done }
    val next = steps.firstOrNull { !it.done }
    val progress by animateFloatAsState(done / steps.size.toFloat(), tween(Motion.Enter, easing = Motion.EnterEasing), label = "setupProgress")
    Row(
        modifier
            .glass(PromptShape)
            .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 4.dp.toPx()
                val inset = w / 2
                val arc = androidx.compose.ui.geometry.Size(size.width - w, size.height - w)
                drawArc(Color.White.copy(alpha = 0.18f), 0f, 360f, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = Stroke(w))
                drawArc(PrayerGreen, -90f, 360f * progress, false, androidx.compose.ui.geometry.Offset(inset, inset), arc, style = Stroke(w, cap = StrokeCap.Round))
            }
            Text("$done/${steps.size}", color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(
                if (next == null) tr("You're set") else tr("Getting started · %1\$s of %2\$s done", done, steps.size),
                color = Palette.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                next?.title ?: tr("Pill Launcher is ready. Settings has the rest whenever you want it."),
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
            )
        }
        if (next != null) {
            Spacer(Modifier.width(18.dp))
            if (nextAction != null) {
                PillButton(nextAction, primary = true, onClick = onNext)
                Spacer(Modifier.width(8.dp))
            }
            PillButton(tr("Not now"), primary = false, onClick = onDismiss)
        }
    }
}
