package app.pill.launcher.ui

import app.pill.launcher.tr
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import android.content.Context
import android.app.ActivityManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.Weather
import androidx.compose.ui.input.key.key
import app.pill.launcher.sunTimes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The cards that read the TV itself: sunrise and sunset, and system info.

/** Today's sunrise and sunset, worked out on the TV from the weather's location. */
@Composable
internal fun DaylightCard(weather: Weather?, compact: Boolean, modifier: Modifier) {
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
            CardLabel(if (compact) tr("Daylight") else tr("Sunrise & sunset"))
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
                        Text(if (weather == null) tr("Finding your area…") else tr("No sunrise today"), style = lineStyle(15.sp), maxLines = 2)
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
internal fun SystemCard(compact: Boolean, modifier: Modifier) {
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
                // The icons say how it's connected (Wi-Fi strength or Ethernet, crossed out offline; a key for VPN)
                WifiIcon(network, if (online) Color.White else Color(0xFFFF8787), 16.dp)
                if (network.vpn) {
                    Spacer(Modifier.width(4.dp))
                    KeyIcon(Color(0xFF8CE99A), 16.dp)
                }
                Spacer(Modifier.width(7.dp))
                CardLabel(
                    if (compact) tr("System") else tr("System · Android %1\$s", android.os.Build.VERSION.RELEASE),
                    color = if (online) Palette.TextTertiary else Color(0xFFFF8787),
                )
            }
            usage?.let { u ->
                UsageBar(tr("Storage"), u.storageFree, u.storageTotal)
                UsageBar(tr("Memory"), u.memoryFree, u.memoryTotal)
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
            Text(tr("%1\$s free", formatBytes(free)), style = lineStyle(14.sp, if (low) Color(0xFFFFC078) else Palette.TextPrimary, 600), maxLines = 1)
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

internal fun formatBytes(bytes: Long): String {
    val gb = bytes / 1e9
    return if (gb >= 10) "%.0f GB".format(gb) else if (gb >= 1) "%.1f GB".format(gb) else "%.0f MB".format(bytes / 1e6)
}
