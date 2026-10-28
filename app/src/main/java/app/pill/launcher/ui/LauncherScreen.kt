package app.pill.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppEntry
import app.pill.launcher.LauncherSettings
import app.pill.launcher.Weather
import kotlinx.coroutines.delay

private val FavoriteIcon = 108.dp
private val GridIcon = 84.dp

/** Height of the favourites row (one tile), kept fixed so pinning the first app does not shift the page. */
private val FavoritesRowHeight = FavoriteIcon + 60.dp

/**
 * Space between the favourites row and the bottom of the first page, holding the
 * "All apps" hint. Also the margin [PageScrollSpec] keeps when it scrolls, so going
 * back up from the grid lands exactly on the first page.
 */
private const val PAGE_EDGE = 0.14f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    apps: List<AppEntry>?,
    weather: Weather?,
    pins: List<String>,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onOpenSystemSettings: () -> Unit,
    /** Bumped each time Home is pressed while the launcher is already showing. */
    homePresses: Int = 0,
) {
    var panelOpen by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    // After an unpin from the favourites row, the package that should take focus (null = the pill)
    var refocus by remember { mutableStateOf<Refocus?>(null) }

    val pillFocus = remember { FocusRequester() }
    val favoriteFocus = remember { HashMap<String, FocusRequester>() }
    val pinnedApps = apps.orEmpty().let { list -> pins.mapNotNull { pkg -> list.firstOrNull { it.pkg == pkg } } }

    fun togglePin(app: AppEntry, fromFavorites: Boolean) {
        val wasPinned = app.pkg in pins
        if (wasPinned && fromFavorites) {
            val i = pinnedApps.indexOfFirst { it.pkg == app.pkg }
            val neighbour = pinnedApps.getOrNull(i + 1) ?: pinnedApps.getOrNull(i - 1)
            refocus = Refocus(neighbour?.pkg)
        }
        onTogglePin(app)
        toast = Toast(if (wasPinned) "Removed ${app.label} from Favorites" else "Added ${app.label} to Favorites")
    }

    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val currentPinned by rememberUpdatedState(pinnedApps)

    // Focus the first favourite, else the settings pill. Page scrolling then brings the first
    // page into view. Retries for a few frames until the target is attached.
    suspend fun focusFirstPage() {
        repeat(30) {
            withFrameNanos { }
            // The favourites row registers its tiles a frame or two after it first composes,
            // so look the target up on every try and only use the pill when there are no favourites
            val first = currentPinned.firstOrNull()
            val target = if (first == null) pillFocus else favoriteFocus[first.pkg]
            if (target != null && runCatching { target.requestFocus() }.isSuccess) return
        }
        runCatching { pillFocus.requestFocus() }
    }

    LaunchedEffect(apps != null) {
        if (apps == null || initialFocusDone) return@LaunchedEffect
        focusFirstPage()
        initialFocusDone = true
    }

    // Home while already home: close settings and go back to the first page
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        panelOpen = false
        focusFirstPage()
    }

    // Back in All apps returns to the first page (on the first page, Back does nothing)
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    BackHandler(enabled = scrolled && !panelOpen) { scope.launch { focusFirstPage() } }

    // Return focus to the pill when the settings panel closes
    LaunchedEffect(panelOpen) {
        if (!panelOpen && initialFocusDone) {
            delay(100)
            runCatching { pillFocus.requestFocus() }
        }
    }

    // Keep focus on the first page after the focused favourite is unpinned
    LaunchedEffect(refocus) {
        val target = refocus ?: return@LaunchedEffect
        delay(50)
        runCatching { (target.pkg?.let { favoriteFocus[it] } ?: pillFocus).requestFocus() }
        refocus = null
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            toastVisible = true
            delay(2200)
            toastVisible = false
        }
    }

    Box(Modifier.fillMaxSize().background(Palette.Ink)) {
        BackgroundSlideshow(
            urls = BackgroundSources.urls,
            intervalMs = settings.intervalSec * 1000L,
            enabled = settings.slideshow,
        )
        BackgroundScrim()

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pageHeight = maxHeight - Spacing.ScreenV
            val pageEdge = maxHeight * PAGE_EDGE
            val pageHeightPx = with(LocalDensity.current) { pageHeight.toPx() }
            val columns = ((maxWidth - Spacing.ScreenH * 2) / tileWidth(GridIcon)).toInt().coerceAtLeast(1)
            // 0 on the first page, 1 once the grid fills the screen; dims the photo behind the apps
            val depth by remember(pageHeightPx) { derivedStateOf { (scroll.value / pageHeightPx).coerceIn(0f, 1f) } }
            Box(Modifier.fillMaxSize().alpha(depth).background(Color(0x8C05070D)))

            val rowScrolling = LocalBringIntoViewSpec.current
            // Every tile is composed (a launcher has at most a few hundred apps), so plain D-pad
            // focus search reaches the grid from the first page; PageScrollSpec does the scrolling.
            val topPx = with(LocalDensity.current) { Spacing.ScreenV.toPx() }
            val pageScrolling = remember(scroll, pageHeightPx) { PageScrollSpec(scroll, topPx + pageHeightPx) }
            CompositionLocalProvider(LocalBringIntoViewSpec provides pageScrolling) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .padding(start = Spacing.ScreenH, end = Spacing.ScreenH, top = Spacing.ScreenV, bottom = 48.dp),
                ) {
                    // First page: clock, weather and favourites only. The grid starts below the fold.
                    Column(Modifier.fillMaxWidth().height(pageHeight)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.Tile), verticalAlignment = Alignment.Top) {
                            TimeWidget()
                            // Fades in once the first reading arrives (and out when turned off)
                            AnimatedContent(
                                targetState = weather,
                                transitionSpec = { fadeIn(tween(500)) togetherWith fadeOut(tween(300)) },
                                contentKey = { it != null },
                                label = "weatherSlot",
                            ) { w ->
                                if (w != null) {
                                    Row {
                                        Spacer(Modifier.width(36.dp))
                                        Box(Modifier.padding(top = 18.dp).width(1.dp).height(84.dp).background(Palette.Hairline))
                                        Spacer(Modifier.width(36.dp))
                                        WeatherWidget(w, settings.weather, Modifier.padding(top = 12.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            SettingsPill(
                                onClick = { panelOpen = true },
                                focusRequester = pillFocus,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        SectionTitle("Favorites", hint = if (pinnedApps.isNotEmpty()) "Hold OK to unpin" else null)
                        Box(Modifier.fillMaxWidth().height(FavoritesRowHeight), contentAlignment = Alignment.CenterStart) {
                            if (pinnedApps.isEmpty()) {
                                EmptyFavorites(loading = apps == null)
                            } else {
                                // The favourites row scrolls sideways the platform's usual way
                                CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrolling) {
                                    // Widened into the side margins so a focused tile's ring is not clipped
                                    LazyRow(
                                        modifier = Modifier.bleed(RowBleed),
                                        contentPadding = PaddingValues(horizontal = RowBleed),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        items(pinnedApps, key = { it.pkg }) { app ->
                                            val requester = favoriteFocus.getOrPut(app.pkg) { FocusRequester() }
                                            AppTile(
                                                app = app,
                                                iconSize = FavoriteIcon,
                                                showPinBadge = false,
                                                onOpen = { onOpen(app) },
                                                onTogglePin = { togglePin(app, fromFavorites = true) },
                                                modifier = Modifier.animateItem().focusRequester(requester),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MoreBelow(
                            count = apps?.size,
                            modifier = Modifier.fillMaxWidth().height(pageEdge).alpha(1f - depth),
                        )
                    }

                    SectionTitle(
                        "All apps",
                        hint = apps?.let { (if (it.size == 1) "1 app" else "${it.size} apps") + "  ·  Hold OK to pin" },
                        modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                    )
                    for (row in apps.orEmpty().chunked(columns)) {
                        Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                            for (app in row) {
                                key(app.pkg) {
                                    AppTile(
                                        app = app,
                                        iconSize = GridIcon,
                                        showPinBadge = app.pkg in pins,
                                        onOpen = { onOpen(app) },
                                        onTogglePin = { togglePin(app, fromFavorites = false) },
                                        // Equal cells across the row, with the icon centred in each
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toastVisible,
            // Bottom centre: clear of the clock and weather on the first page
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
        ) {
            ToastChip(toast?.message.orEmpty())
        }

        if (panelOpen) {
            SettingsPanel(
                settings = settings,
                onChange = onSettingsChange,
                onOpenSystemSettings = onOpenSystemSettings,
                onDismiss = { panelOpen = false },
            )
        }
    }
}

/**
 * Page-like D-pad scrolling for the home screen.
 *
 * Anything on the first page scrolls back to the top. In the grid nothing moves while the
 * focused row is inside the current page; moving below it brings the new row up to
 * [PAGE_EDGE] from the top, so the grid turns over a page at a time, and moving above it
 * puts the row [PAGE_EDGE] above the bottom.
 *
 * The chosen scroll target is remembered: Compose asks again on every animation frame
 * with the item's new offset, and answering from the target (not from whether the item is
 * already visible) keeps the animation going all the way to the page.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PageScrollSpec(
    private val scroll: ScrollState,
    /** Where the first page ends, in px from the top of the content. */
    private val firstPageEnd: Float,
) : BringIntoViewSpec {
    private var target = 0f

    override val scrollAnimationSpec: AnimationSpec<Float> = tween(550, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val current = scroll.value.toFloat()
        // Follow scrolling done some other way (touch, or a jump) before this request
        if (!scroll.isScrollInProgress) target = current
        val top = current + offset
        val bottom = top + size
        val edge = containerSize * PAGE_EDGE
        target = when {
            top < firstPageEnd -> 0f
            top >= target && bottom <= target + containerSize -> target
            top > target -> top - edge
            else -> bottom - (containerSize - edge)
        }.coerceIn(0f, scroll.maxValue.toFloat())
        return target - current
    }
}

private val RowBleed = 16.dp

/** Lets a horizontally clipped child extend [x] past its parent on both sides. */
private fun Modifier.bleed(x: Dp) = layout { measurable, constraints ->
    val extra = (x * 2).roundToPx()
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.minWidth + extra, maxWidth = constraints.maxWidth + extra),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-x.roundToPx(), 0) }
}

/** A short confirmation message. A new instance restarts the hide timer even for the same text. */
private class Toast(val message: String)

private class Refocus(val pkg: String?)

/** "All apps" and a gently bobbing chevron at the bottom of the first page. */
@Composable
private fun MoreBelow(count: Int?, modifier: Modifier = Modifier) {
    // A few gentle bobs, then still: an endless animation would keep the TV redrawing every frame
    val bob = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(1500)
        repeat(3) {
            bob.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
            bob.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
        }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            if (count != null) "All apps  ·  $count" else "All apps",
            style = TextStyle(color = Palette.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = TextShadow),
        )
        Canvas(Modifier.padding(top = 4.dp).size(width = 22.dp, height = 12.dp).offset(y = (bob.value * 4).dp)) {
            val w = size.width
            val h = size.height
            val stroke = 2.5.dp.toPx()
            drawLine(Palette.TextSecondary, Offset(stroke, stroke), Offset(w / 2, h - stroke), stroke, StrokeCap.Round)
            drawLine(Palette.TextSecondary, Offset(w / 2, h - stroke), Offset(w - stroke, stroke), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun SectionTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.Tile),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = TextStyle(
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                shadow = TextShadow,
            ),
        )
        if (hint != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                hint,
                style = TextStyle(color = Palette.TextTertiary, fontSize = 15.sp, shadow = TextShadow),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun EmptyFavorites(loading: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(horizontal = Spacing.Tile)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Palette.Hairline, shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Text(
            if (loading) "Loading apps…" else "Go down to All apps and hold OK on an app to pin it here",
            style = TextStyle(color = Palette.TextSecondary, fontSize = 17.sp, shadow = TextShadow),
        )
    }
}

@Composable
private fun ToastChip(message: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(Palette.Panel)
            .border(1.dp, Palette.Hairline, shape)
            .padding(horizontal = 26.dp, vertical = 14.dp),
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}
