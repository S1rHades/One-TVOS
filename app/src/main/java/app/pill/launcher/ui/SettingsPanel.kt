package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.LauncherSettings
import kotlinx.coroutines.delay

private const val PANEL_MS = 260

/**
 * Right-hand settings panel. It lives in its own dialog window, so D-pad focus
 * stays inside it. Back (the dialog's dismiss request) slides it out before it is removed.
 */
@Composable
fun SettingsPanel(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    onOpenSystemSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
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

    val scrimAlpha by animateFloatAsState(
        if (visible.targetState) 1f else 0f,
        tween(PANEL_MS),
        label = "scrimAlpha",
    )

    Dialog(
        onDismissRequest = close,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(PANEL_MS)) { it } + fadeIn(tween(PANEL_MS)),
                exit = slideOutHorizontally(tween(PANEL_MS)) { it } + fadeOut(tween(PANEL_MS)),
            ) {
                Column(
                    Modifier
                        .width(500.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(topStart = 32.dp, bottomStart = 32.dp))
                        .background(Palette.Panel)
                        .padding(horizontal = 40.dp, vertical = 44.dp),
                ) {
                    Text("Settings", color = Palette.TextPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(28.dp))

                    GroupLabel("Background")
                    SettingRow(
                        title = "Photo slideshow",
                        subtitle = if (settings.slideshow) "A new photo every ${settings.intervalSec} seconds" else "Keeps the current photo",
                        onClick = { onChange(settings.copy(slideshow = !settings.slideshow)) },
                        modifier = Modifier.focusRequester(firstRow),
                    ) { focused -> Switch(checked = settings.slideshow, focused = focused) }

                    Spacer(Modifier.height(10.dp))

                    val options = LauncherSettings.IntervalOptions
                    val current = options.indexOf(settings.intervalSec).coerceAtLeast(0)
                    fun step(by: Int) {
                        val next = (current + by).coerceIn(0, options.lastIndex)
                        if (next != current) onChange(settings.copy(intervalSec = options[next]))
                    }
                    SettingRow(
                        title = "Change photo every",
                        subtitle = if (settings.slideshow) "Use left and right to adjust" else "Turn on the slideshow first",
                        enabled = settings.slideshow,
                        onClick = { onChange(settings.copy(intervalSec = options[(current + 1) % options.size])) },
                        modifier = Modifier.onKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                            when (event.key) {
                                Key.DirectionLeft -> { step(-1); true }
                                Key.DirectionRight -> { step(1); true }
                                else -> false
                            }
                        },
                    ) { focused ->
                        Stepper(
                            value = formatInterval(settings.intervalSec),
                            canDecrease = current > 0,
                            canIncrease = current < options.lastIndex,
                            focused = focused,
                        )
                    }

                    Spacer(Modifier.height(28.dp))
                    GroupLabel("Device")
                    SettingRow(
                        title = "Android TV settings",
                        subtitle = "Network, display, sound and more",
                        onClick = onOpenSystemSettings,
                    ) { focused -> Chevron(focused) }

                    Spacer(Modifier.weight(1f))
                    Footer()
                }
            }
        }
    }
}

private fun formatInterval(sec: Int): String = if (sec % 60 == 0) "${sec / 60} min" else "$sec s"

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        color = Palette.TextTertiary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    trailing: @Composable (focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val bg by animateColorAsState(if (focused) Palette.Focus else Color(0x14FFFFFF), label = "rowBg")
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "rowScale")
    val textColor = if (focused) Palette.Ink else Palette.TextPrimary

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = textColor, fontSize = 21.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = textColor.copy(alpha = 0.65f), fontSize = 15.sp)
        }
        trailing(focused)
    }
}

@Composable
private fun Switch(checked: Boolean, focused: Boolean) {
    val track by animateColorAsState(
        when {
            checked && focused -> Palette.Ink
            checked -> Palette.Focus
            focused -> Color(0x330B0F1A)
            else -> Color(0x33FFFFFF)
        },
        label = "switchTrack",
    )
    val thumbColor = when {
        checked && focused -> Palette.Focus
        checked -> Palette.Ink
        focused -> Palette.Ink
        else -> Palette.Focus
    }
    val thumbOffset by animateDpAsState(if (checked) 22.dp else 0.dp, label = "switchThumb")
    Box(
        Modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .background(thumbColor, CircleShape),
        )
    }
}

@Composable
private fun Stepper(value: String, canDecrease: Boolean, canIncrease: Boolean, focused: Boolean) {
    val color = if (focused) Palette.Ink else Palette.TextPrimary
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (focused) Text("‹", color = color.copy(alpha = if (canDecrease) 0.9f else 0.25f), fontSize = 26.sp)
        Text(
            value,
            color = color,
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        if (focused) Text("›", color = color.copy(alpha = if (canIncrease) 0.9f else 0.25f), fontSize = 26.sp)
    }
}

@Composable
private fun Chevron(focused: Boolean) {
    Text("›", color = if (focused) Palette.Ink else Palette.TextSecondary, fontSize = 30.sp)
}

@Composable
private fun Footer() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Text(
        listOfNotNull("Pill Launcher", version?.let { "v$it" }).joinToString("  ·  ") + "  ·  Press Back to close",
        color = Palette.TextTertiary,
        fontSize = 14.sp,
        modifier = Modifier.padding(start = 4.dp),
    )
}
