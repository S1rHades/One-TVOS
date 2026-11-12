package app.pill.launcher.ui

import app.pill.launcher.rowStep
import app.pill.launcher.tr
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import app.pill.launcher.moved
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent

// The pieces every settings page is built from: groups, rows and what sits at a row's end.

private val SettingRowShape = RoundedCornerShape(16.dp)

/** A labelled block of rows on a faint card. */
@Composable
internal fun Group(label: String?, content: @Composable ColumnScope.() -> Unit) {
    if (label != null) {
        Text(
            label.uppercase(),
            color = Palette.TextTertiary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = tracking(1.4f),
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
    // Groups stand apart more than rows within them do
    Spacer(Modifier.height(Space.Item + 2.dp))
}

/**
 * One setting. Focus (or hover) turns it into a white card with dark text.
 * [onStep], when given, makes Left / Right change the value in place.
 */
@Composable
internal fun SettingRow(
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
    /** Less room above and below: the category list, so all six fit without scrolling. */
    dense: Boolean = false,
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
            .clip(SettingRowShape)
            .background(bg)
            .then(if (moving) Modifier.border(2.dp, Palette.Accent, SettingRowShape) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onPreviewKey == null) Modifier else Modifier.onPreviewKeyEvent(onPreviewKey))
            .then(
                if (onStep == null) Modifier else Modifier.onKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                    when (event.key) {
                        Key.DirectionLeft -> { onStep(rowStep(-1)); true }
                        Key.DirectionRight -> { onStep(rowStep(1)); true }
                        else -> false
                    }
                },
            )
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = if (dense) 5.dp else 10.dp),
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
internal fun Switch(checked: Boolean, focused: Boolean) {
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
internal fun Stepper(value: String, canDecrease: Boolean, canIncrease: Boolean, focused: Boolean) {
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
internal fun Check(focused: Boolean) {
    Text("✓", color = if (focused) Palette.Ink else Palette.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
}

@Composable
internal fun Chevron(focused: Boolean) {
    Text("›", color = if (focused) Palette.Ink else Palette.TextTertiary, fontSize = 26.sp)
}

@Composable
internal fun Footer() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
    }
    Text(
        listOfNotNull(tr("Pill Launcher"), version?.let { "v$it" }, backHint()).joinToString("  ·  "),
        color = Palette.TextTertiary,
        fontSize = 13.sp,
        modifier = Modifier.padding(start = 12.dp, top = 8.dp),
    )
}

@Composable
internal fun Mark(symbol: String, focused: Boolean) {
    Text(symbol, color = if (focused) Palette.Ink else Palette.TextTertiary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun Note(text: String) {
    Text(text, color = Palette.TextTertiary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
}

/** Typing a name to search for (a team, a town): OK opens the TV keyboard. */
@Composable
internal fun TeamSearchField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
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
                    .clip(SettingRowShape)
                    .background(Color(0x14FFFFFF))
                    .border(2.dp, border, SettingRowShape)
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
 * A folded row for the options few people need: "Advanced" and what is inside, and OK
 * unfolds them under it. Progressive disclosure: the page stays short for everyone else.
 */
@Composable
internal fun ColumnScope.Advanced(summary: String, startOpen: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    SettingRow(
        title = tr("Advanced"),
        subtitle = summary,
        onClick = { open = !open },
    ) { focused -> Mark(if (open) "–" else "+", focused) }
    AnimatedVisibility(open) {
        Column { content() }
    }
}

/** A line of text typed with the TV keyboard; OK opens it, Done (or leaving the field) keeps it. */
@Composable
internal fun TextRow(value: String, hint: String, onChange: (String) -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
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
                    .clip(SettingRowShape)
                    .background(Color(0x14FFFFFF))
                    .border(2.dp, border, SettingRowShape)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(hint, color = Palette.TextTertiary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                inner()
            }
        },
    )
}
