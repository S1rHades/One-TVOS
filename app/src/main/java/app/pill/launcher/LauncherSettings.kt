package app.pill.launcher

import android.content.Context
import java.util.Locale

/** How the favourites row handles more apps than fit across the screen. */
enum class FavoritesLayout { Scroll, Wrap }

/** How favourites look: wide app artwork (TV banners) or square icons. */

/** The unit the weather widget shows temperatures in. Off is only read from older settings. */
enum class WeatherMode { Off, Celsius, Fahrenheit }

data class LauncherSettings(
    val slideshow: Boolean = true,
    val intervalSec: Int = 20,
    val weather: WeatherMode = defaultWeatherMode(),
    /** Open the launcher when the TV finishes starting up. */
    val autostart: Boolean = false,
    /** Minutes without a key press before the idle screen; 0 = never. */
    val idleMinutes: Int = 2,
    /** Slow photo zoom and drifting clock while idle (also guards against screen burn-in). */
    val idleAnimation: Boolean = true,
    val favoritesLayout: FavoritesLayout = FavoritesLayout.Scroll,
    /** Sliding and reordering animations in the favourites row. */
    val favoritesAnimation: Boolean = true,
    /** Favourites ordered by use (see [orderByUse]) instead of where they were put. */
    val favoritesByUse: Boolean = false,
    /** Pages opened so far, up to [BackHintTimes]: their titles say "Back to close" only the first few times. */
    val pagesSeen: Int = 0,
    /** Getting started is finished or put away. A new install loads false; anyone who used the launcher before it existed, true. */
    val setupDone: Boolean = true,
    /** Settings → Widgets has been opened: getting started's last step. */
    val widgetsVisited: Boolean = false,
    /** Mosques that worked, newest first: (MAWAQIT ID or address, its name). */
    val recentMosques: List<Pair<String, String>> = emptyList(),
    /** Header cards, in display order; at most [MaxWidgets]. */
    val widgets: List<WidgetKind> = DefaultWidgets,
    /** "Not now" on the home-screen prompt to make Pill Launcher the home screen. */
    val homePromptDismissed: Boolean = false,
    /** A daily set of nature photos from the internet, rather than only the built-in ones. */
    val onlinePhotos: Boolean = true,
    /** Teams the football card follows, in order; Algeria is always first. */
    val teams: List<Team> = listOf(Algeria),
    /** Which teams get the match-day countdown and the kick-off banner. */
    val matchDay: MatchDayMode = MatchDayMode.All,
    /** What the headlines widget picks stories about. */
    val newsTopics: Set<NewsTopic> = NewsTopic.Defaults,
    /** The widgets inside the widget stack, in the order they take turns. */
    val stack: List<WidgetKind> = DefaultStack,
    /** Look for a newer Pill Launcher on GitHub every few hours. */
    val checkUpdates: Boolean = true,
    /** Other apps' notifications in the bar under the widgets (once the TV allows reading them). */
    val appNotices: Boolean = true,
    /** Apps whose notifications stay out of the bar. */
    val mutedApps: Set<String> = emptySet(),
    /** Kick-off reminders, goals and wins in the bar. */
    val footballNotices: Boolean = true,
    /** The connection dropping and coming back, in the bar. */
    val connectionNotices: Boolean = true,
    /** Files keep arriving for 15 minutes after the launcher goes behind another app. */
    val keepReceiving: Boolean = false,
    /** Once an APK's app is installed, the notification bar offers to delete the APK (never on its own). */
    val askInstalledApks: Boolean = true,
    /** Files older than 30 days are removed, with a notice the day before. */
    val tidyOld: Boolean = false,
    /** Other apps' private notifications show only the app's name, not their text. */
    val hidePrivate: Boolean = false,
    /** The mosque the prayer times widget follows: its name in its MAWAQIT address (blank = none chosen). */
    val mosque: String = "",
    /** Where the community MAWAQIT API answers; [DefaultPrayerApi] unless changed. */
    val prayerApi: String = DefaultPrayerApi,
    /** Where the weather is for; null for the TV's approximate location (from its internet address). */
    val weatherPlace: Place? = null,
) {
    companion object {
        val IntervalOptions = listOf(10, 20, 30, 60)
        val IdleOptions = listOf(0, 1, 2, 5, 10)
    }
}

/** Fahrenheit where it is the everyday unit, Celsius everywhere else. */
fun defaultWeatherMode(locale: Locale = Locale.getDefault()): WeatherMode =
    if (locale.country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW")) WeatherMode.Fahrenheit else WeatherMode.Celsius

/** Small SharedPreferences wrapper for settings and pinned apps. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        slideshow = prefs.getBoolean("slideshow", true),
        intervalSec = prefs.getInt("interval", 20),
        weather = prefs.getString("weather", null)
            ?.let { name -> WeatherMode.entries.firstOrNull { it.name == name } }
            ?: defaultWeatherMode(),
        autostart = prefs.getBoolean("autostart", false),
        idleMinutes = prefs.getInt("idleMinutes", 2),
        idleAnimation = prefs.getBoolean("idleAnimation", true),
        favoritesLayout = prefs.getString("favoritesLayout", null)
            ?.let { name -> FavoritesLayout.entries.firstOrNull { it.name == name } }
            ?: FavoritesLayout.Scroll,
        favoritesAnimation = prefs.getBoolean("favoritesAnimation", true),
        favoritesByUse = prefs.getBoolean("favoritesByUse", false),
        pagesSeen = prefs.getInt("pagesSeen", 0),
        recentMosques = recentMosquesFromStored(prefs.getString("recentMosques", null)),
        // Saved favourites mean the launcher was in use before getting started existed
        setupDone = prefs.getBoolean("setupDone", prefs.contains("pins")),
        widgetsVisited = prefs.getBoolean("widgetsVisited", false),
        // Before widgets, the weather setting had an Off choice; it maps to the widget being off
        widgets = parseWidgets(prefs.getString("widgets", null))
            ?: if (prefs.getString("weather", null) == WeatherMode.Off.name) DefaultWidgets - WidgetKind.Weather else DefaultWidgets,
        homePromptDismissed = prefs.getBoolean("homePromptDismissed", false),
        onlinePhotos = prefs.getBoolean("onlinePhotos", true),
        teams = teamsFromJson(prefs.getString("teams", null)),
        matchDay = prefs.getString("matchDay", null)
            ?.let { name -> MatchDayMode.entries.firstOrNull { it.name == name } }
            ?: MatchDayMode.All,
        newsTopics = prefs.getString("newsTopics", null)
            ?.let { stored -> stored.split(",").mapNotNull { name -> NewsTopic.entries.firstOrNull { it.name == name } }.toSet() }
            ?: NewsTopic.Defaults,
        stack = parseStack(prefs.getString("stack", null)) ?: DefaultStack,
        checkUpdates = prefs.getBoolean("checkUpdates", true),
        appNotices = prefs.getBoolean("appNotices", true),
        mutedApps = prefs.getString("mutedApps", "").orEmpty().split(",").filter { it.isNotBlank() }.toSet(),
        footballNotices = prefs.getBoolean("footballNotices", true),
        connectionNotices = prefs.getBoolean("connectionNotices", true),
        keepReceiving = prefs.getBoolean("keepReceiving", false),
        askInstalledApks = prefs.getBoolean("askInstalledApks", true),
        tidyOld = prefs.getBoolean("tidyOld", false),
        hidePrivate = prefs.getBoolean("hidePrivate", false),
        mosque = prefs.getString("mosque", "").orEmpty(),
        prayerApi = prefs.getString("prayerApi", null) ?: DefaultPrayerApi,
        weatherPlace = placeFromJson(prefs.getString("weatherPlace", null)),
    ).let { if (it.weather == WeatherMode.Off) it.copy(weather = defaultWeatherMode()) else it }

    fun save(settings: LauncherSettings) {
        prefs.edit()
            .putBoolean("slideshow", settings.slideshow)
            .putInt("interval", settings.intervalSec)
            .putString("weather", settings.weather.name)
            .putBoolean("autostart", settings.autostart)
            .putInt("idleMinutes", settings.idleMinutes)
            .putBoolean("idleAnimation", settings.idleAnimation)
            .putString("favoritesLayout", settings.favoritesLayout.name)
            .putBoolean("favoritesAnimation", settings.favoritesAnimation)
            .putBoolean("favoritesByUse", settings.favoritesByUse)
            .putInt("pagesSeen", settings.pagesSeen)
            .putString("recentMosques", recentMosquesToStored(settings.recentMosques))
            .putBoolean("setupDone", settings.setupDone)
            .putBoolean("widgetsVisited", settings.widgetsVisited)
            .putString("widgets", settings.widgets.toStored())
            .putBoolean("homePromptDismissed", settings.homePromptDismissed)
            .putBoolean("onlinePhotos", settings.onlinePhotos)
            .putString("teams", teamsToJson(settings.teams))
            .putString("matchDay", settings.matchDay.name)
            .putString("newsTopics", settings.newsTopics.joinToString(",") { it.name })
            .putString("stack", settings.stack.toStored())
            .putBoolean("checkUpdates", settings.checkUpdates)
            .putBoolean("appNotices", settings.appNotices)
            .putString("mutedApps", settings.mutedApps.joinToString(","))
            .putBoolean("footballNotices", settings.footballNotices)
            .putBoolean("connectionNotices", settings.connectionNotices)
            .putBoolean("keepReceiving", settings.keepReceiving)
            .putBoolean("askInstalledApks", settings.askInstalledApks)
            .putBoolean("tidyOld", settings.tidyOld)
            .putBoolean("hidePrivate", settings.hidePrivate)
            .putString("mosque", settings.mosque)
            .putString("prayerApi", settings.prayerApi)
            .putString("weatherPlace", placeToJson(settings.weatherPlace))
            .apply()
    }

    fun readPins(): List<String> =
        prefs.getString("pins", "").orEmpty().split(",").filter { it.isNotBlank() }

    fun savePins(pins: List<String>) {
        prefs.edit().putString("pins", pins.joinToString(",")).apply()
    }

    /** How many times each app has been opened from the launcher, by package. */
    fun readUses(): Map<String, Int> = usesFromStored(prefs.getString("uses", null))

    /** Counts one more opening of [pkg] and returns the new counts. */
    fun addUse(pkg: String): Map<String, Int> {
        val uses = readUses().toMutableMap()
        uses[pkg] = (uses[pkg] ?: 0) + 1
        prefs.edit().putString("uses", usesToStored(uses)).apply()
        return uses
    }

    /** Names given to apps in the launcher, by package. The apps themselves keep theirs. */
    fun readNames(): Map<String, String> = namesFromJson(prefs.getString("names", null))

    fun saveNames(names: Map<String, String>) {
        prefs.edit().putString("names", namesToJson(names)).apply()
    }

    /** The release whose update card was put off with Later, so it doesn't come back. */
    var updateDismissed: String?
        get() = prefs.getString("updateDismissed", null)
        set(value) { prefs.edit().putString("updateDismissed", value).apply() }

    /** Pill Launcher's own notifications (updates, goals, the connection, files), newest first. */
    fun readNotices(): List<Notice> = noticesFromJson(prefs.getString("notices", null))

    fun saveNotices(list: List<Notice>) {
        prefs.edit().putString("notices", noticesToJson(list)).apply()
    }

    /** APKs already asked about (name and size), so keeping one doesn't bring the question back. */
    var askedApks: Set<String>
        get() = prefs.getStringSet("askedApks", emptySet()).orEmpty()
        set(value) { prefs.edit().putStringSet("askedApks", value).apply() }

    /** When the notification list was last opened: anything newer is unread. */
    var noticesSeen: Long
        get() = prefs.getLong("noticesSeen", 0L)
        set(value) { prefs.edit().putLong("noticesSeen", value).apply() }

    /** The version that last ran, to notice an update having happened. */
    var lastVersion: String?
        get() = prefs.getString("lastVersion", null)
        set(value) { prefs.edit().putString("lastVersion", value).apply() }

    /** What's new in the update being installed (version, then its lines), for "Updated to …" afterwards. */
    var pendingNotes: List<String>
        get() = prefs.getString("pendingNotes", "").orEmpty().split("\n").filter { it.isNotBlank() }
        set(value) { prefs.edit().putString("pendingNotes", value.joinToString("\n")).apply() }
}

fun namesFromJson(text: String?): Map<String, String> = runCatching {
    val o = org.json.JSONObject(text ?: return emptyMap())
    o.keys().asSequence().associateWith { o.getString(it) }.filterValues { it.isNotBlank() }
}.getOrDefault(emptyMap())

fun namesToJson(names: Map<String, String>): String = org.json.JSONObject(names.filterValues { it.isNotBlank() }).toString()

/**
 * The name to show for an app: [name] trimmed and kept short, or the app's own name when
 * it is blank or the same. Null means "use the app's own name".
 */
fun cleanName(name: String, original: String): String? =
    name.trim().replace(Regex("\\s+"), " ").take(MaxNameLength).takeIf { it.isNotEmpty() && it != original }

const val MaxNameLength = 30

/** "pkg=3,other=1" ↔ counts; anything unreadable is skipped. */
fun usesFromStored(text: String?): Map<String, Int> =
    text.orEmpty().split(",").mapNotNull { part ->
        val pkg = part.substringBefore("=", "").trim()
        val n = part.substringAfter("=", "").trim().toIntOrNull()
        if (pkg.isEmpty() || n == null || n <= 0) null else pkg to n
    }.toMap()

fun usesToStored(uses: Map<String, Int>): String = uses.entries.joinToString(",") { "${it.key}=${it.value}" }

/**
 * The favourites in order of use, for the serial position effect: people find the first
 * and last places easiest, so the most-opened app goes first and the runner-up last. The
 * rest keep the order they were put in. Apps never opened from the launcher stay where
 * they are; with fewer than three favourites, or nothing opened yet, nothing moves.
 */
fun orderByUse(pins: List<String>, uses: Map<String, Int>): List<String> {
    if (pins.size < 3) return pins
    // Most used first; a tie goes to the one placed earlier
    val ranked = pins.withIndex()
        .filter { (uses[it.value] ?: 0) > 0 }
        .sortedWith(compareByDescending<IndexedValue<String>> { uses[it.value] ?: 0 }.thenBy { it.index })
        .map { it.value }
    if (ranked.isEmpty()) return pins
    val first = ranked[0]
    val last = ranked.getOrNull(1)
    val middle = pins.filter { it != first && it != last }
    return listOf(first) + middle + listOfNotNull(last)
}

/** How many page openings show "Back to close" before the hint goes (Occam's razor: once learned, it's clutter). */
const val BackHintTimes = 3

/** At most this many recent mosques are kept. */
const val RecentMosquesKept = 4

/** [slug] with [name] first among [recent], without a repeat of it, at most [RecentMosquesKept]. */
fun withRecentMosque(recent: List<Pair<String, String>>, slug: String, name: String): List<Pair<String, String>> {
    if (slug.isBlank()) return recent
    return (listOf(slug to name.ifBlank { slug }) + recent.filter { it.first != slug }).take(RecentMosquesKept)
}

/** One per line, "slug\tname"; tabs and line breaks never appear in either. */
fun recentMosquesToStored(recent: List<Pair<String, String>>): String =
    recent.joinToString("\n") { (slug, name) -> slug + "\t" + name.replace('\t', ' ').replace('\n', ' ') }

fun recentMosquesFromStored(text: String?): List<Pair<String, String>> =
    text.orEmpty().lines().mapNotNull { line ->
        val slug = line.substringBefore('\t').trim()
        if (slug.isEmpty()) null else slug to line.substringAfter('\t', slug).trim()
    }.take(RecentMosquesKept)
