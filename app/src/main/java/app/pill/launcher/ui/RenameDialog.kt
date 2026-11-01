package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.AppEntry
import app.pill.launcher.MaxNameLength
import kotlinx.coroutines.delay

private val DialogShape = RoundedCornerShape(26.dp)
private val FieldShape = RoundedCornerShape(16.dp)

/**
 * Gives an app a name of its own in the launcher, the same card as the app menu. The
 * field takes the TV keyboard (OK opens it); Save keeps the name, and Use its own name
 * (once renamed) goes back to the app's. The app itself is never changed.
 */
@Composable
fun RenameDialog(app: AppEntry, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue(app.label, TextRange(app.label.length))) }
    val field = remember { FocusRequester() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var result by remember { mutableStateOf<String?>(null) }
    val close = { visible.targetState = false }
    val save = { name: String -> result = name; close() }

    LaunchedEffect(Unit) {
        delay(80)
        runCatching { field.requestFocus() }
    }
    // Rename once the card has gone, so the new name settles in behind it
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) {
            onDismiss()
            result?.let(onRename)
        }
    }
    val scrimAlpha by animateFloatAsState(if (visible.targetState) 1f else 0f, tween(Motion.Quick), label = "renameScrim")

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(Motion.Quick)) + scaleIn(tween(Motion.Quick, easing = Motion.EnterEasing), initialScale = 0.92f),
                exit = fadeOut(tween(Motion.Quick)) + scaleOut(tween(Motion.Quick, easing = Motion.ExitEasing), targetScale = 0.95f),
            ) {
                Column(
                    Modifier
                        .width(440.dp)
                        .shadow(32.dp, DialogShape)
                        .glass(DialogShape)
                        .padding(22.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(app.icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(Squircle))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Rename", color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Only in Pill Launcher" + (app.originalLabel?.let { " · its own name: $it" } ?: ""),
                                color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    NameField(
                        value = value,
                        onChange = { if (it.text.length <= MaxNameLength) value = it },
                        onDone = { save(value.text) },
                        modifier = Modifier.focusRequester(field),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("Save", primary = true, onClick = { save(value.text) }, enabled = value.text.isNotBlank())
                        if (app.originalLabel != null) {
                            PillButton("Use its own name", primary = false, onClick = { save("") })
                        }
                        Spacer(Modifier.weight(1f))
                        Text("${value.text.length}/$MaxNameLength", color = Palette.TextTertiary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun NameField(value: TextFieldValue, onChange: (TextFieldValue) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Palette.Focus else Palette.Hairline, tween(Motion.Quick), label = "nameBorder")
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 22.sp),
        cursorBrush = SolidColor(Palette.Focus),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Box(
                Modifier
                    .clip(FieldShape)
                    .background(Color(0x14FFFFFF))
                    .border(2.dp, border, FieldShape)
                    .padding(horizontal = 18.dp, vertical = 13.dp),
            ) {
                if (value.text.isEmpty()) Text("Name", color = Palette.TextTertiary, fontSize = 22.sp)
                inner()
            }
        },
    )
}
