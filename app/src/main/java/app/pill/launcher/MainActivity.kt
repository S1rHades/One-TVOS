package app.pill.launcher

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import app.pill.launcher.ui.NetworkWatch
import app.pill.launcher.ui.KeepReceivingMs
import android.os.UserHandle
import android.os.SystemClock
import android.os.Looper
import android.os.Handler
import android.content.pm.LauncherApps
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope

private const val REFRESH_MS = 30 * 60 * 1000L
private const val LIVE_REFRESH_MS = 3 * 60 * 1000L
private const val UPDATE_FIRST_CHECK_MS = 45 * 1000L
private const val UPDATE_CHECK_MS = 6 * 60 * 60 * 1000L
/** Waits between attempts while the weather cannot be fetched (no network yet at boot, say). */
private val RetryDelaysMs = listOf(10_000L, 30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L)

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    /**
     * Bumped when an app is installed, removed or updated (Android says so), and on coming
     * back after an hour away as a safety net: only then is every app's icon read again.
     */
    private var appsChanged by mutableIntStateOf(0)
    private var lastScan = 0L
    private val appWatch = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) { appsChanged++ }
        override fun onPackageAdded(packageName: String?, user: UserHandle?) { appsChanged++ }
        override fun onPackageChanged(packageName: String?, user: UserHandle?) { appsChanged++ }
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { appsChanged++ }
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) { appsChanged++ }
    }
    private var homePresses by mutableIntStateOf(0)

    /** Bumped when the TV wakes (screen on) or the launcher comes back, so it never wakes up idle. */
    private var wakes by mutableIntStateOf(0)
    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            wakes++
        }
    }
    private lateinit var store: SettingsStore
    private lateinit var crashGuard: CrashGuard

    /** Why the launcher started in safe mode (two crashes within a minute); null normally. */
    private var safeMode: String? = null
    private lateinit var footballSource: FootballSource
    private lateinit var weatherSource: WeatherSource
    private lateinit var prayerSource: PrayerSource
    /** Bumped by Refresh on the prayer page or in Settings: fetches again now. */
    private var prayerRefresh by mutableIntStateOf(0)
    private lateinit var newsFeed: NewsFeed
    private lateinit var photoFeed: PhotoFeed

    /** Set after TheSportsDB's search fails once, so later searches use the GitHub list alone. */
    @Volatile private var directSearchFailed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)
        crashGuard = CrashGuard(this).also { it.install() }
        safeMode = crashGuard.safeModeReason()
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        runCatching { getSystemService(LauncherApps::class.java)?.registerCallback(appWatch, Handler(Looper.getMainLooper())) }
        footballSource = FootballSource(this)
        weatherSource = WeatherSource(this)
        prayerSource = PrayerSource(this)
        newsFeed = NewsFeed(this)
        photoFeed = PhotoFeed(this)

        setContent {
            // null until the first scan finishes; later refreshes keep the old list on screen
            val scanned by produceState<List<AppEntry>?>(null, appsChanged) {
                value = withContext(Dispatchers.Default) { loadApps(packageManager, packageName) }
                lastScan = SystemClock.elapsedRealtime()
            }
            // Names given in the launcher replace the apps' own everywhere it shows them
            var names by remember { mutableStateOf(store.readNames()) }
            val apps = remember(scanned, names) { scanned?.let { withNames(it, names) } }
            var pins by remember { mutableStateOf(store.readPins()) }
            var settings by remember { mutableStateOf(store.load()) }
            // Safe mode: no widgets (so no data is fetched), no Files; a minute of normal running forgets the crashes
            val safe = safeMode
            LaunchedEffect(Unit) {
                if (safe == null) {
                    delay(60_000)
                    crashGuard.clear()
                }
            }
            // Refreshed every 30 minutes while a widget needs it; the last reading stays up if a refresh fails,
            // and the saved one shows at once after a restart. Sunrise and sunset need only the location.
            val active = if (safe != null) emptySet() else settings.activeWidgets()
            val weatherOn = WidgetKind.Weather in active || WidgetKind.Daylight in active
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
            val footballOn = WidgetKind.Fennecs in active
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

            // Today's prayer times from MAWAQIT while the widget is on: the saved copy at once, then a fetch,
            // then again just after midnight for the new day (or sooner after a failure, or on Refresh)
            val prayersOn = WidgetKind.Prayers in active
            val mosque = settings.mosque
            val prayerApi = settings.prayerApi
            var prayersError by remember { mutableStateOf<String?>(null) }
            var prayersRefreshing by remember { mutableStateOf(false) }
            val prayers by produceState<PrayerTimes?>(null, prayersOn, mosque, prayerApi, prayerRefresh) {
                value = prayerSource.cached(mosque)
                if (mosque.isBlank()) {
                    prayersError = null
                    return@produceState
                }
                var failures = 0
                while (prayersOn) {
                    prayersRefreshing = true
                    // Cleared even when a new mosque or Refresh cancels this fetch part way
                    val result = try {
                        withContext(Dispatchers.IO) { prayerSource.fetch(prayerApi, mosque) }
                    } finally {
                        prayersRefreshing = false
                    }
                    result.onSuccess { value = it; prayersError = null; failures = 0 }
                    result.onFailure { error ->
                        prayersError = describe(error)
                        failures++
                        value = value?.copy(fromCache = true)
                    }
                    delay(if (result.isSuccess) prayerSource.untilNextDay().toMillis() else RetryDelaysMs[(failures - 1).coerceAtMost(RetryDelaysMs.lastIndex)])
                }
            }

            // Headlines from both sources every 30 minutes while the widget is on; the saved ones at once
            val newsOn = WidgetKind.Headlines in active
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

            // Updates from GitHub: checked a little after start and every 6 hours while allowed
            val updater = remember { Updater(this) }
            val version = remember { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "0" }
            var update by remember { mutableStateOf(UpdateState()) }
            var updateDismissed by remember { mutableStateOf(store.updateDismissed) }
            var updateJob by remember { mutableStateOf<Job?>(null) }
            val updateScope = rememberCoroutineScope()
            val busy = { update.phase == UpdatePhase.Downloading || update.phase == UpdatePhase.Installing }
            suspend fun checkForUpdate() {
                if (busy()) return
                update = update.copy(phase = UpdatePhase.Checking, message = null)
                val now = Instant.now()
                updater.latest()
                    .onSuccess { r ->
                        update = if (isNewer(r.version, version)) UpdateState(UpdatePhase.Available, r, checkedAt = now)
                        else UpdateState(UpdatePhase.UpToDate, checkedAt = now)
                    }
                    .onFailure { update = update.copy(phase = if (update.release != null) UpdatePhase.Available else UpdatePhase.Failed, message = "Can't reach GitHub right now", checkedAt = now) }
            }
            LaunchedEffect(settings.checkUpdates) {
                if (!settings.checkUpdates) return@LaunchedEffect
                delay(UPDATE_FIRST_CHECK_MS)
                while (true) {
                    checkForUpdate()
                    delay(UPDATE_CHECK_MS)
                }
            }
            // A failed or cancelled install comes back from Android's installer
            val installProblem by installProblems.collectAsState()
            LaunchedEffect(installProblem) {
                installProblem?.let { update = update.copy(phase = UpdatePhase.Failed, message = it); installProblems.value = null }
            }

            // Re-checked whenever the launcher comes back (e.g. from the Home app settings)
            val home = remember(refresh) { homeStatus() }

            // Notifications: Pill Launcher's own (kept for a week) and other apps' (while they show them)
            var notices by remember { mutableStateOf(store.readNotices()) }
            val appNotices by NotificationWatcher.appNotices.collectAsState()
            var noticesSeen by remember { mutableLongStateOf(store.noticesSeen) }
            val noticeAccess = remember(refresh) { NotificationWatcher.hasAccess(this) }
            val postNotice = { n: Notice ->
                notices = withNotice(notices, n, Instant.now())
                store.saveNotices(notices)
            }
            // After an update: "Updated to 3.3", with what was new in it
            LaunchedEffect(Unit) {
                val last = store.lastVersion
                if (last != null && last != version && isNewer(version, last)) {
                    val pending = store.pendingNotes
                    val notes = if (pending.firstOrNull() == version) pending.drop(1) else emptyList()
                    postNotice(updatedNotice(version, notes, Instant.now()))
                }
                store.lastVersion = version
                store.pendingNotes = emptyList()
            }

            // Files from phones: received into the launcher's own folder, each one a notification
            val share = remember { fileShare(this) }
            LaunchedEffect(share) {
                share.arrivals.collect { f -> postNotice(fileNotice(f.name, f.size, f.from, Instant.now())) }
            }
            // Files tidies itself on each return to the launcher. An APK whose app is now installed is
            // never deleted on its own: the notification bar asks, once per APK. Old files go only when chosen.
            LaunchedEffect(refresh, settings.askInstalledApks, settings.tidyOld) {
                if (safe != null) return@LaunchedEffect
                val done = withContext(Dispatchers.IO) {
                    share.tidy(installed = { apk -> settings.askInstalledApks && isInstalled(apk) }, removeOld = settings.tidyOld)
                }
                val now = Instant.now()
                val asked = store.askedApks
                for (name in done.installed) {
                    val key = name + "|" + (share.file(name)?.length() ?: 0)
                    if (key in asked) continue
                    store.askedApks = store.askedApks + key
                    postNotice(installedApkNotice(name, now))
                }
                if (done.old.isNotEmpty()) {
                    postNotice(Notice("old:${now.toEpochMilli()}", NoticeKind.File, "Removed ${filesLabel(done.old)}", "Kept for 30 days", now))
                }
                for ((name, noticedAt) in done.tomorrow) {
                    postNotice(Notice("expiring:$name", NoticeKind.File, "$name goes tomorrow", "Files keeps things for 30 days · save it on a phone to keep it", Instant.ofEpochMilli(noticedAt), target = name))
                }
            }

            // A launcher never exits on Back
            BackHandler(enabled = true) { }

            LauncherScreen(
                apps = apps,
                weather = weather.takeIf { weatherOn },
                weatherError = weatherError.takeIf { weatherOn },
                prayers = prayers.takeIf { prayersOn },
                prayersError = prayersError.takeIf { prayersOn },
                prayersRefreshing = prayersRefreshing && prayersOn,
                onRefreshPrayers = { prayerRefresh++ },
                football = football,
                news = news.takeIf { newsOn },
                searchTeams = { query -> withContext(Dispatchers.IO) { findTeams(query) } },
                onlinePhotos = onlinePhotos,
                pins = pins,
                settings = if (safe != null) settings.copy(widgets = emptyList()) else settings,
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
                onRename = { app, name ->
                    val own = app.originalLabel ?: app.label
                    names = cleanName(name, own)?.let { names + (app.pkg to it) } ?: (names - app.pkg)
                    store.saveNames(names)
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
                update = update,
                version = version,
                updateDismissed = updateDismissed,
                onCheckUpdate = { updateScope.launch { checkForUpdate() } },
                onStartUpdate = start@{
                    val release = update.release ?: return@start
                    if (busy()) return@start
                    if (!updater.canInstall()) {
                        val opened = updater.askToInstall()
                        update = update.copy(
                            message = if (opened) "Allow Pill Launcher to install apps, then choose Update again"
                            else "Allow it in the TV's Settings → Apps → Special app access → Install unknown apps",
                        )
                        return@start
                    }
                    store.pendingNotes = listOf(release.version) + release.notes
                    updateJob = updateScope.launch {
                        update = update.copy(phase = UpdatePhase.Downloading, progress = 0f, message = null)
                        val apk = try {
                            var shown = 0f
                            updater.download(release) { p ->
                                if (p - shown >= 0.01f || p >= 1f) {
                                    shown = p
                                    update = update.copy(progress = p)
                                }
                            }
                        } catch (e: CancellationException) {
                            update = update.copy(phase = UpdatePhase.Available, progress = 0f)
                            throw e
                        } catch (e: Exception) {
                            update = update.copy(phase = UpdatePhase.Failed, message = "Couldn't download it: " + updateProblem(e))
                            return@launch
                        }
                        update = update.copy(phase = UpdatePhase.Installing, progress = 1f)
                        // Android's installer in the background, else its own install screen
                        val started = runCatching { updater.install(apk) }
                        if (started.isFailure && !updater.installWithSystemScreen(apk)) {
                            update = update.copy(phase = UpdatePhase.Failed, message = "Couldn't start the install: " + updateProblem(started.exceptionOrNull()!!))
                        }
                    }
                },
                onCancelUpdate = { updateJob?.cancel() },
                onLaterUpdate = {
                    updateDismissed = update.release?.version
                    store.updateDismissed = updateDismissed
                },
                notices = (notices + appNotices).sortedByDescending { it.at },
                noticesSeen = noticesSeen,
                onNoticesSeen = {
                    noticesSeen = System.currentTimeMillis()
                    store.noticesSeen = noticesSeen
                },
                onPostNotice = { postNotice(it) },
                onDismissNotice = { n ->
                    if (n.kind == NoticeKind.App) {
                        NotificationWatcher.dismiss(n)
                    } else {
                        notices = notices.filter { it.id != n.id }
                        store.saveNotices(notices)
                    }
                },
                onOpenAppNotice = { NotificationWatcher.open(this, it) },
                noticeAccess = noticeAccess,
                onNoticeAccess = { NotificationWatcher.askForAccess(this) },
                share = share.takeIf { safe == null },
                onOpenFile = { file -> openFile(file, updater) },
                safeMode = safe,
                onTryNormally = {
                    crashGuard.clear()
                    safeMode = null
                    recreate()
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

    /**
     * Opens a file from the Files page with the TV's apps: an APK with Android's installer
     * (asking for permission to install first), anything else with whatever app handles it.
     * Returns a message when nothing can.
     */
    private fun openFile(file: java.io.File, updater: Updater): String? {
        if (fileKind(file.name) == FileKind.App) {
            if (!updater.canInstall()) {
                return if (updater.askToInstall()) "Allow Pill Launcher to install apps, then choose Install again"
                else "Allow it in the TV's Settings → Apps → Special app access → Install unknown apps"
            }
            return if (updater.installWithSystemScreen(file)) null else "This TV can't install apps from files"
        }
        val ext = file.extension.lowercase()
        val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val uri = runCatching { androidx.core.content.FileProvider.getUriForFile(this, "$packageName.updates", file) }.getOrNull()
            ?: return "The TV can't open this file"
        val opened = tryStartActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return if (opened) null else "No app on this TV opens .$ext files"
    }

    // singleTask: pressing Home while the launcher exists arrives here
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) homePresses++
    }

    // Files runs while the launcher is on screen (so a phone can send without opening Files),
    // and with Keep receiving for 15 minutes after another app comes to the front
    override fun onStart() {
        super.onStart()
        NetworkWatch.onScreen = true
        NetworkWatch.refresh()
        if (safeMode == null) {
            val share = fileShare(this)
            Thread({ share.start() }, "share-start").start()
        }
    }

    override fun onStop() {
        NetworkWatch.onScreen = false
        if (safeMode == null) fileShare(this).stop(if (store.load().keepReceiving) KeepReceivingMs else 0)
        super.onStop()
    }

    /** Whether the app inside [apk] is installed at that version or newer. */
    private fun isInstalled(apk: java.io.File): Boolean {
        val archive = packageManager.getPackageArchiveInfo(apk.path, 0) ?: return false
        val installed = runCatching { packageManager.getPackageInfo(archive.packageName, 0) }.getOrNull() ?: return false
        return androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(installed) >= androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(archive)
    }

    private fun filesLabel(names: List<String>): String = if (names.size == 1) names[0] else "${names.size} files"

    override fun onResume() {
        super.onResume()
        refresh++
        if (lastScan != 0L && SystemClock.elapsedRealtime() - lastScan > 60 * 60 * 1000L) appsChanged++
        wakes++
    }

    override fun onDestroy() {
        runCatching { getSystemService(LauncherApps::class.java)?.unregisterCallback(appWatch) }
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
            // The system asks to confirm; afterwards the TV's own home screen takes over
            SystemAction.Uninstall ->
                if (tryStartActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))) null
                else "This TV can't uninstall apps from here. Use Settings → Apps instead"
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
