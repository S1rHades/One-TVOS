package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.pill.launcher.AppEntry

/** Width of a tile for a given icon size: the icon plus room for the label. */
fun tileWidth(iconSize: Dp): Dp = iconSize + 36.dp

/**
 * Squircle app icon. OK opens the app, long-press OK pins or unpins it.
 * On focus the tile lifts (scale, glow, white ring) and draws above its neighbours.
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
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        if (focused) 1.12f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "tileScale",
    )
    val glow by animateDpAsState(if (focused) 16.dp else 0.dp, label = "tileGlow")
    val ring by animateColorAsState(if (focused) Palette.Focus else Color.Transparent, label = "tileRing")
    val fill by animateColorAsState(if (focused) Palette.GlassStrong else Palette.Glass, label = "tileFill")

    Column(
        modifier
            .zIndex(if (focused) 1f else 0f)
            .width(tileWidth(iconSize))
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // focus styling below replaces the default ripple
                onClick = onOpen,
                onLongClick = onTogglePin,
            )
            .padding(Spacing.Tile),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .shadow(glow, Squircle, ambientColor = Color.White, spotColor = Color.White)
                .clip(Squircle)
                .background(fill)
                .border(2.5.dp, ring, Squircle),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().padding(iconSize / 6),
            )
            if (showPinBadge) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(iconSize / 9)
                        .size(9.dp)
                        .shadow(2.dp, CircleShape)
                        .background(Palette.Focus, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            app.label,
            style = TextStyle(
                color = if (focused) Palette.TextPrimary else Palette.TextSecondary,
                fontSize = 15.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.Center,
                shadow = TextShadow,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
