package app.pill.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Pieces of the update's progress, shared by the notification bar, Settings → Updates and Files

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
