package app.pill.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import kotlin.coroutines.coroutineContext

/** A Pill Launcher release on GitHub: its version, the APK to download, and what's new. */
data class Release(val version: String, val apkUrl: String, val sizeBytes: Long, val notes: List<String>)

/** Where an update has got to. */
enum class UpdatePhase { Idle, Checking, UpToDate, Available, Downloading, Installing, Failed }

data class UpdateState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    /** The newer release, once one is found. */
    val release: Release? = null,
    /** 0 to 1 while downloading. */
    val progress: Float = 0f,
    val checkedAt: Instant? = null,
    /** Why the last step failed, in plain words. */
    val message: String? = null,
)

/** True when [candidate] (like "3.1") is a later version than [current] ("3.0"). */
fun isNewer(candidate: String, current: String): Boolean {
    val a = candidate.split('.').map { it.toIntOrNull() ?: 0 }
    val b = current.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

private val ApkName = Regex("""^pill-launcher-([0-9][0-9.]*)\.apk$""")

/**
 * The release GitHub's API describes in [json]: the version comes from its versioned APK
 * (pill-launcher-3.1.apk), the notes from the changelog section in its description, as
 * up to three plain lines. Null when it has no such APK.
 */
fun parseRelease(json: String): Release? = runCatching {
    val o = JSONObject(json)
    val assets = o.getJSONArray("assets")
    val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
        .firstOrNull { ApkName.matches(it.optString("name")) } ?: return null
    val version = ApkName.find(apk.getString("name"))!!.groupValues[1].trimEnd('.')
    Release(version, apk.getString("browser_download_url"), apk.optLong("size"), releaseNotes(o.optString("body")))
}.getOrNull()

/** The bullet points under the first "## " heading of a release description, without markdown. */
fun releaseNotes(body: String): List<String> {
    val lines = body.lines()
    val start = lines.indexOfFirst { it.startsWith("## ") }
    if (start < 0) return emptyList()
    return lines.drop(start + 1)
        .takeWhile { !it.startsWith("## ") }
        .filter { it.trimStart().startsWith("- ") }
        .map { it.trimStart().removePrefix("- ").replace("**", "").replace("`", "").trim() }
        .filter { it.isNotEmpty() }
        .take(3)
}

/**
 * Finds, downloads and installs a newer Pill Launcher from the repository's GitHub
 * releases. Installing goes through Android's package installer, which asks the viewer
 * to confirm; the update is signed with the same key, so settings and favourites stay.
 */
class Updater(private val context: Context) {

    suspend fun latest(): Result<Release> = withContext(Dispatchers.IO) {
        runCatching {
            val c = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PillLauncher")
            }
            try {
                if (c.responseCode != 200) error("GitHub answered ${c.responseCode}")
                parseRelease(c.inputStream.bufferedReader().readText()) ?: error("The newest release has no APK")
            } finally {
                c.disconnect()
            }
        }
    }

    /** Whether Android lets Pill Launcher install apps; the first update asks for it. */
    fun canInstall(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Opens the screen where Pill Launcher may be allowed to install apps; false if this TV has none. */
    fun askToInstall(): Boolean = runCatching {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /**
     * Downloads [release]'s APK into the cache, reporting 0 to 1 as it goes. Tries the
     * newest release's link, then the version's own release, and says why when both fail.
     */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "pill-launcher-${release.version}.apk")
        val links = listOf(
            release.apkUrl,
            "https://github.com/S1rHades/One-TVOS/releases/download/v${release.version}/pill-launcher-${release.version}.apk",
        ).distinct()
        var problem: Exception? = null
        for (link in links) {
            try {
                fetch(link, file, release.sizeBytes, onProgress)
                if (file.length() > 0) return@withContext file
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                problem = e
            }
        }
        throw problem ?: IllegalStateException("The download was empty")
    }

    private suspend fun fetch(link: String, file: File, expected: Long, onProgress: (Float) -> Unit) {
        var url = link
        // GitHub sends the file from another host; follow up to five redirects by hand
        repeat(6) {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "PillLauncher")
                setRequestProperty("Accept", "application/octet-stream")
            }
            try {
                val code = c.responseCode
                when {
                    code in 300..399 -> {
                        val next = c.getHeaderField("Location") ?: error("GitHub gave no download link")
                        url = URL(URL(url), next).toString()
                    }
                    code == 200 -> {
                        val total = c.contentLengthLong.takeIf { it > 0 } ?: expected
                        var read = 0L
                        c.inputStream.use { input ->
                            file.outputStream().use { out ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    out.write(buffer, 0, n)
                                    read += n
                                    if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                        }
                        if (total > 0 && read < total) error("The download was cut short")
                        return
                    }
                    else -> error("GitHub answered $code")
                }
            } finally {
                c.disconnect()
            }
        }
        error("Too many redirects")
    }

    /**
     * Opens Android's own install screen for [apk], for TVs whose background installer
     * refuses. False if this TV has no such screen.
     */
    fun installWithSystemScreen(apk: File): Boolean = runCatching {
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    /** Hands the APK to Android's installer, which shows its own confirmation. */
    fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("pill-launcher.apk", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = PendingIntent.getBroadcast(context, id, Intent(context, InstallResultReceiver::class.java), flags)
            session.commit(result.intentSender)
        }
    }

    private companion object {
        const val LATEST_API = "https://api.github.com/repos/S1rHades/One-TVOS/releases/latest"
    }
}

/**
 * Android's answer to an install: it either needs the viewer to confirm (its screen is
 * opened) or has failed, and why is passed to the launcher through [installProblems].
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { installProblems.value = "Android couldn't show the install screen" }
            }
            PackageInstaller.STATUS_SUCCESS -> installProblems.value = null
            PackageInstaller.STATUS_FAILURE_ABORTED, PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_STORAGE ->
                installProblems.value = installProblem(status)
            else -> {
                // Some TVs refuse background installs: Android's own install screen may still work
                val apk = File(context.cacheDir, "updates").listFiles()?.maxByOrNull { it.lastModified() }
                val opened = apk != null && Updater(context).installWithSystemScreen(apk)
                if (!opened) {
                    val detail = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)?.take(80)
                    installProblems.value = installProblem(status) + (detail?.let { " ($it)" } ?: "")
                }
            }
        }
    }
}

/** The last install failure, in plain words; null when there was none. */
val installProblems = MutableStateFlow<String?>(null)

fun installProblem(status: Int): String = when (status) {
    PackageInstaller.STATUS_FAILURE_ABORTED -> "The update was cancelled"
    PackageInstaller.STATUS_FAILURE_CONFLICT -> "This copy was signed differently: uninstall it, then install the new one from GitHub"
    PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough space on the TV for the update"
    PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This TV can't install the update"
    PackageInstaller.STATUS_FAILURE_BLOCKED -> "The TV blocked the update"
    else -> "The update couldn't be installed"
}

/** A short, plain reason for a failure, for the update card and Settings. */
fun updateProblem(e: Throwable): String {
    val name = e.javaClass.simpleName
    return when {
        e is java.net.UnknownHostException -> "can't reach GitHub"
        e is java.net.SocketTimeoutException -> "the connection timed out"
        e is javax.net.ssl.SSLException -> "a secure connection to GitHub failed"
        e is SecurityException -> "Android didn't allow it"
        else -> (e.message?.takeIf { it.isNotBlank() } ?: name).take(80)
    }
}
