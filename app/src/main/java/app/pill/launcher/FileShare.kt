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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

data class ShareState(
    /** Taking files now. */
    val running: Boolean = false,
    /** The TV's address on the home network, or null when it has none. */
    val address: String? = null,
    val port: Int = 0,
    val code: String = "",
    val transfers: List<Transfer> = emptyList(),
    val files: List<SharedFile> = emptyList(),
    val freeBytes: Long = 0,
    val totalBytes: Long = 0,
    /** Why it can't take files, in plain words. */
    val problem: String? = null,
    /** Still taking files until this time (ms) after leaving the page; null while the page is open or once stopped. */
    val until: Long? = null,
) {
    /** What the phone opens: the address with the code, so scanning needs no typing. */
    val link: String? get() = address?.let { "http://$it:$port/?k=$code" }
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

private const val CodeLetters = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
private const val FirstPort = 8642
private const val SpareBytes = 50L * 1000 * 1000
private const val SendersFile = ".senders.json"

/**
 * Files between the TV and phones on the same network, with nothing to install: while it
 * runs, a small web server answers on [FirstPort] with a page for sending files (PUT, one
 * file per request) and fetching the TV's. Every request but the page needs the code shown
 * on the TV, and only home-network addresses are answered. Files go into [dir].
 */
class FileShare(
    val dir: File,
    /** The page phones open (share/index.html from the app's assets). */
    private val page: () -> String,
    /** The TV's address; replaceable for tests. */
    private val address: () -> String? = ::homeAddress,
    val code: String = newCode(),
) {
    val state = MutableStateFlow(ShareState(code = code))

    /** Each file as it arrives. */
    val arrivals = MutableSharedFlow<SharedFile>(extraBufferCapacity = 16)

    @Volatile private var socket: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val timer = Timer("share-stop", true)
    private var stopTask: java.util.TimerTask? = null
    private val transfers = ConcurrentHashMap<String, Transfer>()
    private val wrongCodes = ConcurrentHashMap<String, Int>()

    init {
        dir.mkdirs()
        refresh()
    }

    /** Starts taking files (or keeps going, cancelling a pending stop). */
    @Synchronized
    fun start() {
        stopTask?.cancel()
        stopTask = null
        if (socket != null) {
            state.value = state.value.copy(until = null, address = address())
            return
        }
        val bound = (FirstPort until FirstPort + 8).firstNotNullOfOrNull { port ->
            runCatching { ServerSocket(port, 16) }.getOrNull()
        }
        if (bound == null) {
            state.value = state.value.copy(running = false, problem = "Another app is using the ports file sharing needs")
            return
        }
        socket = bound
        val workers = Executors.newCachedThreadPool { r -> Thread(r, "share").apply { isDaemon = true } }
        pool = workers
        Thread({ acceptLoop(bound, workers) }, "share-accept").apply { isDaemon = true }.start()
        val here = address()
        state.value = state.value.copy(
            running = true,
            address = here,
            port = bound.localPort,
            until = null,
            problem = if (here == null) "Connect the TV to your home network to share files" else null,
        )
        refresh()
    }

    /** Stops now, or after [afterMs] (to keep taking files for a while after leaving the page). */
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
        state.value = state.value.copy(running = false, until = null, transfers = emptyList())
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

    /** A link a phone can open to save [name], while sharing runs. */
    fun linkFor(name: String): String? {
        val s = state.value
        val host = s.address ?: return null
        if (!s.running) return null
        return "http://$host:${s.port}/api/file?k=$code&name=" + URLEncoder.encode(name, "UTF-8").replace("+", "%20")
    }

    private fun acceptLoop(server: ServerSocket, workers: ExecutorService) {
        while (!server.isClosed) {
            val client = try {
                server.accept()
            } catch (e: SocketException) {
                break
            } catch (e: Exception) {
                continue
            }
            runCatching { workers.execute { client.use { handle(it) } } }.onFailure { runCatching { client.close() } }
        }
    }

    private class Request(val method: String, val path: String, val params: Map<String, String>, val headers: Map<String, String>, val body: InputStream)

    private fun handle(client: Socket) {
        client.soTimeout = 30_000
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val remote = client.inetAddress
        if (!isHomeNetwork(remote)) return respond(out, 403, "text/plain", "Only devices on the home network can share files")
        val request = runCatching { readRequest(input) }.getOrNull() ?: return respond(out, 400, "text/plain", "Bad request")
        if (request.path == "/" && request.method == "GET") return respond(out, 200, "text/html; charset=utf-8", page())
        if (request.path == "/favicon.ico") return respond(out, 404, "text/plain", "")
        // Everything else needs the code; a device that keeps guessing is turned away
        val who = remote.hostAddress.orEmpty()
        if ((wrongCodes[who] ?: 0) >= 20) return respond(out, 429, "text/plain", "Too many wrong codes")
        if (!request.params["k"].equals(code, ignoreCase = true)) {
            wrongCodes.merge(who, 1) { a, b -> a + b }
            return respond(out, 403, "text/plain", "Wrong code")
        }
        when {
            request.method == "GET" && request.path == "/api/files" -> respond(out, 200, "application/json", filesJson())
            request.method == "PUT" && request.path == "/api/upload" -> receive(request, out)
            request.method == "GET" && request.path == "/api/file" -> send(request, out)
            else -> respond(out, 404, "text/plain", "Not found")
        }
    }

    private fun filesJson(): String {
        refresh()
        val s = state.value
        val files = JSONArray()
        for (f in s.files) files.put(JSONObject().put("name", f.name).put("size", f.size).put("at", f.modified))
        return JSONObject().put("files", files).put("free", s.freeBytes).toString()
    }

    private fun receive(request: Request, out: OutputStream) {
        val length = request.headers["content-length"]?.toLongOrNull() ?: return respond(out, 411, "text/plain", "Send one file at a time, with its length")
        if (length > dir.usableSpace - SpareBytes) return respond(out, 507, "text/plain", "Not enough space on the TV for this file")
        val from = senderOf(request.headers["user-agent"])
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
        val saved = SharedFile(name, target.length(), target.lastModified(), from)
        arrivals.tryEmit(saved)
        respond(out, 200, "application/json", JSONObject().put("name", name).toString())
    }

    private fun send(request: Request, out: OutputStream) {
        val file = file(request.params["name"].orEmpty()) ?: return respond(out, 404, "text/plain", "No such file on the TV")
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

    companion object {
        fun newCode(random: SecureRandom = SecureRandom()): String = String(CharArray(4) { CodeLetters[random.nextInt(CodeLetters.length)] })

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
                    return Request(parts[0].uppercase(), path, params, headers, input)
                }
                val colon = line.indexOf(':')
                if (colon > 0) headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
            }
            return null
        }

        private fun respond(out: OutputStream, status: Int, type: String, body: String) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val reason = when (status) {
                200 -> "OK"; 400 -> "Bad Request"; 403 -> "Forbidden"; 404 -> "Not Found"; 411 -> "Length Required"
                429 -> "Too Many Requests"; 507 -> "Insufficient Storage"; else -> "Error"
            }
            val head = "HTTP/1.1 $status $reason\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n"
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
    ).also { shared = it }
}
