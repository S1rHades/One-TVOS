package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.FavoritesLayout
import app.pill.launcher.Fixtures
import app.pill.launcher.News
import app.pill.launcher.PrayerTimes
import app.pill.launcher.Place
import app.pill.launcher.Weather
import app.pill.launcher.NewsTopic
import app.pill.launcher.TeamHit
import java.time.Instant
import app.pill.launcher.HomeStatus
import app.pill.launcher.FileShare
import app.pill.launcher.LauncherSettings
import app.pill.launcher.MaxWidgets
import app.pill.launcher.WeatherMode
import app.pill.launcher.WidgetKind
import app.pill.launcher.moved
import app.pill.launcher.withRowToggled
import app.pill.launcher.stackShown
import app.pill.launcher.UpdateState
import kotlinx.coroutines.delay

private const val SETTINGS_PANEL_MS = 320

/** A page change: the old page fades in [PageOut], then the new one in [PageIn], both sliding over [PageSlide]. */
private const val PageOut = 90
private const val PageIn = 180
private const val PageSlide = 260

/** How far a list goes soft at an edge with more beyond it. */
private val ListEdge = 22.dp
private val PanelShape = RoundedCornerShape(28.dp)
/**
 * Settings, as a floating card on the right. It lives in its own dialog window, so
 * D-pad focus stays inside it. Back (the dialog's dismiss request) slides it out
 * before it is removed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    /** Runs a TV & system shortcut; returns a message to show under its row (a result, or why it can't). */
    onSystemAction: (SystemAction) -> String? = { null },
    onDismiss: () -> Unit,
    /** Why photos or weather last failed to load, shown under their rows; null when fine. */
    photoError: String? = null,
    weatherError: String? = null,
    /** The reading on screen, to name the place Automatic found. */
    weather: Weather? = null,
    /** The prayer times as fetched, why not, and whether a fetch is running; Refresh now asks again. */
    prayers: PrayerTimes? = null,
    prayersError: String? = null,
    prayersRefreshing: Boolean = false,
    onRefreshPrayers: () -> Unit = {},
    /** The followed teams' latest fixtures, for each team's status line. */
    football: List<Fixtures> = emptyList(),
    /** The last headlines fetch, for each source's status line. */
    news: News? = null,
    /** Teams matching a search (off the main thread), for adding one. */
    searchTeams: suspend (String) -> List<TeamHit> = { emptyList() },
    /** Towns matching a name (off the main thread), for the weather's place. */
    searchPlaces: suspend (String) -> Result<List<Place>> = { Result.success(emptyList()) },
    /** Credit for the online photo on screen. */
    photoCredit: String? = null,
    home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
    onMakeDefault: () -> String? = { null },
    onHomeHelper: () -> String? = { null },
    onAllowAutostart: () -> String? = { null },
    /** True while a Widgets page is open, so the home screen can stand beside the panel as a live preview. */
    onCustomizing: (Boolean) -> Unit = {},
    /** The stack widget focused on the Widget stack page, to hold it in view on the home screen. */
    onStackPreview: (WidgetKind?) -> Unit = {},
    /** Updates from GitHub, for the Updates page. */
    update: UpdateState = UpdateState(),
    version: String = "",
    onCheckUpdate: () -> Unit = {},
    onStartUpdate: () -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    /** The page to open on (Updates, from "Updated to …" in the notification bar); null for the list. */
    startPage: SettingsPage? = null,
    /** Whether the TV lets Pill Launcher read other apps' notifications. */
    noticeAccess: Boolean = false,
    /** Opens the TV's Notification access screen; a message when it can't. */
    onNoticeAccess: () -> String? = { null },
    /** Apps with notifications now, or muted, as (package, name). */
    noticeApps: List<Pair<String, String>> = emptyList(),
    /** Files between phones and the TV, for its trusted devices; null where there is none. */
    share: FileShare? = null,
) {
    // A message from the last attempt, shown under the row until the panel closes
    var defaultNote by remember { mutableStateOf<String?>(null) }
    var helperNote by remember { mutableStateOf<String?>(null) }
    var autostartNote by remember { mutableStateOf<String?>(null) }
    // Each press on a widget past the limit shows the toast again
    var limitToasts by remember { mutableIntStateOf(0) }
    var limitToastShown by remember { mutableStateOf(false) }
    LaunchedEffect(limitToasts) {
        if (limitToasts > 0) {
            limitToastShown = true
            delay(2600)
            limitToastShown = false
        }
    }
    // null: the list of categories; otherwise that category's page
    var page by remember { mutableStateOf(startPage) }
    var lastPage by remember { mutableStateOf(SettingsPage.HomeScreen) }
    val pageFirst = remember { FocusRequester() }
    val categoryFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    // On the Widgets page: a row turned on or off moves between the two groups, and focus goes with it
    val widgetFocus = remember { WidgetKind.entries.associateWith { FocusRequester() } }
    var refocusWidget by remember { mutableStateOf<WidgetKind?>(null) }
    LaunchedEffect(refocusWidget, settings.widgets) {
        val kind = refocusWidget ?: return@LaunchedEffect
        repeat(5) {
            delay(30)
            if (runCatching { widgetFocus.getValue(kind).requestFocus() }.isSuccess) {
                refocusWidget = null
                return@LaunchedEffect
            }
        }
        refocusWidget = null
    }
    // Rows on a page that open a page of their own (Widgets → Football teams, Headlines)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    // The widget being moved in the stack (hold OK, then Left / Right); Back puts it down
    var movingStackItem by remember { mutableStateOf<WidgetKind?>(null) }
    val close = {
        visible.targetState = false
        onCustomizing(false)
        onStackPreview(null)
    }
    // On the Widgets pages the home screen is the preview, so it isn't dimmed
    val customizing = page?.root == SettingsPage.Widgets
    LaunchedEffect(customizing) { onCustomizing(customizing) }
    LaunchedEffect(page) {
        // Getting started's widgets step is done once the page has been seen
        if (page == SettingsPage.Widgets && !settings.widgetsVisited) onChange(settings.copy(widgetsVisited = true))
        movingStackItem = null
        if (page != SettingsPage.Stack) onStackPreview(null)
    }

    // Focus follows the page: its first row going in, the category it came from coming back
    LaunchedEffect(page) {
        delay(if (page == null && lastPage == SettingsPage.HomeScreen) 150 else 60)
        val current = page
        val target = when {
            current == null -> categoryFocus.getValue(lastPage.root)
            // Back from a page inside this one: the row that opened it
            lastPage.parent == current -> categoryFocus.getValue(lastPage)
            else -> pageFirst
        }
        if (current != null) lastPage = current
        repeat(5) {
            if (runCatching { target.requestFocus() }.isSuccess) return@LaunchedEffect
            delay(50)
        }
    }

    // Remove the dialog only once the slide-out has finished
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onDismiss()
    }

    val scrimAlpha by rememberDim(visible.targetState && !customizing, if (customizing) Motion.Page else SETTINGS_PANEL_MS)
    val motion = tween<Float>(SETTINGS_PANEL_MS, easing = FastOutSlowInEasing)

    Dialog(
        // Back on a page returns to the list of categories; on the list it closes the panel
        onDismissRequest = {
            when {
                movingStackItem != null -> movingStackItem = null
                page != null -> page = page?.parent
                else -> close()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            // The bar stays at the top right; the panel starts below it
            TopNav(NavItem.Settings)
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(SETTINGS_PANEL_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(motion),
                exit = slideOutHorizontally(tween(SETTINGS_PANEL_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(motion),
            ) {
                Column(
                    Modifier
                        .padding(top = NavRoom, bottom = 16.dp, start = 24.dp, end = 24.dp)
                        .width(440.dp)
                        .fillMaxHeight()
                        .shadow(32.dp, PanelShape)
                        .glass(PanelShape)
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                ) {
                    // The heading changes with the page below it, not ahead of it
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = { fadeIn(tween(PageIn, delayMillis = PageOut)) togetherWith fadeOut(tween(PageOut)) },
                        // The panel's width, so a longer heading is never clipped while it grows
                        modifier = Modifier.fillMaxWidth(),
                        label = "settingsHeading",
                    ) { shown ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                            if (shown == null) {
                                GearIcon(tint = Palette.TextPrimary, iconSize = 26.dp)
                            } else {
                                Text("‹", color = Palette.TextSecondary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(shown?.title ?: "Settings", color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    // The category list, or one category's page sliding in over it
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val forward = targetState != null
                            // The old page has gone before the new one shows, so their rows never lie over each other
                            (slideInHorizontally(tween(PageSlide, easing = FastOutSlowInEasing)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(PageIn, delayMillis = PageOut))) togetherWith
                                (slideOutHorizontally(tween(PageSlide, easing = FastOutSlowInEasing)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(PageOut)))
                        },
                        modifier = Modifier.weight(1f),
                        label = "settingsPage",
                    ) { shown ->
                        val scroll = rememberScrollState()
                        val edge = with(LocalDensity.current) { ListEdge.toPx() }
                        // Rows go soft at an edge with more beyond it, and the focused row stops clear of that
                        CompositionLocalProvider(LocalBringIntoViewSpec provides remember(edge) { RowScrollSpec(edge, animate = true) }) {
                        Column(Modifier.fillMaxSize().glassEdges(scroll, ListEdge).verticalScroll(scroll)) {
                            when (shown) {
                                null -> Group(null) {
                                    for (category in SettingsPage.entries.filter { it.parent == null }) {
                                        SettingRow(
                                            title = category.title,
                                            subtitle = summaryOf(category, settings, home, update, version),
                                            dense = true,
                                            subtitleLines = 1,
                                            onClick = { page = category },
                                            modifier = Modifier.focusRequester(categoryFocus.getValue(category)),
                                        ) { focused -> Chevron(focused) }
                                    }
                                }
                                SettingsPage.Background -> Group(null) {
                            SettingRow(
                                title = "Photo slideshow",
                                subtitle = when {
                                    photoError != null -> "Can't load photos: $photoError"
                                    settings.slideshow -> "A new photo every ${formatInterval(settings.intervalSec)}"
                                    else -> "Keeps the current photo"
                                },
                                onClick = { onChange(settings.copy(slideshow = !settings.slideshow)) },
                                modifier = Modifier.focusRequester(pageFirst),
                            ) { focused -> Switch(checked = settings.slideshow, focused = focused) }

                            SettingRow(
                                title = "Online nature photos",
                                subtitle = if (settings.onlinePhotos) {
                                    photoCredit?.let { "Now: $it" } ?: "A new set every day from Wikimedia Commons"
                                } else {
                                    "Uses the 12 built-in photos"
                                },
                                onClick = { onChange(settings.copy(onlinePhotos = !settings.onlinePhotos)) },
                            ) { focused -> Switch(checked = settings.onlinePhotos, focused = focused) }

                            val options = LauncherSettings.IntervalOptions
                            val current = options.indexOf(settings.intervalSec).coerceAtLeast(0)
                            SettingRow(
                                title = "Change photo every",
                                subtitle = if (settings.slideshow) null else "Turn on the slideshow first",
                                enabled = settings.slideshow,
                                onClick = { onChange(settings.copy(intervalSec = options[(current + 1) % options.size])) },
                                onStep = { by ->
                                    val next = (current + by).coerceIn(0, options.lastIndex)
                                    if (next != current) onChange(settings.copy(intervalSec = options[next]))
                                },
                            ) { focused ->
                                Stepper(formatInterval(settings.intervalSec), current > 0, current < options.lastIndex, focused)
                            }
                                }
                                SettingsPage.Widgets -> {
                                    // The widgets on, numbered in their order on the home screen, apart from the
                                    // rest: four to think about, not a list of ten switches
                                    val available = WidgetKind.entries.filter { it !in settings.widgets }
                                    val firstRow = (settings.widgets + available).first()
                                    val widgetRow: @Composable (WidgetKind) -> Unit = { kind ->
                                        val position = settings.widgets.indexOf(kind)
                                        val on = position >= 0
                                        val full = !on && settings.widgets.size >= MaxWidgets
                                        SettingRow(
                                            title = (if (on) "${position + 1}  " else "") + kind.title,
                                            subtitle = when {
                                                on && kind == WidgetKind.Weather && weatherError != null -> "Can't get the weather: $weatherError"
                                                on && kind == WidgetKind.Fennecs && football.firstOrNull()?.error != null -> "Can't get fixtures: ${football.first().error}"
                                                on && kind == WidgetKind.Headlines && news != null && news.headlines.isEmpty() -> "Can't get headlines · see Headlines below"
                                                on && kind == WidgetKind.Stack && settings.stackShown().isEmpty() -> "Empty · choose its widgets below"
                                                on && kind == WidgetKind.Prayers && settings.mosque.isBlank() -> "Choose a mosque · see Prayer times below"
                                                on && kind == WidgetKind.Prayers && prayers == null && prayersError != null -> "Can't reach MAWAQIT · see Prayer times below"
                                                on && settings.widgets.size > 1 -> "Position ${position + 1} of ${settings.widgets.size} · Left / Right to move"
                                                !on && kind in settings.stackShown() && WidgetKind.Stack in settings.widgets ->
                                                    if (full) "In the widget stack" else "In the widget stack · OK shows it by itself"
                                                full -> "Turn another widget off first"
                                                else -> kind.description
                                            },
                                            dimmed = full,
                                            modifier = Modifier
                                                .focusRequester(widgetFocus.getValue(kind))
                                                .then(if (kind == firstRow) Modifier.focusRequester(pageFirst) else Modifier),
                                            onClick = {
                                                if (full) {
                                                    limitToasts++
                                                } else {
                                                    onChange(settings.withRowToggled(kind))
                                                    // It moves to the other group: keep focus on it
                                                    refocusWidget = kind
                                                }
                                            },
                                            onStep = if (!on) null else { by -> onChange(settings.copy(widgets = settings.widgets.moved(kind, by))) },
                                        ) { focused -> Switch(checked = on, focused = focused) }
                                    }
                                    if (settings.widgets.isNotEmpty()) {
                                        Group("On · ${settings.widgets.size} of $MaxWidgets · Left / Right moves one") {
                                            for (kind in settings.widgets) key(kind) { widgetRow(kind) }
                                        }
                                    }
                                    if (available.isNotEmpty()) {
                                        Group("Available") {
                                            for (kind in available) key(kind) { widgetRow(kind) }
                                        }
                                    }
                                    Group(null) {
                                        SettingRow(
                                            title = SettingsPage.Stack.title,
                                            subtitle = settings.stack.joinToString(", ") { it.title }.ifEmpty { "Empty" } +
                                                if (WidgetKind.Stack in settings.widgets) "" else " · turn on Widget stack above to show it",
                                            onClick = { page = SettingsPage.Stack },
                                            modifier = Modifier.focusRequester(categoryFocus.getValue(SettingsPage.Stack)),
                                        ) { focused -> Chevron(focused) }
                                        SettingRow(
                                            title = SettingsPage.Football.title,
                                            subtitle = settings.teams.joinToString(", ") { it.name } + " · match day: " + settings.matchDay.title.lowercase(),
                                            onClick = { page = SettingsPage.Football },
                                            modifier = Modifier.focusRequester(categoryFocus.getValue(SettingsPage.Football)),
                                        ) { focused -> Chevron(focused) }
                                        SettingRow(
                                            title = SettingsPage.News.title,
                                            subtitle = NewsTopic.entries.filter { it in settings.newsTopics }.joinToString(", ") { it.title },
                                            onClick = { page = SettingsPage.News },
                                            modifier = Modifier.focusRequester(categoryFocus.getValue(SettingsPage.News)),
                                        ) { focused -> Chevron(focused) }
                                        SettingRow(
                                            title = SettingsPage.Prayers.title,
                                            subtitle = prayers?.mosque?.ifBlank { null } ?: settings.mosque.ifBlank { "No mosque chosen" },
                                            onClick = { page = SettingsPage.Prayers },
                                            modifier = Modifier.focusRequester(categoryFocus.getValue(SettingsPage.Prayers)),
                                        ) { focused -> Chevron(focused) }
                                    }
                                    Group(null) {
                            SettingRow(
                                title = SettingsPage.WeatherPlace.title,
                                subtitle = settings.weatherPlace?.label ?: ("Automatic" + (weather?.place?.takeIf { it.isNotBlank() }?.let { " · now $it" } ?: "")),
                                onClick = { page = SettingsPage.WeatherPlace },
                                modifier = Modifier.focusRequester(categoryFocus.getValue(SettingsPage.WeatherPlace)),
                            ) { focused -> Chevron(focused) }
                            // Shown or hidden under Widgets; only the unit lives here
                            val units = listOf(WeatherMode.Celsius, WeatherMode.Fahrenheit)
                            val unit = units.indexOf(settings.weather).coerceAtLeast(0)
                            SettingRow(
                                title = "Temperature unit",
                                subtitle = "For the weather widget",
                                onClick = { onChange(settings.copy(weather = units[(unit + 1) % units.size])) },
                                onStep = { by ->
                                    val next = (unit + by).coerceIn(0, units.lastIndex)
                                    if (next != unit) onChange(settings.copy(weather = units[next]))
                                },
                            ) { focused ->
                                Stepper(weatherLabel(settings.weather), unit > 0, unit < units.lastIndex, focused)
                            }
                                    }
                                }
                                SettingsPage.Favorites -> Group(null) {
                            val layouts = FavoritesLayout.entries
                            val layoutIndex = layouts.indexOf(settings.favoritesLayout)
                            SettingRow(
                                title = "Layout",
                                modifier = Modifier.focusRequester(pageFirst),
                                subtitle = when (settings.favoritesLayout) {
                                    FavoritesLayout.Scroll -> "One row that slides when you go past the edge"
                                    FavoritesLayout.Wrap -> "As many as fit across; the rest on rows below"
                                },
                                onClick = { onChange(settings.copy(favoritesLayout = layouts[(layoutIndex + 1) % layouts.size])) },
                                onStep = { by ->
                                    val next = (layoutIndex + by).coerceIn(0, layouts.lastIndex)
                                    if (next != layoutIndex) onChange(settings.copy(favoritesLayout = layouts[next]))
                                },
                            ) { focused ->
                                Stepper(
                                    if (settings.favoritesLayout == FavoritesLayout.Scroll) "Scroll" else "Wrap",
                                    layoutIndex > 0,
                                    layoutIndex < layouts.lastIndex,
                                    focused,
                                )
                            }
                            SettingRow(
                                title = "Order",
                                subtitle = if (settings.favoritesByUse) {
                                    "Most opened first, the next most opened last"
                                } else {
                                    "Where you put them · Menu → Move"
                                },
                                onClick = { onChange(settings.copy(favoritesByUse = !settings.favoritesByUse)) },
                                onStep = { by ->
                                    val byUse = by > 0
                                    if (byUse != settings.favoritesByUse) onChange(settings.copy(favoritesByUse = byUse))
                                },
                            ) { focused ->
                                Stepper(if (settings.favoritesByUse) "By use" else "Yours", settings.favoritesByUse, !settings.favoritesByUse, focused)
                            }
                            SettingRow(
                                title = "Animations",
                                subtitle = if (settings.favoritesAnimation) "Smooth sliding and rearranging" else "Instant, no motion",
                                onClick = { onChange(settings.copy(favoritesAnimation = !settings.favoritesAnimation)) },
                            ) { focused -> Switch(checked = settings.favoritesAnimation, focused = focused) }
                                }
                                SettingsPage.Idle -> Group(null) {
                            val idleOptions = LauncherSettings.IdleOptions
                            val idleIndex = idleOptions.indexOf(settings.idleMinutes).coerceAtLeast(0)
                            SettingRow(
                                title = "Go idle after",
                                modifier = Modifier.focusRequester(pageFirst),
                                subtitle = if (settings.idleMinutes == 0) {
                                    "Never: the apps always stay on screen"
                                } else {
                                    "Without a key press, only the widgets stay"
                                },
                                onClick = { onChange(settings.copy(idleMinutes = idleOptions[(idleIndex + 1) % idleOptions.size])) },
                                onStep = { by ->
                                    val next = (idleIndex + by).coerceIn(0, idleOptions.lastIndex)
                                    if (next != idleIndex) onChange(settings.copy(idleMinutes = idleOptions[next]))
                                },
                            ) { focused ->
                                Stepper(idleLabel(settings.idleMinutes), idleIndex > 0, idleIndex < idleOptions.lastIndex, focused)
                            }
                            SettingRow(
                                title = "Idle animation",
                                subtitle = if (settings.idleMinutes == 0) "Turn on the idle screen first" else "Slow photo zoom; widgets step a few dp every 3 min to spare the screen",
                                enabled = settings.idleMinutes != 0,
                                onClick = { onChange(settings.copy(idleAnimation = !settings.idleAnimation)) },
                            ) { focused -> Switch(checked = settings.idleAnimation, focused = focused) }
                                }
                                SettingsPage.Home -> Group(null) {
                            SettingRow(
                                title = "Open at startup",
                                modifier = Modifier.focusRequester(pageFirst),
                                subtitle = autostartNote ?: when {
                                    !settings.autostart -> "Show Pill Launcher when the TV turns on"
                                    home.canAutostart -> "Opens when the TV turns on"
                                    else -> "Needs one more permission, below"
                                },
                                onClick = {
                                    val on = !settings.autostart
                                    onChange(settings.copy(autostart = on))
                                    // Android blocks opening at boot without one of these; go straight to the fix
                                    if (on && !home.canAutostart) autostartNote = onAllowAutostart()
                                },
                            ) { focused -> Switch(checked = settings.autostart, focused = focused) }
                            if (settings.autostart && !home.canAutostart) {
                                SettingRow(
                                    title = "Allow opening at startup",
                                    subtitle = "Turn on \"Display over other apps\" for Pill Launcher",
                                    onClick = { autostartNote = onAllowAutostart() },
                                ) { focused -> Chevron(focused) }
                            }

                            SettingRow(
                                title = "Default home screen",
                                subtitle = defaultNote ?: if (home.isDefault) {
                                    "Pill Launcher opens on Home and after closing apps"
                                } else {
                                    "Make Pill Launcher open on Home and after closing apps"
                                },
                                onClick = { if (!home.isDefault) defaultNote = onMakeDefault() },
                            ) { focused -> if (home.isDefault) Check(focused) else Chevron(focused) }

                            if (!home.isDefault) {
                                SettingRow(
                                    title = "Home button helper",
                                    subtitle = helperNote ?: if (home.redirectOn) {
                                        "On: returns here whenever the TV's own home screen opens"
                                    } else {
                                        "For TVs that won't change their home screen. Turn on \"Pill Launcher Home button\" in Accessibility"
                                    },
                                    onClick = { helperNote = onHomeHelper() },
                                ) { focused -> if (home.redirectOn) Check(focused) else Chevron(focused) }
                            }
                                }
                                SettingsPage.HomeScreen -> Group(null) {
                                    for ((i, sub) in listOf(SettingsPage.Background, SettingsPage.Favorites, SettingsPage.Idle).withIndex()) {
                                        SettingRow(
                                            title = sub.title,
                                            subtitle = summaryOf(sub, settings, home, update, version),
                                            onClick = { page = sub },
                                            modifier = Modifier
                                                .focusRequester(categoryFocus.getValue(sub))
                                                .then(if (i == 0) Modifier.focusRequester(pageFirst) else Modifier),
                                        ) { focused -> Chevron(focused) }
                                    }
                                }
                                SettingsPage.System -> Column {
                                    Group(null) {
                                        SettingRow(
                                            title = SettingsPage.Updates.title,
                                            subtitle = summaryOf(SettingsPage.Updates, settings, home, update, version),
                                            onClick = { page = SettingsPage.Updates },
                                            modifier = Modifier
                                                .focusRequester(categoryFocus.getValue(SettingsPage.Updates))
                                                .focusRequester(pageFirst),
                                        ) { focused -> Chevron(focused) }
                                    }
                                    SystemPage(onSystemAction, Modifier)
                                }
                                SettingsPage.Files -> FilesSettingsPage(settings, onChange, share, Modifier.focusRequester(pageFirst))
                                SettingsPage.Notifications -> NotificationsPage(settings, onChange, noticeAccess, onNoticeAccess, noticeApps, Modifier.focusRequester(pageFirst))
                                SettingsPage.Updates -> UpdatesPage(
                                    settings = settings,
                                    onChange = onChange,
                                    update = update,
                                    version = version,
                                    onCheck = onCheckUpdate,
                                    onStart = onStartUpdate,
                                    onCancel = onCancelUpdate,
                                    firstModifier = Modifier.focusRequester(pageFirst),
                                )
                                SettingsPage.Football -> FootballPage(settings, onChange, football, searchTeams, Modifier.focusRequester(pageFirst))
                                SettingsPage.News -> NewsPage(settings, onChange, news, Modifier.focusRequester(pageFirst))
                                SettingsPage.WeatherPlace -> WeatherPlacePage(settings, onChange, weather, nearMosque = prayers?.spot != null, searchPlaces, Modifier.focusRequester(pageFirst))
                                SettingsPage.Prayers -> PrayersPage(settings, onChange, prayers, prayersError, prayersRefreshing, onRefreshPrayers, Modifier.focusRequester(pageFirst))
                                SettingsPage.Stack -> StackPage(
                                    settings = settings,
                                    onChange = onChange,
                                    moving = movingStackItem,
                                    onMoving = { movingStackItem = it },
                                    onPreview = onStackPreview,
                                    firstModifier = Modifier.focusRequester(pageFirst),
                                )
                            }
                        }
                        }
                    }

                    Footer()
                }
            }
            // Over the bottom of the panel, centred on it
            AnimatedVisibility(
                visible = limitToastShown,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 40.dp).width(440.dp),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                Box(contentAlignment = Alignment.Center) { ToastChip("Maximum of $MaxWidgets widgets can be active at once") }
            }
        }
    }
}

private fun idleLabel(minutes: Int): String = if (minutes == 0) "Off" else "$minutes min"

private fun formatInterval(sec: Int): String = if (sec % 60 == 0) "${sec / 60} min" else "$sec s"

private fun weatherLabel(mode: WeatherMode): String = when (mode) {
    WeatherMode.Off -> "Off" // only in settings saved before widgets
    WeatherMode.Celsius -> "°C"
    WeatherMode.Fahrenheit -> "°F"
}

/** The settings categories, each on its own page; a page with a [parent] opens from that page. */
enum class SettingsPage(val title: String, val parent: SettingsPage? = null) {
    // The categories, in the list's order: six, so the whole list fits without scrolling
    HomeScreen("Home screen"),
    Widgets("Widgets"),
    Notifications("Notifications"),
    Files("Files"),
    Home("Home & startup"),
    System("TV & system"),
    // Pages inside a category
    Background("Background", HomeScreen),
    Favorites("Favorites row", HomeScreen),
    Idle("Idle screen", HomeScreen),
    Updates("Updates", System),
    Stack("Widget stack", Widgets),
    Football("Football teams", Widgets),
    News("Headlines", Widgets),
    Prayers("Prayer times", Widgets),
    WeatherPlace("Weather location", Widgets),
    ;

    /** The category this page is under, in the list. */
    val root: SettingsPage get() = parent?.root ?: this
}

/** One line under each category, saying what it is set to now. */
private fun summaryOf(page: SettingsPage, settings: LauncherSettings, home: HomeStatus, update: UpdateState = UpdateState(), version: String = ""): String = when (page) {
    SettingsPage.Updates -> updateSummary(update, version)
    SettingsPage.HomeScreen -> listOf(
        if (settings.slideshow) "Photos every ${formatInterval(settings.intervalSec)}" else "One photo",
        summaryOf(SettingsPage.Favorites, settings, home).lowercase(),
        if (settings.idleMinutes == 0) "no idle screen" else "idle after ${settings.idleMinutes} min",
    ).joinToString(" · ")
    SettingsPage.Background -> listOf(
        if (settings.onlinePhotos) "Online nature photos" else "Built-in photos",
        if (settings.slideshow) "every ${formatInterval(settings.intervalSec)}" else "slideshow off",
    ).joinToString(", ")
    SettingsPage.Widgets -> settings.widgets.joinToString(", ") { it.title.substringBefore(" (") }.ifEmpty { "None" }
    SettingsPage.Favorites -> (if (settings.favoritesLayout == FavoritesLayout.Scroll) "Scroll" else "Wrap") +
        if (settings.favoritesByUse) ", by use" else ""
    SettingsPage.Idle -> if (settings.idleMinutes == 0) "Off" else "After ${settings.idleMinutes} min"
    SettingsPage.Home -> when {
        home.isDefault -> "Pill Launcher is the home screen"
        home.redirectOn -> "Home button helper on"
        else -> "Not the home screen yet"
    }
    SettingsPage.Notifications -> listOfNotNull(
        if (settings.appNotices) "App notifications" else null,
        if (settings.footballNotices) "football" else null,
        if (settings.connectionNotices) "connection" else null,
        if (settings.prayerNotices) "prayer times" else null,
    ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Updates and files only" }
    SettingsPage.Files -> listOfNotNull(
        if (settings.keepReceiving) "Keep receiving" else null,
        if (settings.askInstalledApks) "asks before deleting APKs" else null,
        if (settings.tidyOld) "files kept 30 days" else null,
    ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Phones, tidying" }
    SettingsPage.System -> "Updates, backup, free up memory, network, display, sound, uninstall"
    SettingsPage.Football -> settings.teams.joinToString(", ") { it.name }
    SettingsPage.News -> settings.newsTopics.joinToString(", ") { it.title }
    SettingsPage.Stack -> settings.stack.joinToString(", ") { it.title }
    SettingsPage.Prayers -> settings.mosque.ifBlank { "No mosque chosen" }
    SettingsPage.WeatherPlace -> settings.weatherPlace?.name ?: "Automatic"
}
