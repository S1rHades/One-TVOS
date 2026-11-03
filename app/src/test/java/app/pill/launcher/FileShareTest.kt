package app.pill.launcher

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.Socket
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files

/**
 * The Files share end to end on this machine: pairing by the QR key or by the six-digit
 * code and an OK on the TV, uploads arriving whole under safe names, and nothing working
 * without pairing, the TV's own address, or the page's header.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileShareTest {

    private lateinit var dir: File
    private lateinit var share: FileShare
    private val iPhone = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("share").toFile()
        share = FileShare(File(dir, "Files"), page = { "<html>phone page</html>" }, address = { "127.0.0.1" }, code = "482913", key = "k".repeat(32))
        share.start()
        assertTrue("the share is running", share.state.value.running)
    }

    @After
    fun tearDown() {
        share.stop()
        dir.deleteRecursively()
    }

    private val port get() = share.state.value.port

    private class Answer(val status: Int, val body: ByteArray, val headers: Map<String, List<String>>) {
        val cookie: String? get() = headers["Set-Cookie"]?.firstOrNull()?.substringBefore(';')
    }

    private fun call(
        method: String,
        path: String,
        cookie: String? = null,
        header: Boolean = true,
        body: ByteArray? = null,
    ): Answer {
        val c = URL("http://127.0.0.1:$port$path").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.requestMethod = method
        c.setRequestProperty("User-Agent", iPhone)
        if (header) c.setRequestProperty("X-Pill", "1")
        if (cookie != null) c.setRequestProperty("Cookie", cookie)
        if (body != null) {
            c.doOutput = true
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        val status = c.responseCode
        val bytes = (if (status < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        val headers = c.headerFields.filterKeys { it != null }
        c.disconnect()
        return Answer(status, bytes, headers)
    }

    /** Scanning the QR code: paired at once, and sent back to the plain address. */
    private fun scan(): String {
        val a = call("GET", "/?p=" + "k".repeat(32), header = false)
        assertEquals(302, a.status)
        assertEquals("/", a.headers["Location"]?.first())
        return a.cookie!!
    }

    @Test
    fun scanningPairsAndAFileGoesUpAndComesBack() = runBlocking {
        val cookie = scan()
        assertEquals(listOf("iPhone"), share.state.value.devices.map { it.name })
        val bytes = ByteArray(700_000) { (it % 251).toByte() }
        // Listening before the upload starts, since arrivals are not replayed
        val arrived = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { share.arrivals.first() } }
        assertEquals(200, call("PUT", "/api/upload?name=" + URLEncoder.encode("../holiday.jpg", "UTF-8"), cookie, body = bytes).status)
        val file = arrived.await()
        assertEquals("holiday.jpg", file.name)
        assertEquals("an iPhone", file.from)
        assertTrue(File(share.dir, "holiday.jpg").readBytes().contentEquals(bytes))
        assertFalse("no part file is left", share.dir.listFiles()!!.any { it.name.endsWith(".part") })

        val listed = call("GET", "/api/files", cookie)
        assertEquals(200, listed.status)
        assertEquals("holiday.jpg", JSONObject(String(listed.body)).getJSONArray("files").getJSONObject(0).getString("name"))

        // The same name again is kept beside it, never over it
        assertEquals(200, call("PUT", "/api/upload?name=holiday.jpg", cookie, body = byteArrayOf(1, 2, 3)).status)
        assertTrue(File(share.dir, "holiday (2).jpg").exists())

        // Save links work without the header (a plain link on the page)
        val back = call("GET", "/api/file?name=holiday.jpg", cookie, header = false)
        assertEquals(200, back.status)
        assertTrue(back.body.contentEquals(bytes))
    }

    @Test
    fun nothingWorksWithoutPairing() {
        assertEquals(200, call("GET", "/", header = false).status)
        assertEquals("<html>phone page</html>", String(call("GET", "/", header = false).body))
        assertEquals(401, call("GET", "/api/files").status)
        assertEquals(401, call("PUT", "/api/upload?name=x.txt", body = byteArrayOf(1)).status)
        assertEquals(401, call("GET", "/api/files", cookie = "pill=madeup").status)
        assertFalse(File(share.dir, "x.txt").exists())
        // A wrong QR key pairs nothing
        assertEquals(200, call("GET", "/?p=wrong", header = false).status)
        assertTrue(share.state.value.devices.isEmpty())
    }

    @Test
    fun changesNeedThePagesHeader() {
        val cookie = scan()
        assertEquals(403, call("PUT", "/api/upload?name=x.txt", cookie, header = false, body = byteArrayOf(1)).status)
        assertEquals(403, call("POST", "/api/pair?code=482913", header = false).status)
        assertFalse(File(share.dir, "x.txt").exists())
    }

    @Test
    fun onlyTheTvsOwnAddressIsAnswered() {
        val cookie = scan()
        // As another website would, through a name of its own pointing at the TV
        Socket("127.0.0.1", port).use { s ->
            s.getOutputStream().write("GET /api/files HTTP/1.1\r\nHost: evil.example:$port\r\nX-Pill: 1\r\nCookie: $cookie\r\n\r\n".toByteArray())
            val first = s.getInputStream().bufferedReader().readLine()
            assertTrue(first, first.contains(" 403 "))
        }
    }

    @Test
    fun typingTheCodeWaitsForAllowOnTheTv() {
        assertEquals(403, call("POST", "/api/pair?code=000000").status)
        val asked = call("POST", "/api/pair?code=482913")
        assertEquals(202, asked.status)
        val id = JSONObject(String(asked.body)).getString("wait")
        assertEquals("iPhone", share.state.value.pending?.name)
        assertEquals(202, call("GET", "/api/pair?wait=$id").status)
        share.approve(id)
        assertNull(share.state.value.pending)
        val got = call("GET", "/api/pair?wait=$id")
        assertEquals(200, got.status)
        assertEquals(200, call("GET", "/api/files", got.cookie).status)

        // Block turns the next one away
        val again = JSONObject(String(call("POST", "/api/pair?code=482913").body)).getString("wait")
        share.block(again)
        assertEquals(403, call("GET", "/api/pair?wait=$again").status)
    }

    @Test
    fun guessingTheCodeIsStopped() {
        repeat(10) { assertEquals(403, call("POST", "/api/pair?code=11111$it").status) }
        assertEquals(429, call("POST", "/api/pair?code=482913").status)
    }

    @Test
    fun forgettingPhonesAndSendToPhoneLinks() {
        val cookie = scan()
        assertEquals(200, call("PUT", "/api/upload?name=notes.pdf", cookie, body = byteArrayOf(1, 2, 3)).status)
        val link = share.linkFor("notes.pdf")!!
        assertEquals(200, call("GET", link.substringAfter(":$port"), header = false).status)

        share.forget(share.state.value.devices.single().id)
        assertEquals(401, call("GET", "/api/files", cookie).status)

        val before = share.state.value.code
        scan()
        share.signOutAll()
        assertTrue(share.state.value.devices.isEmpty())
        assertTrue(share.state.value.code != before || share.state.value.key != "k".repeat(32))
    }

    @Test
    fun filesTidyThemselves() {
        val day = 24 * 3600 * 1000L
        val now = System.currentTimeMillis()
        File(share.dir, "app.apk").writeBytes(byteArrayOf(1))
        File(share.dir, "old.pdf").apply { writeBytes(byteArrayOf(1)); setLastModified(now - 31 * day) }
        File(share.dir, "soon.pdf").apply { writeBytes(byteArrayOf(1)); setLastModified(now - 29 * day - 3600_000) }
        File(share.dir, "new.pdf").writeBytes(byteArrayOf(1))
        val kept = share.tidy(installed = { false }, removeOld = false, now = now)
        assertTrue(kept.installed.isEmpty() && kept.old.isEmpty() && kept.tomorrow.isEmpty())
        val done = share.tidy(installed = { it.name == "app.apk" }, removeOld = true, now = now)
        // An installed app's APK is only found: the TV asks before deleting it
        assertEquals(listOf("app.apk"), done.installed)
        assertTrue(File(share.dir, "app.apk").exists())
        assertEquals(listOf("old.pdf"), done.old)
        assertEquals(listOf("soon.pdf"), done.tomorrow.map { it.first })
        assertEquals(setOf("app.apk", "soon.pdf", "new.pdf"), share.state.value.files.map { it.name }.toSet())
    }
}
