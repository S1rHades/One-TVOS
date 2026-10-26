package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
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

private val TextShadow = Shadow(Color(0x99000000), Offset(0f, 4f), 12f)

/** Large clock and date for the top-left corner. Ticks once per second, aligned to the second boundary. */
@Composable
fun TimeWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val is24h = DateFormat.is24HourFormat(context)
    val locale = Locale.getDefault()

    val hourMinute = remember(is24h, locale) {
        DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm", locale)
    }
    val seconds = remember(locale) { DateTimeFormatter.ofPattern(":ss", locale) }
    val amPm = remember(locale) { DateTimeFormatter.ofPattern(" a", locale) }
    val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", locale) }

    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L - System.currentTimeMillis() % 1000L)
        }
    }

    val small = SpanStyle(fontSize = 34.sp, color = Color(0xCCFFFFFF), fontWeight = FontWeight.Normal)
    val clock = buildAnnotatedString {
        append(now.format(hourMinute))
        withStyle(small) {
            append(now.format(seconds))
            if (!is24h) append(now.format(amPm))
        }
    }

    Column(modifier) {
        Text(
            text = clock,
            style = TextStyle(
                color = Color.White,
                fontSize = 76.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-1).sp,
                shadow = TextShadow,
            ),
        )
        Text(
            text = now.format(date),
            style = TextStyle(
                color = Color(0xE6FFFFFF),
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                shadow = TextShadow,
            ),
        )
    }
}
