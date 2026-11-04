package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.AppEntry
import kotlinx.coroutines.delay

private const val MENU_MS = 200
private val CardShape = RoundedCornerShape(26.dp)
private val ItemShape = RoundedCornerShape(16.dp)
private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/** What the app menu offers: Move only for favourites, Uninstall only for removable apps. */
enum class MenuAction { TogglePin, Move, Rename, AppInfo, Uninstall }

/**
 * An app's menu, opened by holding OK (or Menu, or a long-press): add to or remove from
 * Favorites, move it along the favourites row, rename it, its system info page, and uninstall.
 * [okHeld] means the OK press that opened it is still down: its release (and any key
 * repeats before it) is ignored, so it can't pick the first item by itself.
 */
@Composable
fun AppMenu(
    app: AppEntry,
    pinned: Boolean,
    okHeld: Boolean,
    onAction: (MenuAction) -> Unit,
    onDismiss: () -> Unit,
) {
    MenuCard(
        okHeld = okHeld,
        header = {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(44.dp).clip(Squircle))
            Spacer(Modifier.width(14.dp))
            Text(app.label, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        choices = buildList {
            add(MenuChoice(if (pinned) "Remove from Favorites" else "Add to Favorites") { onAction(MenuAction.TogglePin) })
            if (pinned) add(MenuChoice("Move", "Left / Right along Favorites") { onAction(MenuAction.Move) })
            add(MenuChoice("Rename…", app.originalLabel?.let { "Its own name: $it" }) { onAction(MenuAction.Rename) })
            add(MenuChoice("App info", "In the TV's settings") { onAction(MenuAction.AppInfo) })
            if (app.removable) add(MenuChoice("Uninstall", warning = true) { onAction(MenuAction.Uninstall) })
        },
        onDismiss = onDismiss,
    )
}

/** One line of a [MenuCard]: what it says, a grey hint under it, and what it does once the menu has gone. */
class MenuChoice(val title: String, val hint: String? = null, val warning: Boolean = false, val act: () -> Unit)

/**
 * A small glass menu in the middle of the screen, over a dim: a [header], then [choices].
 * A choice acts once the menu has gone, so a new screen doesn't open over it. When opened
 * by holding OK ([okHeld]), the release of that press is ignored.
 */
@Composable
fun MenuCard(
    okHeld: Boolean,
    header: @Composable RowScope.() -> Unit,
    choices: List<MenuChoice>,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    var chosen by remember { mutableStateOf<MenuChoice?>(null) }
    var ready by remember { mutableStateOf(!okHeld) }
    val close = { visible.targetState = false }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) {
            onDismiss()
            chosen?.act?.invoke()
        }
    }
    val scrimAlpha by rememberDim(visible.targetState, MENU_MS)

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.key !in OkKeys) return@onPreviewKeyEvent false
                    when (event.type) {
                        // Repeats of a held key never act; a fresh press means the opening one is over
                        KeyEventType.KeyDown -> if (event.nativeKeyEvent.repeatCount > 0) true else { ready = true; false }
                        KeyEventType.KeyUp -> if (!ready) { ready = true; true } else false
                        else -> false
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(tween(MENU_MS)) + scaleIn(tween(MENU_MS, easing = FastOutSlowInEasing), initialScale = 0.92f),
                exit = fadeOut(tween(MENU_MS)) + scaleOut(tween(MENU_MS), targetScale = 0.95f),
            ) {
                Column(
                    Modifier
                        .width(360.dp)
                        .shadow(32.dp, CardShape)
                        .glass(CardShape)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(Modifier.padding(start = 10.dp, top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, content = header)
                    for ((i, choice) in choices.withIndex()) {
                        MenuItem(
                            choice.title,
                            { chosen = choice; close() },
                            if (i == 0) Modifier.focusRequester(first) else Modifier,
                            hint = choice.hint,
                            warning = choice.warning,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    warning: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var focused by remember { mutableStateOf(false) }
    val active = focused || hovered
    val bg by animateColorAsState(if (active) Palette.Focus else Color.Transparent, tween(140), label = "menuBg")
    val scale by animateFloatAsState(if (active) 1.02f else 1f, label = "menuScale")
    val rest = if (warning) Color(0xFFFFA8A8) else Palette.TextPrimary
    val text by animateColorAsState(if (active) (if (warning) Color(0xFFC92A2A) else Palette.Ink) else rest, tween(140), label = "menuText")
    Column(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(ItemShape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(title, color = text, fontSize = 19.sp, fontWeight = FontWeight.Medium)
        if (hint != null) {
            Spacer(Modifier.height(1.dp))
            Text(hint, color = text.copy(alpha = 0.6f), fontSize = 14.sp)
        }
    }
}
