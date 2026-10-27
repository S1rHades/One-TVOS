package app.pill.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import app.pill.launcher.AppEntry

/** Gap between the icon and its focus ring. */
private val RingGap = 5.dp
private val RingWidth = 3.dp

/** Width of a tile for a given icon size: the icon, the ring around it and a little air. */
fun tileWidth(iconSize: Dp): Dp = iconSize + Spacing.Tile * 2

/**
 * App icon, shown edge to edge in a squircle. OK opens the app, long-press OK pins or
 * unpins it. On focus (or pointer hover) the tile lifts, the label brightens and a crisp
 * white ring appears a few dp outside the icon, and the tile draws above its neighbours.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    iconSize: Dp,
    showPinBadge: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val active = focused || hovered

    val scale by animateFloatAsState(
        if (active) 1.1f else 1f,
        spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "tileScale",
    )
    val ring by animateFloatAsState(if (active) 1f else 0f, tween(180), label = "tileRing")
    val lift by animateDpAsState(if (active) 18.dp else 4.dp, label = "tileLift")

    Column(
        modifier
            .zIndex(if (active) 1f else 0f)
            .width(tileWidth(iconSize))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = interaction,
                indication = null, // the ring and lift replace the default ripple
                onClick = onOpen,
                onLongClick = onTogglePin,
            )
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
                    .shadow(lift, Squircle, ambientColor = Color.Black, spotColor = Color.Black)
                    .clip(Squircle),
            )
            if (showPinBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .size(12.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Palette.Focus, CircleShape)
                        .padding(3.dp)
                        .background(Palette.Accent, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            style = TextStyle(
                color = if (active) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                shadow = TextShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
