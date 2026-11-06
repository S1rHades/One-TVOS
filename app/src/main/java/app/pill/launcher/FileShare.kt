package app.pill.launcher

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Timer
import java.util.concurrent.TimeUnit
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.Semaphore
import java.util.concurrent.SynchronousQueue
import java.util.TimerTask
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.schedule

/** What a file is, for its card and the chips on the Files page. */
enum class FileKind(val title: String) { Photo("Photos"), Video("Videos"), App("Apps"), Document("Documents") }

private val PhotoTypes = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif")
private val VideoTypes = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "3gp")

fun fileKind(name: String): FileKind = when (name.substringAfterLast('.', "").lowercase()) {
    in PhotoTypes -> FileKind.Photo
    in VideoTypes -> FileKind.Video
    "apk" -> FileKind.App
    else -> FileKind.Document
}

/** A file on the TV, in the Files folder. */
data class SharedFile(val name: String, val size: Long, val modified: Long, val from: String?) {
    val kind: FileKind get() = fileKind(name)
}

/** A file on its way to the TV. */
data class Transfer(val name: String, val read: Long, val total: Long, val from: String)

/**
 * What tidying found: APKs whose app is [installed] (kept: the TV asks before deleting
 * them), [old] files it removed, and the files that go tomorrow (with when that was noticed).
 */
data class Tidied(val installed: List<String>, val old: List<String>, val tomorrow: List<Pair<String, Long>>)

/** A phone allowed to use Files: paired by scanning the code, or by typing it and an OK on the TV. */
data class TrustedDevice(val id: String, val name: String, val pairedAt: Long, val lastUsed: Long)

/** A phone that typed the code and waits for an OK on the TV. */
data class PairRequest(val id: String, val name: String, val at: Long)

/** A request from a phone: when, from where, what it asked for, and what the TV answered (or what went wrong). */
data class Visit(val at: Long, val from: String, val asked: String, val outcome: String)

data class ShareState(
    /** Taking files now. */
    val running: Boolean = false,
    /** The TV's address on the home network, or null when it has none. */
    val address: String? = null,
    val port: Int = 0,
    /** The six digits to type on a phone that can't scan. */
    val code: String = "",
    /** The long key the QR code carries: scanning it pairs the phone with no typing. */
    val key: String = "",
    val transfers: List<Transfer> = emptyList(),
    val files: List<SharedFile> = emptyList(),
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Why it can't take files, in plain words. */
    val problem: String? = null,
    /** Still taking files until this time (ms) after the launcher went behind another app; null otherwise. */
    val until: Long? = null,
    /** A phone waiting for Allow or Block on the TV. */
    val pending: PairRequest? = null,
    /** Phones paired in the last 90 days. */
    val devices: List<TrustedDevice> = emptyList(),
    /** The last request a phone made, and how it went: shows whether a phone reaches the TV at all. */
    val lastVisit: Visit? = null,
) {
    /** What the QR code opens: the address with the pairing key. */
    val link: String? get() = address?.let { "http://$it:$port/?p=$key" }
}

/** "folder/../My photo?.jpg" → "My photo.jpg": a name safe to save under, never empty. */
fun safeFileName(raw: String?): String {
    val last = raw.orEmpty().replace('\\', '/').substringAfterLast('/')
    val clean = last.filter { it >= ' ' && it !in "/\\:*?\"<>|" }.trim().trim('.').trim()
    return clean.take(120).ifEmpty { "file" }
}

/** [name], or "name (2).ext" and so on when [taken] says it is used. */
fun uniqueFileName(name: String, taken: (String) -> Boolean): String {
    if (!taken(name)) return name
    val dot = name.lastIndexOf('.').takeIf { it > 0 }
    val base = if (dot != null) name.substring(0, dot) else name
    val ext = if (dot != null) name.substring(dot) else ""
    var i = 2
    while (taken("$base ($i)$ext")) i++
    return "$base ($i)$ext"
}

/** Who sent a file, from the browser's User-Agent: "an iPhone", "an Android phone", "a computer". */
fun senderOf(userAgent: String?): String {
    val ua = userAgent.orEmpty()
    return when {
        "iPhone" in ua -> "an iPhone"
        "iPad" in ua -> "an iPad"
        "Android" in ua && "Mobile" in ua -> "an Android phone"
        "Android" in ua -> "an Android tablet"
        listOf("Windows", "Macintosh", "CrOS", "Linux", "X11").any { it in ua } -> "a computer"
        else -> "a phone"
    }
}

/** A device's name for the list of trusted devices: "iPhone", "Android phone", "Computer". */
fun deviceName(userAgent: String?): String =
    senderOf(userAgent).removePrefix("an ").removePrefix("a ").let { name ->
        // "iPhone" and "iPad" keep their small i
        if (name.startsWith("iP")) name else name.replaceFirstChar { it.uppercase() }
    }

/** Only devices on the home network (and the TV itself) may use the share. */
fun isHomeNetwork(a: InetAddress): Boolean =
    a.isLoopbackAddress || a.isSiteLocalAddress || a.isLinkLocalAddress ||
        (a is Inet6Address && (a.address[0].toInt() and 0xFE) == 0xFC)

/** The TV's IPv4 address on the home network: wired first, then Wi-Fi, then anything else. */
fun homeAddress(): String? = runCatching {
    NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual }
        .sortedBy { when { it.name.startsWith("eth") -> 0; it.name.startsWith("wlan") -> 1; else -> 2 } }
        .flatMap { it.inetAddresses.toList() }
        .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }
        ?.hostAddress
}.getOrNull()

private const val FirstPort = 8642
/** Connections handled at once (each phone's browser opens several). */
private const val MaxConnections = 32
/** How long a connection may wait for its request, or stall in the middle of one. */
private const val IdleMs = 60_000
private const val SpareBytes = 50L * 1000 * 1000
private const val SendersFile = ".senders.json"
private const val Cookie = "pill"
/** A paired phone stays trusted this long. */
private const val TrustMs = 90L * 24 * 3600 * 1000
/** A phone waiting for an OK on the TV gives up after this. */
private const val PairWaitMs = 60_000L
/** A link from Send to phone works this long. */
private const val LinkMs = 15 * 60 * 1000L
/** Wrong codes from one address before it is turned away for [BlockMs]. */
private const val MaxWrongCodes = 10
private const val BlockMs = 10 * 60 * 1000L

/**
 * Files between the TV and phones on the same network, with nothing to install: while it
 * runs, a small web server answers on [FirstPort] at the TV's home-network address only.
 *
 * Getting in: scanning the TV's QR code (it carries a long random key) pairs a phone at
 * once; typing the six-digit code instead asks for Allow on the TV. A paired phone gets a
 * private cookie and stays trusted for 90 days, until removed or until every phone is
 * signed out. Every request must name the TV's own address (so another website can't
 * borrow the phone's browser) and anything that changes something must carry a header
 * other websites can't add. At most 32 connections are handled at once, and 2 uploads.
 */
class FileShare(
    val dir: File,
    /** The page phones open (share/index.html from the app's assets). */
    private val page: () -> String,
    /** The TV's address; replaceable for tests. */
    private val address: () -> String? = ::homeAddress,
    /** Where paired phones are kept (hashed), outside the shared folder. */
    private val devicesFile: File = File(dir.parentFile ?: dir, "share-devices.json"),
    /** The interface to listen on for an address: that address itself (screenshot tests listen on this machine). */
    private val listenOn: (String) -> InetAddress? = { runCatching { InetAddress.getByName(it) }.getOrNull() },
    code: String = newCode(),
    key: String = newKey(),
) {
    val state = MutableStateFlow(ShareState(code = code, key = key))

    /** Each file as it arrives. */
    val arrivals = MutableSharedFlow<SharedFile>(extraBufferCapacity = 16)

    @Volatile private var socket: ServerSocket? = null
    private var pool: ThreadPoolExecutor? = null
    private val uploads = Semaphore(2)
    private val timer = Timer("share-timer", true)
    private var stopTask: TimerTask? = null
    private val transfers = ConcurrentHashMap<String, Transfer>()
    private val wrongCodes = ConcurrentHashMap<String, Pair<Int, Long>>()
    /** Approved requests waiting to collect their cookie, by request id. */
    private val approved = ConcurrentHashMap<String, String>()
    private val blocked = ConcurrentHashMap.newKeySet<String>()
    /** Send to phone links: token to (file name, expiry). */
    private val links = ConcurrentHashMap<String, Pair<String, Long>>()
    private val random = SecureRandom()
    /** Hash of each paired phone's cookie. */
    private val tokens = ConcurrentHashMap<String, TrustedDevice>()

    init {
        dir.mkdirs()
        readDevices()
        refresh()
    }

    /** Starts taking files (or keeps going, cancelling a pending stop). */
    @Synchronized
    fun start() {
        stopTask?.cancel()
        stopTask = null
        val here = address()
        if (socket != null) {
            // Still listening where the code points. When the TV's address has changed (a new
            // lease from the router, Wi-Fi to cable), the code would point at the new address
            // while the server listened on the old one: start again on the new one
            if (here == null || here == state.value.address) {
                state.value = state.value.copy(until = null)
                return
            }
            stop()
        }
        if (here == null) {
            state.value = state.value.copy(running = false, address = null, problem = "Connect the TV to your home network to share files")
            return
        }
        val bind = listenOn(here) ?: return run {
            state.value = state.value.copy(running = false, problem = "Connect the TV to your home network to share files")
        }
        val bound = (FirstPort until FirstPort + 8).firstNotNullOfOrNull { port ->
            runCatching { ServerSocket(port, 16, bind) }.getOrNull()
        }
        if (bound == null) {
            state.value = state.value.copy(running = false, problem = "Another app is using the ports file sharing needs")
            return
        }
        socket = bound
        // A thread for each connection, up to [MaxConnections]: a phone's browser opens a few
        // connections ahead of time and may leave them unused, and with a small fixed pool those
        // held every thread, so the phone's real request waited unanswered until they timed out
        val workers = ThreadPoolExecutor(0, MaxConnections, 30, TimeUnit.SECONDS, SynchronousQueue()) { r -> Thread(r, "share").apply { isDaemon = true } }
        pool = workers
        Thread({ acceptLoop(bound, workers) }, "share-accept").apply { isDaemon = true }.start()
        state.value = state.value.copy(running = true, address = here, port = bound.localPort, until = null, problem = null)
        refresh()
    }

    /** Stops now, or after [afterMs] (to keep taking files for a while after the launcher goes behind another app). */
    @Synchronized
    fun stop(afterMs: Long = 0) {
        stopTask?.cancel()
        if (afterMs > 0 && socket != null) {
            state.value = state.value.copy(until = System.currentTimeMillis() + afterMs)
            stopTask = timer.schedule(afterMs) { stop() }
            return
        }
        stopTask = null
        runCatching { socket?.close() }
        socket = null
        pool?.shutdownNow()
        pool = null
        transfers.clear()
        state.value = state.value.copy(running = false, until = null, transfers = emptyList(), pending = null)
    }

    /** Re-reads the folder: the files, newest first, and the space left. */
    fun refresh() {
        val senders = readSenders()
        val files = dir.listFiles().orEmpty()
            .filter { it.isFile && !it.name.startsWith(".") }
            .map { SharedFile(it.name, it.length(), it.lastModified(), senders[it.name]) }
            .sortedByDescending { it.modified }
        state.value = state.value.copy(files = files, freeBytes = dir.usableSpace, totalBytes = dir.totalSpace)
    }

    fun file(name: String): File? = File(dir, safeFileName(name)).takeIf { it.isFile && it.parentFile == dir }

    fun delete(name: String) {
        file(name)?.delete()
        writeSenders(readSenders() - name)
        refresh()
    }

    /**
     * Files tidying itself. APKs whose app is now installed ([installed] says) are only
     * found, never deleted: the TV asks first. With [removeOld], files older than 30 days
     * go. Returns those APKs, what went, and what goes tomorrow.
     */
    fun tidy(installed: (File) -> Boolean, removeOld: Boolean, now: Long = System.currentTimeMillis()): Tidied {
        val day = 24 * 3600 * 1000L
        val apks = mutableListOf<String>()
        val old = mutableListOf<String>()
        val tomorrow = mutableListOf<Pair<String, Long>>()
        for (f in dir.listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") }) {
            val age = now - f.lastModified()
            when {
                removeOld && age >= 30 * day -> if (f.delete()) old += f.name
                removeOld && age >= 29 * day -> tomorrow += f.name to (f.lastModified() + 29 * day)
            }
            if (f.exists() && f.name.endsWith(".apk", ignoreCase = true) && runCatching { installed(f) }.getOrDefault(false)) apks += f.name
        }
        if (old.isNotEmpty()) {
            writeSenders(readSenders() - old.toSet())
            refresh()
        }
        return Tidied(apks, old, tomorrow)
    }

    /** A link a phone can open to save [name] for the next 15 minutes, while sharing runs. */
    fun linkFor(name: String): String? {
        val s = state.value
        val host = s.address ?: return null
        if (!s.running || file(name) == null) return null
        val token = hex(16)
        links[token] = name to System.currentTimeMillis() + LinkMs
        return "http://$host:${s.port}/f/$token"
    }

    /** Allow on the TV: the waiting phone collects its cookie on its next check. */
    fun approve(requestId: String) {
        val request = state.value.pending?.takeIf { it.id == requestId } ?: return
        approved[requestId] = trust(request.name)
        state.value = state.value.copy(pending = null)
    }

    /** Block on the TV. */
    fun block(requestId: String) {
        blocked += requestId
        if (state.value.pending?.id == requestId) state.value = state.value.copy(pending = null)
    }

    /** Removes one phone: it needs pairing again. */
    fun forget(deviceId: String) {
        tokens.entries.removeAll { it.value.id == deviceId }
        saveDevices()
    }

    /** Signs out every phone, with a new code and QR key. */
    fun signOutAll() {
        tokens.clear()
        approved.clear()
        saveDevices()
        state.value = state.value.copy(code = newCode(random), key = newKey(random), pending = null)
    }

    /** A phone asking to pair with the typed code (also how tests make one). */
    fun askToPair(name: String): PairRequest {
        val request = PairRequest(hex(8), name, System.currentTimeMillis())
        state.value = state.value.copy(pending = request)
        timer.schedule(PairWaitMs) {
            if (state.value.pending?.id == request.id) state.value = state.value.copy(pending = null)
            blocked += request.id
        }
        return request
    }

    private fun trust(name: String): String {
        val token = hex(32)
        val now = System.currentTimeMillis()
        tokens[sha(token)] = TrustedDevice(hex(4), name, now, now)
        saveDevices()
        return token
    }

    private fun deviceFor(request: Request): TrustedDevice? {
        val token = request.cookies[Cookie] ?: return null
        val hash = sha(token)
        val device = tokens[hash] ?: return null
        val now = System.currentTimeMillis()
        if (now - device.pairedAt > TrustMs) {
            tokens.remove(hash)
            saveDevices()
            return null
        }
        if (now - device.lastUsed > 60_000) {
            tokens[hash] = device.copy(lastUsed = now)
            saveDevices()
        }
        return device
    }

    private fun acceptLoop(server: ServerSocket, workers: ThreadPoolExecutor) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                continue
            }
            // Busy ([MaxConnections] open at once): turn it away rather than pile up
            runCatching { workers.execute { client.use { handle(it) } } }.onFailure { runCatching { client.close() } }
        }
    }

    private class Request(
        val method: String,
        val path: String,
        val params: Map<String, String>,
        val headers: Map<String, String>,
        val cookies: Map<String, String>,
        val body: InputStream,
    )

    private fun handle(client: Socket) {
        // A connection opened ahead of time is kept for a minute (longer than a browser keeps one
        // it hasn't used): closing it sooner can catch the browser just as it sends its request
        // there, and a browser that keeps finding its connections closed retries, then gives up
        client.soTimeout = IdleMs
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val remote = client.inetAddress
        if (!isHomeNetwork(remote)) return respond(out, 403, "text/plain", "Only devices on the home network can share files")
        // A connection the browser opened ahead of time and never used, or closed: just close it.
        // Answering it ("Bad request") would leave a stale reply for the browser's next use of it.
        val request = runCatching { readRequest(input) }.getOrNull() ?: return
        val asked = if (request.path == "/" && request.params.containsKey("p")) "the scanned code" else "${request.method} ${request.path}"
        // Whatever goes wrong, the phone gets an answer: a connection closed with none makes Chrome
        // send the request again and again, then give up with ERR_TOO_MANY_RETRIES
        // Noted as the answer's first line goes out, so the TV shows it before the phone has it.
        // The browser's own icon request says nothing about how the visit went
        val answer = StatusOut(out) { status ->
            if (request.path != "/favicon.ico") visit(remote, asked, if (status < 400) "answered" else "turned away ($status)")
        }
        try {
            serve(request, remote, answer)
        } catch (e: Throwable) {
            // Throwable: an Error (a method this Android version lacks, say) mustn't close the connection silently either
            visit(remote, asked, "failed: " + (e.message?.let { "${e::class.java.simpleName}: $it" } ?: e::class.java.simpleName))
            if (answer.status == null) respond(out, 500, "text/plain", "The TV couldn't answer (${e::class.java.simpleName}). Try again, or open its Files page")
        }
    }

    private fun visit(remote: java.net.InetAddress, asked: String, outcome: String) {
        state.value = state.value.copy(lastVisit = Visit(System.currentTimeMillis(), remote.hostAddress.orEmpty(), asked, outcome))
    }

    /** Remembers the status of the answer written through it. */
    private class StatusOut(val inner: OutputStream, val onStatus: (Int) -> Unit) : OutputStream() {
        var status: Int? = null
        override fun write(b: Int) = inner.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) {
            if (status == null && len >= 12) {
                val start = String(b, off, minOf(len, 12), Charsets.ISO_8859_1)
                if (start.startsWith("HTTP/1.1 ")) status = start.substring(9, 12).toIntOrNull()?.also(onStatus)
            }
            inner.write(b, off, len)
        }
        override fun flush() = inner.flush()
    }

    private fun serve(request: Request, remote: java.net.InetAddress, out: OutputStream) {
        // Only requests made to the TV's own address: another website can't borrow the phone's browser
        val s = state.value
        val host = request.headers["host"].orEmpty()
        if (host != "${s.address}:${s.port}") return respond(out, 403, "text/plain", "Open the TV's address directly")
        val changes = request.method != "GET"
        if (changes && request.headers["x-pill"] != "1") return respond(out, 403, "text/plain", "Use the TV's page to do that")
        val who = remote.hostAddress.orEmpty()
        when {
            request.method == "GET" && request.path == "/" -> {
                if (request.params["p"] == s.key && s.key.isNotEmpty()) {
                    // Scanned: paired at once. The page itself comes back with the cookie (no redirect, which
                    // costs a second connection), and its script takes the key out of the address bar
                    val token = trust(deviceName(request.headers["user-agent"]))
                    return respond(out, 200, "text/html; charset=utf-8", page(), listOf(cookieHeader(token)))
                }
                respond(out, 200, "text/html; charset=utf-8", page())
            }
            request.path == "/favicon.ico" -> respond(out, 404, "text/plain", "")
            request.method == "GET" && request.path.startsWith("/f/") -> sendLink(request.path.removePrefix("/f/"), out)
            request.method == "POST" && request.path == "/api/pair" -> askWithCode(request, who, out)
            request.method == "GET" && request.path == "/api/pair" -> collect(request, out)
            else -> {
                val device = deviceFor(request) ?: return respond(out, 401, "text/plain", "Pair this phone with the TV first")
                when {
                    request.method == "GET" && request.path == "/api/files" -> {
                        if (request.headers["x-pill"] != "1") return respond(out, 403, "text/plain", "Use the TV's page to do that")
                        respond(out, 200, "application/json", filesJson())
                    }
                    request.method == "PUT" && request.path == "/api/upload" -> receive(request, device, out)
                    request.method == "GET" && request.path == "/api/file" -> send(file(request.params["name"].orEmpty()), out)
                    else -> respond(out, 404, "text/plain", "Not found")
                }
            }
        }
    }

    private fun askWithCode(request: Request, who: String, out: OutputStream) {
        val now = System.currentTimeMillis()
        val (wrong, since) = wrongCodes[who] ?: (0 to now)
        if (wrong >= MaxWrongCodes && now - since < BlockMs) return respond(out, 429, "text/plain", "Too many wrong codes. Try again in 10 minutes")
        if (request.params["code"] != state.value.code) {
            wrongCodes[who] = (if (now - since >= BlockMs) 1 else wrong + 1) to (if (now - since >= BlockMs) now else since)
            return respond(out, 403, "text/plain", "That code didn't work")
        }
        wrongCodes.remove(who)
        val asked = askToPair(deviceName(request.headers["user-agent"]))
        respond(out, 202, "application/json", JSONObject().put("wait", asked.id).toString())
    }

    private fun collect(request: Request, out: OutputStream) {
        val id = request.params["wait"].orEmpty()
        approved.remove(id)?.let { token -> return respond(out, 200, "application/json", "{}", listOf(cookieHeader(token))) }
        if (id in blocked) return respond(out, 403, "text/plain", "The TV didn't allow this phone")
        if (state.value.pending?.id == id) return respond(out, 202, "application/json", "{}")
        respond(out, 403, "text/plain", "Ask again")
    }

    private fun filesJson(): String {
        refresh()
        val s = state.value
        val files = JSONArray()
        for (f in s.files) files.put(JSONObject().put("name", f.name).put("size", f.size).put("at", f.modified))
        return JSONObject().put("files", files).put("free", s.freeBytes).toString()
    }

    private fun receive(request: Request, device: TrustedDevice, out: OutputStream) {
        val length = request.headers["content-length"]?.toLongOrNull() ?: return respond(out, 411, "text/plain", "Send one file at a time, with its length")
        if (length > dir.usableSpace - SpareBytes) return respond(out, 507, "text/plain", "Not enough space on the TV for this file")
        if (!uploads.tryAcquire(60, TimeUnit.SECONDS)) return respond(out, 503, "text/plain", "The TV is busy with other files. Try again")
        try {
            receiveFile(request, device, length, out)
        } finally {
            uploads.release()
        }
    }

    private fun receiveFile(request: Request, device: TrustedDevice, length: Long, out: OutputStream) {
        val from = senderOf(request.headers["user-agent"]).ifEmpty { device.name }
        val name = synchronized(this) {
            uniqueFileName(safeFileName(request.params["name"])) { File(dir, it).exists() || transfers.containsKey(it) }
                .also { transfers[it] = Transfer(it, 0, length, from) }
        }
        publishTransfers()
        val part = File(dir, ".$name.part")
        var read = 0L
        try {
            part.outputStream().use { sink ->
                val buffer = ByteArray(64 * 1024)
                var shown = 0L
                while (read < length) {
                    val n = request.body.read(buffer, 0, minOf(buffer.size.toLong(), length - read).toInt())
                    if (n < 0) break
                    sink.write(buffer, 0, n)
                    read += n
                    if (read - shown >= 256 * 1024) {
                        shown = read
                        transfers[name] = Transfer(name, read, length, from)
                        publishTransfers()
                    }
                }
            }
        } catch (e: Exception) {
            // The phone went away; what came so far is thrown away below
        }
        transfers.remove(name)
        if (read < length) {
            part.delete()
            publishTransfers()
            return respond(out, 400, "text/plain", "The file didn't arrive in full")
        }
        val target = File(dir, name)
        if (!part.renameTo(target)) {
            part.delete()
            publishTransfers()
            return respond(out, 500, "text/plain", "The TV couldn't save the file")
        }
        writeSenders(readSenders() + (name to from))
        refresh()
        publishTransfers()
        arrivals.tryEmit(SharedFile(name, target.length(), target.lastModified(), from))
        respond(out, 200, "application/json", JSONObject().put("name", name).toString())
    }

    private fun sendLink(token: String, out: OutputStream) {
        val (name, until) = links[token] ?: return respond(out, 404, "text/plain", "This link has ended. Choose Send to phone again")
        if (System.currentTimeMillis() > until) {
            links.remove(token)
            return respond(out, 404, "text/plain", "This link has ended. Choose Send to phone again")
        }
        send(file(name), out)
    }

    private fun send(file: File?, out: OutputStream) {
        if (file == null) return respond(out, 404, "text/plain", "No such file on the TV")
        val encoded = URLEncoder.encode(file.name, "UTF-8").replace("+", "%20")
        val head = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: ${file.length()}\r\n" +
            "Content-Disposition: attachment; filename*=UTF-8''$encoded\r\nConnection: close\r\n\r\n"
        out.write(head.toByteArray(Charsets.ISO_8859_1))
        file.inputStream().use { it.copyTo(out, 64 * 1024) }
        out.flush()
    }

    private fun publishTransfers() {
        state.value = state.value.copy(transfers = transfers.values.sortedBy { it.name })
    }

    private fun readSenders(): Map<String, String> = runCatching {
        val o = JSONObject(File(dir, SendersFile).readText())
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    @Synchronized
    private fun writeSenders(map: Map<String, String>) {
        runCatching { File(dir, SendersFile).writeText(JSONObject(map).toString()) }
    }

    private fun readDevices() {
        runCatching {
            val a = JSONArray(devicesFile.readText())
            val now = System.currentTimeMillis()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val d = TrustedDevice(o.getString("id"), o.getString("name"), o.getLong("pairedAt"), o.getLong("lastUsed"))
                if (now - d.pairedAt <= TrustMs) tokens[o.getString("hash")] = d
            }
        }
        publishDevices()
    }

    @Synchronized
    private fun saveDevices() {
        val a = JSONArray()
        for ((hash, d) in tokens) {
            a.put(JSONObject().put("hash", hash).put("id", d.id).put("name", d.name).put("pairedAt", d.pairedAt).put("lastUsed", d.lastUsed))
        }
        runCatching { devicesFile.writeText(a.toString()) }
        publishDevices()
    }

    private fun publishDevices() {
        state.value = state.value.copy(devices = tokens.values.sortedByDescending { it.lastUsed })
    }

    private fun hex(bytes: Int): String = ByteArray(bytes).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

    companion object {
        /** Six digits, easy to type on a phone. */
        fun newCode(random: SecureRandom = SecureRandom()): String = "%06d".format(random.nextInt(1_000_000))

        /** 128 random bits for the QR code: far beyond guessing. */
        fun newKey(random: SecureRandom = SecureRandom()): String = ByteArray(16).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

        private fun sha(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun cookieHeader(token: String) = "Set-Cookie: $Cookie=$token; Max-Age=${TrustMs / 1000}; Path=/; HttpOnly; SameSite=Strict"

        private fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b < 0) return if (line.isEmpty()) null else line.toString()
                if (b == '\n'.code) return line.toString().trimEnd('\r')
                line.append(b.toChar())
                if (line.length > 8192) error("Line too long")
            }
        }

        private fun readRequest(input: InputStream): Request? {
            val first = readLine(input) ?: return null
            val parts = first.split(' ')
            if (parts.size < 2) return null
            val headers = HashMap<String, String>()
            repeat(100) {
                val line = readLine(input) ?: return null
                if (line.isEmpty()) {
                    val target = parts[1]
                    val path = target.substringBefore('?')
                    val params = target.substringAfter('?', "").split('&').filter { '=' in it }.associate {
                        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                    }
                    val cookies = headers["cookie"].orEmpty().split(';').map { it.trim() }.filter { '=' in it }
                        .associate { it.substringBefore('=') to it.substringAfter('=') }
                    return Request(parts[0].uppercase(), path, params, headers, cookies, input)
                }
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            return null
        }

        private fun respond(out: OutputStream, status: Int, type: String, body: String, extra: List<String> = emptyList()) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val reason = when (status) {
                200 -> "OK"; 202 -> "Accepted"; 302 -> "Found"; 400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
                404 -> "Not Found"; 411 -> "Length Required"; 429 -> "Too Many Requests"; 503 -> "Service Unavailable"
                507 -> "Insufficient Storage"; else -> "Error"
            }
            val head = buildString {
                append("HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n")
                for (line in extra) append(line).append("\r\n")
                append("Cache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nConnection: close\r\n\r\n")
            }
            runCatching {
                out.write(head.toByteArray(Charsets.ISO_8859_1))
                out.write(bytes)
                out.flush()
            }
        }
    }
}

private var shared: FileShare? = null

/** The launcher's file share, one for the whole app: files in its own folder, the phone's page from its assets. */
fun fileShare(context: android.content.Context): FileShare = synchronized(FileShare::class.java) {
    val app = context.applicationContext
    shared ?: FileShare(
        dir = app.getExternalFilesDir("Files") ?: File(app.filesDir, "Files"),
        page = { app.assets.open("share/index.html").bufferedReader().use { it.readText() } },
        devicesFile = File(app.filesDir, "share-devices.json"),
    ).also { shared = it }
}
