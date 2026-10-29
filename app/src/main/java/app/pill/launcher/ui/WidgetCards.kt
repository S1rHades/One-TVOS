package app.pill.launcher.ui

import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
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
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.Fixtures
import app.pill.launcher.Match
import app.pill.launcher.Side
import app.pill.launcher.Sky
import app.pill.launcher.Weather
import app.pill.launcher.WeatherMode
import app.pill.launcher.WidgetKind
import app.pill.launcher.shortCode
import app.pill.launcher.shortCompetition
import app.pill.launcher.skyFor
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

/** Widths each card is designed for, roomy and compact. The row scales them to fit. */
private fun naturalWidth(kind: WidgetKind, compact: Boolean): Dp = when (kind) {
    WidgetKind.Clock -> if (compact) 146.dp else 220.dp
    WidgetKind.Weather -> if (compact) 160.dp else 240.dp
    WidgetKind.Fennecs -> if (compact) 204.dp else 270.dp
    WidgetKind.Daylight -> if (compact) 146.dp else 200.dp
    WidgetKind.System -> if (compact) 176.dp else 220.dp
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
    fixtures: Fixtures?,
    fixturesError: String?,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val gaps = Gap * (widgets.size - 1).coerceAtLeast(0)
        // The widths are designed at the default text size; a larger TV text size needs more
        val textScale = LocalDensity.current.fontScale.coerceAtLeast(1f)
        fun natural(kind: WidgetKind, compact: Boolean) = naturalWidth(kind, compact) * textScale
        val full = widgets.fold(0.dp) { sum, kind -> sum + natural(kind, compact = false) }
        // Four cards, or full-size ones that don't fit, use the compact layouts
        val compact = widgets.size >= 4 || full + gaps > maxWidth
        val needed = widgets.fold(0.dp) { sum, kind -> sum + natural(kind, compact) }
        // Every card stretches by the same factor, so the row always fills its width: two cards
        // share it between them, four squeeze into their compact layouts
        val share = if (needed == 0.dp) 1f else (maxWidth - gaps) / needed
        LazyRow(
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
                when (kind) {
                    WidgetKind.Clock -> ClockCard(compact, item)
                    WidgetKind.Weather -> WeatherCard(weather, weatherError, mode, compact, animate, item)
                    WidgetKind.Fennecs -> FennecsCard(fixtures, fixturesError, compact, item)
                    WidgetKind.Daylight -> DaylightCard(weather, compact, item)
                    WidgetKind.System -> SystemCard(compact, item)
                }
            }
        }
    }
}

/**
 * Frosted glass with a soft wash of [accent] from the top left, a hairline edge, and a
 * small lift under a pointer. Settles in with a slight rise and scale when it appears.
 */
@Composable
private fun WidgetCard(accent: Color, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val lift by animateFloatAsState(if (hovered) 1f else 0f, FocusSpring, label = "widgetLift")
    val wash by animateColorAsState(accent, tween(900), label = "widgetWash")
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
    val rise = with(LocalDensity.current) { 10.dp.toPx() }

    Box(
        modifier
            .height(WidgetHeight)
            .graphicsLayer {
                val s = 0.95f + 0.05f * appear.value + 0.025f * lift
                scaleX = s
                scaleY = s
                translationY = (1f - appear.value) * rise
            }
            .hoverable(interaction)
            .glass(CardShape)
            .background(Brush.linearGradient(listOf(wash.copy(alpha = 0.2f + 0.08f * lift), Color.Transparent), start = Offset.Zero, end = Offset(520f, 360f)))
            .border(1.5.dp, Color.White.copy(alpha = 0.32f * lift), CardShape)
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

/** Hours and minutes (no seconds) and the date. Ticks on each minute boundary. */
@Composable
private fun ClockCard(compact: Boolean, modifier: Modifier) {
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
    WidgetCard(Color(0xFF9DB4FF), modifier) {
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
private fun WeatherCard(weather: Weather?, error: String?, mode: WeatherMode, compact: Boolean, animate: Boolean, modifier: Modifier) {
    WidgetCard(skyAccent(weather), modifier) {
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

/** Warm for sun, blue-grey for cloud, deep blue for rain and night. */
private fun skyAccent(weather: Weather?): Color = when {
    weather == null -> Color(0xFF8FA3C8)
    !weather.isDay -> Color(0xFF5B6CFF)
    else -> when (skyFor(weather.code)) {
        Sky.Clear -> Color(0xFFFFB347)
        Sky.PartlyCloudy -> Color(0xFFFFCF70)
        Sky.Cloudy, Sky.Fog -> Color(0xFFA9B6CC)
        Sky.Rain -> Color(0xFF4DABF7)
        Sky.Snow -> Color(0xFFD0EBFF)
        Sky.Storm -> Color(0xFF9775FA)
    }
}

// ---- Algeria (Les Fennecs) ---------------------------------------------------------

/**
 * Algeria's next fixture (competition, teams with crests, local kick-off time) and the
 * latest result with W / D / L. A match being played shows its score and a pulsing LIVE.
 * Without data it says so; a saved copy is marked when it's old.
 */
@Composable
private fun FennecsCard(fixtures: Fixtures?, error: String?, compact: Boolean, modifier: Modifier) {
    val now = Instant.now()
    val live = listOfNotNull(fixtures?.last, fixtures?.next).firstOrNull { it.isLive(now) }
    val upcoming = fixtures?.next?.takeIf { it != live }
    WidgetCard(if (live != null) AlgeriaRed else AlgeriaGreen, modifier) {
        Column(Modifier.align(Alignment.Center), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            when {
                live != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot()
                        Spacer(Modifier.width(6.dp))
                        CardLabel(if (compact) "Live" else "Live · " + shortCompetition(live.competition), color = Color(0xFFFF8A9B))
                    }
                    Teams(live, compact, showScore = true)
                    Text("Les Fennecs are playing now", style = lineStyle(14.sp, Palette.TextTertiary), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                upcoming != null -> {
                    CardLabel(if (compact) "Next" else "Next · ${shortCompetition(upcoming.competition)}")
                    Teams(upcoming, compact, showScore = false)
                    Text(kickoffText(upcoming.kickoff, compact), style = lineStyle(15.sp, Palette.TextPrimary, 560), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                else -> {
                    CardLabel("Les Fennecs")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AlgeriaFlag(26.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                fixtures != null -> "No match scheduled yet"
                                error != null -> "Can't reach match data · see Settings"
                                else -> "Loading fixtures…"
                            },
                            style = lineStyle(15.sp, Palette.TextPrimary),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            fixtures?.last?.takeIf { it != live && it.outcome != null }?.let { LastResult(it, compact) }
            if (fixtures?.fromCache == true && Duration.between(fixtures.fetchedAt, now) > Duration.ofHours(6)) {
                Text("Offline · saved " + savedText(fixtures.fetchedAt), style = lineStyle(12.sp, Palette.TextTertiary), maxLines = 1)
            }
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
        val pad = if (compact) " " else "  "
        val middle = if (showScore && match.homeScore != null && match.awayScore != null) "$pad${match.homeScore}–${match.awayScore}$pad" else "${pad}v$pad"
        Text(
            buildAnnotatedString {
                append(home)
                withStyle(SpanStyle(color = Palette.TextTertiary, fontWeight = FontWeight(if (showScore) 700 else 500))) { append(middle) }
                append(away)
            },
            style = lineStyle(if (compact) 17.sp else 19.sp, Palette.TextPrimary, 640),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Crest(match.away, 24.dp)
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
            "${if (compact) "" else "Last  "}${match.algeriaScore}–${match.opponentScore} v $opponent",
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
private fun kickoffText(kickoff: Instant?, compact: Boolean): String {
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
private fun Crest(side: Side, size: Dp) {
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
                WifiIcon(network, if (online) Color.White else Color(0xFFFF8787), 16.dp)
                Spacer(Modifier.width(7.dp))
                CardLabel(
                    "System · " + network.label + (if (network.vpn) " · VPN" else "") + if (compact) "" else " · Android ${android.os.Build.VERSION.RELEASE}",
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
