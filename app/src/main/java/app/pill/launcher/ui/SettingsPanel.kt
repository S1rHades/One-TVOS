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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import app.pill.launcher.HomeStatus
import app.pill.launcher.LauncherSettings
import app.pill.launcher.WeatherMode
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
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
    /** Why photos or weather last failed to load, shown under their rows; null when fine. */
    photoError: String? = null,
    weatherError: String? = null,
    home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
    onMakeDefault: () -> String? = { null },
    onHomeHelper: () -> String? = { null },
    onAllowAutostart: () -> String? = { null },
) {
    // A message from the last attempt, shown under the row until the panel closes
    var defaultNote by remember { mutableStateOf<String?>(null) }
    var helperNote by remember { mutableStateOf<String?>(null) }
    var autostartNote by remember { mutableStateOf<String?>(null) }
    val firstRow = remember { FocusRequester() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstRow.requestFocus() }
    }

    // Remove the dialog only once the slide-out has finished
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onDismiss()
    }

    val scrimAlpha by animateFloatAsState(if (visible.targetState) 1f else 0f, tween(PANEL_MS), label = "scrimAlpha")
    val motion = tween<Float>(PANEL_MS, easing = FastOutSlowInEasing)

    Dialog(
        onDismissRequest = close,
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
                        .clip(PanelShape)
                        .background(Palette.Panel)
                        .border(1.dp, Palette.Hairline, PanelShape)
                        .padding(horizontal = 22.dp, vertical = 22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                        GearIcon(tint = Palette.TextPrimary, iconSize = 26.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Settings", color = Palette.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(18.dp))

                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        Group("Home screen") {
                            SettingRow(
                                title = "Photo slideshow",
                                subtitle = when {
                                    photoError != null -> "Can't load photos: $photoError"
                                    settings.slideshow -> "A new photo every ${formatInterval(settings.intervalSec)}"
                                    else -> "Keeps the current photo"
                                },
                                onClick = { onChange(settings.copy(slideshow = !settings.slideshow)) },
                                modifier = Modifier.focusRequester(firstRow),
                            ) { focused -> Switch(checked = settings.slideshow, focused = focused) }

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

                            val modes = WeatherMode.entries
                            val mode = modes.indexOf(settings.weather)
                            SettingRow(
                                title = "Weather",
                                subtitle = when (settings.weather) {
                                    WeatherMode.Off -> "Hidden"
                                    else -> weatherError?.let { "Can't get the weather: $it" } ?: "Beside the clock, for your approximate area"
                                },
                                onClick = { onChange(settings.copy(weather = modes[(mode + 1) % modes.size])) },
                                onStep = { by ->
                                    val next = (mode + by).coerceIn(0, modes.lastIndex)
                                    if (next != mode) onChange(settings.copy(weather = modes[next]))
                                },
                            ) { focused ->
                                Stepper(weatherLabel(settings.weather), mode > 0, mode < modes.lastIndex, focused)
                            }
                        }

                        Group("Favorites row") {
                            val layouts = FavoritesLayout.entries
                            val layoutIndex = layouts.indexOf(settings.favoritesLayout)
                            SettingRow(
                                title = "Layout",
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

                        Group("Idle screen") {
                            val idleOptions = LauncherSettings.IdleOptions
                            val idleIndex = idleOptions.indexOf(settings.idleMinutes).coerceAtLeast(0)
                            SettingRow(
                                title = "Go idle after",
                                subtitle = if (settings.idleMinutes == 0) {
                                    "Never: the apps always stay on screen"
                                } else {
                                    "Without a key press, only the clock and weather stay"
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
                                subtitle = if (settings.idleMinutes == 0) "Turn on the idle screen first" else "Slow photo zoom; the clock drifts to spare the screen",
                                enabled = settings.idleMinutes != 0,
                                onClick = { onChange(settings.copy(idleAnimation = !settings.idleAnimation)) },
                            ) { focused -> Switch(checked = settings.idleAnimation, focused = focused) }
                        }

                        Group("Home & startup") {
                            SettingRow(
                                title = "Open at startup",
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

                        Group("Device") {
                            SettingRow(
                                title = "Android TV settings",
                                subtitle = "Network, display, sound and more",
                                onClick = onOpenSystemSettings,
                            ) { focused -> Chevron(focused) }
                        }
                    }

                    Footer()
                }
            }
        }
    }
}

private fun idleLabel(minutes: Int): String = if (minutes == 0) "Off" else "$minutes min"

private fun formatInterval(sec: Int): String = if (sec % 60 == 0) "${sec / 60} min" else "$sec s"

private fun weatherLabel(mode: WeatherMode): String = when (mode) {
    WeatherMode.Off -> "Off"
    WeatherMode.Celsius -> "°C"
    WeatherMode.Fahrenheit -> "°F"
}

/** A labelled block of rows on a faint card. */
@Composable
private fun Group(label: String, content: @Composable ColumnScope.() -> Unit) {
    Text(
        label.uppercase(),
        color = Palette.TextTertiary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
    )
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
            .alpha(if (enabled) 1f else 0.4f)
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
