package app.pill.launcher.ui

import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import android.content.Context
import android.app.ActivityManager
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import app.pill.launcher.Fixtures
import app.pill.launcher.Match
import app.pill.launcher.MatchDayMode
import app.pill.launcher.MatchPhase
import app.pill.launcher.NewsTopic
import app.pill.launcher.PickedHeadline
import app.pill.launcher.Side
import app.pill.launcher.Team
import app.pill.launcher.cardTeams
import app.pill.launcher.matchDayApplies
import app.pill.launcher.matchPhase
import app.pill.launcher.Weather
import app.pill.launcher.WeatherMode
import app.pill.launcher.PrayerTimes
import app.pill.launcher.WidgetKind
import app.pill.launcher.FootballMoment
import app.pill.launcher.MomentKind
import app.pill.launcher.momentText
import app.pill.launcher.StackTurnSec
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.focusable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.random.Random
import app.pill.launcher.shortCode
import app.pill.launcher.shortCompetition
import app.pill.launcher.sunTimes
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** All header cards share this height, so the row reads as one band. */
val WidgetHeight = 112.dp
private val CardShape = RoundedCornerShape(26.dp)
private val Gap = 14.dp
/** Room beside the row for a focused card, which grows by up to 4.5%. */
private val FocusRoom = 24.dp

/** Widths each card is designed for, roomy and compact. The row scales them to fit. */
private fun naturalWidth(kind: WidgetKind, compact: Boolean): Dp = when (kind) {
    WidgetKind.Clock -> if (compact) 146.dp else 220.dp
    WidgetKind.Weather -> if (compact) 160.dp else 240.dp
    WidgetKind.Fennecs -> if (compact) 204.dp else 270.dp
    WidgetKind.Daylight -> if (compact) 146.dp else 200.dp
    WidgetKind.System -> if (compact) 176.dp else 220.dp
    WidgetKind.Headlines -> if (compact) 176.dp else 280.dp
    WidgetKind.Prayers -> if (compact) 186.dp else 290.dp
    // As wide as the widest card inside it; see WidgetRow
    WidgetKind.Stack -> if (compact) 176.dp else 280.dp
}

private val AlgeriaGreen = Color(0xFF006633)
private val AlgeriaRed = Color(0xFFD21034)

/**
 * The header's widgets, in the user's order, filling the row: each card gets a share of
 * the width in proportion to its design width, so two cards stretch and four tighten
 * (switching to compact layouts). Cards fade in and out as they are turned on and off,
 * glide to their new place when reordered, and resize smoothly.
 */
@Composable
fun WidgetRow(
    widgets: List<WidgetKind>,
    weather: Weather?,
    weatherError: String?,
    mode: WeatherMode,
    /** The followed teams' fixtures, in order (Algeria first). */
    football: List<Fixtures>,
    matchDay: MatchDayMode,
    /** The headlines picked for the card, and what to say while there are none. */
    headlines: List<PickedHeadline>,
    headlinesStatus: String?,
    animate: Boolean,
    modifier: Modifier = Modifier,
    /** OK on a card opens its page. Cards without one aren't focusable. */
    onOpenClock: (() -> Unit)? = null,
    onOpenWeather: (() -> Unit)? = null,
    onOpenFootball: ((Team) -> Unit)? = null,
    onOpenHeadline: ((Int) -> Unit)? = null,
    /** The widgets inside the widget stack, in order. */
    stack: List<WidgetKind> = emptyList(),
    /** A stack widget to show now (the one being set up in Settings), rather than taking turns. */
    stackPreview: WidgetKind? = null,
    /** A goal or a win to celebrate on the football card; null the rest of the time. */
    moment: FootballMoment? = null,
    /** Today's prayer times, why they couldn't be fetched, and whether a mosque is chosen at all. */
    prayers: PrayerTimes? = null,
    prayersError: String? = null,
    hasMosque: Boolean = false,
    onOpenPrayers: (() -> Unit)? = null,
) {
    val content = WidgetContent(
        weather, weatherError, mode, football, matchDay, headlines, headlinesStatus, animate,
        onOpenClock, onOpenWeather, onOpenFootball, onOpenHeadline, moment,
        prayers, prayersError, hasMosque, onOpenPrayers,
    )
    BoxWithConstraints(modifier) {
        val gaps = Gap * (widgets.size - 1).coerceAtLeast(0)
        // The widths are designed at the default text size; a larger TV text size needs more
        val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        fun natural(kind: WidgetKind, compact: Boolean): Dp =
            if (kind == WidgetKind.Stack && stack.isNotEmpty()) stack.maxOf { naturalWidth(it, compact) } * textScale
            else naturalWidth(kind, compact) * textScale
        val full = widgets.fold(0.dp) { sum, kind -> sum + natural(kind, compact = false) }
        // Four cards, or full-size ones that don't fit, use the compact layouts
        val compact = widgets.size >= 4 || full + gaps > maxWidth
        val needed = widgets.fold(0.dp) { sum, kind -> sum + natural(kind, compact) }
        // Every card stretches by the same factor, so the row always fills its width: two cards
        // share it between them, four squeeze into their compact layouts
        val share = if (needed == 0.dp) 1f else (maxWidth - gaps) / needed
        // A lazy row clips its sides, which cut off a focused card's grown edge at either end.
        // So the row is laid out FocusRoom wider on each side, with that room as padding.
        LazyRow(
            modifier = Modifier.layout { measurable, constraints ->
                val room = FocusRoom.roundToPx()
                val row = measurable.measure(constraints.offset(horizontal = room * 2))
                layout(row.width - room * 2, row.height) { row.place(-room, 0) }
            },
            contentPadding = PaddingValues(horizontal = FocusRoom),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            userScrollEnabled = false,
            verticalAlignment = Alignment.Top,
        ) {
            items(widgets, key = { it.name }) { kind ->
                val item = Modifier
                    .animateItem(
                        fadeInSpec = tween(450, delayMillis = 120),
                        placementSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset(1, 1)),
                        fadeOutSpec = tween(220),
                    )
                    .width(animateDpAsState(natural(kind, compact) * share, tween(450, easing = FastOutSlowInEasing), label = "widgetWidth").value)
                if (kind == WidgetKind.Stack) StackCard(stack, stackPreview, content, compact, item)
                else content.Card(kind, compact, item)
            }
        }
    }
}

/** What every card needs, so a stack can draw any of them. */
private class WidgetContent(
    val weather: Weather?,
    val weatherError: String?,
    val mode: WeatherMode,
    val football: List<Fixtures>,
    val matchDay: MatchDayMode,
    val headlines: List<PickedHeadline>,
    val headlinesStatus: String?,
    val animate: Boolean,
    val onOpenClock: (() -> Unit)?,
    val onOpenWeather: (() -> Unit)?,
    val onOpenFootball: ((Team) -> Unit)?,
    val onOpenHeadline: ((Int) -> Unit)?,
    val moment: FootballMoment?,
    val prayers: PrayerTimes?,
    val prayersError: String?,
    val hasMosque: Boolean,
    val onOpenPrayers: (() -> Unit)?,
) {
    @Composable
    fun Card(kind: WidgetKind, compact: Boolean, modifier: Modifier) {
        when (kind) {
            WidgetKind.Clock -> ClockCard(compact, modifier, onOpenClock)
            WidgetKind.Weather -> WeatherCard(weather, weatherError, mode, compact, animate, modifier, onOpenWeather.takeIf { weather?.forecast != null })
            WidgetKind.Fennecs -> FootballCard(football, matchDay, compact, modifier, onOpenFootball, moment.takeIf { animate }, animate)
            WidgetKind.Daylight -> DaylightCard(weather, compact, modifier)
            WidgetKind.System -> SystemCard(compact, modifier)
            WidgetKind.Headlines -> HeadlinesCard(headlines, headlinesStatus, compact, modifier, onOpenHeadline)
            WidgetKind.Prayers -> PrayerCard(prayers, prayersError, hasMosque, compact, modifier, onOpenPrayers)
            WidgetKind.Stack -> Unit
        }
    }
}

/**
 * Inside a widget stack a card doesn't take focus itself: the stack does, shows the
 * card's focus, and passes OK on to it.
 */
private class StackSlot {
    var action by mutableStateOf<(() -> Unit)?>(null)
    var focused by mutableStateOf(false)
    /** Left / Right for the card on show (the football card's teams). */
    var keys by mutableStateOf<((KeyEvent) -> Boolean)?>(null)
}

private val LocalStackSlot = compositionLocalOf<StackSlot?> { null }

/**
 * Several widgets in one place, taking turns: every [StackTurnSec] seconds the next
 * slides up from below, with dots down the right edge saying which is showing. While it
 * has focus it stays put, Up / Down flips through by hand (Down from the last goes on to
 * Favorites), and OK opens the page of the card on show. [preview] holds one in view
 * while it is set up in Settings; a goal brings the football card forward.
 */
@Composable
private fun StackCard(stack: List<WidgetKind>, preview: WidgetKind?, content: WidgetContent, compact: Boolean, modifier: Modifier) {
    var index by remember { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }
    var focused by remember { mutableStateOf(false) }
    val slots = remember { mutableStateMapOf<WidgetKind, StackSlot>() }
    val count = stack.size
    val shown = stack.getOrNull(index.coerceIn(0, (count - 1).coerceAtLeast(0)))
    fun go(to: Int) {
        if (to == index || to !in 0 until count) return
        forward = to > index
        index = to
    }
    // Takes turns while nobody is looking at it closely
    LaunchedEffect(stack, focused, preview, content.moment, content.animate) {
        while (count > 1 && !focused && preview == null && content.moment == null) {
            delay(StackTurnSec * 1000L)
            forward = true
            index = (index + 1) % count
        }
    }
    LaunchedEffect(preview, stack) { preview?.let { stack.indexOf(it) }?.takeIf { it >= 0 }?.let(::go) }
    LaunchedEffect(content.moment, stack) { if (content.moment != null) stack.indexOf(WidgetKind.Fennecs).takeIf { it >= 0 }?.let(::go) }
    if (index >= count && count > 0) index = 0

    val lift by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "stackFocus")
    Box(
        modifier
            .height(WidgetHeight)
            .graphicsLayer {
                val s = 1f + 0.045f * lift
                scaleX = s
                scaleY = s
            }
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { event ->
                // Left / Right belong to the card on show when it uses them (the football card's teams)
                if (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) {
                    return@onPreviewKeyEvent shown?.let { slots[it]?.keys?.invoke(event) } == true
                }
                // Up / Down flip through the stack; past either end focus moves on as usual
                val step = when (event.key) {
                    Key.DirectionUp -> -1
                    Key.DirectionDown -> 1
                    else -> return@onPreviewKeyEvent false
                }
                val to = index + step
                if (to !in 0 until count) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyDown) go(to)
                true
            }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                shown?.let { slots[it]?.action?.invoke() }
            }
            .semantics { contentDescription = "Widget stack" + (shown?.let { ": ${it.title}" } ?: "") }
            .clip(CardShape),
    ) {
        if (shown == null) {
            WidgetCard(accent = NeutralTint, modifier = Modifier.fillMaxSize()) {
                Text(
                    "Choose its widgets in Settings → Widgets → Widget stack",
                    style = lineStyle(15.sp, Palette.TextPrimary),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        } else {
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    val enter = tween<IntOffset>(Motion.Page, easing = Motion.EnterEasing)
                    val exit = tween<IntOffset>(Motion.Exit + 120, easing = Motion.ExitEasing)
                    (slideInVertically(enter) { if (forward) it else -it } + fadeIn(tween(Motion.Enter, delayMillis = 60))) togetherWith
                        (slideOutVertically(exit) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(Motion.Exit))) using
                        SizeTransform(clip = true)
                },
                label = "stack",
                modifier = Modifier.fillMaxSize(),
            ) { kind ->
                val slot = slots.getOrPut(kind) { StackSlot() }
                SideEffect { slot.focused = focused }
                CompositionLocalProvider(LocalStackSlot provides slot) {
                    content.Card(kind, compact, Modifier.fillMaxSize())
                }
            }
        }
        if (count > 1) {
            StackDots(count, index.coerceIn(0, count - 1), Modifier.align(Alignment.CenterEnd).padding(end = 8.dp))
        }
    }
}

/** Which card of the stack is showing: a column of dots, the current one longer and brighter. */
@Composable
private fun StackDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(count) { i ->
            val on = i == current
            val h by animateDpAsState(if (on) 14.dp else 5.dp, Motion.glide(), label = "stackDot")
            val a by animateFloatAsState(if (on) 0.9f else 0.32f, tween(Motion.Quick), label = "stackDotAlpha")
            Box(Modifier.width(5.dp).height(h).background(Color.White.copy(alpha = a), CircleShape))
        }
    }
}

/**
 * Frosted glass with a soft wash of [accent] from the top left, a hairline edge, and a
 * small lift under a pointer. Settles in with a slight rise and scale when it appears.
 * With [onClick] it takes focus like a tile: it grows a little and gets a white edge,
 * and OK opens its page.
 */
@Composable
private fun WidgetCard(
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** How strong the wash is; brighter on a match day. */
    wash: Float = 0.2f,
    /** Extra wash on top, already animated (a goal's flash). */
    flash: Float = 0f,
    /** Keys for the card while it has focus (the football card's Left / Right). */
    onKey: ((KeyEvent) -> Boolean)? = null,
    /** Told when the card gains or loses focus. */
    onFocusChange: ((Boolean) -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var ownFocus by remember { mutableStateOf(false) }
    // In a stack, the stack holds focus and does the growing; the card shows the edge
    val slot = LocalStackSlot.current
    if (slot != null) SideEffect {
        slot.action = onClick
        slot.keys = onKey
    }
    val focused = slot?.focused ?: ownFocus
    LaunchedEffect(focused) { onFocusChange?.invoke(focused) }
    val lift by animateFloatAsState(if (hovered || focused) 1f else 0f, FocusSpring, label = "widgetLift")
    val focusLift by animateFloatAsState(if (focused) 1f else 0f, FocusSpring, label = "widgetFocus")
    val washColor by animateColorAsState(accent, tween(900), label = "widgetWash")
    val washAlpha by animateFloatAsState(wash, tween(900), label = "widgetWashAlpha")
    val appear = remember { Animatable(if (slot != null) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
    val grow = if (slot != null) 0f else 1f
    val rise = with(LocalDensity.current) { 10.dp.toPx() }

    Box(
        modifier
            .height(WidgetHeight)
            .graphicsLayer {
                val s = 0.95f + 0.05f * appear.value + (0.025f * lift + 0.02f * focusLift) * grow
                scaleX = s
                scaleY = s
                translationY = (1f - appear.value) * rise
            }
            .hoverable(interaction)
            .then(
                if (onClick == null || slot != null) Modifier
                else Modifier
                    .then(if (onKey != null) Modifier.onPreviewKeyEvent(onKey) else Modifier)
                    .onFocusChanged { ownFocus = it.isFocused }
                    .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            )
            .glass(CardShape)
            .background(Brush.linearGradient(listOf(washColor.copy(alpha = (washAlpha + flash + 0.08f * lift).coerceAtMost(1f)), Color.Transparent), start = Offset.Zero, end = Offset(520f, 360f)))
            .border((1.5f + 1f * focusLift).dp, Color.White.copy(alpha = 0.32f * lift + 0.6f * focusLift), CardShape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
private fun CardLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary) {
    Text(
        text.uppercase(),
        style = TextStyle(fontFamily = Flex, color = color, fontSize = 12.sp, fontWeight = FontWeight(650), letterSpacing = 1.3.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun lineStyle(size: TextUnit, color: Color = Palette.TextSecondary, weight: Int = 500) =
    TextStyle(fontFamily = Flex, color = color, fontSize = size, fontWeight = FontWeight(weight))

// ---- Clock -------------------------------------------------------------------------

/** Hours and minutes (no seconds) and the date. Ticks on each minute boundary. OK opens the clock page. */
@Composable
private fun ClockCard(compact: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    val hourMinute = remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", locale) }
    val amPm = remember(locale) { DateTimeFormatter.ofPattern(" a", locale) }
    // Locale-aware order and punctuation, e.g. "Wednesday, 28 Oct" or "Wed, Oct 28"
    val date = remember(locale, compact) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (compact) "EEEdMMM" else "EEEEdMMM"), locale)
    }
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val clock = buildAnnotatedString {
        append(now.format(hourMinute))
        withStyle(SpanStyle(fontFamily = Flex, fontSize = if (compact) 17.sp else 20.sp, color = Palette.TextSecondary, fontWeight = FontWeight(560), letterSpacing = 0.5.sp)) {
            if (!is24h) append(now.format(amPm))
        }
    }
    WidgetCard(LocalWallpaperTint.current.value, modifier, onClick) {
        Column(Modifier.align(Alignment.Center)) {
            Text(
                clock,
                style = TextStyle(fontFamily = FlexDisplay, color = Color.White, fontSize = if (compact) 46.sp else 54.sp, fontWeight = FontWeight(260), letterSpacing = (-1.2).sp),
                maxLines = 1,
            )
            Text(now.format(date), style = lineStyle(if (compact) 15.sp else 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---- Weather -----------------------------------------------------------------------

@Composable
private fun WeatherCard(weather: Weather?, error: String?, mode: WeatherMode, compact: Boolean, animate: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    WidgetCard(LocalWallpaperTint.current.value, modifier, onClick = onClick) {
        if (weather == null) {
            Column(Modifier.align(Alignment.Center)) {
                CardLabel("Weather")
                Spacer(Modifier.height(6.dp))
                Text(if (error != null) "Can't reach the weather service" else "Finding your area…", style = lineStyle(16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            WeatherWidget(weather, mode, compact, Modifier.align(Alignment.Center), animate = animate)
        }
    }
}

// ---- Football ----------------------------------------------------------------------

/**
 * The followed teams' fixtures, one team at a time: every 20 seconds the card moves to the
 * next team. Each shows its next fixture (competition, teams with crests, local kick-off
 * time) and its latest result with W / D / L. On a match day the card stays on the teams
 * playing that day and counts down to kick-off; a match being played shows its score and
 * a pulsing LIVE. Without data it says so; a saved copy is marked when it's old.
 */
@Composable
private fun FootballCard(
    football: List<Fixtures>,
    matchDay: MatchDayMode,
    compact: Boolean,
    modifier: Modifier,
    onClick: ((Team) -> Unit)?,
    moment: FootballMoment? = null,
    /** False on the idle screen, where the turn line is left out (nothing moves there). */
    animate: Boolean = true,
) {
    val zone = ZoneId.systemDefault()
    // Ticks every second while a countdown is on screen, otherwise twice a minute
    val now by produceState(Instant.now(), football, matchDay) {
        while (true) {
            value = Instant.now()
            val counting = football.any { f ->
                matchDayApplies(f.team, matchDay) && matchPhase(f, value, zone).let { it == MatchPhase.Today || it == MatchPhase.Soon }
            }
            delay(if (counting) 1000L - System.currentTimeMillis() % 1000L else 30_000L)
        }
    }
    val shown = cardTeams(football, matchDay, now, zone)
    // Which team is on show. Every 20 seconds the next takes over, a line along the bottom
    // filling up until then; while the card has focus it waits, and Left / Right flip by hand.
    var turn by remember { mutableIntStateOf(0) }
    var cardFocused by remember { mutableStateOf(false) }
    val count = shown.size
    val index = if (count == 0) 0 else turn.mod(count)
    var turnProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(count, turn, cardFocused, moment) {
        if (count < 2 || moment != null) {
            turnProgress = 0f
            return@LaunchedEffect
        }
        if (cardFocused) return@LaunchedEffect
        // Frame by frame from where it got to; an endless animation as far as Compose is concerned
        val from = turnProgress
        var start = -1L
        while (true) {
            val p = withInfiniteAnimationFrameMillis { ms ->
                if (start < 0) start = ms
                from + (ms - start) / TeamTurnMs.toFloat()
            }
            if (p >= 1f) {
                turnProgress = 0f
                turn++
                break
            }
            turnProgress = p
        }
    }
    val flip = { by: Int ->
        val to = index + by
        if (count > 1 && to in 0 until count) {
            turn = to
            true
        } else {
            false
        }
    }
    val keys: (KeyEvent) -> Boolean = { event ->
        val by = when (event.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            else -> 0
        }
        // Past the first or last team, Left / Right move on to the next widget as usual
        when {
            by == 0 || count < 2 || (index + by) !in 0 until count -> false
            event.type == KeyEventType.KeyDown -> {
                flip(by)
                turnProgress = 0f
                true
            }
            else -> true
        }
    }
    // A goal or a win brings its team forward for as long as it lasts
    val celebrated = moment?.let { m -> football.firstOrNull { it.team.id == m.team.id } }
    val current = celebrated ?: shown.getOrNull(index)
    val phase = current?.takeIf { matchDayApplies(it.team, matchDay) }?.let { matchPhase(it, now, zone) } ?: MatchPhase.None
    val countdown = phase == MatchPhase.Today || phase == MatchPhase.Soon
    // The green flash: up at once on a goal, then easing back over a few seconds
    val flash = remember { Animatable(0f) }
    LaunchedEffect(moment) {
        if (moment == null) { flash.snapTo(0f); return@LaunchedEffect }
        repeat(if (moment.kind == MomentKind.Goal) 2 else 1) {
            flash.animateTo(1f, tween(180, easing = Motion.EnterEasing))
            flash.animateTo(0.35f, tween(700, easing = Motion.ExitEasing))
        }
        flash.animateTo(0f, tween(2400, easing = LinearEasing))
    }
    WidgetCard(
        accent = if (moment != null) GoalGreen else if (phase == MatchPhase.Live) AlgeriaRed else AlgeriaGreen,
        modifier = modifier,
        onClick = current?.let { c -> onClick?.let { open -> { open(c.team) } } },
        wash = if (countdown || phase == MatchPhase.Live || moment != null) 0.42f else 0.2f,
        flash = 0.5f * flash.value,
        onKey = keys,
        onFocusChange = { cardFocused = it },
    ) {
        Crossfade(current, animationSpec = tween(600), label = "footballTeam", modifier = Modifier.align(Alignment.Center)) { f ->
            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.CenterStart) {
                when {
                    f == null -> Text("Loading fixtures…", style = lineStyle(15.sp, Palette.TextPrimary), maxLines = 1)
                    moment != null && f.team.id == moment.team.id -> MomentFixtures(moment, f, now, compact)
                    else -> TeamFixtures(f, if (f.team.id == current?.team?.id) phase else MatchPhase.None, now, compact)
                }
            }
        }
        if (moment?.kind == MomentKind.Win) Confetti(moment.at, Modifier.matchParentSize())
        if (count > 1 && moment == null) {
            PageDots(count, index, Modifier.align(Alignment.TopEnd))
            if (animate) TurnLine({ turnProgress }, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
        }
    }
}

/** How long each followed team stays on the football card. */
private const val TeamTurnMs = 20_000

/**
 * A hairline along the bottom of the card filling up until the next team takes over.
 * Read in the draw phase, so it moves without recomposing the card.
 */
@Composable
private fun TurnLine(progress: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val r = size.height / 2
        drawRoundRect(Color(0x24FFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        val p = progress().coerceIn(0f, 1f)
        if (p > 0f) {
            drawRoundRect(
                Color(0x99FFFFFF),
                size = Size(size.width * p, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
    }
}

private val GoalGreen = Color(0xFF2B8A3E)
private val GoalText = Color(0xFF8CE99A)

/**
 * The card during a goal or a win: the same three lines as a live match, so nothing
 * shifts. The label says what happened, the score flips to the new one, and the last
 * line names the team and minute. Every line is one line, cut short with an ellipsis.
 */
@Composable
private fun MomentFixtures(m: FootballMoment, f: Fixtures, now: Instant, compact: Boolean) {
    val match = listOfNotNull(f.last, f.next).firstOrNull { it.id == m.match.id } ?: m.match
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        CardLabel(
            when (m.kind) {
                MomentKind.Goal -> if (compact) "Goal" else "Goal · " + shortCompetition(match.competition)
                MomentKind.Win -> if (compact) "Full time" else "Full time · " + shortCompetition(match.competition)
            },
            color = GoalText,
        )
        Teams(match, compact, showScore = true)
        Text(
            momentText(m, compact),
            style = lineStyle(14.sp, GoalText, 640),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A short burst of confetti for a win, falling from the top of the card and fading out
 * within two seconds. It is drawn inside the card, so none of it leaves the card's edge.
 */
@Composable
private fun Confetti(seed: Instant, modifier: Modifier) {
    val progress = remember(seed) { Animatable(0f) }
    LaunchedEffect(seed) { progress.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val pieces = remember(seed) {
        val r = Random(seed.toEpochMilli())
        List(26) { Triple(r.nextFloat(), r.nextFloat() * 0.35f, r.nextInt(ConfettiColors.size)) to (r.nextFloat() * 2f - 1f) }
    }
    if (progress.value >= 1f) return
    Canvas(modifier.clipToBounds()) {
        val t = progress.value
        val fade = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
        for ((piece, sway) in pieces) {
            val (x, delayAt, colour) = piece
            val local = ((t - delayAt) / (1f - delayAt)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val cx = size.width * x + sway * 18.dp.toPx() * sin(local * PI.toFloat() * 2f)
            val cy = -6.dp.toPx() + (size.height + 12.dp.toPx()) * local
            val w = 6.dp.toPx()
            val h = 3.dp.toPx()
            drawRect(
                ConfettiColors[colour].copy(alpha = 0.9f * fade),
                topLeft = Offset(cx - w / 2, cy - h / 2),
                size = Size(w, h * (0.6f + 0.4f * cos(local * 9f + x * 6f).let { it * it })),
            )
        }
    }
}

private val ConfettiColors = listOf(Color(0xFF51CF66), Color.White, Color(0xFFFFD43B), Color(0xFFFF6B6B), Color(0xFF74C0FC))

@Composable
private fun TeamFixtures(f: Fixtures, phase: MatchPhase, now: Instant, compact: Boolean) {
    val live = listOfNotNull(f.last, f.next).firstOrNull { it.isLive(now) }
    val upcoming = f.next?.takeIf { it != live }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        when {
            live != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                    CardLabel(if (compact) "Live" else "Live · " + shortCompetition(live.competition), color = Color(0xFFFF8A9B))
                }
                Teams(live, compact, showScore = true)
                Text(
                    (if (f.team.isAlgeria) "Les Fennecs" else f.team.name) + " are playing now",
                    style = lineStyle(14.sp, Palette.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            upcoming != null && (phase == MatchPhase.Today || phase == MatchPhase.Soon) -> MatchDayCountdown(upcoming, now, compact)
            upcoming != null -> {
                CardLabel(if (compact) "Next" else "Next · ${shortCompetition(upcoming.competition)}")
                Teams(upcoming, compact, showScore = false)
                Text(kickoffText(upcoming.kickoff, compact), style = lineStyle(15.sp, Palette.TextPrimary, 560), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            else -> {
                CardLabel(if (f.team.isAlgeria) "Les Fennecs" else f.team.name)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Crest(Side(f.team.id, f.team.name, f.team.badge), 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        when {
                            f.fetchedAt != Instant.EPOCH -> "No match scheduled yet"
                            f.error != null -> "Can't reach match data · see Settings"
                            else -> "Loading fixtures…"
                        },
                        style = lineStyle(15.sp, Palette.TextPrimary),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (phase != MatchPhase.Today && phase != MatchPhase.Soon) {
            f.last?.takeIf { it != live && it.outcome != null }?.let { LastResult(it, compact) }
        }
        if (f.fromCache && f.fetchedAt != Instant.EPOCH && Duration.between(f.fetchedAt, now) > Duration.ofHours(6)) {
            Text("Offline · saved " + savedText(f.fetchedAt), style = lineStyle(12.sp, Palette.TextTertiary), maxLines = 1)
        }
    }
}

/** Match day: "Kick-off in", the time left counting down each second, and who plays when. */
@Composable
private fun MatchDayCountdown(match: Match, now: Instant, compact: Boolean) {
    val left = Duration.between(now, match.kickoff ?: now).coerceAtLeast(Duration.ZERO)
    val h = left.toHours()
    val m = left.toMinutes() % 60
    val sec = left.seconds % 60
    val time = if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(Color(0xFF8CE99A), CircleShape))
        Spacer(Modifier.width(6.dp))
        CardLabel(if (compact) "Kick-off in" else "Match day · kick-off in", color = Color(0xFF8CE99A))
    }
    Text(
        time,
        style = TextStyle(fontFamily = FlexDisplay, color = Color.White, fontSize = if (compact) 30.sp else 34.sp, fontWeight = FontWeight(300), fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
    Text(
        "${shortCode(match.home.name)} v ${shortCode(match.away.name)} · " + kickoffText(match.kickoff, compact).substringAfter(" · "),
        style = lineStyle(14.sp, Palette.TextPrimary, 600),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Which of [count] pages is showing, as small dots. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(5.dp).background(Color.White.copy(alpha = if (i == current) 0.85f else 0.28f), CircleShape))
        }
    }
}

@Composable
private fun Teams(match: Match, compact: Boolean, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crest(match.home, 24.dp)
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        val home = if (compact) shortCode(match.home.name) else match.home.name
        val away = if (compact) shortCode(match.away.name) else match.away.name
        val style = lineStyle(if (compact) 17.sp else 19.sp, Palette.TextPrimary, 640)
        val gap = if (compact) 4.dp else 7.dp
        // Each name gives way (with an ellipsis) before the score does, so long names never push it out
        Text(home, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(gap))
        if (showScore && match.homeScore != null && match.awayScore != null) {
            FlipNumber(match.homeScore, style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(700)))
            Text("–", style = style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(700)))
            FlipNumber(match.awayScore, style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(700)))
        } else {
            Text("v", style = style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(500)))
        }
        Spacer(Modifier.width(gap))
        Text(away, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Crest(match.away, 24.dp)
    }
}

/** A score that flips to its new value: the old number slides up and out, the new one up and in, inside its own box. */
@Composable
private fun FlipNumber(value: Int, style: TextStyle) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(Motion.Enter, easing = Motion.EnterEasing)) { if (up) it else -it } + fadeIn(tween(Motion.Quick))) togetherWith
                (slideOutVertically(tween(Motion.Exit, easing = Motion.ExitEasing)) { if (up) -it else it } + fadeOut(tween(Motion.Quick))) using
                SizeTransform(clip = true)
        },
        label = "score",
        modifier = Modifier.clipToBounds(),
    ) { n ->
        Text("$n", style = style.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun LastResult(match: Match, compact: Boolean) {
    val outcome = match.outcome ?: return
    val color = when (outcome) { 'W' -> Color(0xFF51CF66); 'L' -> Color(0xFFFF6B6B); else -> Color(0xFFFFD43B) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(outcome.toString(), style = lineStyle(11.sp, Palette.Ink, 800))
        }
        Spacer(Modifier.width(7.dp))
        val opponent = if (compact) shortCode(match.opponent.name) else match.opponent.name
        Text(
            "${if (compact) "" else "Last  "}${match.ourScore}–${match.opponentScore} v $opponent",
            style = lineStyle(14.sp, Palette.TextTertiary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LiveDot() {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "livePulse",
    )
    Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.background(Color(0xFFFF4D6D), CircleShape))
}

/** "Today · 18:00", "Tomorrow · 18:00" or "Wed 11 Nov · 18:00", in the TV's time zone. */
@Composable
fun kickoffText(kickoff: Instant?, compact: Boolean): String {
    if (kickoff == null) return "Date to be confirmed"
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val local = kickoff.atZone(zone)
    val time = local.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale))
    val days = Duration.between(LocalDate.now(zone).atStartOfDay(zone), local.toLocalDate().atStartOfDay(zone)).toDays()
    val day = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> local.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (compact) "dMMM" else "EEEdMMM"), locale))
    }
    return "$day · $time"
}

private fun savedText(at: Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), Locale.getDefault()))

/** The team's crest from the feed; until it loads (or offline), a flag or initials. */
@Composable
fun Crest(side: Side, size: Dp) {
    var loaded by remember(side.badge) { mutableStateOf(false) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!loaded) {
            if (side.isAlgeria) {
                AlgeriaFlag(size)
            } else {
                Box(
                    Modifier.size(size).background(Color(0x33FFFFFF), CircleShape).border(1.dp, Color(0x40FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(side.name.take(1).uppercase(), style = lineStyle(12.sp, Palette.TextPrimary, 700))
                }
            }
        }
        if (side.badge != null) {
            AsyncImage(model = side.badge, contentDescription = side.name, onSuccess = { loaded = true }, modifier = Modifier.size(size))
        }
    }
}

/** Algeria's flag in a disc: green and white halves, the red crescent and star. */
@Composable
fun AlgeriaFlag(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(CircleShape)) {
        val w = this.size.width
        val h = this.size.height
        drawRect(AlgeriaGreen, size = Size(w / 2, h))
        drawRect(Color.White, topLeft = Offset(w / 2, 0f), size = Size(w / 2, h))
        val c = Offset(w * 0.5f, h * 0.5f)
        val outer = h * 0.25f
        val crescent = Path().apply {
            op(
                Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, outer)) },
                Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(outer * 0.25f, 0f), outer * 0.8f)) },
                PathOperation.Difference,
            )
        }
        drawPath(crescent, AlgeriaRed)
        drawPath(star(c + Offset(outer * 0.42f, 0f), outer * 0.42f), AlgeriaRed)
        drawCircle(Color(0x33000000), radius = h / 2, style = Stroke(1.dp.toPx()))
    }
}

private fun star(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.4f
        // Pointing left, towards the crescent's opening, as on the flag
        val angle = PI + i * PI / 5
        val p = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

// ---- Headlines ---------------------------------------------------------------------

/**
 * One headline at a time from the picked list, a new one every 20 seconds, with its topic
 * and source above it and its age below. A thin line along the bottom fills up until the
 * next one. OK opens the story's summary.
 */
@Composable
private fun HeadlinesCard(picked: List<PickedHeadline>, status: String?, compact: Boolean, modifier: Modifier, onClick: ((Int) -> Unit)?) {
    var index by remember(picked) { mutableIntStateOf(0) }
    LaunchedEffect(picked) {
        while (picked.size > 1) {
            delay(20_000)
            index = (index + 1) % picked.size
        }
    }
    val item = picked.getOrNull(index)
    WidgetCard(Color(0xFFFF8787), modifier, onClick = item?.let { onClick?.let { open -> { open(index) } } }) {
        if (item == null) {
            Column(Modifier.align(Alignment.CenterStart)) {
                CardLabel("Headlines")
                Spacer(Modifier.height(6.dp))
                Text(status ?: "Loading headlines…", style = lineStyle(15.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Crossfade(item, animationSpec = tween(500), label = "headline", modifier = Modifier.align(Alignment.CenterStart)) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val topic = shown.topic?.let { if (compact && it == NewsTopic.World) "World" else it.title } ?: "Top stories"
                    CardLabel(if (compact) topic else "$topic · ${shown.headline.source.title}", color = Color(0xFFFFC9C9))
                    Text(
                        shown.headline.title,
                        style = lineStyle(if (compact) 15.sp else 16.sp, Palette.TextPrimary, 600).copy(lineHeight = if (compact) 18.sp else 20.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            if (compact) shown.headline.source.title else null,
                            shown.headline.published?.let { ageText(it) },
                            if (!compact && picked.size > 1) "${picked.indexOf(shown) + 1} of ${picked.size}" else null,
                        ).joinToString(" · "),
                        style = lineStyle(12.sp, Palette.TextTertiary),
                        maxLines = 1,
                    )
                }
            }
            if (picked.size > 1) {
                // Restarts with each headline; an infinite transition, so it never holds anything up
                key(index) {
                    val progress by rememberInfiniteTransition(label = "headlineTimer").animateFloat(
                        0f, 1f, infiniteRepeatable(tween(20_000, easing = LinearEasing), RepeatMode.Restart), label = "headlineProgress",
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(2.dp)
                            .graphicsLayer {
                                scaleX = progress
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(Color.White.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }
        }
    }
}

/** "Just now", "12 min ago", "3 h ago", "2 days ago". */
fun ageText(at: Instant, now: Instant = Instant.now()): String {
    val d = Duration.between(at, now)
    return when {
        d.toMinutes() < 1 -> "Just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        d.toDays() == 1L -> "Yesterday"
        else -> "${d.toDays()} days ago"
    }
}

// ---- Prayer times ------------------------------------------------------------------

/** Today's prayers from the mosque on MAWAQIT; OK opens the prayer page (also without times, to say why). */
@Composable
private fun PrayerCard(times: PrayerTimes?, error: String?, hasMosque: Boolean, compact: Boolean, modifier: Modifier, onClick: (() -> Unit)?) {
    WidgetCard(PrayerGreen, modifier, onClick = onClick) {
        if (times == null) PrayerEmpty(error, hasMosque, Modifier.align(Alignment.CenterStart))
        else PrayerWidget(times, compact, Modifier.align(Alignment.CenterStart))
    }
}

// ---- Sunrise & sunset --------------------------------------------------------------

/** Today's sunrise and sunset, worked out on the TV from the weather's location. */
@Composable
private fun DaylightCard(weather: Weather?, compact: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            value = ZonedDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val lat = weather?.lat ?: Double.NaN
    val lon = weather?.lon ?: Double.NaN
    val times = remember(lat, lon, now.toLocalDate()) {
        if (lat.isNaN() || lon.isNaN()) null else sunTimes(lat, lon, now.toLocalDate(), now.zone)
    }
    val format = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault())
    WidgetCard(Color(0xFFFFA94D), modifier) {
        Column(Modifier.align(Alignment.Center)) {
            CardLabel(if (compact) "Daylight" else "Sunrise & sunset")
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val fraction = times?.let {
                    val day = Duration.between(it.sunrise, it.sunset).seconds.toFloat()
                    (Duration.between(it.sunrise, now).seconds / day)
                }
                SunArc(fraction, Modifier.size(width = if (compact) 38.dp else 50.dp, height = if (compact) 28.dp else 34.dp))
                Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
                Column {
                    if (times == null) {
                        Text(if (weather == null) "Finding your area…" else "No sunrise today", style = lineStyle(15.sp), maxLines = 2)
                    } else {
                        val size = if (compact) 16.sp else 18.sp
                        Text("↑ " + times.sunrise.format(format), style = lineStyle(size, Palette.TextPrimary, 600), maxLines = 1)
                        Text("↓ " + times.sunset.format(format), style = lineStyle(size, Palette.TextSecondary, 600), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** The day as an arc from sunrise to sunset, with the sun at [fraction] along it (dim below the horizon). */
@Composable
private fun SunArc(fraction: Float?, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.dp.toPx()
        val r = w / 2 - stroke
        val base = h - 4.dp.toPx()
        drawArc(
            Color(0x66FFFFFF),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w / 2 - r, base - r),
            size = Size(r * 2, r * 2),
            style = Stroke(stroke, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))),
        )
        drawLine(Color(0x55FFFFFF), Offset(0f, base), Offset(w, base), stroke / 2)
        if (fraction != null) {
            val up = fraction in 0f..1f
            val t = fraction.coerceIn(0f, 1f)
            val angle = PI * (1 - t)
            val sun = Offset(w / 2 + (r * cos(angle)).toFloat(), base - (r * sin(angle)).toFloat())
            if (up) drawCircle(Color(0x55FFD43B), radius = 9.dp.toPx(), center = sun)
            drawCircle(if (up) Color(0xFFFFD43B) else Color(0x88FFFFFF), radius = 5.dp.toPx(), center = sun)
        }
    }
}

// ---- System info -------------------------------------------------------------------

/** Free and total space of storage and memory, in bytes. */
private data class Usage(val storageFree: Long, val storageTotal: Long, val memoryFree: Long, val memoryTotal: Long)

/**
 * What's worth glancing at about the TV: free storage and memory as bars (amber when
 * running low), the connection with VPN, and the Android version. Settings → TV & system
 * has the matching shortcuts (Free up memory, Storage, System update). Checked every
 * 10 seconds.
 */
@Composable
private fun SystemCard(compact: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val network by rememberNetworkStatus()
    val usage by produceState<Usage?>(null) {
        while (true) {
            value = withContext(Dispatchers.IO) { readUsage(context) }
            delay(10_000)
        }
    }
    val online = network.link != Link.Offline
    WidgetCard(if (online) Color(0xFF38D9A9) else Color(0xFFFF8787), modifier) {
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The icons say how it's connected (Wi-Fi strength or Ethernet, crossed out offline; a key for VPN)
                WifiIcon(network, if (online) Color.White else Color(0xFFFF8787), 16.dp)
                if (network.vpn) {
                    Spacer(Modifier.width(4.dp))
                    KeyIcon(Color(0xFF8CE99A), 16.dp)
                }
                Spacer(Modifier.width(7.dp))
                CardLabel(
                    if (compact) "System" else "System · Android ${android.os.Build.VERSION.RELEASE}",
                    color = if (online) Palette.TextTertiary else Color(0xFFFF8787),
                )
            }
            usage?.let { u ->
                UsageBar("Storage", u.storageFree, u.storageTotal)
                UsageBar("Memory", u.memoryFree, u.memoryTotal)
            }
        }
    }
}

/** "Storage  12 GB free" over a thin bar of how full it is; amber under 10% free. */
@Composable
private fun UsageBar(label: String, free: Long, total: Long) {
    if (total <= 0 || free < 0) return
    val used = (1f - free.toFloat() / total).coerceIn(0f, 1f)
    val low = free.toFloat() / total < 0.1f
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = lineStyle(14.sp, Palette.TextSecondary), maxLines = 1)
            Spacer(Modifier.weight(1f))
            Text(formatBytes(free) + " free", style = lineStyle(14.sp, if (low) Color(0xFFFFC078) else Palette.TextPrimary, 600), maxLines = 1)
        }
        Spacer(Modifier.height(3.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x33FFFFFF))) {
            Box(Modifier.fillMaxWidth(used).height(4.dp).background(if (low) Color(0xFFFFC078) else Color.White, RoundedCornerShape(2.dp)))
        }
    }
}

private fun readUsage(context: Context): Usage {
    val stat = runCatching { StatFs(Environment.getDataDirectory().path) }.getOrNull()
    val memory = ActivityManager.MemoryInfo().also { info ->
        runCatching { context.getSystemService(ActivityManager::class.java).getMemoryInfo(info) }
    }
    return Usage(
        storageFree = stat?.availableBytes ?: -1,
        storageTotal = stat?.totalBytes ?: -1,
        memoryFree = memory.availMem,
        memoryTotal = memory.totalMem,
    )
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1e9
    return if (gb >= 10) "%.0f GB".format(gb) else if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1e6)
}
