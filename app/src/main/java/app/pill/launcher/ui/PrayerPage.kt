package app.pill.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.Prayer
import app.pill.launcher.PrayerTimes
import app.pill.launcher.nextPrayer
import app.pill.launcher.untilText
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

/**
 * The page behind the prayer card: the next prayer large with the time left, the day as
 * a line from Fajr to Isha with the prayers along it, and every prayer with its time and
 * the mosque's iqama where it gives one. **Refresh** at the top left asks MAWAQIT again;
 * the title says when the times last came. Back closes it.
 */
@Composable
fun PrayerPage(
    times: PrayerTimes?,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val now by rememberMinute()
    val clock = rememberClockFormat()
    val next = remember(times, now) { times?.let { nextPrayer(it, now) } }
    val refreshFocus = remember { FocusRequester() }
    // A refresh that worked says so for a moment; one that failed shows its error instead
    var justUpdated by remember { mutableStateOf(false) }
    var wasRefreshing by remember { mutableStateOf(refreshing) }
    val currentError by rememberUpdatedState(error)
    LaunchedEffect(refreshing) {
        val finished = wasRefreshing && !refreshing
        wasRefreshing = refreshing
        if (finished && currentError == null) {
            justUpdated = true
            delay(2500)
            justUpdated = false
        }
    }
    val today = times != null && times.forDate == now.toLocalDate()

    OverlayPage(
        onDismiss = onDismiss,
        width = 880.dp,
        framed = false,
        topLeft = {
            PageButton(
                when {
                    refreshing -> "Refreshing…"
                    justUpdated -> "Updated ✓"
                    else -> "Refresh"
                },
                onClick = onRefresh,
                modifier = Modifier.focusRequester(refreshFocus),
            ) { tint ->
                RefreshIcon(tint, 18.dp, spinning = refreshing)
            }
        },
    ) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { refreshFocus.requestFocus() }
        }
        PageTitle(
            times?.mosque?.ifBlank { null } ?: "Prayer times",
            listOfNotNull(
                when {
                    times == null -> null
                    times.fromCache -> "saved " + ageText(times.fetchedAt).lowercase()
                    else -> "updated " + ageText(times.fetchedAt).lowercase()
                },
                if (times != null && !today) "yesterday's times" else null,
                "MAWAQIT",
                backHint(),
            ).joinToString(" · "),
            Modifier.reveal(0),
        )
        Row(Modifier.fillMaxWidth().height(PageBodyHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageCard(1, Modifier.weight(1.1f).fillMaxHeight()) {
                PageLabel(if (next?.tomorrow == true) "Next · tomorrow" else "Next")
                if (times == null || next == null) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        when {
                            refreshing -> "Asking MAWAQIT…"
                            error != null -> error
                            else -> "No times yet · choose a mosque in Settings → Widgets → Prayer times"
                        },
                        color = Palette.TextSecondary, fontSize = 16.sp, lineHeight = 22.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.weight(1f))
                } else {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        next.prayer.title,
                        style = TextStyle(fontFamily = FlexDisplay, color = Palette.TextPrimary, fontSize = TypeScale.Hero, fontWeight = FontWeight(260)),
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(next.at.format(clock), color = Palette.TextPrimary, fontSize = TypeScale.Title, fontWeight = FontWeight(400), maxLines = 1)
                        Spacer(Modifier.width(12.dp))
                        Text(untilText(next.at, now), color = PrayerGreen, fontSize = 18.sp, fontWeight = FontWeight(600), maxLines = 1, modifier = Modifier.padding(bottom = 5.dp))
                    }
                    times.iqama[next.prayer]?.let { iq ->
                        Text("Iqama " + iq.format(clock), color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    PageLabel("Today")
                    Spacer(Modifier.height(8.dp))
                    DayLine(times, next.prayer.takeIf { !next.tomorrow }, now, Modifier.fillMaxWidth().height(44.dp))
                    Spacer(Modifier.height(8.dp))
                    if (error != null && times.fromCache) {
                        Text("Showing the saved times · " + error.substringAfter(" · ", "couldn't refresh"), color = Color(0xFFFFC078), fontSize = TypeScale.Caption, maxLines = 1)
                    } else {
                        Text(
                            times.shuruq?.let { "Shuruq (sunrise) " + it.format(clock) } ?: "Times from the mosque's own MAWAQIT page",
                            color = Palette.TextTertiary, fontSize = TypeScale.Caption, maxLines = 1,
                        )
                    }
                }
            }
            PageCard(2, Modifier.width(380.dp).fillMaxHeight()) {
                Row(Modifier.fillMaxWidth()) {
                    PageLabel("Prayer", Modifier.weight(1f))
                    PageLabel("Adhan", Modifier.width(AdhanWidth))
                    if (times?.iqama?.isNotEmpty() == true) PageLabel("Iqama", Modifier.width(IqamaWidth))
                }
                if (times == null) {
                    for (p in Prayer.entries) TimeRow(p.title, "–", null, current = false)
                } else {
                    for ((p, t) in times.list) {
                        TimeRow(p.title, t.format(clock), if (times.iqama.isEmpty()) null else times.iqama[p]?.format(clock) ?: "–", current = next?.prayer == p)
                    }
                }
                Spacer(Modifier.weight(1f))
                PageFooter(
                    when {
                        times == null -> "Settings → Widgets → Prayer times chooses the mosque"
                        !today -> "The new day's times come after midnight, or with Refresh"
                        else -> "Refreshed once a day, after midnight"
                    },
                )
            }
        }
    }
}

/** Room for "12:14 PM" in each column, at their sizes. */
private val AdhanWidth = 118.dp
private val IqamaWidth = 86.dp

@Composable
private fun TimeRow(name: String, time: String, iqama: String?, current: Boolean) {
    val color = if (current) Palette.TextPrimary else Palette.TextSecondary
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp), contentAlignment = Alignment.Center) {
            if (current) Box(Modifier.size(7.dp).background(PrayerGreen, CircleShape))
        }
        Text(name, color = color, fontSize = TypeScale.Body, fontWeight = if (current) FontWeight(600) else FontWeight(400), modifier = Modifier.weight(1f))
        Text(time, color = color, fontSize = 20.sp, fontWeight = FontWeight(if (current) 600 else 400), maxLines = 1, softWrap = false, modifier = Modifier.width(AdhanWidth))
        if (iqama != null) Text(iqama, color = Palette.TextTertiary, fontSize = TypeScale.Body, maxLines = 1, softWrap = false, modifier = Modifier.width(IqamaWidth))
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(Color(0x1FFFFFFF)))
}

/**
 * The day from Fajr to Isha as a line, a dot for each prayer (filled once passed, green
 * for the next) and a mark for now.
 */
@Composable
private fun DayLine(times: PrayerTimes, next: Prayer?, now: ZonedDateTime, modifier: Modifier) {
    val list = times.list
    if (list.size < 2) return
    val start = ZonedDateTime.of(now.toLocalDate(), list.first().second, now.zone)
    val end = ZonedDateTime.of(now.toLocalDate(), list.last().second, now.zone)
    val span = Duration.between(start, end).seconds.toFloat().coerceAtLeast(1f)
    fun at(t: ZonedDateTime) = (Duration.between(start, t).seconds / span).coerceIn(0f, 1f)
    val nowAt = at(now)
    val stroke = 3.dp
    Canvas(modifier) {
        val y = size.height / 2
        val w = stroke.toPx()
        drawLine(Color.White.copy(alpha = 0.18f), Offset(0f, y), Offset(size.width, y), w, StrokeCap.Round)
        if (now.isAfter(start)) drawLine(Color.White.copy(alpha = 0.6f), Offset(0f, y), Offset(size.width * nowAt, y), w, StrokeCap.Round)
        for ((p, t) in list) {
            val x = size.width * at(ZonedDateTime.of(now.toLocalDate(), t, now.zone))
            val passed = !ZonedDateTime.of(now.toLocalDate(), t, now.zone).isAfter(now)
            when {
                p == next -> {
                    drawCircle(PrayerGreen.copy(alpha = 0.35f), 9.dp.toPx(), Offset(x, y))
                    drawCircle(PrayerGreen, 5.dp.toPx(), Offset(x, y))
                }
                passed -> drawCircle(Color.White, 4.dp.toPx(), Offset(x, y))
                else -> drawCircle(Color.White.copy(alpha = 0.5f), 4.dp.toPx(), Offset(x, y), style = Stroke(1.5.dp.toPx()))
            }
        }
        if (now.isAfter(start) && now.isBefore(end)) {
            drawLine(Color.White, Offset(size.width * nowAt, y - 9.dp.toPx()), Offset(size.width * nowAt, y + 9.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

/** Two arrows chasing round a circle; [spinning] while a refresh is under way, so the press visibly did something. */
@Composable
fun RefreshIcon(tint: Color, size: androidx.compose.ui.unit.Dp, spinning: Boolean = false) {
    val turn = remember { Animatable(0f) }
    LaunchedEffect(spinning) {
        if (spinning) {
            while (true) {
                turn.animateTo(turn.value + 360f, tween(900, easing = LinearEasing))
            }
        } else {
            // Finish the turn it is on, so it doesn't stop at an odd angle
            val rest = (turn.value / 360f).let { kotlin.math.ceil(it) } * 360f
            turn.animateTo(rest, tween(250, easing = Motion.EnterEasing))
            turn.snapTo(0f)
        }
    }
    Canvas(Modifier.size(size).graphicsLayer { rotationZ = turn.value }) {
        // An open circle turning clockwise, with a solid arrowhead where it ends (at the top),
        // pointing the way it turns: the usual refresh sign, reading clearly at 18–22 dp
        val w = (this.size.minDimension * 0.11f).coerceAtLeast(1.6.dp.toPx())
        val head = this.size.minDimension * 0.26f
        val r = this.size.minDimension / 2 - head * 0.5f
        val c = Offset(this.size.width / 2, this.size.height / 2)
        val start = 0f
        val sweep = 300f
        drawArc(
            tint, start, sweep, false,
            Offset(c.x - r, c.y - r), androidx.compose.ui.geometry.Size(r * 2, r * 2),
            style = Stroke(w, cap = StrokeCap.Round),
        )
        // The arc's end, and the way it is heading there (clockwise on screen)
        val end = Math.toRadians((start + sweep).toDouble())
        val out = Offset(Math.cos(end).toFloat(), Math.sin(end).toFloat())
        val ahead = Offset(-out.y, out.x)
        val at = c + out * r
        val arrow = androidx.compose.ui.graphics.Path().apply {
            val tip = at + ahead * (head * 0.6f)
            val back = at - ahead * (head * 0.2f)
            moveTo(tip.x, tip.y)
            lineTo((back + out * (head * 0.5f)).x, (back + out * (head * 0.5f)).y)
            lineTo((back - out * (head * 0.5f)).x, (back - out * (head * 0.5f)).y)
            close()
        }
        drawPath(arrow, tint)
    }
}
