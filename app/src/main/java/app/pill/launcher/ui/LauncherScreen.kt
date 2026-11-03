package app.pill.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.draw.shadow
import androidx.compose.material3.LocalTextStyle
import kotlinx.coroutines.launch
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.State
import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.composed
import androidx.compose.animation.core.VectorConverter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import android.os.SystemClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppCategory
import app.pill.launcher.AppEntry
import androidx.compose.animation.core.LinearEasing
import app.pill.launcher.Fixtures
import app.pill.launcher.MatchDayMode
import app.pill.launcher.News
import app.pill.launcher.Team
import app.pill.launcher.TeamHit
import app.pill.launcher.WidgetKind
import app.pill.launcher.activeWidgets
import app.pill.launcher.cleanName
import app.pill.launcher.FootballMoment
import app.pill.launcher.footballMoments
import app.pill.launcher.momentMillis
import app.pill.launcher.stackShown
import app.pill.launcher.UpdatePhase
import app.pill.launcher.UpdateState
import androidx.compose.ui.graphics.TransformOrigin
import app.pill.launcher.dueBanner
import app.pill.launcher.pickHeadlines
import androidx.compose.runtime.produceState
import java.time.Instant
import app.pill.launcher.Photo
import app.pill.launcher.FavoritesLayout
import app.pill.launcher.HomeStatus
import app.pill.launcher.LauncherSettings
import app.pill.launcher.Weather
import app.pill.launcher.FileKind
import app.pill.launcher.FileShare
import app.pill.launcher.Notice
import app.pill.launcher.NoticeKind
import app.pill.launcher.backOnlineNotice
import app.pill.launcher.fileKind
import app.pill.launcher.kickoffNotice
import app.pill.launcher.momentNotice
import app.pill.launcher.offlineNotice
import app.pill.launcher.updateNotice
import app.pill.launcher.shownNotice
import app.pill.launcher.InstalledApk
import java.io.File
import kotlinx.coroutines.delay

private val FavoriteIcon = 96.dp

/** Room the greeting and widgets take at the top of the first page. */
private val HeaderHeight = 214.dp

/**
 * Space between the favourites row and the bottom of the first page. Also the margin
 * [PageScrollSpec] keeps when it scrolls, so going back up from the grid lands exactly
 * on the first page.
 */
private const val PAGE_EDGE = 0.14f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherScreen(
    apps: List<AppEntry>?,
    weather: Weather?,
    /** Why the last weather fetch failed, shown in settings; null when it works. */
    weatherError: String? = null,
    /** The followed teams' fixtures, in order (Algeria first); empty until loaded. */
    football: List<Fixtures> = emptyList(),
    /** Headlines from both sources, and why a source failed; null until the first fetch. */
    news: News? = null,
    /** Teams matching a search, for Settings → Football. */
    searchTeams: suspend (String) -> List<TeamHit> = { emptyList() },
    /** Today's online nature photos; empty until loaded, or when unavailable. */
    onlinePhotos: List<Photo> = emptyList(),
    pins: List<String>,
    settings: LauncherSettings,
    onSettingsChange: (LauncherSettings) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onTogglePin: (AppEntry) -> Unit,
    onOpenSystemSettings: () -> Unit,
    /** The TV & system shortcuts in settings; returns a message for the row (a result, or why it can't). */
    onSystemAction: (SystemAction) -> String? = { null },
    /** Move a favourite [by] places along the row. */
    onMovePin: (AppEntry, by: Int) -> Unit = { _, _ -> },
    onUninstall: (AppEntry) -> Unit = {},
    onAppInfo: (AppEntry) -> Unit = {},
    /** Give an app a name in the launcher; a blank name goes back to its own. */
    onRename: (AppEntry, String) -> Unit = { _, _ -> },
    /** The TV's Wi-Fi / network settings, from the status bar's connection button. */
    onOpenNetworkSettings: () -> Unit = {},
    /** Bumped each time Home is pressed while the launcher is already showing. */
    homePresses: Int = 0,
    /** Bumped when the TV wakes or the launcher comes back to the front: always start awake. */
    wakes: Int = 0,
    home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
    /** Ask to become the home screen; returns a message if this TV can't. */
    onMakeDefault: () -> String? = { null },
    /** Open Accessibility settings for the Home button helper; returns a message on failure. */
    onHomeHelper: () -> String? = { null },
    /** Open "Display over other apps" so Autostart can open the launcher at boot. */
    onAllowAutostart: () -> String? = { null },
    /** The TV's own search (Google / Assistant), when it has one. */
    onSystemSearch: (() -> Unit)? = null,
    /** Updates from GitHub: where one has got to, this version, and the release put off with Later. */
    update: UpdateState = UpdateState(),
    version: String = "",
    updateDismissed: String? = null,
    onCheckUpdate: () -> Unit = {},
    onStartUpdate: () -> Unit = {},
    onCancelUpdate: () -> Unit = {},
    onLaterUpdate: () -> Unit = {},
    /** Pill Launcher's kept notifications and other apps' current ones, newest first. */
    notices: List<Notice> = emptyList(),
    /** When the notification list was last opened (ms): anything newer is unread. */
    noticesSeen: Long = 0L,
    onNoticesSeen: () -> Unit = {},
    /** Keeps a notification (a goal, the connection coming back). */
    onPostNotice: (Notice) -> Unit = {},
    /** Dismisses a kept notification, or clears another app's. */
    onDismissNotice: (Notice) -> Unit = {},
    /** Does what another app's notification does. */
    onOpenAppNotice: (Notice) -> Unit = {},
    /** Whether the TV lets Pill Launcher read other apps' notifications, and opening its setting. */
    noticeAccess: Boolean = false,
    onNoticeAccess: () -> String? = { null },
    /** Files between phones and the TV; null where there is none. */
    share: FileShare? = null,
    /** Opens a file from the TV with its apps; a message when it can't. */
    onOpenFile: (File) -> String? = { null },
    /** Why the launcher started in safe mode (after crashing twice), or null normally. */
    safeMode: String? = null,
    onTryNormally: () -> Unit = {},
) {
    var panelOpen by remember { mutableStateOf(false) }
    // The page Settings opens on (Updates, from "Updated to …"); null for the list
    var settingsStart by remember { mutableStateOf<SettingsPage?>(null) }
    // The notification bar opened into its list, and the Files page
    var noticesOpen by remember { mutableStateOf(false) }
    var filesOpen by remember { mutableStateOf(false) }
    // An installed app's APK the notification bar offered to delete: Delete or Keep it
    var deleteAsk by remember { mutableStateOf<Notice?>(null) }
    val bellFocus = remember { FocusRequester() }
    val messageFocus = remember { FocusRequester() }
    var searchOpen by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf<String?>(null) }
    // Who took the photo on screen (online photos only), shown on the idle screen and in settings
    var photoCredit by remember { mutableStateOf<String?>(null) }
    var backdrop by remember { mutableStateOf<ImageBitmap?>(null) }
    var initialFocusDone by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<Toast?>(null) }
    var toastVisible by remember { mutableStateOf(false) }
    // After an unpin from the favourites row, the package that should take focus (null = the pill)
    var refocus by remember { mutableStateOf<Refocus?>(null) }
    var idle by remember { mutableStateOf(false) }
    // The app whose menu is open (and whether OK is still held), and the favourite being moved
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var moving by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<AppEntry?>(null) }
    // Settings → Widgets is open: the home screen steps aside to the left as a live preview
    var customizing by remember { mutableStateOf(false) }
    // The stack widget being set up in Settings, held in view on the home screen
    var stackPreview by remember { mutableStateOf<WidgetKind?>(null) }
    // A goal or win for a followed team, noticed between two fetches, celebrated for a few seconds
    var moment by remember { mutableStateOf<FootballMoment?>(null) }
    var seenFootball by remember { mutableStateOf(football) }
    LaunchedEffect(football) {
        val moments = footballMoments(seenFootball, football, Instant.now())
        moments.lastOrNull()?.let { moment = it }
        if (settings.footballNotices) moments.forEach { onPostNotice(momentNotice(it)) }
        seenFootball = football
    }
    LaunchedEffect(moment) { moment?.let { delay(momentMillis(it.kind)); moment = null } }
    var lastInput by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    // Pages opened from the widgets: the forecast, a team on the football page, a headline
    var forecastOpen by remember { mutableStateOf(false) }
    var clockOpen by remember { mutableStateOf(false) }
    var hubTeam by remember { mutableStateOf<Team?>(null) }
    var headlineAt by remember { mutableStateOf<Int?>(null) }
    // The Internet page, from the rail
    var internetOpen by remember { mutableStateOf(false) }
    val pageOpen = forecastOpen || clockOpen || hubTeam != null || headlineAt != null || internetOpen || filesOpen
    // The All apps chip picked (null: all), and the first chip, where the rail's Apps lands
    var appFilter by remember { mutableStateOf<AppCategory?>(null) }
    val chipFocus = remember { FocusRequester() }
    // The widget row, so Down from the bar can go to it; whether it holds focus right now
    val widgetFocus = remember { FocusRequester() }
    var widgetsFocused by remember { mutableStateOf(false) }
    // Settings was closed by going somewhere from the bar: focus goes there, not back to the gear
    var panelLeftByBar by remember { mutableStateOf(false) }
    // Kick-off banners already seen (by match), so each shows once
    var bannersShown by remember { mutableStateOf(setOf<String>()) }
    val headlines = remember(news, settings.newsTopics) { news?.let { pickHeadlines(it, settings.newsTopics) }.orEmpty() }

    val pillFocus = remember { FocusRequester() }
    val favoriteFocus = remember { HashMap<String, FocusRequester>() }
    val pinnedApps = apps.orEmpty().let { list -> pins.mapNotNull { pkg -> list.firstOrNull { it.pkg == pkg } } }

    fun togglePin(app: AppEntry, fromFavorites: Boolean) {
        val wasPinned = app.pkg in pins
        if (wasPinned && fromFavorites) {
            val i = pinnedApps.indexOfFirst { it.pkg == app.pkg }
            val neighbour = pinnedApps.getOrNull(i + 1) ?: pinnedApps.getOrNull(i - 1)
            refocus = Refocus(neighbour?.pkg)
        }
        onTogglePin(app)
        toast = Toast(if (wasPinned) "Removed ${app.label} from Favorites" else "Added ${app.label} to Favorites")
    }

    LaunchedEffect(moving, pins) {
        val pkg = moving ?: return@LaunchedEffect
        repeat(10) {
            withFrameNanos { }
            if (runCatching { favoriteFocus[pkg]?.requestFocus() }.isSuccess) return@LaunchedEffect
        }
    }
    // A moved favourite that's unpinned or uninstalled ends the move
    if (moving != null && moving !in pins) moving = null

    val scroll = rememberScrollState()
    val customize by animateFloatAsState(if (customizing) 1f else 0f, tween(Motion.Page, easing = Motion.EnterEasing), label = "customize")
    val scope = rememberCoroutineScope()
    val currentPinned by rememberUpdatedState(pinnedApps)

    // Focus the first favourite, else the settings pill. Page scrolling then brings the first
    // page into view. Retries for a few frames until the target is attached.
    suspend fun focusFirstPage() {
        repeat(30) {
            withFrameNanos { }
            // The favourites row registers its tiles a frame or two after it first composes,
            // so look the target up on every try and only use the pill when there are no favourites
            val first = currentPinned.firstOrNull()
            val target = if (first == null) pillFocus else favoriteFocus[first.pkg]
            if (target != null && runCatching { target.requestFocus() }.isSuccess) return
        }
        runCatching { pillFocus.requestFocus() }
    }

    // Down from the bar at the top right. The scrolling page under it covers the whole
    // screen, the bar's corner included, so focus search never finds it "below" the bar;
    // this says where Down goes instead: the widgets (when one takes focus), else the
    // first favourite, and the top of All apps when that is on screen or nothing else is.
    fun leaveBar() {
        scope.launch {
            if (scroll.value == 0) {
                runCatching { widgetFocus.requestFocus() }
                withFrameNanos { }
                if (widgetsFocused) return@launch
                if (runCatching { bellFocus.requestFocus() }.isSuccess) return@launch
                val first = currentPinned.firstOrNull()?.let { favoriteFocus[it.pkg] }
                if (first != null && runCatching { first.requestFocus() }.isSuccess) return@launch
            }
            runCatching { chipFocus.requestFocus() }
        }
    }

    LaunchedEffect(apps != null) {
        if (apps == null || initialFocusDone) return@LaunchedEffect
        focusFirstPage()
        initialFocusDone = true
    }

    // Home while already home: close settings and go back to the first page
    LaunchedEffect(homePresses) {
        if (homePresses == 0) return@LaunchedEffect
        panelOpen = false
        searchOpen = false
        forecastOpen = false
        clockOpen = false
        hubTeam = null
        headlineAt = null
        internetOpen = false
        filesOpen = false
        noticesOpen = false
        idle = false
        focusFirstPage()
    }

    // Waking the TV (or coming back from an app) starts awake, with a fresh idle timer:
    // the timer keeps counting while the screen is off, so it would otherwise wake up idle
    LaunchedEffect(wakes) {
        idle = false
        lastInput = SystemClock.uptimeMillis()
    }

    // Idle: after idleMinutes without a key press, everything but the widgets fades
    // out. Each key press restarts the timer; panels open hold it off.
    LaunchedEffect(settings.idleMinutes, lastInput, panelOpen, searchOpen, pageOpen, noticesOpen) {
        if (settings.idleMinutes == 0 || panelOpen || searchOpen || pageOpen || noticesOpen) return@LaunchedEffect
        delay(settings.idleMinutes * 60_000L)
        idle = true
        // Back to the first page, with focus there too, so waking up lands somewhere visible
        focusFirstPage()
    }
    // While idle the widgets glide to the middle of the screen and grow, then hold still.
    // Waking, they glide back gently, and the rest of Home fades in once they're on their way.
    val idleFocus by animateFloatAsState(
        if (idle) 1f else 0f,
        if (idle) tween(1800, easing = FastOutSlowInEasing) else tween(1300, easing = WakeEasing),
        label = "idleFocus",
    )
    val idleShift = rememberBurnInShift(idle && settings.idleAnimation)
    val idleFade by animateFloatAsState(
        if (idle) 1f else 0f,
        if (idle) tween(1600, easing = FastOutSlowInEasing) else tween(1000, delayMillis = 250, easing = WakeEasing),
        label = "idleFade",
    )

    // The chips above All apps: All, then each kind of app there is; a new chip waves its apps in
    val categories = remember(apps) { listOf<AppCategory?>(null) + AppCategory.entries.filter { c -> apps.orEmpty().any { it.category == c } } }
    val shownApps = apps.orEmpty().filter { appFilter == null || it.category == appFilter }
    val gridClock = remember { Animatable(Motion.RevealMs.toFloat()) }
    var wavedFilter by remember { mutableStateOf(appFilter) }
    LaunchedEffect(appFilter) {
        if (appFilter == wavedFilter) return@LaunchedEffect
        wavedFilter = appFilter
        gridClock.snapTo(0f)
        gridClock.animateTo(Motion.RevealMs.toFloat(), tween(Motion.RevealMs, easing = LinearEasing))
    }
    val gridReveal = remember { derivedStateOf { gridClock.value } }
    // Arriving in All apps, its cards wave in, the same as a new chip's
    val inAllApps by remember { derivedStateOf { scroll.value > 0 } }
    LaunchedEffect(inAllApps) {
        if (!inAllApps) return@LaunchedEffect
        gridClock.snapTo(0f)
        gridClock.animateTo(Motion.RevealMs.toFloat(), tween(Motion.RevealMs + 200, easing = LinearEasing))
    }

    // Back in All apps returns to the first page (on the first page, Back does nothing)
    val scrolled by remember { derivedStateOf { scroll.value > 0 } }
    BackHandler(enabled = scrolled && !panelOpen) { scope.launch { focusFirstPage() } }

    // A followed team about to kick off: a banner that Back hides (checked every 15 seconds)
    val bannerNow by produceState(Instant.now(), football) {
        while (true) {
            value = Instant.now()
            delay(15_000)
        }
    }
    val banner = if (WidgetKind.Fennecs in settings.activeWidgets() && settings.matchDay != MatchDayMode.Off) {
        dueBanner(football, settings.matchDay, bannerNow, bannersShown)
    } else {
        null
    }

    // The connection: a notice while it's been gone a minute, and one saying how long once it's back
    val network by rememberNetworkStatus()
    val offline = network.link == Link.Offline
    var offlineSince by remember { mutableStateOf<Instant?>(null) }
    var offlineShown by remember { mutableStateOf(false) }
    LaunchedEffect(offline) {
        if (offline) {
            val since = Instant.now()
            offlineSince = since
            delay(60_000)
            offlineShown = true
        } else {
            val since = offlineSince
            if (since != null && offlineShown && settings.connectionNotices) onPostNotice(backOnlineNotice(since, Instant.now()))
            offlineSince = null
            offlineShown = false
        }
    }

    // Everything in the notification bar: an update, a kick-off, the connection, then what's kept
    // and other apps' (unless muted), newest first
    var dismissedLive by remember { mutableStateOf(setOf<String>()) }
    val allNotices = remember(notices, update, updateDismissed, banner, offlineShown, dismissedLive, settings, bannerNow) {
        val now = Instant.now()
        listOfNotNull(
            updateNotice(update, updateDismissed, now),
            banner?.takeIf { settings.footballNotices }?.let { (team, match) -> kickoffNotice(team, match, now) },
            offlineSince?.takeIf { offlineShown && settings.connectionNotices }?.let { offlineNotice(it) },
        ).plus(
            notices.filter { it.kind != NoticeKind.App || (settings.appNotices && it.pkg !in settings.mutedApps) }
                .map { shownNotice(it, settings.hidePrivate) },
        )
            .filter { it.id !in dismissedLive }
            .sortedByDescending { it.at }
    }
    val unread = allNotices.count { it.at.toEpochMilli() > noticesSeen }
    val listOver by animateFloatAsState(if (noticesOpen) 1f else 0f, tween(Motion.Enter, easing = Motion.EnterEasing), label = "listOver")
    LaunchedEffect(noticesOpen) { if (noticesOpen) onNoticesSeen() }
    LaunchedEffect(idle) { if (idle) noticesOpen = false }

    fun dismissNotice(n: Notice) {
        when {
            n.kind == NoticeKind.Update -> onLaterUpdate()
            n.id.startsWith("kickoff:") -> bannersShown = bannersShown + n.id.removePrefix("kickoff:")
            n.id.startsWith("offline:") -> dismissedLive = dismissedLive + n.id
            else -> onDismissNotice(n)
        }
    }

    // OK on a notification: what it's about
    fun actOnNotice(n: Notice) {
        when (n.kind) {
            NoticeKind.Update -> {
                if (update.phase == UpdatePhase.Available || update.phase == UpdatePhase.Failed) onStartUpdate()
                return
            }
            NoticeKind.Updated -> {
                settingsStart = SettingsPage.Updates
                panelOpen = true
            }
            NoticeKind.Football -> (football.firstOrNull { it.team.id == n.target } ?: football.firstOrNull())?.let { hubTeam = it.team }
            NoticeKind.Connection -> internetOpen = true
            NoticeKind.File -> {
                val file = n.target?.let { share?.file(it) }
                if (n.id.startsWith(InstalledApk)) {
                    // Asks first: nothing is deleted without a second press
                    if (file != null) deleteAsk = n else onDismissNotice(n)
                } else if (file == null || fileKind(file.name) == FileKind.Photo) {
                    filesOpen = true
                } else {
                    onOpenFile(file)?.let { toast = Toast(it) }
                }
            }
            NoticeKind.App -> onOpenAppNotice(n)
        }
        noticesOpen = false
    }

    // Return focus to the pill when the settings panel closes
    LaunchedEffect(panelOpen) {
        if (panelLeftByBar) {
            panelLeftByBar = false
            return@LaunchedEffect
        }
        if (!panelOpen && initialFocusDone) {
            delay(100)
            runCatching { pillFocus.requestFocus() }
        }
    }

    // Keep focus on the first page after the focused favourite is unpinned
    LaunchedEffect(refocus) {
        val target = refocus ?: return@LaunchedEffect
        delay(50)
        runCatching { (target.pkg?.let { favoriteFocus[it] } ?: pillFocus).requestFocus() }
        refocus = null
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            toastVisible = true
            delay(2200)
            toastVisible = false
        }
    }

    // The photo's colour, which glass, the widgets and the focus glow take a little of. It
    // drifts to each new photo's over a few seconds, well after the photos have crossfaded.
    val tintTarget = remember(backdrop) { backdrop?.let { wallpaperTint(it) } ?: NeutralTint }
    val wallpaperTint = animateColorAsState(tintTarget, tween(3000, easing = FastOutSlowInEasing), label = "wallpaperTint")

    // The bar at the top right leads anywhere from anywhere. Settings opens over whatever is
    // showing (a page, All apps, Search), so Back returns there; the others close what is
    // open first, and Home goes back to the first page.
    fun navigate(item: NavItem) {
        noticesOpen = false
        if (item == NavItem.Settings) {
            settingsStart = null
            panelOpen = true
            return
        }
        // Leaving Settings by the bar: its close mustn't send focus back to the gear
        if (panelOpen) {
            panelLeftByBar = true
            panelOpen = false
        }
        val already = when (item) {
            NavItem.Search -> searchOpen
            NavItem.Internet -> internetOpen
            else -> false
        }
        if (already) return
        forecastOpen = false
        clockOpen = false
        hubTeam = null
        headlineAt = null
        internetOpen = false
        filesOpen = false
        searchOpen = false
        when (item) {
            NavItem.Home -> scope.launch { focusFirstPage() }
            NavItem.Search -> searchOpen = true
            NavItem.Internet -> internetOpen = true
            NavItem.Settings -> Unit
        }
    }
    val navBar: @Composable (NavItem?) -> Unit = { current -> NavBar(current, onSelect = { navigate(it) }) }

    // The blurred photo for glass surfaces, and Roboto Flex as the default for all text
    CompositionLocalProvider(
        LocalNavBar provides navBar,
        LocalBackdrop provides backdrop,
        LocalWallpaperTint provides wallpaperTint,
        LocalTextStyle provides LocalTextStyle.current.merge(TextStyle(fontFamily = Flex)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Palette.Ink)
                .onPreviewKeyEvent { event ->
                    lastInput = SystemClock.uptimeMillis()
                    // While idle, a key only wakes the screen; it does not also act
                    if (idle) {
                        if (event.type == KeyEventType.KeyDown) idle = false
                        true
                    } else {
                        false
                    }
                },
        ) {
            BackgroundSlideshow(
                // Today's online set when there is one, with the built-in photos to fall back on
                photos = if (settings.onlinePhotos && onlinePhotos.isNotEmpty()) onlinePhotos else BackgroundSources.photos,
                fallback = if (settings.onlinePhotos && onlinePhotos.isNotEmpty()) BackgroundSources.photos else emptyList(),
                onShown = {
                    photoCredit = it.credit
                },
                intervalMs = settings.intervalSec * 1000L,
                enabled = settings.slideshow,
                drift = idle && settings.idleAnimation,
                onStatus = { photoError = it },
                onBackdrop = { backdrop = it },
            )
            BackgroundScrim()

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val contentWidth = maxWidth - Spacing.ScreenH * 2
                // Wrapped favourites can need more than one row; the first page grows to fit them
                val favoriteRows = if (settings.favoritesLayout == FavoritesLayout.Wrap) {
                    favoritesPerRow(contentWidth, FavoriteIcon).let { (pinnedApps.size + it - 1) / it }.coerceAtLeast(1)
                } else {
                    1
                }
                val pageHeight = maxOf(
                    maxHeight - Spacing.ScreenV,
                    HeaderHeight + favoritesRowHeight(FavoriteIcon) * favoriteRows + maxHeight * PAGE_EDGE,
                )
                val pageEdge = maxHeight * PAGE_EDGE
                val screenHeight = maxHeight
                val pageHeightPx = with(LocalDensity.current) { pageHeight.toPx() }
                val columns = (contentWidth / (AppCardMinWidth + 10.dp)).toInt().coerceAtLeast(1)
                // 0 on the first page, 1 once the grid fills the screen; dims the photo behind the apps
                val depth by remember(pageHeightPx) { derivedStateOf { (scroll.value / pageHeightPx).coerceIn(0f, 1f) } }
                // Alpha is read in the draw phase, so scrolling does not recompose the screen
                // Scrolling into All apps blurs and dims the photo, so the grid reads cleanly
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = depth }) {
                    backdrop?.let { drawBackdrop(it, IntSize(size.width.toInt(), size.height.toInt())) }
                    drawRect(Color(0x8C05070D))
                }

                // Every tile is composed (a launcher has at most a few hundred apps), so plain D-pad
                // focus search reaches the grid from the first page; PageScrollSpec does the scrolling.
                val topPx = with(LocalDensity.current) { Spacing.ScreenV.toPx() }
                val pageScrolling = remember(scroll, pageHeightPx) { PageScrollSpec(scroll, topPx + pageHeightPx) }
                // While Settings → Widgets is open, the first page shrinks into the space left of the panel
                val previewTarget = previewScale(contentWidth, maxWidth)
                val previewTop = (maxHeight - minOf(pageHeight, maxHeight) * previewTarget) / 2
                CompositionLocalProvider(LocalBringIntoViewSpec provides pageScrolling) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val c = customize
                                if (c > 0f) {
                                    val target = previewTarget
                                    val sc = 1f + (target - 1f) * c
                                    scaleX = sc
                                    scaleY = sc
                                    transformOrigin = TransformOrigin(0f, 0f)
                                    translationX = c * (PreviewMargin.toPx() - Spacing.ScreenH.toPx() * target)
                                    translationY = c * (previewTop.toPx() - Spacing.ScreenV.toPx() * target)
                                }
                            }
                            .verticalScroll(scroll)
                            .padding(start = Spacing.ScreenH, end = Spacing.ScreenH, top = Spacing.ScreenV, bottom = 48.dp),
                    ) {
                        // First page: the greeting, the widgets and favourites only. The grid starts below the fold.
                        // Going down into All apps it dissolves: fading, drifting more slowly than the
                        // scroll and settling back a little, so All apps comes forward over it.
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .height(pageHeight)
                                .graphicsLayer {
                                    val d = depth
                                    if (d > 0f) {
                                        val t = (d * 1.6f).coerceIn(0f, 1f)
                                        alpha = 1f - t * t * (3f - 2f * t)
                                        translationY = scroll.value * 0.35f
                                        val sc = 1f - 0.05f * t
                                        scaleX = sc
                                        scaleY = sc
                                        transformOrigin = TransformOrigin(0.5f, 0f)
                                    }
                                },
                        ) {
                            // Level with the bar at the top right, which has the rest of the line to itself
                            Box(
                                Modifier
                                    .offset(y = NavTop - Spacing.ScreenV)
                                    .padding(start = Spacing.Tile, bottom = 12.dp)
                                    .height(NavHeight)
                                    .dissolveStep({ 1f - idleFade }, step = 1, steps = 3),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                Greeting()
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Spacing.Tile),
                                verticalAlignment = Alignment.Top,
                            ) {
                                WidgetRow(
                                    widgets = settings.widgets,
                                    weather = weather,
                                    weatherError = weatherError,
                                    mode = settings.weather,
                                    football = football,
                                    matchDay = settings.matchDay,
                                    headlines = headlines,
                                    headlinesStatus = if (news != null && headlines.isEmpty()) "Can't reach the news sources · see Settings" else null,
                                    animate = !idle,
                                    modifier = Modifier
                                        .weight(1f)
                                        .focusRequester(widgetFocus)
                                        .onFocusChanged { widgetsFocused = it.hasFocus }
                                        // Down from any widget lands at the start of the notification bar (a widget
                                        // stack uses Down itself first, to flip its cards)
                                        .onKeyEvent { e ->
                                            if (e.key != Key.DirectionDown || safeMode != null) return@onKeyEvent false
                                            if (e.type == KeyEventType.KeyDown) runCatching { messageFocus.requestFocus() }
                                            true
                                        }
                                        .focusGroup()
                                        .idlePresentation(focus = { idleFocus }, shift = { idleShift.value }),
                                    onOpenClock = { clockOpen = true },
                                    onOpenWeather = { forecastOpen = true },
                                    onOpenFootball = { hubTeam = it },
                                    onOpenHeadline = { headlineAt = it },
                                    stack = settings.stackShown(),
                                    stackPreview = stackPreview,
                                    moment = moment.takeIf { !idle },
                                )
                            }

                            // The notification bar, as wide as the widgets: the newest notice, the bell and Files.
                            // In safe mode, a note saying why instead
                            if (safeMode != null) {
                                SafeModeCard(
                                    reason = safeMode,
                                    onTryNormally = onTryNormally,
                                    modifier = Modifier.padding(start = Spacing.Tile, end = Spacing.Tile, top = 14.dp),
                                )
                            } else NotificationBar(
                                notices = allNotices,
                                unread = unread,
                                open = noticesOpen,
                                onOpenChange = { noticesOpen = it },
                                apps = apps.orEmpty(),
                                update = update,
                                onPrimary = { actOnNotice(it) },
                                onDismiss = { dismissNotice(it) },
                                onClearAll = {
                                    allNotices.forEach { dismissNotice(it) }
                                    noticesOpen = false
                                },
                                onLaterUpdate = onLaterUpdate,
                                onCancelUpdate = onCancelUpdate,
                                onFiles = {
                                    noticesOpen = false
                                    if (share != null) filesOpen = true else toast = Toast("Files isn't available on this TV")
                                },
                                bellFocus = bellFocus,
                                messageFocus = messageFocus,
                                // Down from anywhere on the bar starts the row below at the left
                                onDown = {
                                    scope.launch {
                                        val first = currentPinned.firstOrNull()?.let { favoriteFocus[it.pkg] }
                                        if (first == null || runCatching { first.requestFocus() }.isFailure) runCatching { chipFocus.requestFocus() }
                                    }
                                },
                                modifier = Modifier
                                    .padding(start = Spacing.Tile, end = Spacing.Tile, top = 14.dp)
                                    .dissolveStep({ 1f - idleFade }, step = 1, steps = 3)
                                    // Nothing on the idle screen but the widgets and the photo
                                    .then(if (idle) Modifier.clearAndSetSemantics { } else Modifier),
                            )

                            Spacer(Modifier.weight(1f))

                            // Until the launcher is the home screen (or helped), closing apps leads elsewhere
                            AnimatedVisibility(
                                visible = !idle && !home.isDefault && !home.redirectOn && !settings.homePromptDismissed,
                                enter = fadeIn(),
                                exit = fadeOut() + shrinkVertically(),
                            ) {
                                HomePrompt(
                                    onSetUp = {
                                        val cannot = onMakeDefault()
                                        if (cannot != null) {
                                            // The TV keeps its own home screen: the Home button helper is the way
                                            toast = Toast(onHomeHelper() ?: "Turn on Pill Launcher here to bring Home back to it")
                                        }
                                    },
                                    onDismiss = { onSettingsChange(settings.copy(homePromptDismissed = true)) },
                                    modifier = Modifier
                                        .padding(horizontal = Spacing.Tile, vertical = 8.dp)
                                        .dissolveStep({ 1f - idleFade }, step = 2, steps = 3),
                                )
                            }

                            // Favorites step back while the notification list is open over them
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = favoritesRowHeight(FavoriteIcon))
                                    .graphicsLayer { alpha = 1f - 0.85f * listOver }
                                    .dissolveStep({ 1f - idleFade }, step = 2, steps = 3),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                if (pinnedApps.isEmpty()) {
                                    EmptyFavorites(loading = apps == null)
                                } else {
                                    FavoritesRow(
                                        apps = pinnedApps,
                                        layout = settings.favoritesLayout,
                                        animate = settings.favoritesAnimation,
                                        iconSize = FavoriteIcon,
                                        width = contentWidth,
                                        focusFor = { app -> favoriteFocus.getOrPut(app.pkg) { FocusRequester() } },
                                        onOpen = onOpen,
                                        onMenu = { app, held -> menu = MenuRequest(app, held, fromFavorites = true) },
                                        moving = moving,
                                        onMove = onMovePin,
                                        onMoveDone = {
                                            moving = null
                                            toast = Toast("Done")
                                        },
                                    )
                                }
                            }

                            Spacer(Modifier.height(pageEdge))
                        }

                        // All apps fills at least a screen, so it scrolls to the top even with a short filter.
                        // It rises into place and comes into focus as the first page dissolves.
                        val rise = with(LocalDensity.current) { 28.dp.toPx() }
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = screenHeight - Spacing.ScreenV - 48.dp)
                                .graphicsLayer {
                                    val t = ((depth - 0.25f) / 0.6f).coerceIn(0f, 1f)
                                    val e = 1f - (1f - t) * (1f - t) * (1f - t)
                                    alpha = 0.15f + 0.85f * e
                                    translationY = (1f - e) * rise
                                },
                        ) {
                        SectionTitle(
                            "All apps",
                            hint = apps?.let { if (it.size == 1) "1 app" else "${it.size} apps" },
                            modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                        )
                        if (!apps.isNullOrEmpty()) {
                            CategoryChips(
                                categories = categories,
                                selected = appFilter,
                                onSelect = { appFilter = it },
                                firstFocus = chipFocus,
                                modifier = Modifier.padding(start = Spacing.Tile, top = 4.dp, bottom = 16.dp),
                            )
                        }
                        // A new chip waves its apps in
                        CompositionLocalProvider(LocalReveal provides gridReveal) {
                            for ((r, row) in shownApps.chunked(columns).withIndex()) {
                                Row(Modifier.fillMaxWidth().padding(start = 7.dp, end = 7.dp, bottom = 10.dp).bottomEdgeFade(scroll, pageEdge)) {
                                    for ((c, app) in row.withIndex()) {
                                        key(app.pkg) {
                                            AppCard(
                                                app = app,
                                                showPinBadge = app.pkg in pins,
                                                onOpen = { onOpen(app) },
                                                onMenu = { held -> menu = MenuRequest(app, held, fromFavorites = false) },
                                                // Equal cells across the row
                                                modifier = Modifier.weight(1f).padding(horizontal = 5.dp).reveal(r + c),
                                            )
                                        }
                                    }
                                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                        }
                    }
                }
            }

            NavBar(
                current = if (scrolled) null else NavItem.Home,
                onSelect = { navigate(it) },
                settingsFocus = pillFocus,
                onDown = { leaveBar() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = NavTop, end = Spacing.ScreenH)
                    .dissolveStep({ 1f - idleFade }, step = 0, steps = 3),
            )

            AnimatedVisibility(
                visible = toastVisible,
                // Bottom centre: clear of the widgets on the first page
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 36.dp),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
            ) {
                ToastChip(toast?.message.orEmpty())
            }

            if (panelOpen) {
                SettingsPanel(
                    settings = settings,
                    onChange = onSettingsChange,
                    onSystemAction = { action ->
                        if (action == SystemAction.AllSettings) {
                            onOpenSystemSettings()
                            null
                        } else {
                            onSystemAction(action)
                        }
                    },
                    photoError = photoError,
                    weatherError = weatherError,
                    football = football,
                    news = news,
                    searchTeams = searchTeams,
                    photoCredit = photoCredit,
                    home = home,
                    onMakeDefault = onMakeDefault,
                    onHomeHelper = onHomeHelper,
                    onAllowAutostart = onAllowAutostart,
                    onDismiss = { panelOpen = false; customizing = false; stackPreview = null },
                    onCustomizing = { on ->
                        customizing = on
                        if (on) scope.launch { scroll.animateScrollTo(0, tween(Motion.Page, easing = Motion.EnterEasing)) }
                    },
                    onStackPreview = { stackPreview = it },
                    update = update,
                    version = version,
                    onCheckUpdate = onCheckUpdate,
                    onStartUpdate = onStartUpdate,
                    onCancelUpdate = onCancelUpdate,
                    startPage = settingsStart,
                    noticeAccess = noticeAccess,
                    onNoticeAccess = onNoticeAccess,
                    share = share,
                    noticeApps = (notices.filter { it.kind == NoticeKind.App }.mapNotNull { it.pkg } + settings.mutedApps)
                        .distinct()
                        .map { pkg -> pkg to (apps.orEmpty().firstOrNull { it.pkg == pkg }?.label ?: pkg) }
                        .sortedBy { it.second.lowercase() },
                )
            }

            menu?.let { request ->
                val app = request.app
                AppMenu(
                    app = app,
                    pinned = app.pkg in pins,
                    okHeld = request.okHeld,
                    onAction = { action ->
                        when (action) {
                            MenuAction.TogglePin -> togglePin(app, fromFavorites = request.fromFavorites)
                            MenuAction.Move -> {
                                searchOpen = false
                                moving = app.pkg
                                toast = Toast("Left / Right to move ${app.label} · OK when done")
                            }
                            MenuAction.Rename -> renaming = app
                            MenuAction.AppInfo -> onAppInfo(app)
                            MenuAction.Uninstall -> onUninstall(app)
                        }
                    },
                    onDismiss = { menu = null },
                )
            }

            renaming?.let { app ->
                RenameDialog(
                    app = app,
                    onRename = { name ->
                        val own = app.originalLabel ?: app.label
                        val given = cleanName(name, own)
                        if ((given ?: own) != app.label) {
                            onRename(app, name)
                            toast = Toast(if (given == null) "$own has its own name again" else "Renamed $own to $given")
                        }
                    },
                    onDismiss = { renaming = null },
                )
            }

            if (clockOpen) {
                ClockPage(place = weather?.place, onDismiss = { clockOpen = false })
            }
            if (forecastOpen && weather != null) {
                ForecastPage(weather, settings.weather, onDismiss = { forecastOpen = false })
            }
            hubTeam?.let { team ->
                FootballHub(football, team, onDismiss = { hubTeam = null })
            }
            headlineAt?.let { start ->
                HeadlinePage(headlines, start, onDismiss = { headlineAt = null })
            }
            if (internetOpen) {
                InternetPage(onOpenNetworkSettings = onOpenNetworkSettings, onDismiss = { internetOpen = false })
            }
            // Delete an installed app's APK? Only Delete removes it
            deleteAsk?.let { n ->
                val name = n.target.orEmpty()
                MenuCard(
                    okHeld = false,
                    header = {
                        FilesIcon(Palette.TextPrimary, 30.dp)
                        Spacer(Modifier.width(14.dp))
                        Text("Delete $name?", color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    // Keep it first, so the focus starts on the choice that loses nothing
                    choices = listOf(
                        MenuChoice("Keep it", "Files won't ask about it again") { onDismissNotice(n) },
                        MenuChoice("Delete", "The app stays installed; only the file goes", warning = true) {
                            share?.delete(name)
                            onDismissNotice(n)
                            toast = Toast("Deleted $name")
                        },
                    ),
                    onDismiss = { deleteAsk = null },
                )
            }

            // A phone typed the code: Allow or Block, over whatever is showing
            share?.let { files ->
                val pending = files.state.collectAsState().value.pending
                if (pending != null) {
                    key(pending.id) {
                        var answered by remember { mutableStateOf(false) }
                        if (!answered) {
                            MenuCard(
                                okHeld = false,
                                header = {
                                    FilesIcon(Palette.TextPrimary, 30.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Text("${pending.name} wants to connect", color = Palette.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                },
                                choices = listOf(
                                    MenuChoice("Allow", "It can send and save files for 90 days") { files.approve(pending.id) },
                                    MenuChoice("Block", "Someone typed this TV's code on it", warning = true) { files.block(pending.id) },
                                ),
                                onDismiss = { answered = true },
                            )
                        }
                    }
                }
            }
            if (filesOpen && share != null) {
                FilesPage(
                    share = share,
                    keepReceiving = settings.keepReceiving,
                    onKeepReceiving = { onSettingsChange(settings.copy(keepReceiving = it)) },
                    onOpenFile = onOpenFile,
                    onDismiss = { filesOpen = false },
                )
            }

            if (searchOpen) {
                SearchPanel(
                    apps = apps.orEmpty(),
                    pins = pins,
                    onOpen = onOpen,
                    onMenu = { app, held -> menu = MenuRequest(app, held, fromFavorites = false) },
                    onSystemSearch = onSystemSearch,
                    onDismiss = { searchOpen = false },
                )
            }
        }
    }
}

/**
 * Page-like D-pad scrolling for the home screen.
 *
 * Anything on the first page scrolls back to the top. In the grid nothing moves while the
 * focused row is inside the current page; moving below it brings the new row up to
 * [PAGE_EDGE] from the top, so the grid turns over a page at a time, and moving above it
 * puts the row [PAGE_EDGE] above the bottom.
 *
 * The chosen scroll target is remembered: Compose asks again on every animation frame
 * with the item's new offset, and answering from the target (not from whether the item is
 * already visible) keeps the animation going all the way to the page.
 */
@OptIn(ExperimentalFoundationApi::class)
private class PageScrollSpec(
    private val scroll: ScrollState,
    /** Where the first page ends, in px from the top of the content. */
    private val firstPageEnd: Float,
) : BringIntoViewSpec {
    private var target = 0f

    // Between the first page and All apps the move is a page turn: longer, easing out gently.
    // Inside the grid, a row at a time, it stays quick.
    private var pageTurn = false
    override val scrollAnimationSpec: AnimationSpec<Float>
        get() = if (pageTurn) tween(Motion.Page + 100, easing = Motion.EnterEasing) else tween(300, easing = FastOutSlowInEasing)

    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val current = scroll.value.toFloat()
        // Follow scrolling done some other way (touch, or a jump) before this request
        if (!scroll.isScrollInProgress) target = current
        val top = current + offset
        val bottom = top + size
        val edge = containerSize * PAGE_EDGE
        val from = target
        target = when {
            top < firstPageEnd -> 0f
            // Coming down from the first page into All apps: its title goes to the top
            current < firstPageEnd - 1f && bottom <= firstPageEnd + containerSize -> firstPageEnd
            top >= target && bottom <= target + containerSize -> target
            top > target -> top - edge
            else -> bottom - (containerSize - edge)
        }.coerceIn(0f, scroll.maxValue.toFloat())
        // Crossing the fold either way is a page turn
        pageTurn = (from < firstPageEnd - 1f) != (target < firstPageEnd - 1f)
        return target - current
    }
}

/**
 * Fades a grid row as it reaches the bottom of the screen while more rows continue below,
 * so the grid dissolves into the edge like the favourites row. Never the focused row.
 * Position is read in layout and applied in the draw phase, so scrolling only redraws.
 */
@Composable
private fun Modifier.bottomEdgeFade(scroll: ScrollState, zone: Dp): Modifier {
    val zonePx = with(LocalDensity.current) { zone.toPx() }
    var bottom by remember { mutableFloatStateOf(0f) }
    var screen by remember { mutableFloatStateOf(0f) }
    var height by remember { mutableFloatStateOf(0f) }
    var holdsFocus by remember { mutableStateOf(false) }
    val barPx = with(LocalDensity.current) { NavRoom.toPx() }
    return this
        .onGloballyPositioned {
            bottom = it.positionInRoot().y + it.size.height
            height = it.size.height.toFloat()
            screen = it.findRootCoordinates().size.height.toFloat()
        }
        .onFocusChanged { holdsFocus = it.hasFocus }
        .graphicsLayer {
            val t = if (holdsFocus || !scroll.canScrollForward || screen == 0f) 0f
            else ((bottom - (screen - zonePx)) / zonePx).coerceIn(0f, 1f)
            // A row going up under the bar at the top fades away, so the bar always has its space
            val top = bottom - height
            val u = if (holdsFocus || height == 0f) 0f else ((barPx - top) / (height * 0.6f)).coerceIn(0f, 1f)
            alpha = (1f - 0.75f * t * t * (3 - 2 * t)) * (1f - u * u * (3 - 2 * u))
        }
}

/** A short confirmation message. A new instance restarts the hide timer even for the same text. */
private class Toast(val message: String)

private class Refocus(val pkg: String?)

/**
 * Idle presentation of the widget row: at [focus] 1 it sits centred on the screen,
 * enlarged to about 86% of the screen's width (at most 1.4x), offset by [shift]. Read in
 * the draw phase; the row's place is measured before the transform, so it never feeds back.
 */
private fun Modifier.idlePresentation(focus: () -> Float, shift: () -> Offset): Modifier = composed {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var screen by remember { mutableStateOf(IntSize.Zero) }
    this
        .onGloballyPositioned {
            bounds = it.boundsInRoot()
            screen = it.findRootCoordinates().size
        }
        .graphicsLayer {
            val p = focus()
            if (p == 0f || bounds.width == 0f || screen.width == 0) return@graphicsLayer
            val grow = (screen.width * 0.86f / bounds.width).coerceIn(1f, 1.4f)
            val scale = 1f + (grow - 1f) * p
            scaleX = scale
            scaleY = scale
            val s = shift()
            translationX = p * (screen.width / 2f - bounds.center.x + s.x)
            translationY = p * (screen.height * 0.44f - bounds.center.y + s.y)
        }
}

/**
 * Guards plasma and OLED screens while idle without visible motion: every 3 minutes the
 * widgets glide a few dp to a new spot over 4 seconds, then stay perfectly still.
 */
@Composable
private fun rememberBurnInShift(active: Boolean): State<Offset> {
    val density = LocalDensity.current
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(active) {
        if (!active) {
            // Back home with the widgets, rather than jumping as they leave
            shift.animateTo(Offset.Zero, tween(1300, easing = WakeEasing))
            return@LaunchedEffect
        }
        val range = with(density) { 14.dp.toPx() }
        val spots = listOf(Offset(-1f, -0.6f), Offset(1f, 0.4f), Offset(-0.4f, 1f), Offset(0.6f, -1f), Offset(0f, 0f))
        var i = 0
        while (true) {
            delay(3 * 60_000L)
            shift.animateTo(spots[i++ % spots.size] * range, tween(4000, easing = FastOutSlowInEasing))
        }
    }
    return shift.asState()
}

@Composable
private fun SectionTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.Tile),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text,
            style = TextStyle(
                fontFamily = Flex,
                color = Palette.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight(650),
                shadow = TextShadow,
            ),
        )
        if (hint != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                hint,
                style = TextStyle(fontFamily = Flex, color = Palette.TextTertiary, fontSize = 15.sp, shadow = TextShadow),
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
    }
}

@Composable
private fun EmptyFavorites(loading: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .padding(horizontal = Spacing.Tile)
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, Palette.Hairline, shape)
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        Text(
            if (loading) "Loading apps…" else "Go down to All apps and hold OK on an app to pin it here",
            style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = 17.sp, shadow = TextShadow),
        )
    }
}

@Composable
internal fun ToastChip(message: String) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .shadow(24.dp, shape)
            .glass(shape)
            .padding(horizontal = 24.dp, vertical = 13.dp),
    ) {
        Text(message, color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight(560))
    }
}

/** An open app menu: for [app], whether OK is still held, and whether it came from the favourites row. */
private class MenuRequest(val app: AppEntry, val okHeld: Boolean, val fromFavorites: Boolean)

/** Room the settings panel takes at the right of the screen, and the margin around the preview. */
private val PanelRoom = 488.dp
private val PreviewMargin = 28.dp

/** How small the first page gets so it fits left of the settings panel, as a live preview. */
private fun previewScale(contentWidth: Dp, screenWidth: Dp): Float =
    ((screenWidth - PanelRoom - PreviewMargin * 2) / contentWidth).coerceIn(0.3f, 1f)

/**
 * Safe mode, where the notification bar would be: the launcher crashed twice in a row,
 * so it started with only the photo and Favorites. Try normally starts it as usual.
 */
@Composable
private fun SafeModeCard(reason: String, onTryNormally: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .glass(RoundedCornerShape(22.dp))
            .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Safe mode: Pill Launcher stopped unexpectedly", color = Palette.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Widgets, notifications and Files are off for now · $reason", color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(12.dp))
        PillButton("Try normally", primary = true, onClick = onTryNormally)
    }
}
