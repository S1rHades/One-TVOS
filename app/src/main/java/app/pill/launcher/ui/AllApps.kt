package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.pill.launcher.AppCategory
import app.pill.launcher.AppEntry

/** Narrowest an app card may get; the grid fits as many columns as this allows. */
val AppCardMinWidth = 106.dp
val AppCardHeight = 104.dp
private val CardShape = RoundedCornerShape(16.dp)
private val ChipShape = RoundedCornerShape(50)

/**
 * The chips above All apps: All, then each kind of app the TV has. Moving onto a chip
 * picks it (there is nothing else to choose), and a white pill glides to it.
 */
@Composable
fun CategoryChips(
    categories: List<AppCategory?>,
    selected: AppCategory?,
    onSelect: (AppCategory?) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Where each chip sits, so the pill can glide between them
    val placed = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val index = categories.indexOf(selected).coerceAtLeast(0)
    val spot = placed[index]
    val x by animateDpAsState(with(density) { (spot?.first ?: 0f).toDp() }, Motion.glide(), label = "chipX")
    val w by animateDpAsState(with(density) { (spot?.second ?: 0f).toDp() }, Motion.glide(), label = "chipW")
    Box(modifier) {
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .height(38.dp)
                .graphicsLayer { alpha = if (spot == null) 0f else 1f }
                .shadow(10.dp, ChipShape)
                .background(Palette.Focus, ChipShape),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((i, category) in categories.withIndex()) {
                val on = i == index
                val fg by animateColorAsState(if (on) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "chipText")
                Box(
                    Modifier
                        .onPlaced { placed[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(38.dp)
                        .clip(ChipShape)
                        .background(if (on) Color.Transparent else Color(0x1FFFFFFF))
                        .border(1.dp, if (on) Color.Transparent else Color(0x33FFFFFF), ChipShape)
                        .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) onSelect(category) }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(category) }
                        .padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(category?.title ?: "All", color = fg, fontSize = 16.sp, fontWeight = FontWeight(560), maxLines = 1)
                }
            }
        }
    }
}

/**
 * An app in All apps: a frosted card in the app's own colour, its icon, name and kind.
 * OK opens the app; holding OK (or Menu) opens its menu. Focus lifts it, outlines it in
 * white and lets its colour glow.
 */
@Composable
fun AppCard(
    app: AppEntry,
    showPinBadge: Boolean,
    onOpen: () -> Unit,
    onMenu: (okHeld: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = rememberTileInput(onOpen, onMenu)
    val active = input.active
    val scale by animateFloatAsState(if (active) 1.08f else 1f, FocusSpring, label = "cardScale")
    val ring by animateFloatAsState(if (active) 1f else 0f, tween(Motion.Quick), label = "cardRing")
    // No shadow at rest: drawing one under every card is costly on TV hardware
    val lift by animateDpAsState(if (active) 18.dp else 0.dp, label = "cardLift")
    val accent = Color(app.accent)

    Box(
        modifier
            .zIndex(if (active) 1f else 0f)
            .height(AppCardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(input.modifier),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shadowElevation = lift.toPx()
                    shape = CardShape
                    ambientShadowColor = accent
                    spotShadowColor = accent
                }
                .glass(CardShape)
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.22f)), start = Offset.Zero, end = Offset.Infinite),
                    CardShape,
                )
                .border(1.dp, Color(0x33FFFFFF), CardShape),
        )
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = ring }.border(2.5.dp, Palette.Focus, CardShape))
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            AppIcon(app, depth = { 0f }, modifier = Modifier.size(38.dp).clip(Squircle))
            Spacer(Modifier.weight(1f))
            Text(
                app.label,
                style = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight(650), shadow = TextShadow),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (app.category != AppCategory.Other) {
                Text(
                    app.category.title,
                    style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = 12.sp, shadow = TextShadow),
                    maxLines = 1,
                )
            }
        }
        if (showPinBadge) PinBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
    }
}
