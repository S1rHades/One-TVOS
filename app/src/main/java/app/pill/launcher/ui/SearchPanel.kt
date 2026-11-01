package app.pill.launcher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.pill.launcher.nextLetters
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pill.launcher.AppEntry
import kotlinx.coroutines.delay

private const val PANEL_MS = 260
private val CardShape = RoundedCornerShape(28.dp)

/**
 * Finds installed apps by name. A floating card below the bar: pick letters from the
 * strip with Left / Right and OK (no keyboard needed), or type with the TV keyboard (OK
 * on the field opens it), then go down to the matches. Letters that match no app are dimmed. [onSystemSearch], when the
 * TV has a system search (Google / Assistant), adds a shortcut to it.
 */
@Composable
fun SearchPanel(
    apps: List<AppEntry>,
    pins: List<String>,
    onOpen: (AppEntry) -> Unit,
    onMenu: (AppEntry, okHeld: Boolean) -> Unit,
    onSystemSearch: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val field = remember { FocusRequester() }
    val strip = remember { FocusRequester() }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val close = { visible.targetState = false }
    val matches = remember(query, apps) { match(apps, query) }

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { strip.requestFocus() }
    }
    LaunchedEffect(visible.isIdle, visible.currentState) {
        if (visible.isIdle && !visible.currentState) onDismiss()
    }
    val scrimAlpha by animateFloatAsState(if (visible.targetState) 1f else 0f, tween(PANEL_MS), label = "searchScrim")

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().alpha(scrimAlpha).background(Palette.Scrim))
            TopNav(NavItem.Search)
            AnimatedVisibility(
                visibleState = visible,
                modifier = Modifier.align(Alignment.TopCenter),
                enter = slideInVertically(tween(PANEL_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeIn(tween(PANEL_MS)),
                exit = slideOutVertically(tween(PANEL_MS, easing = FastOutSlowInEasing)) { -it / 4 } + fadeOut(tween(PANEL_MS)),
            ) {
                Column(
                    Modifier
                        .padding(top = NavRoom, start = 48.dp, end = 48.dp)
                        .fillMaxWidth()
                        .shadow(32.dp, CardShape)
                        .glass(CardShape)
                        .padding(24.dp),
                ) {
                    SearchField(query, { query = it }, Modifier.focusRequester(field))
                    Spacer(Modifier.height(12.dp))
                    LetterStrip(
                        next = remember(query, apps) { nextLetters(apps.map { it.label }, query) },
                        onKey = { key ->
                            query = when (key) {
                                DeleteKey -> query.dropLast(1)
                                SpaceKey -> if (query.isEmpty() || query.endsWith(" ")) query else "$query "
                                else -> query + key.lowercase()
                            }
                        },
                        firstFocus = strip,
                    )
                    Spacer(Modifier.height(14.dp))

                    when {
                        query.isBlank() -> Hint("Pick letters above, or OK on the search field for the keyboard")
                        matches.isEmpty() -> Hint("No apps match “${query.trim()}”")
                        else -> LazyRow(
                            contentPadding = PaddingValues(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(matches, key = { it.pkg }) { app ->
                                AppTile(
                                    app = app,
                                    iconSize = 84.dp,
                                    showPinBadge = app.pkg in pins,
                                    onOpen = { onOpen(app) },
                                    onMenu = { held -> onMenu(app, held) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }

                    if (onSystemSearch != null) {
                        Spacer(Modifier.height(12.dp))
                        SystemSearchRow(onSystemSearch)
                    }
                }
            }
        }
    }
}

/** Name matches first, then names containing the query anywhere, alphabetically within each. */
private fun match(apps: List<AppEntry>, query: String): List<AppEntry> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    val hits = apps.filter { it.label.contains(q, ignoreCase = true) }
    return hits.sortedWith(compareBy({ !it.label.startsWith(q, ignoreCase = true) }, { it.label.lowercase() }))
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    val border by animateColorAsState(if (focused) Palette.Focus else Palette.Hairline, label = "fieldBorder")
    val shape = RoundedCornerShape(18.dp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 24.sp),
        cursorBrush = SolidColor(Palette.Focus),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier
                    .clip(shape)
                    .background(Color(0x14FFFFFF))
                    .border(2.dp, border, shape)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon(tint = Palette.TextSecondary, iconSize = 24.dp)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text("Search apps", color = Palette.TextTertiary, fontSize = 24.sp)
                    inner()
                }
            }
        },
    )
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Palette.TextSecondary, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp))
}

@Composable
private fun SystemSearchRow(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val bg by animateColorAsState(if (focused) Palette.Focus else Color(0x0FFFFFFF), tween(160), label = "sysBg")
    val fg = if (focused) Palette.Ink else Palette.TextPrimary
    Row(
        Modifier
            .clip(shape)
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(tint = fg, iconSize = 20.dp)
        Spacer(Modifier.width(12.dp))
        Text("Search the TV, films and shows", color = fg, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

private const val DeleteKey = "⌫"
private const val SpaceKey = "Space"
private val Letters = listOf(DeleteKey, SpaceKey) + ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() }
private val KeyShape = RoundedCornerShape(12.dp)

/**
 * The letters, A to Z and 0 to 9, in one row that scrolls as focus moves along it, with
 * Delete and Space at its start. OK adds the focused letter. Letters that no app's name
 * continues with are dimmed (they still work). Focus starts on A.
 */
@Composable
private fun LetterStrip(next: Set<Char>?, onKey: (String) -> Unit, firstFocus: FocusRequester) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = 0)
    LazyRow(
        state = list,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(Letters, key = { it }) { key ->
            val letter = key.singleOrNull()
            val possible = next == null || letter == null || letter.lowercaseChar() in next
            LetterKey(
                key = key,
                possible = possible,
                onClick = { onKey(key) },
                modifier = if (key == "A") Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun LetterKey(key: String, possible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Palette.Focus else Color(0x14FFFFFF), tween(Motion.Quick), label = "keyBg")
    val fg by animateColorAsState(if (focused) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "keyFg")
    val scale by animateFloatAsState(if (focused) 1.12f else 1f, FocusSpring, label = "keyScale")
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (possible || focused) 1f else 0.38f
            }
            .height(40.dp)
            .width(
                when (key) {
                    SpaceKey -> 72.dp
                    DeleteKey -> 48.dp
                    else -> 38.dp
                },
            )
            .clip(KeyShape)
            .background(bg)
            .semantics { contentDescription = if (key == DeleteKey) "Delete" else key }
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(key, color = fg, fontSize = if (key.length > 1 && key != DeleteKey) 15.sp else 19.sp, fontWeight = FontWeight.SemiBold)
    }
}
