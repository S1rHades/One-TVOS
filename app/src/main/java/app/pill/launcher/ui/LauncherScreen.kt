package app.pill.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.launch
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.composed
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppEntry
import app.pill.launcher.Fixtures
import app.pill.launcher.Photo
import app.pill.launcher.FavoritesLayout
import app.pill.launcher.HomeStatus
import app.pill.launcher.LauncherSettings
import app.pill.launcher.Weather
import kotlinx.coroutines.delay

private val FavoriteIcon = 96.dp
private val GridIcon = 84.dp

/** Room the clock, weather and buttons take at the top of the first page. */
private val HeaderHeight = 150.dp

/**
 * Space between the favourites row and the bottom of the first page. Also the margin
 * [PageScrollSpec] keeps when it scrolls, so going back up from the grid lands exactly
 * on the first page.
 */
private const val PAGE_EDGE = 0.14f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    apps: List<AppEntry>?,
    weather: Weather?,
    /** Why the last weather fetch failed, shown in settings; null when it works. */
    weatherError: String? = null,
    /** Algeria's next and latest match, for the Les Fennecs widget. */
    fixtures: Fixtures? = null,
    /** Why the last fixtures fetch failed; null when it works. */
    fixturesError: String? = null,
    /** Today's online nature photos; empty until loaded, or when unavailable. */
    onlinePhotos: List<Photo> = emptyList(),
    pins: List<String>,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onOpenSystemSettings: () -> Unit,
    /** The TV & system shortcuts in settings; returns a message for the row (a result, or why it can't). */
    onSystemAction: (SystemAction) -> String? = { null },
    /** Move a favourite [by] places along the row. */
    onMovePin: (AppEntry, by: Int) -> Unit = { _, _ -> },
    onUninstall: (AppEntry) -> Unit = {},
    onAppInfo: (AppEntry) -> Unit = {},
    /** The TV's Wi-Fi / network settings, from the status bar's connection button. */
    onOpenNetworkSettings: () -> Unit = {},
    /** Bumped each time Home is pressed while the launcher is already showing. */
    homePresses: Int = 0,
    /** Bumped when the TV wakes or the launcher comes back to the front: always start awake. */
    wakes: Int = 0,
    home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
    /** Ask to become the home screen; returns a message if this TV can't. */
    onMakeDefault: () -> String? = { null },
    /** Open Accessibility settings for the Home button helper; returns a message on failure. */
    onHomeHelper: () -> String? = { null },
    /** Open "Display over other apps" so Autostart can open the launcher at boot. */
    onAllowAutostart: () -> String? = { null },
    /** The TV's own search (Google / Assistant), when it has one. */
    onSystemSearch: (() -> Unit)? = null,
) {
    var panelOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    // Who took the photo on screen (online photos only), shown on the idle screen and in settings
    var photoCredit by remember { mutableStateOf<String?>(null) }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    // Accent of the focused app; read only by AmbientLight, so focus moves don't recompose the screen
    val ambient = remember { mutableStateOf<Int?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    // After an unpin from the favourites row, the package that should take focus (null = the pill)
    var refocus by remember { mutableStateOf<Refocus?>(null) }
    var idle by remember { mutableStateOf(false) }
    // The app whose menu is open (and whether OK is still held), and the favourite being moved
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var lastInput by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }

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

    LaunchedEffect(moving, pins) {
        val pkg = moving ?: return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (runCatching { favoriteFocus[pkg]?.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // A moved favourite that's unpinned or uninstalled ends the move
    if (moving != null && moving !in pins) moving = null

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
        searchOpen = false
        idle = false
        focusFirstPage()
    }

    // Waking the TV (or coming back from an app) starts awake, with a fresh idle timer:
    // the timer keeps counting while the screen is off, so it would otherwise wake up idle
    LaunchedEffect(wakes) {
        idle = false
        lastInput = SystemClock.uptimeMillis()
    }

    // Idle: after idleMinutes without a key press, everything but the widgets fades
    // out. Each key press restarts the timer; panels open hold it off.
    LaunchedEffect(settings.idleMinutes, lastInput, panelOpen, searchOpen) {
        if (settings.idleMinutes == 0 || panelOpen || searchOpen) return@LaunchedEffect
        delay(settings.idleMinutes * 60_000L)
        idle = true
        // Back to the first page, with focus there too, so waking up lands somewhere visible
        focusFirstPage()
    }
    // While idle the widgets glide to the middle of the screen and grow, then hold still
    val idleFocus by animateFloatAsState(
        if (idle) 1f else 0f,
        tween(if (idle) 1800 else 600, easing = FastOutSlowInEasing),
        label = "idleFocus",
    )
    val idleShift = rememberBurnInShift(idle && settings.idleAnimation)
    val idleFade by animateFloatAsState(
        if (idle) 1f else 0f,
        tween(if (idle) 1600 else 450, easing = FastOutSlowInEasing),
        label = "idleFade",
    )

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

    // The blurred photo for glass surfaces, and Roboto Flex as the default for all text
    CompositionLocalProvider(
        LocalBackdrop provides backdrop,
        LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = Flex)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Ink)
                .onPreviewKeyEvent { event ->
                    lastInput = SystemClock.uptimeMillis()
                    // While idle, a key only wakes the screen; it does not also act
                    if (idle) {
                        if (event.type == KeyEventType.KeyDown) idle = false
                        true
                    } else {
                        false
                    }
                },
        ) {
            BackgroundSlideshow(
                // Today's online set when there is one, with the built-in photos to fall back on
                photos = if (settings.onlinePhotos && onlinePhotos.isNotEmpty()) onlinePhotos else BackgroundSources.photos,
                fallback = if (settings.onlinePhotos && onlinePhotos.isNotEmpty()) BackgroundSources.photos else emptyList(),
                onShown = { photoCredit = it.credit },
                intervalMs = settings.intervalSec * 1000L,
                enabled = settings.slideshow,
                drift = idle && settings.idleAnimation,
                onStatus = { photoError = it },
                onBackdrop = { backdrop = it },
            )
            BackgroundScrim()
            if (settings.ambientLight) AmbientLight(ambient, fade = { idleFade })

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val contentWidth = maxWidth - Spacing.ScreenH * 2
                // Wrapped favourites can need more than one row; the first page grows to fit them
                val favoriteRows = if (settings.favoritesLayout == FavoritesLayout.Wrap) {
                    favoritesPerRow(contentWidth, settings.favoritesStyle, FavoriteIcon).let { (pinnedApps.size + it - 1) / it }.coerceAtLeast(1)
                } else {
                    1
                }
                val pageHeight = maxOf(
                    maxHeight - Spacing.ScreenV,
                    HeaderHeight + favoritesRowHeight(settings.favoritesStyle, FavoriteIcon) * favoriteRows + maxHeight * PAGE_EDGE,
                )
                val pageEdge = maxHeight * PAGE_EDGE
                val pageHeightPx = with(LocalDensity.current) { pageHeight.toPx() }
                val columns = ((maxWidth - Spacing.ScreenH * 2) / tileWidth(GridIcon)).toInt().coerceAtLeast(1)
                // 0 on the first page, 1 once the grid fills the screen; dims the photo behind the apps
                val depth by remember(pageHeightPx) { derivedStateOf { (scroll.value / pageHeightPx).coerceIn(0f, 1f) } }
                // Alpha is read in the draw phase, so scrolling does not recompose the screen
                // Scrolling into All apps blurs and dims the photo, so the grid reads cleanly
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = depth }) {
                    backdrop?.let { drawBackdrop(it, IntSize(size.width.toInt(), size.height.toInt())) }
                    drawRect(Color(0x8C05070D))
                }

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
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Tile),
                                verticalAlignment = Alignment.Top,
                            ) {
                                WidgetRow(
                                    widgets = settings.widgets,
                                    weather = weather,
                                    weatherError = weatherError,
                                    mode = settings.weather,
                                    fixtures = fixtures,
                                    fixturesError = fixturesError,
                                    animate = !idle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .idlePresentation(focus = { idleFocus }, shift = { idleShift.value }),
                                )
                                Spacer(Modifier.width(16.dp))
                                // Buttons fade with the apps while idle; the widgets stay
                                Row(Modifier.padding(top = 8.dp).graphicsLayer { alpha = 1f - idleFade }) {
                                    QuickActions(
                                        onSearch = { searchOpen = true },
                                        onSettings = { panelOpen = true },
                                        onNetwork = onOpenNetworkSettings,
                                        settingsFocus = pillFocus,
                                    )
                                }
                            }

                            Spacer(Modifier.weight(1f))

                            // Until the launcher is the home screen (or helped), closing apps leads elsewhere
                            AnimatedVisibility(
                                visible = !home.isDefault && !home.redirectOn && !settings.homePromptDismissed,
                                enter = fadeIn(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                HomePrompt(
                                    onSetUp = {
                                        val cannot = onMakeDefault()
                                        if (cannot != null) {
                                            // The TV keeps its own home screen: the Home button helper is the way
                                            toast = Toast(onHomeHelper() ?: "Turn on Pill Launcher here to bring Home back to it")
                                        }
                                    },
                                    onDismiss = { onSettingsChange(settings.copy(homePromptDismissed = true)) },
                                    modifier = Modifier
                                        .padding(horizontal = Spacing.Tile, vertical = 8.dp)
                                        .graphicsLayer { alpha = 1f - idleFade },
                                )
                            }

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = favoritesRowHeight(settings.favoritesStyle, FavoriteIcon))
                                    .graphicsLayer { alpha = 1f - idleFade },
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (pinnedApps.isEmpty()) {
                                    EmptyFavorites(loading = apps == null)
                                } else {
                                    FavoritesRow(
                                        apps = pinnedApps,
                                        layout = settings.favoritesLayout,
                                        style = settings.favoritesStyle,
                                        onFocus = { ambient.value = it.accent },
                                        animate = settings.favoritesAnimation,
                                        iconSize = FavoriteIcon,
                                        width = contentWidth,
                                        focusFor = { app -> favoriteFocus.getOrPut(app.pkg) { FocusRequester() } },
                                        onOpen = onOpen,
                                        onMenu = { app, held -> menu = MenuRequest(app, held, fromFavorites = true) },
                                        moving = moving,
                                        onMove = onMovePin,
                                        onMoveDone = {
                                            moving = null
                                            toast = Toast("Done")
                                        },
                                    )
                                }
                            }

                            Spacer(Modifier.height(pageEdge))
                        }

                        SectionTitle(
                            "All apps",
                            hint = apps?.let { if (it.size == 1) "1 app" else "${it.size} apps" },
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                        for (row in apps.orEmpty().chunked(columns)) {
                            Row(Modifier.fillMaxWidth().padding(bottom = 6.dp).bottomEdgeFade(scroll, pageEdge)) {
                                for (app in row) {
                                    key(app.pkg) {
                                        AppTile(
                                            app = app,
                                            iconSize = GridIcon,
                                            showPinBadge = app.pkg in pins,
                                            onOpen = { onOpen(app) },
                                            onMenu = { held -> menu = MenuRequest(app, held, fromFavorites = false) },
                                            onFocus = { if (it) ambient.value = app.accent },
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

            // The photographer's credit, while the screen rests
            photoCredit?.let { credit ->
                Text(
                    credit,
                    color = Palette.TextTertiary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = Spacing.ScreenH, end = Spacing.ScreenH, bottom = 20.dp)
                        .graphicsLayer { alpha = idleFade },
                )
            }

            AnimatedVisibility(
                visible = toastVisible,
                // Bottom centre: clear of the widgets on the first page
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
                    onSystemAction = { action ->
                        if (action == SystemAction.AllSettings) {
                            onOpenSystemSettings()
                            null
                        } else {
                            onSystemAction(action)
                        }
                    },
                    photoError = photoError,
                    weatherError = weatherError,
                    fixturesError = fixturesError,
                    photoCredit = photoCredit,
                    home = home,
                    onMakeDefault = onMakeDefault,
                    onHomeHelper = onHomeHelper,
                    onAllowAutostart = onAllowAutostart,
                    onDismiss = { panelOpen = false },
                )
            }

            menu?.let { request ->
                val app = request.app
                AppMenu(
                    app = app,
                    pinned = app.pkg in pins,
                    okHeld = request.okHeld,
                    onAction = { action ->
                        when (action) {
                            MenuAction.TogglePin -> togglePin(app, fromFavorites = request.fromFavorites)
                            MenuAction.Move -> {
                                searchOpen = false
                                moving = app.pkg
                                toast = Toast("Left / Right to move ${app.label} · OK when done")
                            }
                            MenuAction.AppInfo -> onAppInfo(app)
                            MenuAction.Uninstall -> onUninstall(app)
                        }
                    },
                    onDismiss = { menu = null },
                )
            }

            if (searchOpen) {
                SearchPanel(
                    apps = apps.orEmpty(),
                    pins = pins,
                    onOpen = onOpen,
                    onMenu = { app, held -> menu = MenuRequest(app, held, fromFavorites = false) },
                    onSystemSearch = onSystemSearch,
                    onDismiss = { searchOpen = false },
                )
            }
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

    override val scrollAnimationSpec: AnimationSpec<Float> = tween(300, easing = FastOutSlowInEasing)

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

/**
 * Fades a grid row as it reaches the bottom of the screen while more rows continue below,
 * so the grid dissolves into the edge like the favourites row. Never the focused row.
 * Position is read in layout and applied in the draw phase, so scrolling only redraws.
 */
@Composable
private fun Modifier.bottomEdgeFade(scroll: ScrollState, zone: Dp): Modifier {
    val zonePx = with(LocalDensity.current) { zone.toPx() }
    var bottom by remember { mutableFloatStateOf(0f) }
    var screen by remember { mutableFloatStateOf(0f) }
    var holdsFocus by remember { mutableStateOf(false) }
    return this
        .onGloballyPositioned {
            bottom = it.positionInRoot().y + it.size.height
            screen = it.findRootCoordinates().size.height.toFloat()
        }
        .onFocusChanged { holdsFocus = it.hasFocus }
        .graphicsLayer {
            val t = if (holdsFocus || !scroll.canScrollForward || screen == 0f) 0f
            else ((bottom - (screen - zonePx)) / zonePx).coerceIn(0f, 1f)
            alpha = 1f - 0.75f * t * t * (3 - 2 * t)
        }
}

/** A short confirmation message. A new instance restarts the hide timer even for the same text. */
private class Toast(val message: String)

private class Refocus(val pkg: String?)

/**
 * Idle presentation of the widget row: at [focus] 1 it sits centred on the screen,
 * enlarged to about 86% of the screen's width (at most 1.4x), offset by [shift]. Read in
 * the draw phase; the row's place is measured before the transform, so it never feeds back.
 */
private fun Modifier.idlePresentation(focus: () -> Float, shift: () -> Offset): Modifier = composed {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    this
        .onGloballyPositioned {
            bounds = it.boundsInRoot()
            screen = it.findRootCoordinates().size
        }
        .graphicsLayer {
            val p = focus()
            if (p == 0f || bounds.width == 0f || screen.width == 0) return@graphicsLayer
            val grow = (screen.width * 0.86f / bounds.width).coerceIn(1f, 1.4f)
            val scale = 1f + (grow - 1f) * p
            scaleX = scale
            scaleY = scale
            val s = shift()
            translationX = p * (screen.width / 2f - bounds.center.x + s.x)
            translationY = p * (screen.height * 0.44f - bounds.center.y + s.y)
        }
}

/**
 * Guards plasma and OLED screens while idle without visible motion: every 3 minutes the
 * widgets glide a few dp to a new spot over 4 seconds, then stay perfectly still.
 */
@Composable
private fun rememberBurnInShift(active: Boolean): State<Offset> {
    val density = LocalDensity.current
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(active) {
        if (!active) {
            shift.snapTo(Offset.Zero)
            return@LaunchedEffect
        }
        val range = with(density) { 14.dp.toPx() }
        val spots = listOf(Offset(-1f, -0.6f), Offset(1f, 0.4f), Offset(-0.4f, 1f), Offset(0.6f, -1f), Offset(0f, 0f))
        var i = 0
        while (true) {
            delay(3 * 60_000L)
            shift.animateTo(spots[i++ % spots.size] * range, tween(4000, easing = FastOutSlowInEasing))
        }
    }
    return shift.asState()
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
                fontFamily = Flex,
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight(650),
                shadow = TextShadow,
            ),
        )
        if (hint != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                hint,
                style = TextStyle(fontFamily = Flex, color = Palette.TextTertiary, fontSize = 15.sp, shadow = TextShadow),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        // A thin rule fading to the right, setting the section apart without a box
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .weight(1f)
                .padding(bottom = 10.dp)
                .height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color(0x47FFFFFF), Color.Transparent))),
        )
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
            style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = 17.sp, shadow = TextShadow),
        )
    }
}

@Composable
internal fun ToastChip(message: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .shadow(24.dp, shape)
            .glass(shape)
            .padding(horizontal = 24.dp, vertical = 13.dp),
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight(560))
    }
}

/**
 * A soft wash of the focused app's colour rising from the lower left, cross-fading in
 * 400 ms as focus moves. Drawn in the draw phase from [accent], so only this layer redraws.
 */
@Composable
private fun AmbientLight(accent: State<Int?>, fade: () -> Float) {
    val color = animateColorAsState(
        accent.value?.let { Color(it) } ?: Color.Transparent,
        tween(400, easing = FastOutSlowInEasing),
        label = "ambient",
    )
    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = 1f - fade() }) {
        val c = color.value
        val origin = Offset(size.width * 0.2f, size.height)
        drawRect(Brush.radialGradient(listOf(c.copy(alpha = c.alpha * 0.42f), Color.Transparent), origin, size.width * 0.62f))
        drawRect(Brush.radialGradient(listOf(c.copy(alpha = c.alpha * 0.3f), Color.Transparent), origin, size.width * 0.3f))
    }
}

/** An open app menu: for [app], whether OK is still held, and whether it came from the favourites row. */
private class MenuRequest(val app: AppEntry, val okHeld: Boolean, val fromFavorites: Boolean)
