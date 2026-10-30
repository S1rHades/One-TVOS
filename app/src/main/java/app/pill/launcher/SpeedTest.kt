package app.pill.launcher

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.coroutineContext

/** What a speed test found. Speeds in megabits per second; null where that part failed. */
data class SpeedResult(val pingMs: Int?, val downMbps: Double?, val upMbps: Double?, val at: Instant)

/** Where a running test has got to. */
enum class SpeedPhase { Ping, Download, Upload }

/** Megabits per second for [bytes] moved in [nanos]. */
fun mbps(bytes: Long, nanos: Long): Double = if (nanos <= 0) 0.0 else bytes * 8.0 / (nanos / 1e9) / 1e6

/** What a download speed is good for, in plain words. */
fun speedVerdict(downMbps: Double): String = when {
    downMbps >= 25 -> "Good for 4K streaming"
    downMbps >= 10 -> "Good for HD streaming"
    downMbps >= 4 -> "Fine for standard video"
    else -> "Slow: video may stop to load"
}

/**
 * Measures the connection against Cloudflare's speed test service: latency (the quickest
 * of a few tiny requests), then download and upload, each for a few seconds, reporting
 * the running speed as it goes. Cancelling the coroutine stops it within a moment.
 */
class SpeedTest(context: Context) {
    private val prefs = context.getSharedPreferences("speedtest", Context.MODE_PRIVATE)

    /** The last finished test, if there was one. */
    fun last(): SpeedResult? {
        val at = prefs.getLong("at", 0L).takeIf { it > 0 } ?: return null
        return SpeedResult(
            pingMs = prefs.getInt("ping", -1).takeIf { it >= 0 },
            downMbps = prefs.getFloat("down", -1f).takeIf { it >= 0 }?.toDouble(),
            upMbps = prefs.getFloat("up", -1f).takeIf { it >= 0 }?.toDouble(),
            at = Instant.ofEpochMilli(at),
        )
    }

    suspend fun measure(onProgress: (SpeedPhase, Double) -> Unit): SpeedResult = withContext(Dispatchers.IO) {
        onProgress(SpeedPhase.Ping, 0.0)
        val ping = runCatching { ping() }.getOrNull()
        onProgress(SpeedPhase.Download, 0.0)
        val down = runCatching { download { onProgress(SpeedPhase.Download, it) } }.getOrNull()
        onProgress(SpeedPhase.Upload, 0.0)
        val up = runCatching { upload { onProgress(SpeedPhase.Upload, it) } }.getOrNull()
        if (ping == null && down == null && up == null) error("Can't reach the test server")
        val result = SpeedResult(ping, down, up, Instant.now())
        prefs.edit()
            .putLong("at", result.at.toEpochMilli())
            .putInt("ping", ping ?: -1)
            .putFloat("down", down?.toFloat() ?: -1f)
            .putFloat("up", up?.toFloat() ?: -1f)
            .apply()
        result
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 6000
        readTimeout = 6000
        setRequestProperty("User-Agent", "PillLauncher")
    }

    /** The quickest of five tiny requests, in ms (the first also sets up the connection). */
    private suspend fun ping(): Int {
        val times = (0 until 5).map {
            coroutineContext.ensureActive()
            val c = open("$BASE/__down?bytes=0")
            val start = System.nanoTime()
            try {
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
            (System.nanoTime() - start) / 1_000_000
        }
        return times.drop(1).minOrNull()?.toInt() ?: times.first().toInt()
    }

    /** Downloads for about [seconds], one large file after another; the average speed. */
    private suspend fun download(seconds: Int = 7, report: (Double) -> Unit): Double {
        val buffer = ByteArray(64 * 1024)
        val start = System.nanoTime()
        val until = start + seconds * 1_000_000_000L
        var bytes = 0L
        var lastReport = start
        while (System.nanoTime() < until) {
            val c = open("$BASE/__down?bytes=$CHUNK")
            try {
                c.inputStream.use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        bytes += n
                        val now = System.nanoTime()
                        if (now - lastReport > 150_000_000L) {
                            report(mbps(bytes, now - start))
                            lastReport = now
                        }
                        if (now >= until) break
                    }
                }
            } finally {
                c.disconnect()
            }
        }
        return mbps(bytes, System.nanoTime() - start).also(report)
    }

    /** Uploads for about [seconds], in chunks; the average speed. */
    private suspend fun upload(seconds: Int = 5, report: (Double) -> Unit): Double {
        val block = ByteArray(64 * 1024) { (it * 31).toByte() }
        val start = System.nanoTime()
        val until = start + seconds * 1_000_000_000L
        var bytes = 0L
        var lastReport = start
        while (System.nanoTime() < until) {
            val c = open("$BASE/__up")
            c.doOutput = true
            c.requestMethod = "POST"
            c.setFixedLengthStreamingMode(UP_CHUNK)
            try {
                c.outputStream.use { out ->
                    var sent = 0L
                    while (sent < UP_CHUNK) {
                        coroutineContext.ensureActive()
                        val n = minOf(block.size.toLong(), UP_CHUNK - sent).toInt()
                        out.write(block, 0, n)
                        sent += n
                        bytes += n
                        val now = System.nanoTime()
                        if (now - lastReport > 150_000_000L) {
                            report(mbps(bytes, now - start))
                            lastReport = now
                        }
                    }
                }
                c.inputStream.use { it.readBytes() }
            } finally {
                c.disconnect()
            }
        }
        return mbps(bytes, System.nanoTime() - start).also(report)
    }

    private companion object {
        const val BASE = "https://speed.cloudflare.com"
        const val CHUNK = 25_000_000L
        const val UP_CHUNK = 4_000_000L
    }
}
