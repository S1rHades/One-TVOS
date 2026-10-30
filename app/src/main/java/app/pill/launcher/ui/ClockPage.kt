package app.pill.launcher.ui

import android.icu.util.ULocale
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as DayNameStyle
import java.time.temporal.IsoFields
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

/** Cities on the clock page, each with its time beside the TV's own. */
val WorldClocks = listOf(
    "Algiers" to "Africa/Algiers",
    "London" to "Europe/London",
    "Mecca" to "Asia/Riyadh",
    "Moscow" to "Europe/Moscow",
    "New York" to "America/New_York",
)

/**
 * The page behind the clock card: the time to the second, the full date with the Islamic
 * (Umm al-Qura) date, this month's calendar, and the time in a few cities. Opened with
 * OK on the card; Back closes it.
 */
@Composable
fun ClockPage(place: String?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val is24h = DateFormat.is24HourFormat(context)
    val focus = remember { FocusRequester() }
    val now by produceState(ZonedDateTime.now(zone)) {
        while (true) {
            value = ZonedDateTime.now(zone)
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }
    val seconds = remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm:ss" else "h:mm:ss a", locale) }
    val short = remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale) }
    val longDate = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMMy"), locale) }
    val today = now.toLocalDate()
    val hijri = remember(today, locale) { hijriDate(today, locale) }

    OverlayPage(onDismiss = onDismiss, width = 860.dp) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { focus.requestFocus() }
        }
        // Something has to hold focus inside the dialog for the remote's keys to reach it
        Row(Modifier.focusRequester(focus).focusable(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    now.format(seconds),
                    style = TextStyle(fontFamily = FlexDisplay, color = Palette.TextPrimary, fontSize = 64.sp, fontWeight = FontWeight(260), letterSpacing = (-1).sp),
                    maxLines = 1,
                )
                Text(now.format(longDate), color = Palette.TextPrimary, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (hijri != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(hijri, color = Palette.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Week ${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · day ${today.dayOfYear} of ${today.lengthOfYear()}",
                    color = Palette.TextTertiary, fontSize = 15.sp, maxLines = 1,
                )
            }
            Spacer(Modifier.width(24.dp))
            MonthGrid(today, locale)
        }

        PageLabel("World clocks", Modifier.padding(top = 20.dp, bottom = 8.dp))
        Row(Modifier.fillMaxWidth()) {
            for ((city, id) in WorldClocks) {
                val there = now.withZoneSameInstant(ZoneId.of(id))
                Column(Modifier.weight(1f)) {
                    Text(city, color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1)
                    Text(there.format(short), color = Palette.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    Text(offsetText(now, there), color = Palette.TextTertiary, fontSize = 13.sp, maxLines = 1)
                }
            }
        }

        PageFooter(
            listOfNotNull(place?.takeIf { it.isNotBlank() }, zoneText(now), "Back to close").joinToString(" · "),
            Modifier.padding(top = 16.dp),
        )
    }
}

/** This month, weeks starting on the locale's first day, today in a white disc. */
@Composable
private fun MonthGrid(today: LocalDate, locale: Locale) {
    val firstDay = WeekFields.of(locale).firstDayOfWeek
    val first = today.withDayOfMonth(1)
    val lead = (first.dayOfWeek.value - firstDay.value + 7) % 7
    val cells = List(lead) { null } + (1..today.lengthOfMonth()).map { first.withDayOfMonth(it) }
    val cell = 36.dp
    Column {
        Text(
            today.format(DateTimeFormatter.ofPattern("LLLL y", locale)),
            color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row {
            for (i in 0 until 7) {
                Text(
                    firstDay.plus(i.toLong()).getDisplayName(DayNameStyle.NARROW, locale),
                    color = Palette.TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.width(cell),
                )
            }
        }
        for (week in cells.chunked(7)) {
            Row {
                for (date in week) {
                    Box(Modifier.size(cell), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val isToday = date == today
                            if (isToday) Box(Modifier.size(30.dp).background(Color.White, CircleShape))
                            Text(
                                "${date.dayOfMonth}",
                                color = if (isToday) Palette.Ink else Palette.TextSecondary,
                                fontSize = 15.sp,
                                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "18 Jumada I 1448 AH", by the Umm al-Qura calendar; null where the TV can't say. */
fun hijriDate(date: LocalDate, locale: Locale): String? = runCatching {
    val calendarLocale = ULocale.forLocale(locale).setKeywordValue("calendar", "islamic-umalqura")
    val format = android.icu.text.DateFormat.getDateInstance(android.icu.text.DateFormat.LONG, calendarLocale)
    format.format(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()))
}.getOrNull()

/** How a city's time differs from here: "Same time", "+2 h", "−1 h 30 min", with the day when it differs. */
fun offsetText(here: ZonedDateTime, there: ZonedDateTime): String {
    val minutes = (there.offset.totalSeconds - here.offset.totalSeconds) / 60
    val day = when (there.toLocalDate().compareTo(here.toLocalDate())) {
        0 -> null
        in 1..Int.MAX_VALUE -> "tomorrow"
        else -> "yesterday"
    }
    val diff = if (minutes == 0) "Same time" else {
        val sign = if (minutes > 0) "+" else "−"
        val h = Math.abs(minutes) / 60
        val m = Math.abs(minutes) % 60
        sign + listOfNotNull(if (h > 0) "$h h" else null, if (m > 0) "$m min" else null).joinToString(" ")
    }
    return listOfNotNull(diff, day).joinToString(", ")
}

/** "UTC+3" for the TV's time zone. */
private fun zoneText(now: ZonedDateTime): String {
    val total = now.offset.totalSeconds / 60
    if (total == 0) return "UTC"
    val sign = if (total > 0) "+" else "−"
    val h = Math.abs(total) / 60
    val m = Math.abs(total) % 60
    return "UTC$sign$h" + if (m > 0) ":%02d".format(m) else ""
}
