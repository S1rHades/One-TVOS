package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
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
import kotlinx.coroutines.launch

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

/** Grid index of the "All apps" title: it follows the first page. */
private const val APPS_TITLE_INDEX = 1

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
) {
    var panelOpen by remember { mutableStateOf(false) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    // After an unpin from the favourites row, the package that should take focus (null = the pill)
    var refocus by remember { mutableStateOf<Refocus?>(null) }

    val pillFocus = remember { FocusRequester() }
    val firstAppFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
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

    // First launch: focus the first favourite, else the settings pill, so the opening view is the first page
    LaunchedEffect(apps != null) {
        if (apps == null || initialFocusDone) return@LaunchedEffect
        delay(150)
        val target = pinnedApps.firstOrNull()?.let { favoriteFocus[it.pkg] } ?: pillFocus
        runCatching { target.requestFocus() }
        initialFocusDone = true
    }

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
            val grid = rememberLazyGridState()
            // 0 on the first page, 1 once the grid fills the screen; dims the photo behind the apps
            val depth by remember(pageHeightPx) {
                derivedStateOf {
                    if (grid.firstVisibleItemIndex > 0) 1f
                    else (grid.firstVisibleItemScrollOffset / pageHeightPx).coerceIn(0f, 1f)
                }
            }
            Box(Modifier.fillMaxSize().alpha(depth).background(Color(0x8C05070D)))

            // The grid sits below the fold, so it is not composed yet and focus search from the
            // favourites row (a nested horizontal list) cannot find it. Down on the first page
            // therefore scrolls the grid in and focuses its first app explicitly.
            val enterGrid = Modifier.onKeyEvent { event ->
                val down = event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown
                if (down && !apps.isNullOrEmpty()) {
                    scope.launch {
                        grid.animateScrollToItem(APPS_TITLE_INDEX)
                        runCatching { firstAppFocus.requestFocus() }
                    }
                }
                down && !apps.isNullOrEmpty()
            }

            val rowScrolling = LocalBringIntoViewSpec.current
            CompositionLocalProvider(LocalBringIntoViewSpec provides PageScrollSpec) {
                LazyVerticalGrid(
                    state = grid,
                    columns = GridCells.Adaptive(minSize = tileWidth(GridIcon)),
                    contentPadding = PaddingValues(
                        start = Spacing.ScreenH,
                        end = Spacing.ScreenH,
                        top = Spacing.ScreenV,
                        bottom = 48.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    // First page: clock, weather and favourites only. The grid starts below the fold.
                    fullWidth("firstPage") {
                        Column(Modifier.fillMaxWidth().height(pageHeight)) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Tile)
                                    // With no favourites, Down from the pill goes straight to the grid
                                    .then(if (pinnedApps.isEmpty()) enterGrid else Modifier),
                                verticalAlignment = Alignment.Top,
                            ) {
                                TimeWidget()
                                if (weather != null) {
                                    Spacer(Modifier.width(36.dp))
                                    Box(Modifier.padding(top = 18.dp).width(1.dp).height(84.dp).background(Palette.Hairline))
                                    Spacer(Modifier.width(36.dp))
                                    WeatherWidget(weather, settings.weather, Modifier.padding(top = 12.dp))
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
                            Box(
                                Modifier.fillMaxWidth().height(FavoritesRowHeight).then(enterGrid),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (pinnedApps.isEmpty()) {
                                    EmptyFavorites(loading = apps == null)
                                } else {
                                    // The favourites row scrolls sideways the platform's usual way
                                    CompositionLocalProvider(LocalBringIntoViewSpec provides rowScrolling) {
                                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    }

                    fullWidth("appsTitle") {
                        SectionTitle(
                            "All apps",
                            hint = apps?.let { if (it.size == 1) "1 app" else "${it.size} apps" },
                            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
                        )
                    }
                    itemsIndexed(apps.orEmpty(), key = { _, app -> app.pkg }) { index, app ->
                        AppTile(
                            app = app,
                            iconSize = GridIcon,
                            showPinBadge = app.pkg in pins,
                            onOpen = { onOpen(app) },
                            onTogglePin = { togglePin(app, fromFavorites = false) },
                            // Fill the cell so the icon is centred in it
                            modifier = Modifier.animateItem().fillMaxWidth()
                                .let { if (index == 0) it.focusRequester(firstAppFocus) else it },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = toastVisible,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 36.dp),
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut() + slideOutVertically { -it / 2 },
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
 * Page-like D-pad scrolling for the home grid. Nothing moves while the focused item is
 * fully on screen. Moving down past the bottom brings the new row up to [PAGE_EDGE]
 * from the top, so the grid turns over a page at a time; moving up past the top puts
 * the item [PAGE_EDGE] above the bottom, which for the favourites row is the first page.
 */
@OptIn(ExperimentalFoundationApi::class)
private object PageScrollSpec : BringIntoViewSpec {
    override val scrollAnimationSpec: AnimationSpec<Float> = tween(550, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val edge = containerSize * PAGE_EDGE
        val end = offset + size
        return when {
            offset < 0f -> end - (containerSize - edge)
            end > containerSize -> offset - edge
            else -> 0f
        }
    }
}

/** A short confirmation message. A new instance restarts the hide timer even for the same text. */
private class Toast(val message: String)

private class Refocus(val pkg: String?)

/** An item that spans the whole grid row (first page, section titles). */
private fun LazyGridScope.fullWidth(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

/** "All apps" and a gently bobbing chevron at the bottom of the first page. */
@Composable
private fun MoreBelow(count: Int?, modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "moreBelow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(
            if (count != null) "All apps  ·  $count" else "All apps",
            style = TextStyle(color = Palette.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium, shadow = TextShadow),
        )
        Canvas(Modifier.padding(top = 4.dp).size(width = 22.dp, height = 12.dp).offset(y = (bob * 4).dp)) {
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
