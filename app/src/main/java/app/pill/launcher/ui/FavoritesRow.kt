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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.pill.launcher.AppEntry
import app.pill.launcher.FavoritesLayout
import app.pill.launcher.FavoritesStyle

/** Space between favourite tiles. */
private val Gap = 8.dp

/** Room above and below the row so a focused (enlarged) tile is never clipped by the edge fade. */
private val RowAir = 10.dp

/** Widens the scrolling row into the side margins, where its edges fade. */
private val RowBleed = 24.dp

/** One curve for every favourites motion, matching the page scroll. */
private fun <T> motion(animate: Boolean): FiniteAnimationSpec<T> =
    if (animate) tween(300, easing = FastOutSlowInEasing) else snap()

/** Width of one favourite tile in [style] ([iconSize] applies to icons). */
fun favoritesItemWidth(style: FavoritesStyle, iconSize: Dp): Dp =
    if (style == FavoritesStyle.Banners) BannerTileWidth else tileWidth(iconSize)

/** How many favourite tiles fit across [width] (Wrap layout). */
fun favoritesPerRow(width: Dp, style: FavoritesStyle, iconSize: Dp): Int =
    ((width + Gap) / (favoritesItemWidth(style, iconSize) + Gap)).toInt().coerceAtLeast(1)

/** Height of one row of favourite tiles, including the air around it. */
fun favoritesRowHeight(style: FavoritesStyle, iconSize: Dp): Dp =
    (if (style == FavoritesStyle.Banners) BannerHeight + 42.dp else iconSize + 60.dp) + RowAir * 2

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
    style: FavoritesStyle,
    animate: Boolean,
    iconSize: Dp,
    width: Dp,
    focusFor: (AppEntry) -> FocusRequester,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (AppEntry) -> Unit = {},
) {
    val tile: @Composable (AppEntry, Boolean, Boolean, Modifier) -> Unit = { app, first, last, extra ->
        val tileModifier = extra
            .focusRequester(focusFor(app))
            .focusProperties {
                if (first) left = FocusRequester.Cancel
                if (last) right = FocusRequester.Cancel
            }
        val focused = { gained: Boolean -> if (gained) onFocus(app) }
        if (style == FavoritesStyle.Banners) {
            BannerTile(app, { onOpen(app) }, { onTogglePin(app) }, tileModifier, focused)
        } else {
            AppTile(app, iconSize, showPinBadge = false, onOpen = { onOpen(app) }, onTogglePin = { onTogglePin(app) }, modifier = tileModifier, onFocus = focused)
        }
    }
    val itemWidth = favoritesItemWidth(style, iconSize)
    val rowHeight = favoritesRowHeight(style, iconSize)
    val body: @Composable (FavoritesLayout) -> Unit = { mode ->
        when (mode) {
            FavoritesLayout.Scroll -> ScrollRow(apps, animate, itemWidth, rowHeight, tile)
            FavoritesLayout.Wrap -> WrapRows(apps, animate, rowHeight, favoritesPerRow(width, style, iconSize), tile)
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
    itemWidth: Dp,
    rowHeight: Dp,
    tile: @Composable (AppEntry, Boolean, Boolean, Modifier) -> Unit,
) {
    val state = rememberLazyListState()
    // Scrolling keeps the focused tile out of the fade zone; the next tile peeks into it
    val fadeZone = with(LocalDensity.current) { (itemWidth * 0.75f).toPx() }
    val peek = fadeZone
    val scrolling = remember(peek, animate) { RowScrollSpec(peek, animate) }
    Box(Modifier.height(rowHeight).bleed(RowBleed)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides scrolling) {
            LazyRow(
                state = state,
                contentPadding = PaddingValues(horizontal = RowBleed, vertical = RowAir),
                horizontalArrangement = Arrangement.spacedBy(Gap),
            ) {
                itemsIndexed(apps, key = { _, app -> app.pkg }) { i, app ->
                    val motionMod = if (animate) Modifier.animateItem() else Modifier
                    tile(app, i == 0, i == apps.lastIndex, motionMod.edgeFade(state, i, fadeZone))
                }
            }
        }
        // A slim track under the row's end showing how far along the favourites you are
        ScrollIndicator(state, Modifier.align(Alignment.BottomEnd).padding(end = RowBleed + 12.dp, bottom = 2.dp))
    }
}

@Composable
private fun WrapRows(
    apps: List<AppEntry>,
    animate: Boolean,
    rowHeight: Dp,
    perRow: Int,
    tile: @Composable (AppEntry, Boolean, Boolean, Modifier) -> Unit,
) {
    Column(if (animate) Modifier.animateContentSize(motion<IntSize>(true)) else Modifier) {
        for (row in apps.chunked(perRow)) {
            Row(
                Modifier.height(rowHeight),
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

/**
 * Fades and slightly shrinks a tile as it enters the outer [zone] of the row, so the row
 * dissolves into the edge instead of being cut off. Only on a side with more to scroll
 * to. Read in the layer phase from the list's layout, so scrolling never recomposes.
 */
private fun Modifier.edgeFade(state: LazyListState, index: Int, zone: Float) = graphicsLayer {
    val info = state.layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return@graphicsLayer
    val start = (item.offset - info.viewportStartOffset).toFloat()
    val end = start + item.size
    val width = info.viewportSize.width.toFloat()
    val right = if (state.canScrollForward) ((end - (width - zone)) / zone).coerceIn(0f, 1f) else 0f
    val left = if (state.canScrollBackward) ((zone - start) / zone).coerceIn(0f, 1f) else 0f
    // Ease in, so the fade is gentle at first and fuller right at the edge
    val t = maxOf(right, left).let { it * it * (3 - 2 * it) }
    alpha = 1f - 0.7f * t
    val scale = 1f - 0.06f * t
    scaleX = scale
    scaleY = scale
}

/** A thin track whose thumb shows the visible part of the row; shown only when it overflows. */
@Composable
private fun ScrollIndicator(state: LazyListState, modifier: Modifier) {
    val overflowing = state.canScrollForward || state.canScrollBackward
    val shown by animateFloatAsState(if (overflowing) 1f else 0f, motion(true), label = "indicator")
    Canvas(modifier.size(width = 64.dp, height = 4.dp).graphicsLayer { alpha = shown }) {
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty() || info.totalItemsCount == 0) return@Canvas
        // Fraction of the row on screen, and how far along it the view is
        val visible = (items.size.toFloat() / info.totalItemsCount).coerceIn(0.15f, 1f)
        val progress = if (state.canScrollForward || state.canScrollBackward) {
            items.first().index.toFloat() / (info.totalItemsCount - items.size).coerceAtLeast(1)
        } else 0f
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Color(0x40FFFFFF), cornerRadius = r)
        val thumb = size.width * visible
        drawRoundRect(
            Color(0xE6FFFFFF),
            topLeft = Offset((size.width - thumb) * progress.coerceIn(0f, 1f), 0f),
            size = Size(thumb, size.height),
            cornerRadius = r,
        )
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
