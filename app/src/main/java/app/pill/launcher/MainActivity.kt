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
    private lateinit var footballSource: FootballSource
    private lateinit var weatherSource: WeatherSource
    private lateinit var newsFeed: NewsFeed
    private lateinit var photoFeed: PhotoFeed

    /** Set after TheSportsDB's search fails once, so later searches use the GitHub list alone. */
    @Volatile private var directSearchFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        footballSource = FootballSource(this)
        weatherSource = WeatherSource(this)
        newsFeed = NewsFeed(this)
        photoFeed = PhotoFeed(this)

        setContent {
            // null until the first scan finishes; later refreshes keep the old list on screen
            val apps by produceState<List<AppEntry>?>(null, refresh) {
                value = withContext(Dispatchers.Default) { loadApps(packageManager, packageName) }
            }
            var pins by remember { mutableStateOf(store.readPins()) }
            var settings by remember { mutableStateOf(store.load()) }
            // Refreshed every 30 minutes while a widget needs it; the last reading stays up if a refresh fails,
            // and the saved one shows at once after a restart. Sunrise and sunset need only the location.
            val weatherOn = WidgetKind.Weather in settings.widgets || WidgetKind.Daylight in settings.widgets
            var weatherError by remember { mutableStateOf<String?>(null) }
            val savedWeather = remember { weatherSource.cached() }
            val weather by produceState(savedWeather, weatherOn) {
                var failures = 0
                while (weatherOn) {
                    val result = withContext(Dispatchers.IO) { weatherSource.fetch() }
                    result.onSuccess { value = it; weatherError = null; failures = 0 }
                    result.onFailure { weatherError = describe(it); failures++ }
                    delay(if (result.isSuccess) REFRESH_MS else RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)])
                }
            }

            // The followed teams' fixtures: the saved copies at once, then fresh ones every 30 minutes,
            // every 3 around a match. A team's own failure keeps its saved copy, marked.
            val footballOn = WidgetKind.Fennecs in settings.widgets
            val teams = settings.teams
            val savedFootball = remember { teams.map { footballSource.cached(it) ?: Fixtures(it, null, null, Instant.EPOCH, fromCache = true) } }
            val football by produceState(savedFootball, footballOn, teams) {
                // A team added or removed shows (or goes) at once, before the fetch
                value = teams.map { t -> value.firstOrNull { it.team.id == t.id } ?: footballSource.cached(t) ?: Fixtures(t, null, null, Instant.EPOCH, fromCache = true) }
                var failures = 0
                while (footballOn) {
                    val list = withContext(Dispatchers.IO) { footballSource.fetch(teams) }
                    value = list
                    val failed = list.all { it.error != null }
                    failures = if (failed) failures + 1 else 0
                    val now = Instant.now()
                    val soon = list.any { f ->
                        f.next?.kickoff?.let { Duration.between(now, it).abs() < Duration.ofHours(3) } == true || f.last?.isLive(now) == true
                    }
                    delay(
                        when {
                            failed -> RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)]
                            soon -> LIVE_REFRESH_MS
                            else -> REFRESH_MS
                        },
                    )
                }
            }

            // Headlines from both sources every 30 minutes while the widget is on; the saved ones at once
            val newsOn = WidgetKind.Headlines in settings.widgets
            val savedNews = remember { newsFeed.cached() }
            val news by produceState(savedNews, newsOn) {
                var failures = 0
                while (newsOn) {
                    val fresh = withContext(Dispatchers.IO) { newsFeed.fetch() }
                    value = fresh
                    failures = if (fresh.errors.size == NewsSource.entries.size) failures + 1 else 0
                    delay(if (failures == 0) REFRESH_MS else RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)])
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
                football = football,
                news = news.takeIf { newsOn },
                loadTable = { league, season -> withContext(Dispatchers.IO) { footballSource.table(league, season) } },
                searchTeams = { query -> withContext(Dispatchers.IO) { findTeams(query) } },
                onlinePhotos = onlinePhotos,
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
    /**
     * Teams for Settings → Football: from the GitHub copy's list (works on any network) and,
     * when the TV can reach it, TheSportsDB's own search for everything else.
     */
    private fun findTeams(query: String): List<TeamHit> {
        val index = footballSource.mirroredTeams()
        val mirrored = index.mapTo(HashSet()) { it.id }
        val direct = if (directSearchFailed) emptyList() else footballSource.searchDirect(query)
            .onFailure { directSearchFailed = true }
            .getOrDefault(emptyList())
        return (searchTeams(index, query) + direct).distinctBy { it.id }.take(8).map { TeamHit(it, it.id in mirrored) }
    }

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
