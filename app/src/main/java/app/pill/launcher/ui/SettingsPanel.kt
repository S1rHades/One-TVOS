package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.LauncherSettings
import kotlinx.coroutines.delay

/**
 * Right-hand settings panel. It lives in its own dialog window, so D-pad focus
 * stays inside it and Back closes it.
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

    LaunchedEffect(Unit) {
        delay(200)
        runCatching { firstRow.requestFocus() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.CenterEnd),
                enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280)),
            ) {
                Column(
                    Modifier
                        .width(520.dp)
                        .fillMaxHeight()
                        .background(Color(0xF2121826))
                        .padding(horizontal = 40.dp, vertical = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text("Settings", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))

                    SettingRow(
                        title = "Background slideshow",
                        value = if (settings.slideshow) "On" else "Off",
                        onClick = { onChange(settings.copy(slideshow = !settings.slideshow)) },
                        modifier = Modifier.focusRequester(firstRow),
                    )
                    SettingRow(
                        title = "Change photo every",
                        value = "${settings.intervalSec} s",
                        onClick = {
                            val options = LauncherSettings.IntervalOptions
                            val next = options[(options.indexOf(settings.intervalSec) + 1) % options.size]
                            onChange(settings.copy(intervalSec = next))
                        },
                    )
                    SettingRow(
                        title = "System settings",
                        value = "Open",
                        onClick = onOpenSystemSettings,
                    )
                    SettingRow(title = "Close", value = "", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) Color.White else Color(0x1FFFFFFF))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val textColor = if (focused) Color(0xFF0B0F1A) else Color.White
        Text(title, color = textColor, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        if (value.isNotEmpty()) {
            Text(value, color = textColor.copy(alpha = 0.75f), fontSize = 22.sp)
        }
    }
}
