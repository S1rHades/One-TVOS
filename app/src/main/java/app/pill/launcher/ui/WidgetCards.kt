package app.pill.launcher.ui

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.isSpecified
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.staticCompositionLocalOf
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import app.pill.launcher.MatchDayMode
import app.pill.launcher.NewsTopic
import app.pill.launcher.PickedHeadline
import app.pill.launcher.Team
import app.pill.launcher.Weather
import app.pill.launcher.WeatherMode
import app.pill.launcher.PrayerTimes
import app.pill.launcher.nextPrayer
import app.pill.launcher.Prayer
import app.pill.launcher.WidgetKind
import app.pill.launcher.FootballMoment
import androidx.compose.foundation.focusable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** All header cards share this height, so the row reads as one band. */
val WidgetHeight = 112.dp
internal val WidgetCardShape = RoundedCornerShape(26.dp)
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
    /** Holding OK (or Menu) on a card: its menu, told whether OK is still down. */
    onWidgetMenu: ((WidgetKind, okHeld: Boolean) -> Unit)? = null,
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
                CompositionLocalProvider(LocalWidgetMenu provides onWidgetMenu?.let { menu -> { held: Boolean -> menu(kind, held) } }) {
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
}

/** What every card needs, so a stack can draw any of them. */
internal class WidgetContent(
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
 * Frosted glass with a soft wash of [accent] from the top left, a hairline edge, and a
 * small lift under a pointer. Settles in with a slight rise and scale when it appears.
 * With [onClick] it takes focus like a tile: it grows a little and gets a white edge,
 * and OK opens its page.
 */
@Composable
internal fun WidgetCard(
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
    // Holding OK (or Menu) opens the widget's own menu, as it does on an app
    val onMenu = LocalWidgetMenu.current
    val tile = rememberTileInput(
        onOpen = { onClick?.invoke() },
        onMenu = { held -> onMenu?.invoke(held) },
        onFocus = { ownFocus = it },
    )
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
                    .then(
                        if (onMenu != null) tile.modifier
                        else Modifier
                            .onFocusChanged { ownFocus = it.isFocused }
                            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
                    ),
            )
            .glass(WidgetCardShape)
            .background(Brush.linearGradient(listOf(washColor.copy(alpha = (washAlpha + flash + 0.08f * lift).coerceAtMost(1f)), Color.Transparent), start = Offset.Zero, end = Offset(520f, 360f)))
            .border((1.5f + 1f * focusLift).dp, Color.White.copy(alpha = 0.32f * lift + 0.6f * focusLift), WidgetCardShape)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        content = content,
    )
}

@Composable
internal fun CardLabel(text: String, modifier: Modifier = Modifier, color: Color = Palette.TextTertiary, shorter: List<String> = emptyList()) {
    FitText(
        text.uppercase(),
        shorter = shorter.map { it.uppercase() },
        modifier = modifier.padding(end = LocalLabelEndRoom.current),
        style = TextStyle(fontFamily = Flex, color = color, fontSize = 12.sp, fontWeight = FontWeight(650), letterSpacing = 1.3.sp),
        maxLines = 1,
    )
}

internal fun lineStyle(size: TextUnit, color: Color = Palette.TextSecondary, weight: Int = 500) =
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
            FitText(
                now.format(date),
                style = lineStyle(if (compact) 15.sp else 17.sp),
                shorter = listOf("EEE, MMM d", "MMM d").map { now.format(DateTimeFormatter.ofPattern(it, Locale.getDefault())) },
            )
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
                FitText(
                    if (error != null) "Can't reach the weather service" else "Finding your area…",
                    style = lineStyle(16.sp),
                    maxLines = 2,
                    shorter = if (error != null) listOf("No weather right now", "Offline") else listOf("Finding…"),
                )
            }
        } else {
            WeatherWidget(weather, mode, compact, Modifier.align(Alignment.Center), animate = animate)
        }
    }
}

// ---- Football ----------------------------------------------------------------------

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
                FitText(status ?: "Loading headlines…", style = lineStyle(15.sp), maxLines = 2, shorter = listOf(if (status == null) "Loading…" else "No headlines"))
            }
        } else {
            Crossfade(item, animationSpec = tween(500), label = "headline", modifier = Modifier.align(Alignment.CenterStart)) { shown ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    val topic = shown.topic?.let { if (compact && it == NewsTopic.World) "World" else it.title } ?: "Top stories"
                    CardLabel("$topic · ${shown.headline.source.title}", color = Color(0xFFFFC9C9), shorter = listOf(topic))
                    FitText(
                        shown.headline.title,
                        style = lineStyle(if (compact) 15.sp else 16.sp, Palette.TextPrimary, 600).copy(lineHeight = if (compact) 18.sp else 20.sp),
                        maxLines = 2,
                    )
                    FitText(
                        listOfNotNull(
                            if (compact) shown.headline.source.title else null,
                            shown.headline.published?.let { ageText(it) },
                            if (!compact && picked.size > 1) "${picked.indexOf(shown) + 1} of ${picked.size}" else null,
                        ).joinToString(" · "),
                        style = lineStyle(12.sp, Palette.TextTertiary),
                        shorter = listOfNotNull(shown.headline.published?.let { ageText(it) }, ""),
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
    // Left / Right on the focused card step through the day's prayers; it goes back to the
    // next prayer ten seconds after the last press, or when focus moves on
    var picked by remember { mutableStateOf<Prayer?>(null) }
    var presses by remember { mutableIntStateOf(0) }
    LaunchedEffect(presses) {
        if (presses == 0) return@LaunchedEffect
        delay(PrayerPickMs)
        picked = null
    }
    val keys: (KeyEvent) -> Boolean = keys@{ event ->
        val day = times ?: return@keys false
        val list = day.list.map { it.first }
        val by = when (event.key) {
            Key.DirectionLeft -> -1
            Key.DirectionRight -> 1
            else -> return@keys false
        }
        // From the picked one, else the next; after Isha (next is tomorrow's Fajr), Left goes back to Isha
        val from = picked?.let { list.indexOf(it) }
            ?: nextPrayer(day, ZonedDateTime.now())?.let { n -> if (n.tomorrow) list.size else list.indexOf(n.prayer) }
            ?: 0
        val to = from + by
        // Past the first or last prayer, Left / Right move on to the next widget as usual
        if (to !in list.indices) return@keys false
        if (event.type == KeyEventType.KeyDown) {
            picked = list[to]
            presses++
        }
        true
    }
    WidgetCard(
        PrayerGreen,
        modifier,
        onClick = onClick,
        onKey = if (times != null) keys else null,
        onFocusChange = { focused -> if (!focused) picked = null },
    ) {
        if (times == null) PrayerEmpty(error, hasMosque, Modifier.align(Alignment.CenterStart))
        else PrayerWidget(times, compact, Modifier.align(Alignment.CenterStart), picked = picked)
    }
}

/** How long a prayer picked with Left / Right stays before the card goes back to the next one. */
private const val PrayerPickMs = 10_000L

// ---- Sunrise & sunset --------------------------------------------------------------

/** The menu for the widget being drawn (hold OK or Menu on it); null where there is none, as inside a stack. */
val LocalWidgetMenu = staticCompositionLocalOf<((okHeld: Boolean) -> Unit)?> { null }

/**
 * A widget's line of text laid out for the room the card has: [text], or when that won't fit,
 * the first of [shorter] that does (most important first, so a narrow card keeps what
 * matters most and drops the rest, never cutting with "…"). Each one is measured first, at
 * its size and then a little smaller (down to [minScale]); an empty string means the line
 * may go altogether. When nothing fits, the last one is cut at a whole word.
 */
@Composable
internal fun FitText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    softWrap: Boolean = maxLines > 1,
    shorter: List<String> = emptyList(),
    minScale: Float = 0.8f,
) {
    val base = style.merge(TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight))
    val size = if (base.fontSize.isSpecified) base.fontSize else 16.sp
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier) {
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val choice = remember(text, shorter, base, width, maxLines, softWrap) {
            fun fits(t: String, scale: Float): Boolean = t.isEmpty() || !measurer.measure(
                t,
                base.copy(fontSize = size * scale),
                overflow = TextOverflow.Clip,
                softWrap = softWrap,
                maxLines = maxLines,
                constraints = Constraints(maxWidth = width),
            ).hasVisualOverflow
            val options = (listOf(text) + shorter).distinct()
            val scales = listOf(1f, 0.92f, minScale)
            options.firstNotNullOfOrNull { t -> scales.firstOrNull { fits(t, it) }?.let { t to it } }
                ?: run {
                    // Nothing fits even small: the last option, to its last whole word that does
                    val words = options.last().split(' ')
                    (words.size - 1 downTo 1).asSequence().map { words.take(it).joinToString(" ") }
                        .firstOrNull { fits(it, minScale) }?.let { it to minScale } ?: ("" to 1f)
                }
        }
        if (choice.first.isNotEmpty()) {
            Text(
                choice.first,
                style = base.copy(fontSize = size * choice.second),
                maxLines = maxLines,
                softWrap = softWrap,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Room a card's top line leaves at its end, for something drawn there (the football card's team dots). */
internal val LocalLabelEndRoom = staticCompositionLocalOf { 0.dp }
