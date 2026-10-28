package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Large clock (hours and minutes) and date for the top-left corner. Ticks on each minute boundary. */
@Composable
fun TimeWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()

    val hourMinute = remember(is24h, locale) {
        DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", locale)
    }
    val amPm = remember(locale) { DateTimeFormatter.ofPattern(" a", locale) }
    // Locale-aware order and punctuation, e.g. "Tuesday, 27 October" or "Tuesday, October 27"
    val date = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale)
    }

    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            // Wake on the next minute boundary (no seconds shown)
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }

    val small = SpanStyle(fontSize = 30.sp, color = Palette.TextSecondary, fontWeight = FontWeight.Normal)
    val clock = buildAnnotatedString {
        append(now.format(hourMinute))
        withStyle(small) {
            if (!is24h) append(now.format(amPm))
        }
    }

    Column(modifier) {
        Text(
            text = clock,
            style = TextStyle(
                color = Color.White,
                fontSize = 68.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
                shadow = TextShadow,
            ),
        )
        Text(
            text = now.format(date),
            style = TextStyle(
                color = Palette.TextSecondary,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                shadow = TextShadow,
            ),
        )
    }
}
