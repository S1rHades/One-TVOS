package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.DayForecast
import app.pill.launcher.HourForecast
import app.pill.launcher.Weather
import app.pill.launcher.WeatherMode
import app.pill.launcher.compassPoint
import app.pill.launcher.describe
import app.pill.launcher.formatTemp
import app.pill.launcher.skyFor
import app.pill.launcher.sunTimes
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val RainBlue = Color(0xFFA5D8FF)
private val SunYellow = Color(0xFFFFD43B)

/**
 * The weather dashboard behind the weather card: four figures for now (feels like,
 * humidity, wind, UV), the week as a chart of each day's range that follows Left / Right,
 * and what's worth knowing about today. Opened with OK on the card; Back closes it.
 */
@Composable
fun ForecastPage(weather: Weather, mode: WeatherMode, onDismiss: () -> Unit) {
    val forecast = weather.forecast ?: return
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val clock = remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale) }
    val hourOnly = remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", locale) }
    val now = LocalDateTime.now(zone)
    val hours = forecast.hours.filter { !it.time.isBefore(now.truncatedTo(ChronoUnit.HOURS)) }.take(24)
    val today = LocalDate.now(zone)
    val days = forecast.days.filter { !it.date.isBefore(today) }.take(7)
    val fahrenheit = mode == WeatherMode.Fahrenheit
    val focus = remember { FocusRequester() }
    var day by remember { mutableIntStateOf(0) }

    OverlayPage(onDismiss = onDismiss, width = 880.dp, framed = false) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { focus.requestFocus() }
        }
        PageTitle(
            weather.place.ifBlank { "Weather" },
            listOf(
                describe(weather.code) + " · " + formatTemp(weather.tempC, mode),
                forecast.source,
                "updated " + ageText(forecast.fetchedAt).lowercase(),
                "Back to close",
            ).joinToString(" · "),
            Modifier.reveal(0),
        )

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard(1, "Feels like", forecast.feelsLikeC?.let { formatTemp(it, mode) }, Modifier.weight(1f)) {
                // The next day's temperatures, as a trend
                Sparkline(hours.map { it.tempC }, SunYellow, Modifier.width(64.dp).height(26.dp))
            }
            StatCard(2, "Humidity", forecast.humidity?.let { "$it%" }, Modifier.weight(1f))
            StatCard(
                3,
                "Wind" + (forecast.windFromDeg?.let { " · " + compassPoint(it) } ?: ""),
                forecast.windKmh?.let { kmh -> if (fahrenheit) "${Math.round(kmh / 1.609)} mph" else "${Math.round(kmh)} km/h" },
                Modifier.weight(1f),
            )
            StatCard(4, "UV index", forecast.uvIndex?.let { "${Math.round(it)}" }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth().height(318.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageCard(5, Modifier.weight(1f).fillMaxSize()) {
                PageLabel("${days.size} days")
                if (days.isNotEmpty()) {
                    WeekChart(
                        days = days,
                        today = today,
                        selected = day,
                        mode = mode,
                        locale = locale,
                        modifier = Modifier
                            .fillMaxSize()
                            .focusRequester(focus)
                            .focusable()
                            .onKeyEvent { event ->
                                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                                when (event.key) {
                                    Key.DirectionLeft -> { day = (day - 1).coerceAtLeast(0); true }
                                    Key.DirectionRight -> { day = (day + 1).coerceAtMost(days.lastIndex); true }
                                    else -> false
                                }
                            },
                    )
                }
            }
            PageCard(6, Modifier.width(272.dp).fillMaxSize()) {
                PageLabel("Today")
                Spacer(Modifier.height(6.dp))
                for (item in todayAgenda(weather, hours, days, today, zone, mode, clock, hourOnly)) {
                    Row(Modifier.padding(top = 10.dp)) {
                        Box(Modifier.padding(top = 6.dp).size(8.dp).background(item.color, CircleShape))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(item.title, color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight(560), maxLines = 1)
                            Text(item.detail, color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** One of the four figures across the top, with room for a small trend. */
@Composable
private fun StatCard(step: Int, label: String, value: String?, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    PageCard(step, modifier.height(82.dp)) {
        PageLabel(label)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(value ?: "–", color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight(450), maxLines = 1, modifier = Modifier.weight(1f))
            extra()
        }
    }
}

/**
 * The week as columns: each day's range drawn as a rounded bar on one scale, its high
 * above and low below, the day's name under it. The selected day takes the photo's colour
 * and a label; the highlight glides as the selection moves.
 */
@Composable
private fun WeekChart(days: List<DayForecast>, today: LocalDate, selected: Int, mode: WeatherMode, locale: Locale, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val tint = LocalWallpaperTint.current
    val at by animateFloatAsState(selected.toFloat(), Motion.glide(), label = "weekDay")
    val low = days.minOf { it.lowC }
    val high = days.maxOf { it.highC }
    val name = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val small = TextStyle(fontFamily = Flex, fontSize = 13.sp, color = Palette.TextSecondary, textAlign = TextAlign.Center)
    val strong = small.copy(color = Palette.TextPrimary, fontWeight = FontWeight(560))
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val col = size.width / days.size
            val top = 46.dp.toPx()
            val bottom = size.height - 54.dp.toPx()
            val barW = (col * 0.42f).coerceAtMost(30.dp.toPx())
            val span = (high - low).coerceAtLeast(1.0)
            fun y(c: Double) = (bottom - ((c - low) / span).toFloat() * (bottom - top))
            for ((i, d) in days.withIndex()) {
                val on = (1f - kotlin.math.abs(i - at)).coerceIn(0f, 1f)
                val cx = col * (i + 0.5f)
                val y0 = y(d.highC)
                val y1 = y(d.lowC).coerceAtLeast(y0 + barW)
                val color = lerp(Color.White.copy(alpha = 0.26f), tint.value, on)
                drawRoundRect(color, Offset(cx - barW / 2, y0), Size(barW, y1 - y0), CornerRadius(barW / 2))
                val hi = measurer.measure(formatTemp(d.highC, mode), strong)
                drawText(hi, topLeft = Offset(cx - hi.size.width / 2f, y0 - hi.size.height - 4.dp.toPx()))
                val lo = measurer.measure(formatTemp(d.lowC, mode), small)
                drawText(lo, topLeft = Offset(cx - lo.size.width / 2f, y1 + 4.dp.toPx()))
                val label = when (d.date) {
                    today -> "Today"
                    else -> d.date.format(name)
                }
                val dn = measurer.measure(label, if (on > 0.5f) strong else small)
                drawText(dn, topLeft = Offset(cx - dn.size.width / 2f, size.height - dn.size.height))
            }
            // The selected day's details, above its bar
            val i = at.toInt().coerceIn(0, days.lastIndex)
            val d = days[selected.coerceIn(0, days.lastIndex)]
            val text = listOfNotNull(
                if (d.date == today) "Today" else d.date.format(name),
                formatTemp(d.lowC, mode) + " to " + formatTemp(d.highC, mode),
                d.rainChance?.takeIf { it >= 20 }?.let { "rain $it%" },
            ).joinToString(" · ")
            val tip = measurer.measure(text, TextStyle(fontFamily = Flex, fontSize = 13.sp, color = Palette.Ink, fontWeight = FontWeight(650)))
            val pad = 10.dp.toPx()
            val w = tip.size.width + pad * 2
            val h = tip.size.height + 8.dp.toPx()
            val cx = (col * (at + 0.5f)).coerceIn(w / 2, size.width - w / 2)
            val ty = (y(days[i].highC) - 24.dp.toPx() - h).coerceAtLeast(0f)
            drawRoundRect(Color.White, Offset(cx - w / 2, ty), Size(w, h), CornerRadius(h / 2))
            drawText(tip, topLeft = Offset(cx - w / 2 + pad, ty + 4.dp.toPx()))
        }
        // The days' skies, in a row under the bars
        Row(Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 22.dp)) {
            for (d in days) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { WeatherIcon(skyFor(d.code), true, 22.dp) }
            }
        }
    }
}

private class AgendaItem(val color: Color, val title: String, val detail: String)

/** What's worth knowing about the next day: the sun, rain, the extreme to come, and tomorrow. */
private fun todayAgenda(
    weather: Weather,
    hours: List<HourForecast>,
    days: List<DayForecast>,
    today: LocalDate,
    zone: ZoneId,
    mode: WeatherMode,
    clock: DateTimeFormatter,
    hourOnly: DateTimeFormatter,
): List<AgendaItem> {
    val items = mutableListOf<AgendaItem>()
    val now = ZonedDateTime.now(zone)
    if (!weather.lat.isNaN()) {
        val sun = sunTimes(weather.lat, weather.lon, today, zone)
        val next = listOfNotNull(sun?.sunrise?.let { "Sunrise" to it }, sun?.sunset?.let { "Sunset" to it }).firstOrNull { it.second.isAfter(now) }
            ?: sunTimes(weather.lat, weather.lon, today.plusDays(1), zone)?.sunrise?.let { "Sunrise" to it }
        next?.let { (what, at) ->
            val left = Duration.between(now, at)
            val inText = if (left.toHours() >= 1) "in ${left.toHours()} h ${left.toMinutes() % 60} min" else "in ${left.toMinutes()} min"
            items += AgendaItem(SunYellow, what, at.format(clock) + " · " + inText)
        }
    }
    val wet = hours.indexOfFirst { (it.rainChance ?: 0) >= 50 || (it.rainMm ?: 0.0) >= 0.5 }
    if (wet >= 0) {
        var end = wet
        while (end + 1 < hours.size && ((hours[end + 1].rainChance ?: 0) >= 50 || (hours[end + 1].rainMm ?: 0.0) >= 0.5)) end++
        val chance = hours.subList(wet, end + 1).mapNotNull { it.rainChance }.maxOrNull()
        val until = hours[end].time.plusHours(1)
        items += AgendaItem(
            RainBlue,
            "Rain likely",
            hours[wet].time.format(hourOnly) + " to " + until.format(hourOnly) + (chance?.let { " · $it%" } ?: ""),
        )
    } else if (hours.isNotEmpty()) {
        items += AgendaItem(RainBlue, "No rain expected", "Over the next ${hours.size} hours")
    }
    if (hours.size >= 2) {
        val warmer = now.hour < 14
        val pick = if (warmer) hours.maxBy { it.tempC } else hours.minBy { it.tempC }
        items += AgendaItem(
            Color(0xFFD6CFA3),
            if (warmer) "Warmest" else "Coolest",
            formatTemp(pick.tempC, mode) + " at " + pick.time.format(hourOnly),
        )
    }
    days.firstOrNull { it.date == today.plusDays(1) }?.let { d ->
        items += AgendaItem(
            Color(0xFFFF8A80),
            "Tomorrow",
            listOfNotNull(
                formatTemp(d.lowC, mode) + " to " + formatTemp(d.highC, mode),
                describe(d.code).lowercase(),
                d.rainChance?.takeIf { it >= 20 }?.let { "rain $it%" },
            ).joinToString(", "),
        )
    }
    return items
}
