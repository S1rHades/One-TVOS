package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
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
    onActive: (Boolean) -> Unit = {},
) {
    RoundButton("Settings", onClick, modifier, focusRequester, onActive) { tint, active ->
        val turn by animateFloatAsState(if (active) 45f else 0f, tween(450), label = "gearTurn")
        GearIcon(tint = tint, iconSize = 26.dp, modifier = Modifier.rotate(turn))
    }
}

private val ButtonSize = 44.dp

/**
 * The status bar, top right, in one frosted capsule: connection (Wi-Fi strength or
 * Ethernet; OK opens the TV's network settings), a key while a VPN is on, then Search and
 * Settings. Left and Right move along it, and one white highlight slides to the focused button.
 */
@Composable
fun QuickActions(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNetwork: () -> Unit,
    settingsFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val network by rememberNetworkStatus()
    // Which button is focused (0 network, 1 search, 2 settings; -1 none), and where each sits
    var focused by remember { mutableIntStateOf(-1) }
    var last by remember { mutableIntStateOf(2) }
    if (focused >= 0) last = focused
    val positions = remember { mutableStateMapOf<Int, Float>() }
    val x by animateFloatAsState(positions[last] ?: 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "highlightX")
    val shown by animateFloatAsState(if (focused >= 0) 1f else 0f, tween(160), label = "highlightAlpha")
    fun track(index: Int) = { active: Boolean -> if (active) focused = index else if (focused == index) focused = -1 }
    fun Modifier.at(index: Int) = onPlaced { positions[index] = it.positionInParent().x }

    Box(
        modifier
            .glass(RoundedCornerShape(50))
            .padding(5.dp),
    ) {
        Box(
            Modifier
                .offset { IntOffset(x.roundToInt(), 0) }
                .size(ButtonSize)
                .graphicsLayer { alpha = if (positions.isEmpty()) 0f else shown }
                .shadow(12.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
                .background(Palette.Focus, CircleShape),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = network.vpn,
                enter = fadeIn(tween(300)) + expandHorizontally(tween(300)),
                exit = fadeOut(tween(200)) + shrinkHorizontally(tween(300)),
            ) {
                // Status only, so not a focus stop
                Box(
                    Modifier.size(ButtonSize).semantics { contentDescription = "VPN on" },
                    contentAlignment = Alignment.Center,
                ) {
                    KeyIcon(tint = VpnGreen, iconSize = 24.dp)
                }
            }
            RoundButton(
                description = "Network: " + network.label + if (network.vpn) ", VPN on" else "",
                onClick = onNetwork,
                modifier = Modifier.at(0),
                focusRequester = null,
                onActive = track(0),
            ) { tint, _ ->
                WifiIcon(network, tint = if (network.link == Link.Offline && tint == Palette.TextPrimary) OfflineRed else tint, iconSize = 24.dp)
            }
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(22.dp).background(Color(0x38FFFFFF)))
            SearchPill(onClick = onSearch, modifier = Modifier.at(1), onActive = track(1))
            SettingsPill(onClick = onSettings, focusRequester = settingsFocus, modifier = Modifier.at(2), onActive = track(2))
        }
    }
}

private val VpnGreen = Color(0xFF8CE99A)
private val OfflineRed = Color(0xFFFF8787)

/** Round glass button with a magnifier, opening search. */
@Composable
fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier, onActive: (Boolean) -> Unit = {}) {
    RoundButton("Search", onClick, modifier, focusRequester = null, onActive = onActive) { tint, active ->
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
    onActive: (Boolean) -> Unit,
    icon: @Composable (tint: Color, active: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val active = focused || hovered

    val scale by animateFloatAsState(if (active) 1.1f else 1f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "pillScale")
    // The white highlight behind the focused button is drawn by QuickActions, so it can slide
    val reportActive by rememberUpdatedState(onActive)
    LaunchedEffect(active) { reportActive(active) }
    val tint by animateColorAsState(if (active) Palette.Ink else Palette.TextPrimary, label = "pillTint")

    Box(
        modifier
            .size(ButtonSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
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
