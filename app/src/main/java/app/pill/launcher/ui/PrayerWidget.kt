package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
fun PrayerWidget(times: PrayerTimes, compact: Boolean, modifier: Modifier = Modifier) {
    val now by rememberMinute()
    val clock = rememberClockFormat()
    val next = remember(times, now) { nextPrayer(times, now) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 5.dp)) {
        // The mosque, and quietly how old the times are (grey once they are only the saved copy)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PageLabel(
                if (compact) "Prayers" else times.mosque.ifBlank { "Prayer times" },
                color = if (times.fromCache) Palette.TextTertiary else PrayerGreen.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f, fill = false),
            )
            if (!compact) {
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (times.fromCache) "saved " else "") + ageText(times.fetchedAt, now.toInstant()).lowercase(),
                    style = prayerStyle(11.sp, Palette.TextTertiary),
                    maxLines = 1,
                )
            }
        }
        if (next == null) {
            Text("No times for today", style = prayerStyle(15.sp), maxLines = 2)
            return@Column
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(next.prayer.title, style = prayerStyle(if (compact) 18.sp else 22.sp, Palette.TextPrimary, 600), maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text(next.at.format(clock), style = prayerStyle(if (compact) 16.sp else 18.sp, Palette.TextPrimary, 500), maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                untilText(next.at, now) + if (next.tomorrow) " · tomorrow" else "",
                style = prayerStyle(if (compact) 13.sp else 14.sp, PrayerGreen, 600),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 2.dp),
            )
        }
        if (compact) {
            val after = times.list.firstOrNull { (p, _) -> p.ordinal > next.prayer.ordinal && !next.tomorrow }
            Text(
                after?.let { (p, t) -> "Then ${p.title} ${t.format(clock)}" } ?: lastLine(times),
                style = prayerStyle(12.sp, Palette.TextTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            PrayerStrip(times, next, clock, Modifier.fillMaxWidth())
        }
    }
}

/** The five prayers side by side, the next one in white with a green dot. */
@Composable
private fun PrayerStrip(times: PrayerTimes, next: NextPrayer?, clock: DateTimeFormatter, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween) {
        for ((prayer, time) in times.list) {
            val current = prayer == next?.prayer
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (current) {
                        Box(Modifier.size(5.dp).background(PrayerGreen, CircleShape))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        shortTitle(prayer),
                        style = TextStyle(fontFamily = Flex, color = if (current) Palette.TextPrimary else Palette.TextTertiary, fontSize = 10.sp, fontWeight = FontWeight(650), letterSpacing = 0.8.sp),
                        maxLines = 1,
                    )
                }
                Text(
                    time.format(clock),
                    style = prayerStyle(13.sp, if (current) Palette.TextPrimary else Palette.TextSecondary, if (current) 600 else 500),
                    maxLines = 1,
                )
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
        Text(
            when {
                !hasMosque -> "Choose a mosque in Settings"
                error != null -> "Can't reach MAWAQIT"
                else -> "Getting today's times…"
            },
            style = prayerStyle(15.sp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
