package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.DayForecast
import app.pill.launcher.Weather
import app.pill.launcher.WeatherMode
import app.pill.launcher.compassPoint
import app.pill.launcher.describe
import app.pill.launcher.formatTemp
import app.pill.launcher.skyFor
import app.pill.launcher.sunTimes
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val RainBlue = Color(0xFFA5D8FF)

/**
 * The forecast behind the weather card: now (with feels like, humidity, wind, UV and the
 * day's sunrise and sunset), the next twelve hours, and seven days with their range of
 * temperatures drawn as bars on one scale. Opened with OK on the card; Back closes it.
 */
@Composable
fun ForecastPage(weather: Weather, mode: WeatherMode, onDismiss: () -> Unit) {
    val forecast = weather.forecast ?: return
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val hourFormat = remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h a", locale) }
    val now = LocalDateTime.now(zone)
    val hours = forecast.hours.filter { !it.time.isBefore(now.truncatedTo(ChronoUnit.HOURS)) }.take(12)
    val today = LocalDate.now(zone)
    val days = forecast.days.filter { !it.date.isBefore(today) }.take(7)
    val fahrenheit = mode == WeatherMode.Fahrenheit
    val focus = remember { FocusRequester() }

    OverlayPage(onDismiss = onDismiss, width = 860.dp) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { focus.requestFocus() }
        }
        // Something has to hold focus inside the dialog for the remote's keys to reach it
        Row(Modifier.focusRequester(focus).focusable(), verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(skyFor(weather.code), weather.isDay, 64.dp)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(formatTemp(weather.tempC, mode), style = TextStyle(fontFamily = FlexDisplay, color = Palette.TextPrimary, fontSize = 50.sp, fontWeight = FontWeight(260)), maxLines = 1)
                Text(
                    listOf(describe(weather.code), weather.place).filter { it.isNotBlank() }.joinToString(" · "),
                    color = Palette.TextSecondary, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            val sun = if (weather.lat.isNaN()) null else sunTimes(weather.lat, weather.lon, today, zone)
            val clock = DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale)
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (line in listOf(
                    listOfNotNull(
                        forecast.feelsLikeC?.let { "Feels like ${formatTemp(it, mode)}" },
                        forecast.humidity?.let { "Humidity $it%" },
                    ),
                    listOfNotNull(
                        forecast.windKmh?.let { kmh ->
                            val speed = if (fahrenheit) "${Math.round(kmh / 1.609)} mph" else "${Math.round(kmh)} km/h"
                            "Wind $speed" + (forecast.windFromDeg?.let { " " + compassPoint(it) } ?: "")
                        },
                        forecast.uvIndex?.let { "UV ${Math.round(it)}" },
                    ),
                    listOfNotNull(sun?.let { "Sunrise ${it.sunrise.format(clock)}" }, sun?.let { "Sunset ${it.sunset.format(clock)}" }),
                ).filter { it.isNotEmpty() }) {
                    Text(line.joinToString("  ·  "), color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
                }
            }
        }

        if (hours.isNotEmpty()) {
            PageLabel("Next hours", Modifier.padding(top = 18.dp, bottom = 8.dp))
            Row(Modifier.fillMaxWidth()) {
                for ((i, hour) in hours.withIndex()) {
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "Now" else hour.time.format(hourFormat), color = Palette.TextTertiary, fontSize = 13.sp, maxLines = 1)
                        Spacer(Modifier.height(4.dp))
                        WeatherIcon(skyFor(hour.code), hour.isDay, 28.dp)
                        Spacer(Modifier.height(4.dp))
                        Text(formatTemp(hour.tempC, mode), color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(rainText(hour.rainChance, hour.rainMm).orEmpty(), color = RainBlue, fontSize = 12.sp, maxLines = 1)
                    }
                }
            }
        }

        if (days.isNotEmpty()) {
            PageLabel("${days.size} days", Modifier.padding(top = 14.dp, bottom = 4.dp))
            val low = days.minOf { it.lowC }
            val high = days.maxOf { it.highC }
            for (day in days) DayRow(day, today, low, high, mode, locale)
        }

        PageFooter(
            "${forecast.source} · updated ${ageText(forecast.fetchedAt).lowercase()} · Back to close",
            Modifier.padding(top = 12.dp),
        )
    }
}

/** "40%" (chance of rain) or "2 mm" (expected rain); nothing when it's unlikely. */
private fun rainText(chance: Int?, mm: Double?): String? = when {
    chance != null -> if (chance >= 20) "$chance%" else null
    mm != null -> if (mm >= 0.2) (if (mm < 1) String.format(Locale.ROOT, "%.1f mm", mm) else "${Math.round(mm)} mm") else null
    else -> null
}

@Composable
private fun DayRow(day: DayForecast, today: LocalDate, low: Double, high: Double, mode: WeatherMode, locale: Locale) {
    Row(Modifier.fillMaxWidth().height(29.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (day.date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> day.date.format(DateTimeFormatter.ofPattern("EEEE", locale))
            },
            color = Palette.TextPrimary, fontSize = 17.sp, maxLines = 1, modifier = Modifier.width(120.dp),
        )
        WeatherIcon(skyFor(day.code), true, 24.dp)
        Text(rainText(day.rainChance, day.rainMm).orEmpty(), color = RainBlue, fontSize = 13.sp, maxLines = 1, modifier = Modifier.width(64.dp).padding(start = 8.dp))
        Text(formatTemp(day.lowC, mode), color = Palette.TextTertiary, fontSize = 17.sp, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
        // The day's range on the week's scale, cool to warm
        Canvas(Modifier.weight(1f).height(8.dp).padding(horizontal = 14.dp)) {
            val span = (high - low).coerceAtLeast(1.0)
            val from = ((day.lowC - low) / span).toFloat() * size.width
            val to = ((day.highC - low) / span).toFloat() * size.width
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.12f), cornerRadius = r)
            drawRoundRect(
                Brush.horizontalGradient(listOf(RainBlue, Color(0xFFFFD43B)), startX = 0f, endX = size.width),
                topLeft = Offset(from, 0f),
                size = Size((to - from).coerceAtLeast(size.height), size.height),
                cornerRadius = r,
            )
        }
        Text(formatTemp(day.highC, mode), color = Palette.TextPrimary, fontSize = 17.sp, modifier = Modifier.width(44.dp))
    }
}
