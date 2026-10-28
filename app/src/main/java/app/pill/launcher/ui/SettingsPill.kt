package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Round glass button with a gear. On D-pad focus (or pointer hover) it fills white,
 * lifts, and the gear turns an eighth of a turn. Center/Enter triggers [onClick].
 */
@Composable
fun SettingsPill(
    onClick: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    RoundButton("Settings", onClick, modifier, focusRequester) { tint, active ->
        val turn by animateFloatAsState(if (active) 45f else 0f, tween(450), label = "gearTurn")
        GearIcon(tint = tint, iconSize = 26.dp, modifier = Modifier.rotate(turn))
    }
}

/**
 * Search and Settings in one frosted capsule, as on Google TV. Left and Right move between
 * them; the focused one fills white.
 */
@Composable
fun QuickActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    settingsFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .shadow(18.dp, RoundedCornerShape(50))
            .glass(RoundedCornerShape(50))
            .padding(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchPill(onClick = onSearch)
        Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(Color(0x38FFFFFF)))
        SettingsPill(onClick = onSettings, focusRequester = settingsFocus)
    }
}

/** Round glass button with a magnifier, opening search. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundButton("Search", onClick, modifier, focusRequester = null) { tint, active ->
        val tilt by animateFloatAsState(if (active) -12f else 0f, tween(450), label = "searchTilt")
        SearchIcon(tint = tint, iconSize = 24.dp, modifier = Modifier.rotate(tilt))
    }
}

/** The shared look of the header buttons: glass at rest, white and lifted when focused. */
@Composable
private fun RoundButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
    focusRequester: FocusRequester?,
    icon: @Composable (tint: Color, active: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val active = focused || hovered

    val scale by animateFloatAsState(if (active) 1.1f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "pillScale")
    val elevation by animateDpAsState(if (active) 14.dp else 0.dp, label = "pillElevation")
    // Transparent at rest: the quick-actions capsule around the buttons provides the glass
    val fill by animateColorAsState(if (active) Palette.Focus else Color.Transparent, label = "pillFill")
    val tint by animateColorAsState(if (active) Palette.Ink else Palette.TextPrimary, label = "pillTint")

    Box(
        modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation, CircleShape, ambientColor = Color.White, spotColor = Color.White)
            .clip(CircleShape)
            .background(fill)
            .semantics { contentDescription = description }
            .onFocusChanged { focused = it.isFocused }
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon(tint, active)
    }
}

/** Outlined magnifier in the same stroke weight as [GearIcon]. */
@Composable
fun SearchIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = s * 0.1f
        val c = Offset(s * 0.42f, s * 0.42f)
        val r = s * 0.28f
        drawCircle(tint, radius = r, center = c, style = Stroke(width = stroke))
        val d = r * 0.7071f
        drawLine(tint, Offset(c.x + d + stroke * 0.3f, c.y + d + stroke * 0.3f), Offset(s * 0.88f, s * 0.88f), stroke, StrokeCap.Round)
    }
}

/** Outlined gear: eight tapered teeth around a ring, drawn as one stroked path. */
@Composable
fun GearIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.085f, join = StrokeJoin.Round)
        drawPath(gearPath(center, s), tint, style = stroke)
        drawCircle(tint, radius = s * 0.13f, center = center, style = stroke)
    }
}

private fun gearPath(c: Offset, s: Float, teeth: Int = 8): Path {
    val outer = s * 0.44f
    val inner = s * 0.34f
    val tipHalf = 9.0 // degrees either side of a tooth's centre at the tip
    val baseHalf = 15.0 // ... and at its base
    fun at(deg: Double, r: Float): Offset {
        val a = Math.toRadians(deg - 90)
        return Offset(c.x + cos(a).toFloat() * r, c.y + sin(a).toFloat() * r)
    }
    return Path().apply {
        val step = 360.0 / teeth
        for (k in 0 until teeth) {
            val a = k * step
            val start = at(a - baseHalf, inner)
            if (k == 0) moveTo(start.x, start.y) else lineTo(start.x, start.y)
            at(a - tipHalf, outer).let { lineTo(it.x, it.y) }
            at(a + tipHalf, outer).let { lineTo(it.x, it.y) }
            at(a + baseHalf, inner).let { lineTo(it.x, it.y) }
            // follow the body circle to the next tooth
            var d = a + baseHalf + 3
            while (d < a + step - baseHalf) {
                at(d, inner).let { lineTo(it.x, it.y) }
                d += 3
            }
        }
        close()
    }
}
