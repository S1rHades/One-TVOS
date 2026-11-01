package app.pill.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.UpdatePhase
import app.pill.launcher.UpdateState
import kotlin.math.roundToInt

private val CardShape = RoundedCornerShape(22.dp)

/**
 * A newer Pill Launcher, on the home screen above Favorites: the same glass bar as the
 * home-screen prompt. Update downloads it with a progress bar, then Android asks to
 * confirm the install; Later hides it until the next version.
 */
@Composable
fun UpdateCard(update: UpdateState, onUpdate: () -> Unit, onLater: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val release = update.release ?: return
    Row(
        modifier
            .glass(CardShape)
            .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).background(Color(0x26FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            DownloadIcon(Palette.TextPrimary, 20.dp)
        }
        Spacer(Modifier.width(14.dp))
        AnimatedContent(
            targetState = update.phase,
            transitionSpec = { fadeIn(tween(Motion.Enter, easing = Motion.EnterEasing)) togetherWith fadeOut(tween(Motion.Exit)) },
            label = "updateText",
            modifier = Modifier.weight(1f, fill = false),
        ) { phase ->
            Column {
                Text(
                    when (phase) {
                        UpdatePhase.Downloading -> "Downloading Pill Launcher ${release.version}"
                        UpdatePhase.Installing -> "Installing Pill Launcher ${release.version}"
                        UpdatePhase.Failed -> "Pill Launcher ${release.version} isn't installed yet"
                        else -> "Pill Launcher ${release.version} is ready"
                    },
                    color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (phase == UpdatePhase.Downloading) {
                    Spacer(Modifier.height(6.dp))
                    ProgressLine(update.progress, Modifier.width(300.dp))
                } else {
                    Text(
                        when (phase) {
                            UpdatePhase.Installing -> "Confirm on the screen Android shows"
                            else -> update.message ?: release.notes.firstOrNull() ?: "Settings and favourites stay as they are"
                        },
                        color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(18.dp))
        when (update.phase) {
            UpdatePhase.Downloading -> {
                Text("${(update.progress * 100).roundToInt()}%", color = Palette.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                PillButton("Cancel", primary = false, onClick = onCancel)
            }
            UpdatePhase.Installing -> Unit
            else -> {
                PillButton(if (update.phase == UpdatePhase.Failed) "Try again" else "Update", primary = true, onClick = onUpdate)
                Spacer(Modifier.width(8.dp))
                PillButton("Later", primary = false, onClick = onLater)
            }
        }
    }
}

/** A thin rounded progress bar: white over a faint track, gliding as it fills. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    val shown by animateFloatAsState(progress.coerceIn(0f, 1f), tween(Motion.Quick), label = "progress")
    Box(modifier.height(4.dp).clip(RoundedCornerShape(50)).background(Color(0x29FFFFFF))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(shown).clip(RoundedCornerShape(50)).background(Palette.Focus))
    }
}

/** An arrow down onto a line. */
@Composable
fun DownloadIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val w = size.width
        val stroke = w * 0.11f
        drawLine(tint, Offset(w * 0.5f, w * 0.12f), Offset(w * 0.5f, w * 0.64f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.28f, w * 0.44f), Offset(w * 0.5f, w * 0.66f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.72f, w * 0.44f), Offset(w * 0.5f, w * 0.66f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(w * 0.18f, w * 0.88f), Offset(w * 0.82f, w * 0.88f), stroke, StrokeCap.Round)
    }
}
