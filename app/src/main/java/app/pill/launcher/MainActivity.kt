package app.pill.launcher

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import java.time.Duration
import java.time.Instant
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import app.pill.launcher.ui.LauncherScreen
import app.pill.launcher.ui.SystemAction
import app.pill.launcher.ui.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val REFRESH_MS = 30 * 60 * 1000L
private const val LIVE_REFRESH_MS = 3 * 60 * 1000L
/** Waits between attempts while the weather cannot be fetched (no network yet at boot, say). */
private val RetryDelaysMs = listOf(10_000L, 30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L)

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)
    private var homePresses by mutableIntStateOf(0)

    /** Bumped when the TV wakes (screen on) or the launcher comes back, so it never wakes up idle. */
    private var wakes by mutableIntStateOf(0)
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wakes++
        }
    }
    private lateinit var store: SettingsStore
    private lateinit var fixturesSource: FixturesSource
    private lateinit var photoFeed: PhotoFeed

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        fixturesSource = FixturesSource(this)
        photoFeed = PhotoFeed(this)

        setContent {
            // null until the first scan finishes; later refreshes keep the old list on screen
            val apps by produceState<List<AppEntry>?>(null, refresh) {
                value = withContext(Dispatchers.Default) { loadApps(packageManager, packageName) }
            }
            var pins by remember { mutableStateOf(store.readPins()) }
            var settings by remember { mutableStateOf(store.load()) }
            // Refreshed every 30 minutes while a widget needs it; the last reading stays up if a refresh fails.
            // Sunrise and sunset need only the location that comes with it.
            val weatherOn = WidgetKind.Weather in settings.widgets || WidgetKind.Daylight in settings.widgets
            var weatherError by remember { mutableStateOf<String?>(null) }
            val weather by produceState<Weather?>(null, weatherOn) {
                var failures = 0
                while (weatherOn) {
                    val result = withContext(Dispatchers.IO) { fetchWeather() }
                    result.onSuccess { value = it; weatherError = null; failures = 0 }
                    result.onFailure { weatherError = describe(it); failures++ }
                    delay(if (result.isSuccess) REFRESH_MS else RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)])
                }
            }

            // Algeria's fixtures: the saved copy at once, then fresh ones every 30 minutes, every 3 around a match
            val fennecsOn = WidgetKind.Fennecs in settings.widgets
            var fixturesError by remember { mutableStateOf<String?>(null) }
            val savedFixtures = remember { fixturesSource.cached() }
            val fixtures by produceState(savedFixtures, fennecsOn) {
                var failures = 0
                while (fennecsOn) {
                    val result = withContext(Dispatchers.IO) { fixturesSource.fetch() }
                    result.onSuccess { value = it; fixturesError = null; failures = 0 }
                    result.onFailure { fixturesError = it.message ?: it::class.java.simpleName; failures++; value = value?.copy(fromCache = true) }
                    val soon = value?.let { f ->
                        val now = Instant.now()
                        f.next?.kickoff?.let { Duration.between(now, it).abs() < Duration.ofHours(3) } == true || f.last?.isLive(now) == true
                    } == true
                    delay(
                        when {
                            result.isFailure -> RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)]
                            soon -> LIVE_REFRESH_MS
                            else -> REFRESH_MS
                        },
                    )
                }
            }

            // Today's online photos: the saved list at once, then the published one, re-checked every 6 hours
            val photosOn = settings.onlinePhotos
            val savedPhotos = remember { photoFeed.cached() }
            val onlinePhotos by produceState(savedPhotos, photosOn) {
                var failures = 0
                while (photosOn) {
                    val result = withContext(Dispatchers.IO) { photoFeed.fetch() }
                    result.onSuccess { if (it != value) value = it; failures = 0 }
                    result.onFailure { failures++ }
                    delay(if (result.isSuccess) 6 * 60 * 60 * 1000L else RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)])
                }
            }

            // Re-checked whenever the launcher comes back (e.g. from the Home app settings)
            val home = remember(refresh) { homeStatus() }

            // A launcher never exits on Back
            BackHandler(enabled = true) { }

            LauncherScreen(
                apps = apps,
                weather = weather.takeIf { weatherOn },
                weatherError = weatherError.takeIf { weatherOn },
                fixtures = fixtures,
                onlinePhotos = onlinePhotos,
                fixturesError = fixturesError.takeIf { fennecsOn },
                pins = pins,
                settings = settings,
                onSettingsChange = {
                    settings = it
                    store.save(it)
                },
                onOpen = { openApp(it) },
                onTogglePin = { app ->
                    pins = if (app.pkg in pins) pins - app.pkg else pins + app.pkg
                    store.savePins(pins)
                },
                homePresses = homePresses,
                wakes = wakes,
                home = home,
                onMakeDefault = { requestDefaultHome() },
                onHomeHelper = { openAccessibilitySettings() },
                onAllowAutostart = { requestOverlayPermission() },
                onSystemSearch = remember { systemSearchIntent() }?.let { search ->
                    { tryStartActivity(search); Unit }
                },
                onMovePin = { app, by ->
                    val from = pins.indexOf(app.pkg)
                    val to = (from + by).coerceIn(0, pins.lastIndex)
                    if (from >= 0 && to != from) {
                        pins = pins.toMutableList().apply { add(to, removeAt(from)) }
                        store.savePins(pins)
                    }
                },
                onUninstall = { app ->
                    // The system asks to confirm; the app list refreshes when the launcher resumes
                    tryStartActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
                onAppInfo = { app ->
                    tryStartActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                },
                onSystemAction = { action -> runSystemAction(action) },
                onOpenNetworkSettings = {
                    // TVs differ in which of these screens they have
                    listOf(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS, Settings.ACTION_SETTINGS)
                        .firstOrNull { tryStartActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                },
                onOpenSystemSettings = {
                    try {
                        startActivity(
                            Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } catch (e: ActivityNotFoundException) {
                        // no settings app available
                    }
                },
            )
        }
    }

    // singleTask: pressing Home while the launcher exists arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    override fun onResume() {
        super.onResume()
        refresh++
        wakes++
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOn) }
        super.onDestroy()
    }

    /** The TV's own search screen (Google TV / Android TV search, or the assistant), if any. */
    private fun systemSearchIntent(): Intent? =
        listOf(Intent("android.search.action.GLOBAL_SEARCH"), Intent(Intent.ACTION_ASSIST))
            .map { it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            .firstOrNull { it.resolveActivity(packageManager) != null }

    /** One TV & system shortcut from settings; returns what to show under its row. */
    private fun runSystemAction(action: SystemAction): String? {
        fun open(vararg actions: String): String? =
            if (actions.any { tryStartActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) null
            else "This TV doesn't have that screen"
        return when (action) {
            SystemAction.FreeMemory -> freeMemory()
            SystemAction.Network -> open(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIRELESS_SETTINGS)
            SystemAction.Display -> open(Settings.ACTION_DISPLAY_SETTINGS)
            SystemAction.Sound -> open(Settings.ACTION_SOUND_SETTINGS)
            SystemAction.Storage -> open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Settings.ACTION_MEMORY_CARD_SETTINGS)
            // Not a public constant, but TVs and phones alike answer it
            SystemAction.Update -> open("android.settings.SYSTEM_UPDATE_SETTINGS", Settings.ACTION_DEVICE_INFO_SETTINGS)
            SystemAction.DateTime -> open(Settings.ACTION_DATE_SETTINGS)
            SystemAction.About -> open(Settings.ACTION_DEVICE_INFO_SETTINGS)
            SystemAction.AllSettings -> open(Settings.ACTION_SETTINGS)
        }
    }

    /**
     * Asks Android to stop the background processes of every other app (each restarts if
     * it needs to) and reports how much memory came free. Android 14 and later only let
     * an app stop its own, so there this mostly reports what is free.
     */
    private fun freeMemory(): String {
        val am = getSystemService(ActivityManager::class.java) ?: return "Not available on this TV"
        fun available() = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }.availMem
        val before = available()
        packageManager.getInstalledApplications(0)
            .filter { it.packageName != packageName }
            .forEach { runCatching { am.killBackgroundProcesses(it.packageName) } }
        val after = available()
        val freed = (after - before).coerceAtLeast(0) / 1_000_000
        val free = after / 1_000_000
        return if (freed >= 10) "Freed $freed MB · $free MB now free" else "$free MB free · nothing more to close"
    }

    /** Starts [intent]; false if no app handles it or it isn't open to us. */
    private fun tryStartActivity(intent: Intent): Boolean =
        try {
            startActivity(intent)
            true
        } catch (e: ActivityNotFoundException) {
            false
        } catch (e: SecurityException) {
            false
        }

    private fun openApp(app: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // app removed or disabled
        } catch (e: SecurityException) {
            // activity not exported
        }
    }
}
