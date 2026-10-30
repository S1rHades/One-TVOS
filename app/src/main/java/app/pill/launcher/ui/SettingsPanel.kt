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
import app.pill.launcher.NewsSource
import app.pill.launcher.NewsTopic
import app.pill.launcher.TeamHit
import app.pill.launcher.withAlgeriaFirst
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import java.time.Instant
import app.pill.launcher.FavoritesStyle
import app.pill.launcher.HomeStatus
import app.pill.launcher.LauncherSettings
import app.pill.launcher.MaxWidgets
import app.pill.launcher.WeatherMode
import app.pill.launcher.WidgetKind
import app.pill.launcher.moved
import app.pill.launcher.toggled
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
    /** The followed teams' latest fixtures, for each team's status line. */
    football: List<Fixtures> = emptyList(),
    /** The last headlines fetch, for each source's status line. */
    news: News? = null,
    /** Teams matching a search (off the main thread), for adding one. */
    searchTeams: suspend (String) -> List<TeamHit> = { emptyList() },
    /** Credit for the online photo on screen. */
    photoCredit: String? = null,
    home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
    onMakeDefault: () -> String? = { null },
    onHomeHelper: () -> String? = { null },
    onAllowAutostart: () -> String? = { null },
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
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var lastPage by remember { mutableStateOf(SettingsPage.Background) }
    val pageFirst = remember { FocusRequester() }
    val categoryFocus = remember { SettingsPage.entries.associateWith { FocusRequester() } }
    // Rows on a page that open a page of their own (Widgets → Football teams, Headlines)
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }

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

    val scrimAlpha by animateFloatAsState(if (visible.targetState) 1f else 0f, tween(PANEL_MS), label = "scrimAlpha")
    val motion = tween<Float>(PANEL_MS, easing = FastOutSlowInEasing)

    Dialog(
        // Back on a page returns to the list of categories; on the list it closes the panel
        onDismissRequest = { if (page != null) page = page?.parent else close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(PANEL_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeIn(motion),
                exit = slideOutHorizontally(tween(PANEL_MS, easing = FastOutSlowInEasing)) { it / 3 } + fadeOut(motion),
            ) {
                Column(
                    Modifier
                        .padding(vertical = 16.dp, horizontal = 24.dp)
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
                                            subtitle = summaryOf(category, settings, home),
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

                            SettingRow(
                                title = "Ambient light",
                                subtitle = if (settings.ambientLight) "Tints the photo with the focused app's colour" else "Photo shown as it is",
                                onClick = { onChange(settings.copy(ambientLight = !settings.ambientLight)) },
                            ) { focused -> Switch(checked = settings.ambientLight, focused = focused) }
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
                                            on && settings.widgets.size > 1 -> "Position ${position + 1} of ${settings.widgets.size} · Left / Right to move"
                                            full -> "Turn another widget off first"
                                            else -> kind.description
                                        },
                                        dimmed = full,
                                        modifier = if (kind == ordered.first()) Modifier.focusRequester(pageFirst) else Modifier,
                                        onClick = {
                                            if (full) limitToasts++ else onChange(settings.copy(widgets = settings.widgets.toggled(kind)))
                                        },
                                        onStep = if (!on) null else { by -> onChange(settings.copy(widgets = settings.widgets.moved(kind, by))) },
                                    ) { focused -> Switch(checked = on, focused = focused) }
                                }
                            }
                                    }
                                    Group(null) {
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
                                    }
                                    Group(null) {
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
                            val styles = FavoritesStyle.entries
                            val styleIndex = styles.indexOf(settings.favoritesStyle)
                            SettingRow(
                                title = "Tile style",
                                subtitle = if (settings.favoritesStyle == FavoritesStyle.Banners) "App artwork, or a card made from the icon" else "Square app icons",
                                onClick = { onChange(settings.copy(favoritesStyle = styles[(styleIndex + 1) % styles.size])) },
                                onStep = { by ->
                                    val next = (styleIndex + by).coerceIn(0, styles.lastIndex)
                                    if (next != styleIndex) onChange(settings.copy(favoritesStyle = styles[next]))
                                },
                            ) { focused ->
                                Stepper(if (settings.favoritesStyle == FavoritesStyle.Banners) "Banners" else "Icons", styleIndex > 0, styleIndex < styles.lastIndex, focused)
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
                                SettingsPage.Football -> FootballPage(settings, onChange, football, searchTeams, Modifier.focusRequester(pageFirst))
                                SettingsPage.News -> NewsPage(settings, onChange, news, Modifier.focusRequester(pageFirst))
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
            .onFocusChanged { focused = it.isFocused }
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
            Text(title, color = textColor, fontSize = 19.sp, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, color = textColor.copy(alpha = 0.6f), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
    System("TV & system"),
    Football("Football teams", Widgets),
    News("Headlines", Widgets),
    ;

    /** The category this page is under, in the list. */
    val root: SettingsPage get() = parent?.root ?: this
}

/** One line under each category, saying what it is set to now. */
private fun summaryOf(page: SettingsPage, settings: LauncherSettings, home: HomeStatus): String = when (page) {
    SettingsPage.Background -> listOf(
        if (settings.onlinePhotos) "Online nature photos" else "Built-in photos",
        if (settings.slideshow) "every ${formatInterval(settings.intervalSec)}" else "slideshow off",
    ).joinToString(", ")
    SettingsPage.Widgets -> settings.widgets.joinToString(", ") { it.title.substringBefore(" (") }.ifEmpty { "None" }
    SettingsPage.Favorites -> "${if (settings.favoritesLayout == FavoritesLayout.Scroll) "Scroll" else "Wrap"}, " +
        if (settings.favoritesStyle == FavoritesStyle.Banners) "banners" else "icons"
    SettingsPage.Idle -> if (settings.idleMinutes == 0) "Off" else "After ${settings.idleMinutes} min"
    SettingsPage.Home -> when {
        home.isDefault -> "Pill Launcher is the home screen"
        home.redirectOn -> "Home button helper on"
        else -> "Not the home screen yet"
    }
    SettingsPage.System -> "Free up memory, network, display, sound, updates, uninstall"
    SettingsPage.Football -> settings.teams.joinToString(", ") { it.name }
    SettingsPage.News -> settings.newsTopics.joinToString(", ") { it.title }
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
        TeamSearchField(query, { query = it })
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

@Composable
private fun Mark(symbol: String, focused: Boolean) {
    Text(symbol, color = if (focused) Palette.Ink else Palette.TextTertiary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun Note(text: String) {
    Text(text, color = Palette.TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
}

/** Typing a team's name: OK opens the TV keyboard. */
@Composable
private fun TeamSearchField(value: String, onChange: (String) -> Unit) {
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
                    if (value.isEmpty()) Text("Search teams", color = Palette.TextTertiary, fontSize = 19.sp)
                    inner()
                }
            }
        },
    )
}
