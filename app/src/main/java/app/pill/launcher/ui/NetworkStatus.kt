package app.pill.launcher.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.delay

enum class Link { Offline, WiFi, Ethernet, Mobile, Other }

/** How the TV is connected, the Wi-Fi signal (0-4 bars) and whether a VPN is up. */
data class NetworkStatus(val link: Link, val wifiBars: Int, val vpn: Boolean) {
    val label: String
        get() = when (link) {
            Link.Offline -> "Offline"
            Link.WiFi -> "Wi-Fi"
            Link.Ethernet -> "Ethernet"
            Link.Mobile -> "Mobile data"
            Link.Other -> "Online"
        }
}

/**
 * The current [NetworkStatus], updated the moment a network or VPN comes or goes (a
 * network callback) and every 10 seconds for the Wi-Fi signal strength.
 */
@Composable
fun rememberNetworkStatus(): State<NetworkStatus> {
    val context = LocalContext.current.applicationContext
    val status = remember { mutableStateOf(readNetwork(context)) }
    DisposableEffect(context) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { status.value = readNetwork(context) }
            override fun onLost(network: Network) { status.value = readNetwork(context) }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { status.value = readNetwork(context) }
        }
        // Without NOT_VPN, so VPN networks are reported too
        val request = NetworkRequest.Builder().removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN).build()
        val registered = runCatching { cm?.registerNetworkCallback(request, callback) }.isSuccess && cm != null
        onDispose { if (registered) runCatching { cm?.unregisterNetworkCallback(callback) } }
    }
    LaunchedEffect(context) {
        while (true) {
            delay(10_000)
            status.value = readNetwork(context)
        }
    }
    return status
}

@Suppress("DEPRECATION") // allNetworks and connectionInfo: the replacements need newer Android than many TVs run
fun readNetwork(context: Context): NetworkStatus {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return NetworkStatus(Link.Offline, 0, false)
    val all = runCatching { cm.allNetworks.mapNotNull { cm.getNetworkCapabilities(it) } }.getOrDefault(emptyList())
    val vpn = all.any { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }
    // With a VPN up the default network is the VPN; the link is the network underneath it
    val physical = all.filter { !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) }
    val link = when {
        physical.any { it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) } -> Link.Ethernet
        physical.any { it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) } -> Link.WiFi
        physical.any { it.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) } -> Link.Mobile
        physical.isNotEmpty() -> Link.Other
        else -> Link.Offline
    }
    val bars = if (link != Link.WiFi) 0 else runCatching {
        val wifi = context.getSystemService(WifiManager::class.java)
        WifiManager.calculateSignalLevel(wifi.connectionInfo.rssi, 5)
    }.getOrDefault(3)
    return NetworkStatus(link, bars, vpn)
}

/** Wi-Fi fan with [bars] of 4 lit (a dot plus three arcs), or crossed out when offline. */
@Composable
fun WifiIcon(status: NetworkStatus, tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = s * 0.1f
        val base = Offset(s / 2, s * 0.82f)
        if (status.link == Link.Ethernet) {
            // Three linked boxes, a common network glyph
            val box = Size(s * 0.26f, s * 0.22f)
            val top = Offset(s / 2 - box.width / 2, s * 0.12f)
            drawRect(tint, top, box, style = Stroke(stroke * 0.8f))
            drawLine(tint, Offset(s / 2, top.y + box.height), Offset(s / 2, s * 0.52f), stroke * 0.8f)
            drawLine(tint, Offset(s * 0.22f, s * 0.52f), Offset(s * 0.78f, s * 0.52f), stroke * 0.8f)
            for (x in listOf(s * 0.22f, s * 0.78f)) {
                drawLine(tint, Offset(x, s * 0.52f), Offset(x, s * 0.62f), stroke * 0.8f)
                drawRect(tint, Offset(x - box.width / 2, s * 0.62f), box, style = Stroke(stroke * 0.8f))
            }
            return@Canvas
        }
        val lit = if (status.link == Link.Offline) 0 else status.wifiBars.coerceIn(1, 4)
        fun shade(level: Int) = if (level <= lit) tint else tint.copy(alpha = tint.alpha * 0.3f)
        drawCircle(shade(1), radius = stroke * 0.75f, center = base)
        for (i in 1..3) {
            val r = s * (0.2f + 0.19f * i) - s * 0.1f
            drawArc(
                shade(i + 1),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(base.x - r, base.y - r),
                size = Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        if (status.link == Link.Offline) {
            drawLine(tint, Offset(s * 0.18f, s * 0.14f), Offset(s * 0.82f, s * 0.86f), stroke, StrokeCap.Round)
        }
    }
}

/** A key: round bow, shaft and two teeth. Shown while a VPN is on. */
@Composable
fun KeyIcon(tint: Color, iconSize: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = s * 0.11f
        val bow = Offset(s * 0.3f, s * 0.5f)
        drawCircle(tint, radius = s * 0.17f, center = bow, style = Stroke(stroke))
        drawLine(tint, Offset(bow.x + s * 0.17f, bow.y), Offset(s * 0.9f, bow.y), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.72f, bow.y), Offset(s * 0.72f, bow.y + s * 0.17f), stroke, StrokeCap.Round)
        drawLine(tint, Offset(s * 0.86f, bow.y), Offset(s * 0.86f, bow.y + s * 0.13f), stroke, StrokeCap.Round)
    }
}
