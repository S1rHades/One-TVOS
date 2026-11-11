package app.pill.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.pill.launcher.AppEntry
import app.pill.launcher.Notice
import app.pill.launcher.noticeGroups
import app.pill.launcher.NoticeKind
import app.pill.launcher.UpdatePhase
import app.pill.launcher.UpdateState
import app.pill.launcher.noticeAge
import kotlinx.coroutines.delay
import java.time.Instant

/** The bar's height: the same as the bar at the top right. */
private val BarHeight = NavHeight
private val BarShape = RoundedCornerShape(BarHeight / 2)
private val ButtonSize = 40.dp
private val RowShape = RoundedCornerShape(18.dp)
/** The open list stops growing here and scrolls. */
private val ListMax = 236.dp

/**
 * The bar under the widgets, as wide as they are: the newest notification on the left
 * (OK does what it says; holding OK dismisses it), then the bell and Files on the right.
 * OK on the bell opens the bar downwards into every notification, over Favorites; Back,
 * or the bell again, folds it up.
 */

/** How long the bar's old line takes to fade before the new one starts. */
private const val LineSwapMs = 140

@Composable
fun NotificationBar(
    notices: List<Notice>,
    /** How many arrived since the list was last opened: the bell's dot. */
    unread: Int,
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    apps: List<AppEntry>,
    update: UpdateState,
    onPrimary: (Notice) -> Unit,
    onDismiss: (Notice) -> Unit,
    onClearAll: () -> Unit,
    onLaterUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onFiles: () -> Unit,
    bellFocus: FocusRequester,
    /** The newest notification (or All caught up), at the left: where Down from the widgets lands. */
    messageFocus: FocusRequester = remember { FocusRequester() },
    /** Down from the bar (folded up): where focus goes, the start of the row below. */
    onDown: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(30_000)
            value = Instant.now()
        }
    }
    val top = notices.firstOrNull()
    val firstRow = remember { FocusRequester() }
    // Each row's focus, so dismissing one hands focus to the one that takes its place
    val rowFocus = remember { HashMap<String, FocusRequester>() }
    var refocusAt by remember { mutableStateOf<Int?>(null) }
    // Grouped by where they came from; focus and dismissal follow this order
    val groups = remember(notices, apps) { noticeGroups(notices) { pkg -> apps.firstOrNull { it.pkg == pkg }?.label } }
    val ordered = remember(groups) { groups.flatMap { it.second } }
    val currentNotices by rememberUpdatedState(ordered)
    LaunchedEffect(refocusAt) {
        val at = refocusAt ?: return@LaunchedEffect
        // A few frames for the list to lose the dismissed row
        repeat(6) {
            delay(30)
            val next = currentNotices.getOrNull(at) ?: currentNotices.getOrNull(at - 1)
            val target = next?.let { rowFocus[it.id] } ?: bellFocus
            if (runCatching { target.requestFocus() }.isSuccess && it >= 2) {
                refocusAt = null
                return@LaunchedEffect
            }
        }
        refocusAt = null
    }
    var holdsFocus by remember { mutableStateOf(false) }

    // Opening moves focus into the list; folding it up brings focus back to the bell
    var wasOpen by remember { mutableStateOf(open) }
    LaunchedEffect(open) {
        if (open == wasOpen) return@LaunchedEffect
        wasOpen = open
        delay(40)
        if (open && notices.isNotEmpty()) runCatching { firstRow.requestFocus() }
        if (!open && holdsFocus) runCatching { bellFocus.requestFocus() }
    }
    BackHandler(enabled = open) { onOpenChange(false) }

    // The bell swings when something new arrives (not on first showing)
    val swing = remember { Animatable(0f) }
    var lastTop by remember { mutableStateOf(top?.id) }
    LaunchedEffect(top?.id) {
        if (top?.id == lastTop) return@LaunchedEffect
        lastTop = top?.id
        if (top == null) return@LaunchedEffect
        swing.animateTo(
            0f,
            keyframes {
                durationMillis = 900
                16f at 120
                -12f at 280
                8f at 440
                -4f at 600
                0f at 900
            },
        )
    }

    Box(
        modifier
            .fillMaxWidth()
            .height(BarHeight)
            .zIndex(2f)
            .onFocusChanged { holdsFocus = it.hasFocus }
            // Back folds the open list up (the remote's Back reaches the focused row first)
            .onPreviewKeyEvent { event ->
                if (!open && event.key == Key.DirectionDown && onDown != null) {
                    if (event.type == KeyEventType.KeyDown) onDown()
                    return@onPreviewKeyEvent true
                }
                if (!open || event.key != Key.Back) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) onOpenChange(false)
                true
            },
    ) {
        val sheetShadow by animateFloatAsState(if (open) 1f else 0f, tween(Motion.Enter), label = "sheetShadow")
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .shadow((28 * sheetShadow).dp, BarShape)
                .glass(BarShape, tint = if (open) Color(0xE6121826) else GlassTint),
        ) {
            Row(
                Modifier.fillMaxWidth().height(BarHeight).padding(horizontal = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.CenterStart) {
                    AnimatedContent(
                        targetState = open to top,
                        contentKey = { (isOpen, n) -> if (isOpen) "open" else n?.id ?: "none" },
                        transitionSpec = {
                            // The old line is gone before the new one shows, so the two never read on top of each other
                            (slideInVertically(tween(Motion.Enter, easing = Motion.EnterEasing)) { it / 2 } + fadeIn(tween(Motion.Enter - LineSwapMs, delayMillis = LineSwapMs))) togetherWith
                                (slideOutVertically(tween(Motion.Exit, easing = Motion.ExitEasing)) { -it / 2 } + fadeOut(tween(LineSwapMs)))
                        },
                        label = "noticeTop",
                        modifier = Modifier.fillMaxWidth(),
                    ) { (isOpen, shown) ->
                        when {
                            isOpen -> ListHeader(count = notices.size, unread = unread)
                            shown == null -> CaughtUp(onOpen = { onOpenChange(true) }, focus = messageFocus)
                            else -> TopNotice(shown, more = notices.size - 1, apps = apps, now = now, onOpen = { onPrimary(shown) }, onDismiss = { onDismiss(shown) }, focus = messageFocus)
                        }
                    }
                }
                if (open && notices.isNotEmpty()) {
                    SmallPill("Clear all", onClick = onClearAll)
                    Spacer(Modifier.width(10.dp))
                }
                Box(Modifier.padding(horizontal = 8.dp).size(width = 1.dp, height = 22.dp).background(Color(0x40FFFFFF)))
                BarButton(
                    label = if (unread > 0) "Notifications, $unread new" else "Notifications",
                    current = open,
                    onClick = { onOpenChange(!open) },
                    modifier = Modifier.focusRequester(bellFocus),
                ) { tint ->
                    Box {
                        BellIcon(tint, 22.dp, Modifier.graphicsLayer { rotationZ = swing.value; transformOrigin = TransformOrigin(0.5f, 0.1f) })
                        if (unread > 0 && !open) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-1).dp)
                                    .size(9.dp)
                                    .background(UnreadRed, CircleShape)
                                    .border(1.5.dp, Color(0xFF1B2232), CircleShape),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                BarButton(label = "Share files", current = false, onClick = onFiles) { tint -> FilesIcon(tint, 22.dp) }
            }

            AnimatedVisibility(
                visible = open,
                enter = expandVertically(tween(Motion.Enter, easing = Motion.EnterEasing), expandFrom = Alignment.Top) + fadeIn(tween(Motion.Enter)),
                exit = shrinkVertically(tween(Motion.Exit, easing = Motion.ExitEasing), shrinkTowards = Alignment.Top) + fadeOut(tween(Motion.Exit)),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = ListMax)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 6.dp, end = 6.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (notices.isEmpty()) {
                        Text(
                            "Nothing new. Updates, goals, files from your phone and other apps' notifications show here.",
                            color = Palette.TextSecondary,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                    for ((source, group) in groups) {
                        key("group:$source") {
                        Text(
                            source.uppercase(),
                            color = Palette.TextTertiary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.3.sp,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 14.dp, top = 8.dp, bottom = 2.dp),
                        )
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0x0FFFFFFF)),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                    for (n in group) {
                        val i = ordered.indexOf(n)
                        key(n.id) {
                            NoticeRow(
                                notice = n,
                                apps = apps,
                                update = update,
                                now = now,
                                onOpen = { onPrimary(n) },
                                onDismiss = {
                                    onDismiss(n)
                                    refocusAt = i
                                },
                                onLater = onLaterUpdate,
                                onCancel = onCancelUpdate,
                                modifier = Modifier
                                    .focusRequester(rowFocus.getOrPut(n.id) { FocusRequester() })
                                    .then(if (i == 0) Modifier.focusRequester(firstRow) else Modifier),
                            )
                        }
                    }
                        }
                        }
                    }
                }
            }
        }
    }
}

private val UnreadRed = Color(0xFFFF6B5B)

@Composable
private fun ListHeader(count: Int, unread: Int) {
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Palette.TextPrimary, fontWeight = FontWeight.SemiBold)) { append("Notifications") }
            withStyle(SpanStyle(color = Palette.TextTertiary)) {
                append(
                    when {
                        count == 0 -> ""
                        unread > 0 -> " · $unread new · hold OK on one to dismiss"
                        else -> " · hold OK on one to dismiss"
                    },
                )
            }
        },
        fontSize = 17.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp),
    )
}

/** The newest notification, on one line in the bar: OK does what it says, holding OK dismisses it. */
@Composable
private fun TopNotice(notice: Notice, more: Int, apps: List<AppEntry>, now: Instant, onOpen: () -> Unit, onDismiss: () -> Unit, focus: FocusRequester) {
    val input = rememberTileInput(onOpen, { onDismiss() })
    val active = input.active
    val bg by animateColorAsState(if (active) Palette.Focus else Color.Transparent, tween(Motion.Quick), label = "topBg")
    val fg by animateColorAsState(if (active) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "topFg")
    Row(
        Modifier
            .fillMaxWidth()
            .height(ButtonSize)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .focusRequester(focus)
            .then(input.modifier)
            .padding(start = 6.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NoticeIcon(notice, apps, active, 30.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(noticeTitle(notice, apps)) }
                withStyle(SpanStyle(color = fg.copy(alpha = 0.66f))) {
                    noticeText(notice, apps).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                    if (!isLive(notice)) append(" · " + noticeAge(notice.at, now))
                }
            },
            color = fg,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(0.001f))
        if (more > 0) {
            Spacer(Modifier.width(12.dp))
            Text(
                "+$more",
                color = fg,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(if (active) Color(0x1A0B0F1A) else Color(0x29FFFFFF), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 3.dp),
            )
        }
    }
}

/**
 * Nothing new: a quiet line that still takes focus, so Down from the widgets always stops
 * at the bar. OK opens the (empty) list.
 */
@Composable
private fun CaughtUp(onOpen: () -> Unit, focus: FocusRequester) {
    val input = rememberTileInput(onOpen, { onOpen() })
    val active = input.active
    val bg by animateColorAsState(if (active) Palette.Focus else Color.Transparent, tween(Motion.Quick), label = "caughtUpBg")
    val fg by animateColorAsState(if (active) Palette.Ink else Palette.TextTertiary, tween(Motion.Quick), label = "caughtUpFg")
    // As wide as the bar, so Down from any widget above stops here; the white pill stays around the words
    Box(Modifier.fillMaxWidth().height(ButtonSize).focusRequester(focus).then(input.modifier), contentAlignment = Alignment.CenterStart) {
        Row(
            Modifier
                .height(ButtonSize)
                .clip(RoundedCornerShape(50))
                .background(bg)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All caught up", color = fg, fontSize = 16.sp, maxLines = 1)
        }
    }
}

/** A notification in the open list, with its buttons (Update, Later) on the right for an update. */
@Composable
private fun NoticeRow(
    notice: Notice,
    apps: List<AppEntry>,
    update: UpdateState,
    now: Instant,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onLater: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val input = rememberTileInput(onOpen, { onDismiss() })
    val active = input.active
    val bg by animateColorAsState(if (active) Palette.Focus else Color.Transparent, tween(Motion.Quick), label = "rowBg")
    val fg by animateColorAsState(if (active) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "rowFg")
    val scale by animateFloatAsState(if (active) 1.01f else 1f, FocusSpring, label = "rowScale")
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            modifier
                .weight(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RowShape)
                .background(bg)
                .then(input.modifier)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NoticeIcon(notice, apps, active, 36.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(noticeTitle(notice, apps), color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val text = noticeText(notice, apps)
                if (text.isNotEmpty()) {
                    Text(text, color = fg.copy(alpha = 0.68f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(12.dp))
            if (!isLive(notice)) Text(noticeAge(notice.at, now), color = fg.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1)
        }
        if (notice.kind == NoticeKind.Update) {
            when (update.phase) {
                UpdatePhase.Available, UpdatePhase.Failed -> {
                    Spacer(Modifier.width(10.dp))
                    PillButton(if (update.phase == UpdatePhase.Failed) "Try again" else "Update", primary = true, onClick = onOpen)
                    Spacer(Modifier.width(8.dp))
                    PillButton("Later", primary = false, onClick = onLater)
                }
                UpdatePhase.Downloading -> {
                    Spacer(Modifier.width(12.dp))
                    ProgressLine(update.progress, Modifier.width(120.dp))
                    Spacer(Modifier.width(12.dp))
                    PillButton("Cancel", primary = false, onClick = onCancel)
                }
                else -> Unit
            }
            Spacer(Modifier.width(6.dp))
        }
    }
}

/** Happening now (a kick-off countdown, no connection): its own text says when, so no age. */
private fun isLive(n: Notice): Boolean = n.id.startsWith("kickoff:") || n.id.startsWith("offline:")

/** What a notification is called in the bar: for another app's, the app's name. */
private fun noticeTitle(n: Notice, apps: List<AppEntry>): String =
    if (n.kind == NoticeKind.App) apps.firstOrNull { it.pkg == n.pkg }?.label ?: n.title else n.title

/** The line under the title: for another app's, its title and text together. */
private fun noticeText(n: Notice, apps: List<AppEntry>): String =
    if (n.kind == NoticeKind.App && apps.any { it.pkg == n.pkg }) listOf(n.title, n.text).filter { it.isNotBlank() }.joinToString(" · ") else n.text

/** The square on the left of a notification: the app's icon, or a symbol for Pill Launcher's own. */
@Composable
private fun NoticeIcon(n: Notice, apps: List<AppEntry>, active: Boolean, size: Dp) {
    val app = if (n.kind == NoticeKind.App) apps.firstOrNull { it.pkg == n.pkg } else null
    if (app != null) {
        AppIcon(app, depth = { 0f }, modifier = Modifier.size(size).clip(Squircle))
        return
    }
    val shape = RoundedCornerShape(size * 0.3f)
    val tint = if (active) Color.White else Palette.TextPrimary
    Box(
        Modifier.size(size).background(if (active) Palette.Ink else Color(0x26FFFFFF), shape),
        contentAlignment = Alignment.Center,
    ) {
        val glyph = size * 0.56f
        when (n.kind) {
            NoticeKind.Update -> DownloadIcon(tint, glyph)
            NoticeKind.Updated -> CheckIcon(if (active) Color.White else Color(0xFF8CE99A), glyph)
            NoticeKind.Football -> BallIcon(tint, glyph)
            NoticeKind.Connection -> WifiIcon(
                NetworkStatus(if (n.id.startsWith("offline")) Link.Offline else Link.WiFi, 4, false),
                if (n.id.startsWith("offline") && !active) Color(0xFFFF8787) else tint,
                glyph,
            )
            NoticeKind.File -> FilesIcon(tint, glyph)
            NoticeKind.Prayer -> CrescentIcon(if (active) Color.White else PrayerGreen, glyph)
            NoticeKind.App -> Text(n.title.take(1).uppercase(), color = tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** A round button in the bar: a white disc while focused, a grey one while its list is open. */
@Composable
private fun BarButton(label: String, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, icon: @Composable (Color) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            focused -> Palette.Focus
            current -> Color(0x38FFFFFF)
            else -> Color.Transparent
        },
        tween(Motion.Quick),
        label = "barButtonBg",
    )
    val tint by animateColorAsState(if (focused) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "barButtonTint")
    Box(
        modifier
            .size(ButtonSize)
            .background(bg, CircleShape)
            .semantics { contentDescription = label }
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon(tint) }
}

/** A small rounded button in the open list's header (Clear all). */
@Composable
private fun SmallPill(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Palette.Focus else Color(0x24FFFFFF), tween(Motion.Quick), label = "smallPillBg")
    val fg by animateColorAsState(if (focused) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "smallPillFg")
    Box(
        Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** A bell outline, in the bar's stroke weight. */
@Composable
fun BellIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val bell = Path().apply {
            moveTo(s * 0.22f, s * 0.7f)
            lineTo(s * 0.22f, s * 0.46f)
            cubicTo(s * 0.22f, s * 0.26f, s * 0.35f, s * 0.15f, s * 0.5f, s * 0.15f)
            cubicTo(s * 0.65f, s * 0.15f, s * 0.78f, s * 0.26f, s * 0.78f, s * 0.46f)
            lineTo(s * 0.78f, s * 0.7f)
            lineTo(s * 0.86f, s * 0.78f)
            lineTo(s * 0.14f, s * 0.78f)
            close()
        }
        drawPath(bell, tint, style = stroke)
        drawLine(tint, Offset(s * 0.42f, s * 0.9f), Offset(s * 0.58f, s * 0.9f), s * 0.09f, StrokeCap.Round)
    }
}

/** Files: an arrow up beside an arrow down, for sending and receiving. */
@Composable
fun FilesIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val w = s * 0.09f
        // Up, on the left
        drawLine(tint, Offset(s * 0.33f, s * 0.16f), Offset(s * 0.33f, s * 0.72f), w, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.33f, s * 0.16f), Offset(s * 0.17f, s * 0.32f), w, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.33f, s * 0.16f), Offset(s * 0.49f, s * 0.32f), w, StrokeCap.Round)
        // Down, on the right
        drawLine(tint, Offset(s * 0.67f, s * 0.28f), Offset(s * 0.67f, s * 0.84f), w, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.67f, s * 0.84f), Offset(s * 0.51f, s * 0.68f), w, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.67f, s * 0.84f), Offset(s * 0.83f, s * 0.68f), w, StrokeCap.Round)
    }
}

/** A tick. */
@Composable
private fun CheckIcon(tint: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = size.minDimension
        val path = Path().apply {
            moveTo(s * 0.18f, s * 0.52f)
            lineTo(s * 0.4f, s * 0.74f)
            lineTo(s * 0.82f, s * 0.28f)
        }
        drawPath(path, tint, style = Stroke(width = s * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** A crescent moon, for a prayer's time. */
@Composable
private fun CrescentIcon(tint: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = size.minDimension
        val moon = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = Offset(s * 0.46f, s * 0.5f), radius = s * 0.4f)) }
        val bite = Path().apply { addOval(androidx.compose.ui.geometry.Rect(center = Offset(s * 0.62f, s * 0.42f), radius = s * 0.33f)) }
        drawPath(Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, moon, bite), tint)
    }
}

/** A football: a circle with a pentagon at its heart. */
@Composable
private fun BallIcon(tint: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = size.minDimension
        val c = Offset(s / 2, s / 2)
        drawCircle(tint, s * 0.4f, c, style = Stroke(width = s * 0.09f))
        val r = s * 0.15f
        val penta = Path().apply {
            for (i in 0 until 5) {
                val a = Math.toRadians(-90.0 + 72.0 * i)
                val p = Offset(c.x + r * Math.cos(a).toFloat(), c.y + r * Math.sin(a).toFloat())
                if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
            }
            close()
        }
        drawPath(penta, tint)
    }
}
