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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.file.Files

/**
 * The Files share end to end on this machine: a phone's upload arrives whole under a safe
 * name, is listed and comes back down byte for byte, and nothing works without the code.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileShareTest {

    private lateinit var dir: File
    private lateinit var share: FileShare

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("share").toFile()
        share = FileShare(dir, page = { "<html>phone page</html>" }, address = { "127.0.0.1" }, code = "AB23")
        share.start()
        assertTrue("the share is running", share.state.value.running)
    }

    @After
    fun tearDown() {
        share.stop()
        dir.deleteRecursively()
    }

    private fun url(path: String) = URL("http://127.0.0.1:${share.state.value.port}$path")

    private fun put(name: String, body: ByteArray, code: String = "AB23", agent: String = "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0)"): Int {
        val c = url("/api/upload?k=$code&name=" + URLEncoder.encode(name, "UTF-8")).openConnection() as HttpURLConnection
        c.requestMethod = "PUT"
        c.doOutput = true
        c.setFixedLengthStreamingMode(body.size)
        c.setRequestProperty("User-Agent", agent)
        c.outputStream.use { it.write(body) }
        return c.responseCode.also { c.disconnect() }
    }

    private fun get(path: String): Pair<Int, ByteArray> {
        val c = url(path).openConnection() as HttpURLConnection
        val code = c.responseCode
        val body = (if (code < 400) c.inputStream else c.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
        c.disconnect()
        return code to body
    }

    @Test
    fun aFileGoesUpAndComesBack() = runBlocking {
        val bytes = ByteArray(700_000) { (it % 251).toByte() }
        // Listening before the upload starts, since arrivals are not replayed
        val arrived = async(start = CoroutineStart.UNDISPATCHED) { withTimeout(5000) { share.arrivals.first() } }
        assertEquals(200, put("../holiday.jpg", bytes))
        val file = arrived.await()
        assertEquals("holiday.jpg", file.name)
        assertEquals("an iPhone", file.from)
        assertTrue(File(dir, "holiday.jpg").readBytes().contentEquals(bytes))
        assertFalse("no part file is left", dir.listFiles()!!.any { it.name.endsWith(".part") })

        // Listed for the phone and on the TV, with who sent it
        val (listed, json) = get("/api/files?k=AB23")
        assertEquals(200, listed)
        assertEquals("holiday.jpg", JSONObject(String(json)).getJSONArray("files").getJSONObject(0).getString("name"))
        assertEquals("an iPhone", share.state.value.files.single().from)

        // The same name again is kept beside it, never over it
        assertEquals(200, put("holiday.jpg", byteArrayOf(1, 2, 3)))
        assertTrue(File(dir, "holiday (2).jpg").exists())

        val (status, back) = get("/api/file?k=AB23&name=holiday.jpg")
        assertEquals(200, status)
        assertTrue(back.contentEquals(bytes))
    }

    @Test
    fun theCodeIsNeeded() {
        assertEquals(200, get("/").first)
        assertEquals("<html>phone page</html>", String(get("/").second))
        assertEquals(403, get("/api/files?k=ZZZZ").first)
        assertEquals(403, put("x.txt", byteArrayOf(1), code = "ZZZZ"))
        assertFalse(File(dir, "x.txt").exists())
        // The code isn't case-sensitive, so typing it is easy
        assertEquals(200, get("/api/files?k=ab23").first)
    }

    @Test
    fun deletingAndStopping() {
        assertEquals(200, put("notes.pdf", byteArrayOf(1, 2, 3)))
        share.refresh()
        assertEquals(listOf("notes.pdf"), share.state.value.files.map { it.name })
        assertTrue(share.linkFor("notes.pdf")!!.startsWith("http://127.0.0.1:"))
        share.delete("notes.pdf")
        assertTrue(share.state.value.files.isEmpty())
        share.stop()
        assertFalse(share.state.value.running)
        assertEquals(null, share.linkFor("notes.pdf"))
    }
}
