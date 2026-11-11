package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.NextPrayer
import app.pill.launcher.Prayer
import app.pill.launcher.PrayerTimes
import app.pill.launcher.nextPrayer
import app.pill.launcher.untilText
import app.pill.launcher.whenText
import kotlinx.coroutines.delay
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The prayer card's green. */
val PrayerGreen = Color(0xFF69DB7C)

/** The minute, ticking on each minute boundary, for countdowns and the next-prayer mark. */
@Composable
fun rememberMinute(): State<ZonedDateTime> = produceState(ZonedDateTime.now()) {
    while (true) {
        value = ZonedDateTime.now()
        delay(60_000L - System.currentTimeMillis() % 60_000L)
    }
}

/** "HH:mm" or "h:mm a", as the TV's clock is set. */
@Composable
fun rememberClockFormat(): DateTimeFormatter {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()
    return remember(is24h, locale) { DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a", locale) }
}

private fun prayerStyle(size: TextUnit, color: Color = Palette.TextSecondary, weight: Int = 500) =
    TextStyle(fontFamily = Flex, color = color, fontSize = size, fontWeight = FontWeight(weight))

/**
 * Inside the prayer card: the mosque's name as the label, the next prayer large with how
 * long until it, and the five prayers along the bottom with the next one bright. Compact,
 * the row gives way to the following prayer alone.
 */
@Composable
fun PrayerWidget(
    times: PrayerTimes,
    compact: Boolean,
    modifier: Modifier = Modifier,
    /** A prayer picked with Left / Right on the card, shown in the next one's place; null for the next. */
    picked: Prayer? = null,
) {
    val now by rememberMinute()
    val clock = rememberClockFormat()
    val next = remember(times, now) { nextPrayer(times, now) }
    // The picked prayer, today, in the big line instead of the next one
    val pickedAt = picked?.let { p -> times.times[p]?.let { ZonedDateTime.of(now.toLocalDate(), it, now.zone) } }
    val big = if (picked != null && pickedAt != null && picked != next?.prayer) picked to pickedAt else null
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)) {
        // The mosque, and quietly how old the times are (grey once they are only the saved copy)
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The mosque's name comes first; the age gets what room is left, or goes
            FitText(
                times.mosque.ifBlank { "Prayer times" }.uppercase(),
                style = TextStyle(fontFamily = Flex, color = if (times.fromCache) Palette.TextTertiary else PrayerGreen.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight(650), letterSpacing = 1.3.sp),
                shorter = listOf("PRAYERS"),
            )
            Spacer(Modifier.width(8.dp))
            FitText(
                (if (times.fromCache) "saved " else "") + ageText(times.fetchedAt, now.toInstant()).lowercase(),
                style = prayerStyle(11.sp, Palette.TextTertiary),
                shorter = listOf(ageText(times.fetchedAt, now.toInstant()).lowercase(), ""),
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        if (next == null) {
            Text("No times for today", style = prayerStyle(15.sp), maxLines = 2)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            val title = big?.first?.title ?: next.prayer.title
            val at = big?.second ?: next.at
            Text(title, style = prayerStyle(if (compact) 18.sp else 22.sp, Palette.TextPrimary, 600), maxLines = 1, softWrap = false)
            Spacer(Modifier.width(8.dp))
            Text(at.format(clock), style = prayerStyle(if (compact) 16.sp else 18.sp, Palette.TextPrimary, 500), maxLines = 1, softWrap = false, modifier = Modifier.padding(bottom = 1.dp))
            Spacer(Modifier.width(8.dp))
            FitText(
                if (big != null) whenText(big.second, now) else untilText(next.at, now) + if (next.tomorrow) " · tomorrow" else "",
                // Green counts down to the next prayer; another one picked by hand is quieter
                style = prayerStyle(if (compact) 13.sp else 14.sp, if (big != null) Palette.TextSecondary else PrayerGreen, 600),
                maxLines = 1,
                // Short of room: the bare countdown, then nothing (the time beside it says when)
                shorter = listOf(
                    (if (big != null) whenText(big.second, now) else untilText(next.at, now)).removePrefix("in ").replace(" min", "m").replace(" h ", "h "),
                    "",
                ),
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        if (compact) {
            val after = times.list.firstOrNull { (p, _) -> p.ordinal > next.prayer.ordinal && !next.tomorrow }
            // On a Friday, until it has begun: the Friday prayer, where the mosque gives its time
            val friday = times.jumua?.takeIf { now.dayOfWeek == java.time.DayOfWeek.FRIDAY && now.toLocalTime().isBefore(it) && times.forDate == now.toLocalDate() }
            FitText(
                when {
                    big != null -> "Next: ${next.prayer.title} ${next.at.format(clock)}"
                    friday != null -> "Friday prayer ${friday.format(clock)}"
                    else -> after?.let { (p, t) -> "Then ${p.title} ${t.format(clock)}" } ?: lastLine(times)
                },
                style = prayerStyle(12.sp, Palette.TextTertiary),
                maxLines = 1,
                shorter = listOfNotNull(
                    when {
                        big != null -> "${next.prayer.title} ${next.at.format(clock)}"
                        friday != null -> "Friday ${friday.format(clock)}"
                        else -> after?.let { (p, t) -> "${p.title} ${t.format(clock)}" }
                    },
                    "",
                ),
            )
        } else {
            PrayerStrip(times, next, clock, Modifier.fillMaxWidth(), picked = big?.first)
        }
    }
}

/** The five prayers side by side, the next one in white with a green dot. */
@Composable
private fun PrayerStrip(times: PrayerTimes, next: NextPrayer?, clock: DateTimeFormatter, modifier: Modifier = Modifier, picked: Prayer? = null) {
    // As much as the card's width allows: all five with AM / PM, then all five without it,
    // then the bright one with the prayers either side of it
    BoxWithConstraints(modifier) {
    val per = maxWidth / times.list.size.coerceAtLeast(1)
    val bright = picked ?: next?.prayer
    val shown = when {
        per >= 44.dp -> times.list
        else -> {
            val at = times.list.indexOfFirst { it.first == bright }.coerceAtLeast(0)
            val from = (at - 1).coerceIn(0, (times.list.size - 3).coerceAtLeast(0))
            times.list.drop(from).take(3)
        }
    }
    val bare = per < 84.dp
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for ((prayer, time) in shown) {
            // The one in the big line is the bright one: the next prayer, or the one picked by hand
            val current = prayer == (picked ?: next?.prayer)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        Box(Modifier.size(5.dp).background(if (picked != null) Palette.TextPrimary else PrayerGreen, CircleShape))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        shortTitle(prayer),
                        style = TextStyle(fontFamily = Flex, color = if (current) Palette.TextPrimary else Palette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight(650), letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                }
                Text(
                    // Short of room, "12:14" says it; the AM / PM is plain from the prayer
                    time.format(clock).let { if (bare) it.replace(Regex("""\s?[AaPp]\.?\s?[Mm]\.?$"""), "") else it },
                    style = prayerStyle(13.sp, if (current) Palette.TextPrimary else Palette.TextSecondary, if (current) 600 else 500),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
    }
}

private fun shortTitle(p: Prayer): String = p.title.uppercase()

/** "Saved 3 h ago" or "Yesterday's times" under a compact card, when nothing comes after the next prayer. */
private fun lastLine(times: PrayerTimes): String =
    if (times.fromCache) "Saved " + ageText(times.fetchedAt).lowercase() else "Updated " + ageText(times.fetchedAt).lowercase()

/** The card's words while there are no times: why, in a line or two. */
@Composable
fun PrayerEmpty(error: String?, hasMosque: Boolean, modifier: Modifier = Modifier) {
    Column(modifier) {
        PageLabel("Prayer times")
        Spacer(Modifier.height(6.dp))
        FitText(
            when {
                !hasMosque -> "Choose a mosque in Settings"
                error != null && error.startsWith("MAWAQIT has no mosque") -> "Mosque not found · see Settings"
                error != null -> "Can't reach MAWAQIT"
                else -> "Getting today's times…"
            },
            style = prayerStyle(15.sp),
            maxLines = 2,
            shorter = listOf(
                when {
                    !hasMosque -> "Choose a mosque"
                    error != null && error.startsWith("MAWAQIT has no mosque") -> "Mosque not found"
                    error != null -> "Offline"
                    else -> "Loading…"
                },
            ),
        )
    }
}
