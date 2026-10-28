package app.pill.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pill.launcher.AppEntry
import app.pill.launcher.FavoritesLayout

/** Space between favourite tiles. */
private val Gap = 8.dp

/** Room above and below the row so a focused (enlarged) tile is never clipped by the edge fade. */
private val RowAir = 10.dp

/** Widens the scrolling row into the side margins, where its edges fade. */
private val RowBleed = 24.dp

/** One curve for every favourites motion, matching the page scroll. */
private fun <T> motion(animate: Boolean): FiniteAnimationSpec<T> =
    if (animate) tween(300, easing = FastOutSlowInEasing) else snap()

/** How many favourite tiles fit across [width] (Wrap layout). */
fun favoritesPerRow(width: Dp, iconSize: Dp): Int =
    ((width + Gap) / (tileWidth(iconSize) + Gap)).toInt().coerceAtLeast(1)

/** Height of one row of favourite tiles, including the air around it. */
fun favoritesRowHeight(iconSize: Dp): Dp = iconSize + 60.dp + RowAir * 2

/**
 * The favourites, as one sliding row ([FavoritesLayout.Scroll]) or as rows that wrap
 * ([FavoritesLayout.Wrap]). Left and Right stop at the ends of each row instead of jumping
 * to other controls; in Wrap, Down moves to the next row.
 */
@OptIn(ExperimentalComposeUiApi::class) // FocusRequester.Cancel
@Composable
fun FavoritesRow(
    apps: List<AppEntry>,
    layout: FavoritesLayout,
    animate: Boolean,
    iconSize: Dp,
    width: Dp,
    focusFor: (AppEntry) -> FocusRequester,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tile: @Composable (AppEntry, Boolean, Boolean, Modifier) -> Unit = { app, first, last, extra ->
        AppTile(
            app = app,
            iconSize = iconSize,
            showPinBadge = false,
            onOpen = { onOpen(app) },
            onTogglePin = { onTogglePin(app) },
            modifier = extra
                .focusRequester(focusFor(app))
                .focusProperties {
                    if (first) left = FocusRequester.Cancel
                    if (last) right = FocusRequester.Cancel
                },
        )
    }
    val body: @Composable (FavoritesLayout) -> Unit = { mode ->
        when (mode) {
            FavoritesLayout.Scroll -> ScrollRow(apps, animate, iconSize, tile)
            FavoritesLayout.Wrap -> WrapRows(apps, animate, iconSize, favoritesPerRow(width, iconSize), tile)
        }
    }
    if (animate) {
        AnimatedContent(
            targetState = layout,
            transitionSpec = {
                fadeIn(motion<Float>(true)) togetherWith fadeOut(motion<Float>(true)) using
                    SizeTransform(clip = false) { _, _ -> motion<IntSize>(true) }
            },
            contentAlignment = Alignment.BottomStart,
            modifier = modifier,
            label = "favoritesLayout",
        ) { body(it) }
    } else {
        Column(modifier) { body(layout) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollRow(
    apps: List<AppEntry>,
    animate: Boolean,
    iconSize: Dp,
    tile: @Composable (AppEntry, Boolean, Boolean, Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    // Keep half of the next tile in view, so it is clear more apps follow
    val peek = with(LocalDensity.current) { (tileWidth(iconSize) / 2).toPx() }
    val scrolling = remember(peek, animate) { RowScrollSpec(peek, animate) }
    Box(Modifier.height(favoritesRowHeight(iconSize)).bleed(RowBleed)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrolling) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = RowBleed, vertical = RowAir),
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                itemsIndexed(apps, key = { _, app -> app.pkg }) { i, app ->
                    tile(app, i == 0, i == apps.lastIndex, if (animate) Modifier.animateItem() else Modifier)
                }
            }
        }
        // Small arrows in the margins while more apps are hidden that way
        MoreMarker(state.canScrollBackward, pointsRight = false, Modifier.align(Alignment.CenterStart))
        MoreMarker(state.canScrollForward, pointsRight = true, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun WrapRows(
    apps: List<AppEntry>,
    animate: Boolean,
    iconSize: Dp,
    perRow: Int,
    tile: @Composable (AppEntry, Boolean, Boolean, Modifier) -> Unit,
) {
    Column(if (animate) Modifier.animateContentSize(motion<IntSize>(true)) else Modifier) {
        for (row in apps.chunked(perRow)) {
            Row(
                Modifier.height(favoritesRowHeight(iconSize)),
                horizontalArrangement = Arrangement.spacedBy(Gap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                row.forEachIndexed { i, app ->
                    key(app.pkg) { tile(app, i == 0, i == row.lastIndex, Modifier) }
                }
            }
        }
    }
}

/**
 * Sideways scrolling for the favourites: nothing moves while the focused tile and a
 * [peek] of its neighbour are in view; otherwise the row slides just far enough, on the
 * shared curve (or instantly with animations off).
 */
@OptIn(ExperimentalFoundationApi::class)
private class RowScrollSpec(private val peek: Float, animate: Boolean) : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = motion(animate)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val end = offset + size
        return when {
            size + peek * 2 > containerSize -> offset - (containerSize - size) / 2
            end > containerSize - peek -> end - (containerSize - peek)
            offset < peek -> offset - peek
            else -> 0f
        }
    }
}

/** A small glass chevron that fades in while there is more to scroll in its direction. */
@Composable
private fun MoreMarker(visible: Boolean, pointsRight: Boolean, modifier: Modifier) {
    val alpha by animateFloatAsState(if (visible) 1f else 0f, motion(true), label = "moreMarker")
    Canvas(
        modifier
            .padding(top = 0.dp, bottom = 34.dp) // centre on the icons, not the labels
            .size(22.dp)
            .graphicsLayer { this.alpha = alpha }
            .clip(CircleShape)
            .background(Color(0x66000000)),
    ) {
        val w = size.width
        val stroke = 2.dp.toPx()
        val dir = if (pointsRight) 1f else -1f
        val tip = Offset(w / 2 + dir * w * 0.12f, w / 2)
        drawLine(Color.White, Offset(tip.x - dir * w * 0.2f, w * 0.3f), tip, stroke, StrokeCap.Round)
        drawLine(Color.White, Offset(tip.x - dir * w * 0.2f, w * 0.7f), tip, stroke, StrokeCap.Round)
    }
}

/** Lets a horizontally clipped child extend [x] past its parent on both sides. */
internal fun Modifier.bleed(x: Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-x.roundToPx(), 0) }
}
