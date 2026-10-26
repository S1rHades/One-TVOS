package app.pill.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Rounded pill with a gear icon. On D-pad focus it scales up, gains an
 * elevated white glow and flips to a solid white fill. Center/Enter triggers [onClick].
 */
@Composable
fun SettingsPill(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.14f else 1f, label = "pillScale")
    val elevation by animateDpAsState(if (focused) 18.dp else 0.dp, label = "pillElevation")
    val shape = RoundedCornerShape(50)

    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, shape, ambientColor = Color.White, spotColor = Color.White)
            .clip(shape)
            .background(if (focused) Color.White else Color(0x59000000))
            .border(2.dp, if (focused) Color.White else Color(0x80FFFFFF), shape)
            .semantics { contentDescription = "Settings" }
            .onFocusChanged { focused = it.isFocused }
            .focusRequester(focusRequester)
            .clickable(onClick = onClick)
            .padding(horizontal = 44.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        GearIcon(tint = if (focused) Color(0xFF0B0F1A) else Color.White, iconSize = 38.dp)
    }
}

/** Gear drawn on a canvas: a ring with eight rounded teeth. */
@Composable
fun GearIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val c = center
        val r = this.size.minDimension / 2f
        val toothWidth = r * 0.3f
        for (k in 0 until 8) {
            val angle = Math.toRadians(k * 45.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = tint,
                start = Offset(c.x + dx * r * 0.6f, c.y + dy * r * 0.6f),
                end = Offset(c.x + dx * r * 0.93f, c.y + dy * r * 0.93f),
                strokeWidth = toothWidth,
                cap = StrokeCap.Round,
            )
        }
        drawCircle(tint, radius = r * 0.5f, style = Stroke(width = r * 0.26f))
    }
}
