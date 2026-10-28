package app.pill.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import app.pill.launcher.AppEntry

/** Gap between the icon and its focus outline. */
private val RingGap = 4.dp
private val RingWidth = 2.5.dp

/** Width of a tile for a given icon size: the icon, the outline around it and a little air. */
fun tileWidth(iconSize: Dp): Dp = iconSize + Spacing.Tile * 2

/** Spring shared by every focusable tile: a small, physical overshoot. */
val FocusSpring = spring<Float>(dampingRatio = 0.6f, stiffness = 400f)

/**
 * App icon, shown edge to edge in a squircle. OK opens the app; holding OK (or Menu) pins
 * or unpins it. On focus (or pointer hover) the tile springs to 1.12x, glows in the app's
 * own colour, gains a crisp white outline and a bold label, and draws above its neighbours.
 */
@Composable
fun AppTile(
    app: AppEntry,
    iconSize: Dp,
    showPinBadge: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
) {
    val input = rememberTileInput(onOpen, onTogglePin, onFocus)
    val active = input.active
    val accent = Color(app.accent)
    val scale by animateFloatAsState(if (active) 1.12f else 1f, FocusSpring, label = "tileScale")
    val ring by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "tileRing")
    // No shadow at rest: drawing one under every icon is costly on TV hardware
    val lift by animateDpAsState(if (active) 20.dp else 0.dp, label = "tileLift")

    Column(
        modifier
            .zIndex(if (active) 1f else 0f)
            .width(tileWidth(iconSize))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(input.modifier)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(iconSize + (RingGap + RingWidth) * 2), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = ring }
                    .border(RingWidth, Palette.Focus, Squircle),
            )
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(iconSize)
                    .shadow(lift, Squircle, ambientColor = accent, spotColor = accent)
                    .clip(Squircle),
            )
            if (showPinBadge) PinBadge(Modifier.align(Alignment.TopEnd))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = TextStyle(
                fontFamily = Flex,
                color = if (active) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight(700) else FontWeight(500),
                textAlign = TextAlign.Center,
                shadow = TextShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** Pinned marker: a crisp vector star on a white disc, the same size on every tile. */
@Composable
fun PinBadge(modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .size(20.dp)
            .shadow(3.dp, CircleShape)
            .background(Palette.Focus, CircleShape),
    ) {
        val c = center
        val outer = size.minDimension * 0.34f
        val inner = outer * 0.45f
        val star = Path().apply {
            for (k in 0 until 10) {
                val r = if (k % 2 == 0) outer else inner
                val a = Math.toRadians(-90.0 + k * 36.0)
                val x = c.x + (r * kotlin.math.cos(a)).toFloat()
                val y = c.y + (r * kotlin.math.sin(a)).toFloat() + outer * 0.06f
                if (k == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(star, Palette.Accent)
    }
}
