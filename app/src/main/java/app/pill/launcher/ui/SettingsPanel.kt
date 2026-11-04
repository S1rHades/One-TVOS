package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.FavoritesLayout
import app.pill.launcher.Fixtures
import app.pill.launcher.MatchDayMode
import app.pill.launcher.MaxTeams
import app.pill.launcher.News
import app.pill.launcher.PrayerTimes
import app.pill.launcher.Place
import app.pill.launcher.Weather
import app.pill.launcher.DefaultPrayerApi
import app.pill.launcher.cleanMosqueSlug
import app.pill.launcher.cleanPrayerApi
import app.pill.launcher.NewsSource
import app.pill.launcher.NewsTopic
import app.pill.launcher.TeamHit
import app.pill.launcher.withAlgeriaFirst
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import java.time.Instant
import app.pill.launcher.HomeStatus
import app.pill.launcher.FileShare
import app.pill.launcher.LauncherSettings
import app.pill.launcher.MaxWidgets
import app.pill.launcher.WeatherMode
import app.pill.launcher.WidgetKind
import app.pill.launcher.moved
import app.pill.launcher.toggled
import app.pill.launcher.withRowToggled
import app.pill.launcher.withStackToggled
import app.pill.launcher.withStackMoved
import app.pill.launcher.stackShown
import app.pill.launcher.StackTurnSec
import app.pill.launcher.UpdatePhase
import app.pill.launcher.UpdateState
import kotlin.math.roundToInt
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private const val PANEL_MS = 320
private val PanelShape = RoundedCornerShape(28.dp)
private val RowShape = RoundedCornerShape(16.dp)

/**
 * Settings, as a floating card on the right. It lives in its own dialog window, so
 * D-pad focus stays inside it. Back (the dialog's dismiss request) slides it out
 * before it is removed.
 */
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
    var lastPage by remember { mutableStateOf(SettingsPage.Background) }
    val pageFirst = remember { FocusRequester() }
    val categoryFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
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
        movingStackItem = null
        if (page != SettingsPage.Stack) onStackPreview(null)
    }

    // Focus follows the page: its first row going in, the category it came from coming back
    LaunchedEffect(page) {
        delay(if (page == null && lastPage == SettingsPage.Background) 150 else 60)
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

    val scrimAlpha by animateFloatAsState(
        if (visible.targetState && !customizing) 1f else 0f,
        tween(if (customizing) Motion.Page else PANEL_MS, easing = Motion.EnterEasing),
        label = "scrimAlpha",
    )
    val motion = tween<Float>(PANEL_MS, easing = FastOutSlowInEasing)

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
                enter = slideInHorizontally(tween(PANEL_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(motion),
                exit = slideOutHorizontally(tween(PANEL_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(motion),
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        if (page == null) {
                            GearIcon(tint = Palette.TextPrimary, iconSize = 26.dp)
                        } else {
                            Text("‹", color = Palette.TextSecondary, fontSize = 30.sp, fontWeight = FontWeight.Medium)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(page?.title ?: "Settings", color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(18.dp))

                    // The category list, or one category's page sliding in over it
                    AnimatedContent(
                        targetState = page,
                        transitionSpec = {
                            val forward = targetState != null
                            (slideInHorizontally(tween(240, easing = FastOutSlowInEasing)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(240))) togetherWith
                                (slideOutHorizontally(tween(240, easing = FastOutSlowInEasing)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(160)))
                        },
                        modifier = Modifier.weight(1f),
                        label = "settingsPage",
                    ) { shown ->
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                            when (shown) {
                                null -> Group(null) {
                                    for (category in SettingsPage.entries.filter { it.parent == null }) {
                                        SettingRow(
                                            title = category.title,
                                            subtitle = summaryOf(category, settings, home, update, version),
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
                                    Group("${settings.widgets.size} of $MaxWidgets on · Left / Right moves one") {
                            // On widgets first, in header order, then the others
                            val ordered = settings.widgets + WidgetKind.entries.filter { it !in settings.widgets }
                            for (kind in ordered) {
                                key(kind) {
                                    val position = settings.widgets.indexOf(kind)
                                    val on = position >= 0
                                    val full = !on && settings.widgets.size >= MaxWidgets
                                    SettingRow(
                                        title = kind.title,
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
                                        modifier = if (kind == ordered.first()) Modifier.focusRequester(pageFirst) else Modifier,
                                        onClick = {
                                            if (full) limitToasts++ else onChange(settings.withRowToggled(kind))
                                        },
                                        onStep = if (!on) null else { by -> onChange(settings.copy(widgets = settings.widgets.moved(kind, by))) },
                                    ) { focused -> Switch(checked = on, focused = focused) }
                                }
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
                                SettingsPage.System -> SystemPage(onSystemAction, Modifier.focusRequester(pageFirst))
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
                                SettingsPage.WeatherPlace -> WeatherPlacePage(settings, onChange, weather, searchPlaces, Modifier.focusRequester(pageFirst))
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

/** A labelled block of rows on a faint card. */
@Composable
private fun Group(label: String?, content: @Composable ColumnScope.() -> Unit) {
    if (label != null) {
        Text(
            label.uppercase(),
            color = Palette.TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
        )
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0x0FFFFFFF))
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        content = content,
    )
    Spacer(Modifier.height(14.dp))
}

/**
 * One setting. Focus (or hover) turns it into a white card with dark text.
 * [onStep], when given, makes Left / Right change the value in place.
 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Looks unavailable but still takes focus and clicks (to explain why). */
    dimmed: Boolean = false,
    onStep: ((Int) -> Unit)? = null,
    /** Sees keys before the row does (holding OK on the Widget stack page). */
    onPreviewKey: ((KeyEvent) -> Boolean)? = null,
    /** Being moved: a white edge while it travels. */
    moving: Boolean = false,
    /** A line of text to read rather than a setting: smaller, wrapping title. */
    small: Boolean = false,
    subtitleLines: Int = 2,
    trailing: @Composable (focused: Boolean) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val active = (focused || hovered) && enabled
    val bg by animateColorAsState(if (active) Palette.Focus else Color.Transparent, tween(160), label = "rowBg")
    val scale by animateFloatAsState(if (active) 1.02f else 1f, label = "rowScale")
    val textColor by animateColorAsState(if (active) Palette.Ink else Palette.TextPrimary, tween(160), label = "rowText")

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled && !dimmed) 1f else 0.4f)
            .clip(RowShape)
            .background(bg)
            .then(if (moving) Modifier.border(2.dp, Palette.Accent, RowShape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onPreviewKey == null) Modifier else Modifier.onPreviewKeyEvent(onPreviewKey))
            .then(
                if (onStep == null) Modifier else Modifier.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onStep(-1); true }
                        Key.DirectionRight -> { onStep(1); true }
                        else -> false
                    }
                },
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = if (small) 16.sp else 19.sp, fontWeight = if (small) FontWeight.Normal else FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = textColor.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = subtitleLines, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing(active)
    }
}

@Composable
private fun Switch(checked: Boolean, focused: Boolean) {
    val track by animateColorAsState(
        when {
            checked -> Palette.Accent
            focused -> Color(0x260B0F1A)
            else -> Color(0x33FFFFFF)
        },
        label = "switchTrack",
    )
    val thumbOffset by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switchThumb")
    Box(
        Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(track)
            .padding(3.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .shadow(2.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun Stepper(value: String, canDecrease: Boolean, canIncrease: Boolean, focused: Boolean) {
    val color = if (focused) Palette.Ink else Palette.TextPrimary
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (focused) Text("‹", color = color.copy(alpha = if (canDecrease) 0.85f else 0.2f), fontSize = 24.sp)
        Text(
            value,
            color = color,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        if (focused) Text("›", color = color.copy(alpha = if (canIncrease) 0.85f else 0.2f), fontSize = 24.sp)
    }
}

@Composable
private fun Check(focused: Boolean) {
    Text("✓", color = if (focused) Palette.Ink else Palette.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun Chevron(focused: Boolean) {
    Text("›", color = if (focused) Palette.Ink else Palette.TextTertiary, fontSize = 26.sp)
}

@Composable
private fun Footer() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Text(
        listOfNotNull("Pill Launcher", version?.let { "v$it" }, "Back to close").joinToString("  ·  "),
        color = Palette.TextTertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
    )
}

/** The settings categories, each on its own page; a page with a [parent] opens from that page. */
enum class SettingsPage(val title: String, val parent: SettingsPage? = null) {
    Background("Background"),
    Widgets("Widgets"),
    Favorites("Favorites row"),
    Idle("Idle screen"),
    Home("Home & startup"),
    Notifications("Notifications"),
    Files("Files"),
    System("TV & system"),
    Updates("Updates"),
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
    SettingsPage.Background -> listOf(
        if (settings.onlinePhotos) "Online nature photos" else "Built-in photos",
        if (settings.slideshow) "every ${formatInterval(settings.intervalSec)}" else "slideshow off",
    ).joinToString(", ")
    SettingsPage.Widgets -> settings.widgets.joinToString(", ") { it.title.substringBefore(" (") }.ifEmpty { "None" }
    SettingsPage.Favorites -> if (settings.favoritesLayout == FavoritesLayout.Scroll) "Scroll" else "Wrap"
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
    ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Updates and files only" }
    SettingsPage.Files -> listOfNotNull(
        if (settings.keepReceiving) "Keep receiving" else null,
        if (settings.askInstalledApks) "asks before deleting APKs" else null,
        if (settings.tidyOld) "files kept 30 days" else null,
    ).joinToString(", ").replaceFirstChar { it.uppercase() }.ifEmpty { "Phones, tidying" }
    SettingsPage.System -> "Free up memory, network, display, sound, updates, uninstall"
    SettingsPage.Football -> settings.teams.joinToString(", ") { it.name }
    SettingsPage.News -> settings.newsTopics.joinToString(", ") { it.title }
    SettingsPage.Stack -> settings.stack.joinToString(", ") { it.title }
    SettingsPage.Prayers -> settings.mosque.ifBlank { "No mosque chosen" }
    SettingsPage.WeatherPlace -> settings.weatherPlace?.name ?: "Automatic"
}

/** Shortcuts on the TV & system page: one action here, the rest open the TV's own screens. */
enum class SystemAction(val title: String, val subtitle: String) {
    FreeMemory("Free up memory", "Close apps running in the background"),
    Network("Network & Wi-Fi", "Connection, Wi-Fi networks, VPN"),
    Display("Display", "Resolution, picture, screen saver"),
    Sound("Sound", "Audio output, volume, surround"),
    Storage("Storage", "What's using space, clear app data"),
    Update("System update", "Check for a firmware update"),
    DateTime("Date & time", "Time zone and clock format"),
    About("About this TV", "Model, Android version, security patch"),
    AllSettings("All Android TV settings", "Everything else"),
    Uninstall("Uninstall Pill Launcher", "Removes the launcher and its settings; the TV's own home screen takes over"),
}

@Composable
private fun SystemPage(onAction: (SystemAction) -> String?, firstModifier: Modifier) {
    // The last result or problem for each row, shown under it until the panel closes
    val notes = remember { mutableStateMapOf<SystemAction, String>() }
    Group(null) {
        for (action in SystemAction.entries - SystemAction.Uninstall) {
            SettingRow(
                title = action.title,
                subtitle = notes[action] ?: action.subtitle,
                onClick = { onAction(action)?.let { notes[action] = it } ?: notes.remove(action) },
                modifier = if (action == SystemAction.entries.first()) firstModifier else Modifier,
            ) { focused -> Chevron(focused) }
        }
    }
    // Two presses, so a stray OK doesn't start it; the TV then asks to confirm as well
    var armed by remember { mutableStateOf(false) }
    Group("Pill Launcher") {
        SettingRow(
            title = SystemAction.Uninstall.title,
            subtitle = notes[SystemAction.Uninstall]
                ?: if (armed) "Press OK again to uninstall" else SystemAction.Uninstall.subtitle,
            onClick = {
                if (!armed) {
                    armed = true
                } else {
                    armed = false
                    onAction(SystemAction.Uninstall)?.let { notes[SystemAction.Uninstall] = it } ?: notes.remove(SystemAction.Uninstall)
                }
            },
            modifier = Modifier.onFocusChanged { if (!it.isFocused) armed = false },
        ) { focused -> Mark("✕", focused) }
    }
}

/**
 * The teams the football card follows: Algeria (always first) and up to five more, which
 * Left / Right reorders and OK removes. Below, a search to add one. A team the GitHub copy
 * keeps fixtures for works on any network; others need the TV to reach TheSportsDB.
 */
@Composable
private fun FootballPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    football: List<Fixtures>,
    searchTeams: suspend (String) -> List<TeamHit>,
    firstModifier: Modifier,
) {
    val teams = settings.teams
    Group("Following · ${teams.size} of $MaxTeams · Left / Right moves") {
        for ((i, team) in teams.withIndex()) {
            key(team.id) {
                val f = football.firstOrNull { it.team.id == team.id }
                val status = when {
                    f == null -> "Loads with the next refresh"
                    f.error != null && f.fetchedAt == Instant.EPOCH -> "Can't get fixtures: ${f.error}"
                    f.error != null -> "Offline · showing the saved copy"
                    f.viaMirror -> "Updated from the GitHub copy"
                    else -> "Updated from TheSportsDB"
                }
                SettingRow(
                    title = team.name,
                    subtitle = if (team.isAlgeria) "Always first · $status" else "$status · OK removes",
                    modifier = if (i == 0) firstModifier else Modifier,
                    onClick = { if (!team.isAlgeria) onChange(settings.copy(teams = teams - team)) },
                    onStep = if (team.isAlgeria) null else { by ->
                        val to = (i + by).coerceIn(1, teams.lastIndex)
                        if (to != i) onChange(settings.copy(teams = teams.toMutableList().apply { add(to, removeAt(i)) }))
                    },
                ) { focused -> if (team.isAlgeria) Check(focused) else Mark("✕", focused) }
            }
        }
    }

    var query by remember { mutableStateOf("") }
    // null while a search is running
    var hits by remember { mutableStateOf<List<TeamHit>?>(emptyList()) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        hits = null
        delay(450) // wait for typing to pause
        hits = searchTeams(query)
    }
    Group("Add a team") {
        TeamSearchField(query, { query = it }, hint = "Search teams")
        val found = hits
        when {
            query.isBlank() -> Note("A club or a country, e.g. MC Alger, Arsenal, Morocco")
            found == null -> Note("Searching…")
            found.isEmpty() -> Note("No teams found for “${query.trim()}”")
            else -> for (hit in found) {
                key(hit.team.id) {
                    val following = teams.any { it.id == hit.team.id }
                    val full = teams.size >= MaxTeams
                    SettingRow(
                        title = hit.team.name,
                        subtitle = listOfNotNull(
                            hit.team.league,
                            hit.team.country,
                            when {
                                following -> "following"
                                full -> "remove a team first"
                                hit.mirrored -> "works on any network"
                                else -> "needs a direct connection to TheSportsDB"
                            },
                        ).joinToString(" · "),
                        dimmed = following || full,
                        onClick = { if (!following && !full) onChange(settings.copy(teams = withAlgeriaFirst(teams + hit.team))) },
                    ) { focused -> if (following) Check(focused) else Mark("+", focused) }
                }
            }
        }
    }

    Group(null) {
        val modes = MatchDayMode.entries
        val current = modes.indexOf(settings.matchDay)
        SettingRow(
            title = "Match day",
            subtitle = if (settings.matchDay == MatchDayMode.Off) "No countdown or kick-off banner" else "A countdown on the card that day, and a banner 10 minutes before kick-off",
            onClick = { onChange(settings.copy(matchDay = modes[(current + 1) % modes.size])) },
            onStep = { by ->
                val next = (current + by).coerceIn(0, modes.lastIndex)
                if (next != current) onChange(settings.copy(matchDay = modes[next]))
            },
        ) { focused -> Stepper(settings.matchDay.title, current > 0, current < modes.lastIndex, focused) }
    }
}

/** What the headlines widget picks stories about, and how each source is doing. */
@Composable
private fun NewsPage(settings: LauncherSettings, onChange: (LauncherSettings) -> Unit, news: News?, firstModifier: Modifier) {
    Group("Topics") {
        for ((i, topic) in NewsTopic.entries.withIndex()) {
            val on = topic in settings.newsTopics
            SettingRow(
                title = topic.title,
                subtitle = when (topic) {
                    NewsTopic.Algeria -> "Stories that mention Algeria"
                    NewsTopic.Russia -> "Russia, the Kremlin, RT's Russia section"
                    NewsTopic.World -> "Diplomacy, elections, conflicts, the UN"
                    NewsTopic.AfricaMiddleEast -> "North Africa, the Sahel, the Gulf, the Levant"
                    NewsTopic.Economy -> "Markets, trade, energy, currencies"
                    NewsTopic.Sport -> "Football and other sport"
                } + if (on && settings.newsTopics.size == 1) " · keep at least one" else "",
                modifier = if (i == 0) firstModifier else Modifier,
                // At least one topic stays on; with none the card would only show top stories
                onClick = { if (!on) onChange(settings.copy(newsTopics = settings.newsTopics + topic)) else if (settings.newsTopics.size > 1) onChange(settings.copy(newsTopics = settings.newsTopics - topic)) },
            ) { focused -> Switch(checked = on, focused = focused) }
        }
    }
    Group("Sources") {
        for (source in NewsSource.entries) {
            val count = news?.headlines?.get(source)?.size
            val error = news?.errors?.get(source)
            SettingRow(
                title = source.title + " English",
                subtitle = when {
                    news == null -> "Loading…"
                    error != null && count != null -> "Offline · showing $count saved stories"
                    error != null -> if (source == NewsSource.RT) "Not available on this network" else "Can't load: $error"
                    else -> "$count stories"
                } + if (source == NewsSource.RT) " · read directly only, never through the GitHub copy" else "",
                onClick = {},
            ) { focused -> if (error == null && count != null) Check(focused) else Mark("–", focused) }
        }
    }
}

/**
 * What shows in the notification bar under the widgets: other apps' notifications (once
 * the TV allows reading them), each app's on or off, and Pill Launcher's own football and
 * connection notices. Updates and files always show.
 */
@Composable
private fun NotificationsPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    access: Boolean,
    onAccess: () -> String?,
    apps: List<Pair<String, String>>,
    firstModifier: Modifier,
) {
    var accessNote by remember { mutableStateOf<String?>(null) }
    Group(null) {
        SettingRow(
            title = "Other apps' notifications",
            subtitle = accessNote ?: when {
                !access -> "Needs Notification access for Pill Launcher · OK to allow it"
                settings.appNotices -> "In the bar under the widgets"
                else -> "Kept out of the bar"
            },
            onClick = {
                if (!access) accessNote = onAccess() else onChange(settings.copy(appNotices = !settings.appNotices))
            },
            modifier = firstModifier,
        ) { focused -> if (access) Switch(checked = settings.appNotices, focused = focused) else Chevron(focused) }
        if (access && settings.appNotices) {
            SettingRow(
                title = "Hide private messages",
                subtitle = if (settings.hidePrivate) "Messages an app marks private show as \"New notification\"" else "Messages show their text",
                onClick = { onChange(settings.copy(hidePrivate = !settings.hidePrivate)) },
            ) { focused -> Switch(checked = settings.hidePrivate, focused = focused) }
        }
        SettingRow(
            title = "Football",
            subtitle = "Kick-off reminders, goals and wins for the teams you follow",
            onClick = { onChange(settings.copy(footballNotices = !settings.footballNotices)) },
        ) { focused -> Switch(checked = settings.footballNotices, focused = focused) }
        SettingRow(
            title = "Connection",
            subtitle = "When the internet drops, and how long it was gone",
            onClick = { onChange(settings.copy(connectionNotices = !settings.connectionNotices)) },
        ) { focused -> Switch(checked = settings.connectionNotices, focused = focused) }
    }
    if (access && settings.appNotices && apps.isNotEmpty()) {
        Group("Apps") {
            for ((pkg, name) in apps) {
                val muted = pkg in settings.mutedApps
                SettingRow(
                    title = name,
                    subtitle = if (muted) "Muted" else "Shows in the bar",
                    onClick = { onChange(settings.copy(mutedApps = if (muted) settings.mutedApps - pkg else settings.mutedApps + pkg)) },
                ) { focused -> Switch(checked = !muted, focused = focused) }
            }
        }
    }
    Note("Updates and files from your phone always show. Hold OK on a notification in the bar to dismiss it.")
}

/**
 * Files: whether sharing keeps going behind other apps, how Files tidies itself, and the
 * phones allowed in. OK on a phone removes it; signing out every phone also makes a new code.
 */
@Composable
private fun FilesSettingsPage(settings: LauncherSettings, onChange: (LauncherSettings) -> Unit, share: FileShare?, firstModifier: Modifier) {
    Group(null) {
        SettingRow(
            title = "Keep receiving",
            subtitle = if (settings.keepReceiving) "15 minutes after you leave the launcher for another app" else "Only while the launcher is on screen",
            onClick = { onChange(settings.copy(keepReceiving = !settings.keepReceiving)) },
            modifier = firstModifier,
        ) { focused -> Switch(checked = settings.keepReceiving, focused = focused) }
        SettingRow(
            title = "Offer to delete installed APKs",
            subtitle = "Once an APK's app is installed, the notification bar asks. Nothing is deleted without you",
            onClick = { onChange(settings.copy(askInstalledApks = !settings.askInstalledApks)) },
        ) { focused -> Switch(checked = settings.askInstalledApks, focused = focused) }
        SettingRow(
            title = "Remove files after 30 days",
            subtitle = "The notification bar says so the day before",
            onClick = { onChange(settings.copy(tidyOld = !settings.tidyOld)) },
        ) { focused -> Switch(checked = settings.tidyOld, focused = focused) }
    }
    if (share == null) return
    val state by share.state.collectAsState()
    Group("Trusted devices") {
        if (state.devices.isEmpty()) {
            Note("No phone is paired yet. Scan the code on the Files page to pair one.")
        }
        for (d in state.devices) {
            key(d.id) {
                SettingRow(
                    title = d.name,
                    subtitle = "Paired ${pairedDay(d.pairedAt)} · OK removes it",
                    onClick = { share.forget(d.id) },
                ) { focused -> Mark("✕", focused) }
            }
        }
        SettingRow(
            title = "Sign out every phone",
            subtitle = "Code now ${state.code}. Signing out makes a new one",
            onClick = { share.signOutAll() },
        ) { focused -> Chevron(focused) }
    }
}

private fun pairedDay(ms: Long): String =
    java.time.format.DateTimeFormatter.ofPattern("MMM d", java.util.Locale.getDefault())
        .format(java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()))

@Composable
private fun Mark(symbol: String, focused: Boolean) {
    Text(symbol, color = if (focused) Palette.Ink else Palette.TextTertiary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Note(text: String) {
    Text(text, color = Palette.TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
}

/** Typing a name to search for (a team, a town): OK opens the TV keyboard. */
@Composable
private fun TeamSearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Palette.Focus else Palette.Hairline, label = "teamFieldBorder")
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 19.sp),
        cursorBrush = SolidColor(Palette.Focus),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(RowShape)
                    .background(Color(0x14FFFFFF))
                    .border(2.dp, border, RowShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon(tint = Palette.TextSecondary, iconSize = 20.dp)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(hint, color = Palette.TextTertiary, fontSize = 19.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            }
        },
    )
}

/**
 * Settings → Widgets → Weather location: Automatic (the TV's approximate location, from
 * its internet address, which can be a city some way off), or a town found by name, or
 * coordinates typed in. The forecast is for that exact point.
 */
@Composable
private fun WeatherPlacePage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    weather: Weather?,
    searchPlaces: suspend (String) -> Result<List<Place>>,
    firstModifier: Modifier,
) {
    val chosen = settings.weatherPlace
    Group("Weather for") {
        SettingRow(
            title = "Automatic",
            subtitle = "From the TV's internet address, which may be the nearest city" +
                (if (chosen == null) weather?.place?.takeIf { it.isNotBlank() }?.let { " · now $it" } ?: "" else ""),
            onClick = { onChange(settings.copy(weatherPlace = null)) },
            modifier = firstModifier,
        ) { focused -> if (chosen == null) Check(focused) else Box(Modifier) }
        if (chosen != null) {
            SettingRow(
                title = chosen.name,
                subtitle = listOf(chosen.region, chosen.country).filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .ifEmpty { "Coordinates" } + " · the forecast for this exact place",
                onClick = {},
            ) { focused -> Check(focused) }
        }
    }

    var query by remember { mutableStateOf("") }
    // null while a search is running; a failure when it couldn't be reached
    var found by remember { mutableStateOf<Result<List<Place>>?>(Result.success(emptyList())) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            found = Result.success(emptyList())
            return@LaunchedEffect
        }
        found = null
        delay(450) // wait for typing to pause
        found = searchPlaces(query)
    }
    Group("Choose a town") {
        TeamSearchField(query, { query = it }, hint = "Town or village, e.g. Beni Saf")
        val result = found
        val places = result?.getOrNull()
        when {
            query.isBlank() -> Note("Any town or village, or a latitude and longitude such as 35.30, -1.38")
            result == null -> Note("Searching…")
            result.isFailure -> Note("Can't reach the place search: ${result.exceptionOrNull()?.let { describe(it) }}")
            places.isNullOrEmpty() -> Note("No places found for “${query.trim()}”. Try its name without accents, or its coordinates")
            else -> for (p in places) {
                key(p.key) {
                    val current = chosen?.key == p.key
                    SettingRow(
                        title = p.name,
                        subtitle = listOf(p.region, p.country).filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { "Coordinates" } +
                            String.format(java.util.Locale.ROOT, " · %.2f, %.2f", p.lat, p.lon),
                        onClick = {
                            onChange(settings.copy(weatherPlace = p))
                            query = ""
                        },
                    ) { focused -> if (current) Check(focused) else Mark("+", focused) }
                }
            }
        }
    }
}

/**
 * Settings → Widgets → Prayer times: the mosque (its name in its MAWAQIT address, typed
 * with the TV keyboard, or pasted as the whole address), what came back and when, Refresh
 * now, and the API's address for anyone running their own copy of it.
 */
@Composable
private fun PrayersPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    prayers: PrayerTimes?,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    firstModifier: Modifier,
) {
    Group("Mosque") {
        var mosque by remember(settings.mosque) { mutableStateOf(settings.mosque) }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            TextRow(
                value = mosque,
                hint = "Mosque name on MAWAQIT, e.g. grande-mosquee-de-paris",
                onChange = { mosque = it },
                onDone = { onChange(settings.copy(mosque = cleanMosqueSlug(mosque))) },
                // With no mosque yet the page opens on the field; once there is one, on Refresh now (focusing
                // the field would bring up the keyboard each time)
                modifier = if (settings.mosque.isBlank()) firstModifier else Modifier,
            )
        }
        Note("Find your mosque on mawaqit.net and copy the last part of its address. The whole address pastes too.")
        val status = when {
            settings.mosque.isBlank() -> "Type the mosque, then Done"
            refreshing -> "Asking MAWAQIT…"
            prayers == null && error != null -> "Can't reach MAWAQIT: $error"
            prayers == null -> "Loads with the next refresh"
            error != null -> "Offline · showing times saved " + ageText(prayers.fetchedAt).lowercase()
            prayers.fromCache -> "Saved " + ageText(prayers.fetchedAt).lowercase() + " · refreshes after midnight"
            else -> "Updated " + ageText(prayers.fetchedAt).lowercase() + " · refreshes after midnight"
        }
        SettingRow(
            title = "Refresh now",
            subtitle = (prayers?.mosque?.ifBlank { null }?.let { "$it · " } ?: "") + status,
            onClick = { if (!refreshing) onRefresh() },
            enabled = settings.mosque.isNotBlank(),
            modifier = if (settings.mosque.isBlank()) Modifier else firstModifier,
        ) { focused -> RefreshIcon(if (focused) Palette.Ink else Palette.TextTertiary, 22.dp) }
    }
    Group("API") {
        var api by remember(settings.prayerApi) { mutableStateOf(settings.prayerApi) }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            TextRow(
                value = api,
                hint = DefaultPrayerApi,
                onChange = { api = it },
                onDone = { onChange(settings.copy(prayerApi = cleanPrayerApi(api))) },
            )
        }
        Note(
            if (settings.prayerApi == DefaultPrayerApi) "The community MAWAQIT API. Change it only if you run your own copy."
            else "Your own copy of the API. Clear the field for the community one.",
        )
    }
}

/** A line of text typed with the TV keyboard; OK opens it, Done (or leaving the field) keeps it. */
@Composable
private fun TextRow(value: String, hint: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Palette.Focus else Palette.Hairline, label = "textRowBorder")
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 17.sp),
        cursorBrush = SolidColor(Palette.Focus),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged {
                if (focused && !it.isFocused) onDone()
                focused = it.isFocused
            },
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(RowShape)
                    .background(Color(0x14FFFFFF))
                    .border(2.dp, border, RowShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(hint, color = Palette.TextTertiary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}

private val StackOkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/**
 * Settings → Widgets → Widget stack: which widgets take turns in the stack, and in what
 * order. OK adds or removes one. Moving works like moving a favourite: hold OK (or press
 * Menu) on one in the stack, then Left / Right (or Up / Down) moves it, and OK or Back
 * puts it down. The home screen beside the panel shows the focused one.
 */
@Composable
private fun StackPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    moving: WidgetKind?,
    onMoving: (WidgetKind?) -> Unit,
    onPreview: (WidgetKind?) -> Unit,
    firstModifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    // A move started by holding OK: the release that follows doesn't put it down
    var heldMove by remember { mutableStateOf(false) }
    val inStack = settings.stack
    val others = WidgetKind.entries.filter { it != WidgetKind.Stack && it !in inStack }
    val label = when {
        moving != null -> "Moving ${moving.title} · Left / Right, then OK"
        inStack.isEmpty() -> "Empty · OK adds a widget"
        else -> "${inStack.size} take turns · hold OK to move one"
    }
    Group(label) {
        for (kind in inStack + others) {
            key(kind) {
                val on = kind in inStack
                val position = inStack.indexOf(kind)
                val onHome = !on && kind in settings.widgets
                SettingRow(
                    title = kind.title,
                    subtitle = when {
                        moving == kind -> "Position ${position + 1} of ${inStack.size}"
                        on -> "Position ${position + 1} of ${inStack.size}" + if (inStack.size > 1) " · hold OK to move" else ""
                        onHome -> "On the home screen by itself · OK moves it into the stack"
                        else -> kind.description
                    },
                    moving = moving == kind,
                    modifier = (if (kind == (inStack + others).first()) firstModifier else Modifier)
                        .onFocusChanged { if (it.isFocused) onPreview(kind.takeIf { k -> k in settings.stack }) },
                    onClick = { if (moving == null) onChange(settings.withStackToggled(kind)) },
                    onPreviewKey = { event ->
                        val down = event.type == KeyEventType.KeyDown
                        val up = event.type == KeyEventType.KeyUp
                        val ok = event.key in StackOkKeys
                        when {
                            moving == kind -> {
                                when (event.key) {
                                    Key.DirectionLeft, Key.DirectionUp -> if (down) onChange(settings.withStackMoved(kind, -1))
                                    Key.DirectionRight, Key.DirectionDown -> if (down) onChange(settings.withStackMoved(kind, 1))
                                    else -> if (up && (ok || event.key == Key.Back || event.key == Key.Escape || event.key == Key.ButtonB)) {
                                        if (heldMove) heldMove = false else onMoving(null)
                                    }
                                }
                                true
                            }
                            moving != null || !on -> false
                            event.key == Key.Menu -> { if (down) onMoving(kind); true }
                            ok && down && event.nativeKeyEvent.repeatCount > 0 -> true
                            ok && down -> {
                                // Many remotes never repeat a held key, so the hold is timed here
                                holdJob?.cancel()
                                holdJob = scope.launch {
                                    delay(HoldToMoveMs)
                                    heldMove = true
                                    onMoving(kind)
                                }
                                false
                            }
                            ok && up -> { holdJob?.cancel(); false }
                            else -> false
                        }
                    },
                ) { focused ->
                    if (moving == kind) MoveMarks() else Switch(checked = on, focused = focused)
                }
            }
        }
    }
    Note("The stack shows one widget at a time and slides up to the next every $StackTurnSec seconds. On the home screen, Up / Down flips through it by hand.")
}

private const val HoldToMoveMs = 550L

/** The two round arrows a widget being moved carries, gently nudging apart, as on a favourite being moved. */
@Composable
private fun MoveMarks() {
    val nudge by rememberInfiniteTransition(label = "stackMove").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "stackMoveNudge",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (left in listOf(true, false)) {
            Box(
                Modifier
                    .graphicsLayer { translationX = (if (left) -1f else 1f) * 3.dp.toPx() * nudge }
                    .size(26.dp)
                    .background(Palette.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (left) "‹" else "›", color = Palette.Focus, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            if (left) Spacer(Modifier.width(6.dp))
        }
    }
}

/** One line on where updates stand, for the category list and the check row. */
private fun updateSummary(update: UpdateState, version: String): String {
    val release = update.release
    val v = if (version.isNotEmpty()) "Version $version" else "This version"
    return when (update.phase) {
        UpdatePhase.Checking -> "Checking GitHub…"
        UpdatePhase.Downloading -> "Downloading ${release?.version} · ${(update.progress * 100).roundToInt()}%"
        UpdatePhase.Installing -> "Installing ${release?.version}"
        UpdatePhase.Available -> "${release?.version} is ready to install"
        UpdatePhase.Failed -> update.message ?: "Couldn't check for updates"
        UpdatePhase.UpToDate -> "$v · up to date" + (update.checkedAt?.let { " · checked ${ageText(it).lowercase()}" } ?: "")
        UpdatePhase.Idle -> "$v · not checked yet"
    }
}

/**
 * Settings → Updates: check GitHub for a newer Pill Launcher, install it (with the same
 * progress as the card on the home screen), and whether to check by itself. What's new
 * in the newer version is listed under it.
 */
@Composable
private fun UpdatesPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    update: UpdateState,
    version: String,
    onCheck: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    firstModifier: Modifier,
) {
    val release = update.release
    Group(null) {
        SettingRow(
            title = "Check for updates",
            subtitle = updateSummary(update, version).takeIf { release == null || update.phase == UpdatePhase.Checking }
                ?: "Version $version · ${release?.version} is out",
            onClick = onCheck,
            enabled = update.phase != UpdatePhase.Downloading && update.phase != UpdatePhase.Installing,
            modifier = firstModifier,
        ) { focused -> Chevron(focused) }
        if (release != null) {
            SettingRow(
                title = when (update.phase) {
                    UpdatePhase.Downloading -> "Downloading ${release.version}"
                    UpdatePhase.Installing -> "Installing ${release.version}"
                    UpdatePhase.Failed -> "Try ${release.version} again"
                    else -> "Update to ${release.version}"
                },
                subtitle = when (update.phase) {
                    UpdatePhase.Downloading -> "OK cancels"
                    UpdatePhase.Installing -> "Confirm on the screen Android shows"
                    else -> update.message ?: "About ${(release.sizeBytes / 1_000_000).coerceAtLeast(1)} MB · settings and favourites stay"
                },
                onClick = { if (update.phase == UpdatePhase.Downloading) onCancel() else onStart() },
                enabled = update.phase != UpdatePhase.Installing,
            ) { focused ->
                if (update.phase == UpdatePhase.Downloading) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${(update.progress * 100).roundToInt()}%", color = if (focused) Palette.Ink else Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        ProgressLine(update.progress, Modifier.width(72.dp))
                    }
                } else {
                    DownloadIcon(if (focused) Palette.Ink else Palette.TextPrimary, 20.dp)
                }
            }
        }
        SettingRow(
            title = "Check automatically",
            subtitle = if (settings.checkUpdates) "Every few hours, and a card on the home screen when one is out" else "Only when you check here",
            onClick = { onChange(settings.copy(checkUpdates = !settings.checkUpdates)) },
        ) { focused -> Switch(checked = settings.checkUpdates, focused = focused) }
    }
    // Rows that take focus, so Down scrolls through everything (plain text can't be reached)
    if (release != null && release.notes.isNotEmpty()) {
        Group("What's new in ${release.version}") {
            for (line in release.notes) {
                SettingRow(title = line, subtitle = null, onClick = {}, small = true) { }
            }
        }
    }
    Group(null) {
        SettingRow(
            title = "About updates",
            subtitle = "From Pill Launcher's releases on GitHub, signed with the same key, so settings and favourites stay. Android asks you to confirm each install, and the first time to allow Pill Launcher to install apps.",
            onClick = {},
            subtitleLines = 4,
        ) { }
    }
}
