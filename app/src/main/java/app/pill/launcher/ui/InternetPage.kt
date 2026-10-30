package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.SpeedPhase
import app.pill.launcher.SpeedResult
import app.pill.launcher.SpeedTest
import app.pill.launcher.speedVerdict
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.log10

private val GoodGreen = Color(0xFF5FD38A)

/**
 * The Internet page, from the rail: a speed test (ping, then download on a dial and a
 * graph, then upload) with Start test / Test again, and a button to the TV's own network
 * settings. Back closes it, stopping a test that's running.
 */
@Composable
fun InternetPage(onOpenNetworkSettings: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val tester = remember { SpeedTest(context) }
    val network by rememberNetworkStatus()
    val scope = rememberCoroutineScope()
    var last by remember { mutableStateOf(tester.last()) }
    var phase by remember { mutableStateOf<SpeedPhase?>(null) }
    var result by remember { mutableStateOf<SpeedResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var down by remember { mutableDoubleStateOf(0.0) }
    var up by remember { mutableDoubleStateOf(0.0) }
    var ping by remember { mutableStateOf<Int?>(null) }
    val samples = remember { mutableStateListOf<Double>() }
    var job by remember { mutableStateOf<Job?>(null) }
    val startFocus = remember { FocusRequester() }

    fun start() {
        if (job?.isActive == true) return
        result = null
        error = null
        down = 0.0
        up = 0.0
        ping = null
        samples.clear()
        job = scope.launch {
            try {
                val r = tester.measure { p, mbps ->
                    // Called off the main thread; state writes are safe from any thread
                    phase = p
                    if (mbps > 0 && p == SpeedPhase.Download) {
                        down = mbps
                        samples.add(mbps)
                    }
                    if (mbps > 0 && p == SpeedPhase.Upload) up = mbps
                }
                ping = r.pingMs
                r.downMbps?.let { down = it }
                r.upMbps?.let { up = it }
                result = r
                last = r
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "The test didn't finish"
            } finally {
                phase = null
            }
        }
    }
    val running = phase != null

    OverlayPage(onDismiss = onDismiss, width = 880.dp, framed = false) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { startFocus.requestFocus() }
        }
        PageTitle("Internet", "Test your connection · Back to close", Modifier.reveal(0))
        Row(Modifier.fillMaxWidth().height(404.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageCard(1, Modifier.weight(1.1f).fillMaxHeight()) {
                PageLabel(if (phase == SpeedPhase.Upload) "Upload" else "Download")
                Box(Modifier.fillMaxWidth().height(200.dp).padding(top = 12.dp), contentAlignment = Alignment.Center) {
                    Dial(if (phase == SpeedPhase.Upload) up else down, Modifier.size(width = 280.dp, height = 180.dp))
                    Column(Modifier.padding(top = 50.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            String.format(Locale.ROOT, "%.1f", if (phase == SpeedPhase.Upload) up else down),
                            style = TextStyle(fontFamily = FlexDisplay, color = Palette.TextPrimary, fontSize = 60.sp, fontWeight = FontWeight(260)),
                            maxLines = 1,
                        )
                        Text("Mbps", color = Palette.TextSecondary, fontSize = 15.sp)
                    }
                }
                Text(
                    when {
                        phase == SpeedPhase.Ping -> "Checking the connection…"
                        phase == SpeedPhase.Download -> "Testing download…"
                        phase == SpeedPhase.Upload -> "Testing upload…"
                        error != null -> error!!
                        result != null -> "Tested just now"
                        else -> "Press OK to start"
                    },
                    color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                val verdict = result?.downMbps?.let { speedVerdict(it) }
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    if (verdict != null) {
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(GoodGreen.copy(alpha = 0.18f))
                                .border(1.dp, GoodGreen.copy(alpha = 0.45f), RoundedCornerShape(50))
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(8.dp).background(GoodGreen, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(verdict, color = Palette.TextPrimary, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                PageButtons(
                    first = when {
                        running -> "Testing…"
                        result != null || error != null -> "Test again"
                        else -> "Start test"
                    },
                    onFirst = { start() },
                    onSecond = onOpenNetworkSettings,
                    firstFocus = startFocus,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            PageCard(2, Modifier.width(380.dp).fillMaxHeight()) {
                PageLabel("Results")
                ResultRow("Ping", ping?.let { "$it" } ?: "–", "ms")
                ResultRow("Download", result?.downMbps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "–", "Mbps")
                ResultRow("Upload", result?.upMbps?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "–", "Mbps")
                Spacer(Modifier.height(12.dp))
                SpeedGraph(samples, Modifier.fillMaxWidth().height(64.dp))
                Spacer(Modifier.weight(1f))
                Text(
                    network.label + when (network.link) {
                        Link.WiFi -> " · signal " + listOf("very weak", "weak", "fair", "good", "excellent")[network.wifiBars.coerceIn(0, 4)]
                        else -> ""
                    } + if (network.vpn) " · VPN on" else "",
                    color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1,
                )
                last?.takeIf { it != result }?.let { l ->
                    Text(
                        "Last test: " + ageText(l.at).lowercase() + (l.downMbps?.let { ", " + String.format(Locale.ROOT, "%.0f", it) + " Mbps" } ?: ""),
                        color = Palette.TextTertiary, fontSize = 13.sp, maxLines = 1,
                    )
                }
                Text("Measured with Cloudflare's speed test", color = Palette.TextTertiary, fontSize = 12.sp, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** A half-circle dial, filling as the speed rises (log scale: 1, 10, 100, 1000 Mbps). */
@Composable
private fun Dial(mbps: Double, modifier: Modifier) {
    val tint = LocalWallpaperTint.current
    val shown by animateFloatAsState((log10(1 + mbps) / 3.0).toFloat().coerceIn(0f, 1f), tween(Motion.Quick), label = "dial")
    Canvas(modifier) {
        val stroke = 14.dp.toPx()
        val d = minOf(size.width, size.height * 2) - stroke
        val topLeft = Offset((size.width - d) / 2, stroke / 2)
        drawArc(Color.White.copy(alpha = 0.14f), 180f, 180f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        if (shown > 0.005f) drawArc(tint.value, 180f, 180f * shown, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
    }
}

/** The download speed as it was measured, left to right. */
@Composable
private fun SpeedGraph(samples: List<Double>, modifier: Modifier) {
    val tint = LocalWallpaperTint.current
    Canvas(modifier) {
        drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        if (samples.size < 2) return@Canvas
        val top = samples.max().coerceAtLeast(1.0)
        val path = Path()
        // Room for a full test's samples, so the line grows rightwards as it runs
        val slots = maxOf(samples.size, 46)
        for ((i, v) in samples.withIndex()) {
            val x = i * size.width / (slots - 1)
            val y = size.height - (v / top).toFloat() * (size.height - 4.dp.toPx())
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, tint.value, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun ResultRow(label: String, value: String, unit: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, color = Palette.TextSecondary, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight(400), maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Text(unit, color = Palette.TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(bottom = 4.dp))
    }
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(1.dp).background(Color(0x1FFFFFFF)))
}

/**
 * Two buttons side by side, [first] (the test) and Network settings; a white pill glides
 * to the focused one.
 */
@Composable
private fun PageButtons(first: String, onFirst: () -> Unit, onSecond: () -> Unit, firstFocus: FocusRequester, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    var focused by remember { mutableIntStateOf(0) }
    val placed = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val spot = placed[focused]
    val x by animateDpAsState(with(density) { (spot?.first ?: 0f).toDp() }, Motion.glide(), label = "buttonX")
    val w by animateDpAsState(with(density) { (spot?.second ?: 0f).toDp() }, Motion.glide(), label = "buttonW")
    Box(modifier) {
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .height(42.dp)
                .shadow(10.dp, RoundedCornerShape(50))
                .background(Palette.Focus, RoundedCornerShape(50)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((i, label) in listOf(first, "Network settings").withIndex()) {
                val on = i == focused
                val fg by animateColorAsState(if (on) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "buttonText")
                Row(
                    Modifier
                        .onPlaced { placed[i] = it.positionInParent().x to it.size.width.toFloat() }
                        .height(42.dp)
                        .then(if (i == 0) Modifier.width(150.dp) else Modifier)
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Color.Transparent else Color(0x1FFFFFFF))
                        .border(1.dp, if (on) Color.Transparent else Color(0x33FFFFFF), RoundedCornerShape(50))
                        .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) focused = i }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (i == 0) onFirst() else onSecond() }
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    if (i == 1) {
                        WifiIcon(NetworkStatus(Link.WiFi, 4, false), fg, 18.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight(600), maxLines = 1)
                }
            }
        }
    }
}
