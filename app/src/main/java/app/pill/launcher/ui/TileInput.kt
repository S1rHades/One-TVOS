package app.pill.launcher.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long OK must be held to pin or unpin. */
private const val HOLD_MS = 550L

/** Keys that act as OK on remotes, keyboards and game controllers. */
private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/** Whether a tile is focused or hovered, and the modifier that makes it so. */
class TileInput(val active: Boolean, val modifier: Modifier)

/**
 * Input shared by every app tile: OK opens, holding OK (timed here, since many remotes,
 * HDMI-CEC especially, never send key repeats) or the Menu key pins or unpins, and touch
 * or a pointer click and long-press work too. [onFocus] reports focus changes.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberTileInput(onOpen: () -> Unit, onTogglePin: () -> Unit, onFocus: (Boolean) -> Unit = {}): TileInput {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val open by rememberUpdatedState(onOpen)
    val togglePin by rememberUpdatedState(onTogglePin)
    val focusChanged by rememberUpdatedState(onFocus)
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    var okDown by remember { mutableStateOf(false) }

    val modifier = Modifier
        .onFocusChanged {
            if (focused != it.isFocused) focusChanged(it.isFocused)
            focused = it.isFocused
            if (!it.isFocused) {
                okDown = false
                holdJob?.cancel()
            }
        }
        .onPreviewKeyEvent { event ->
            val key = event.key
            when {
                key == Key.Menu -> {
                    if (event.type == KeyEventType.KeyUp) togglePin()
                    true
                }
                key !in OkKeys -> false
                event.type == KeyEventType.KeyDown -> {
                    if (!okDown) {
                        okDown = true
                        holdJob = scope.launch {
                            delay(HOLD_MS)
                            togglePin()
                        }
                    }
                    true
                }
                event.type == KeyEventType.KeyUp -> {
                    // A key-up without our key-down (focus moved here mid-press) is just swallowed
                    if (okDown && holdJob?.isActive == true) {
                        holdJob?.cancel()
                        open()
                    }
                    okDown = false
                    holdJob = null
                    true
                }
                else -> false
            }
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null, // scale, glow and outline replace the default ripple
            onClick = { open() },
            onLongClick = { togglePin() },
        )
    return TileInput(focused || hovered, modifier)
}
