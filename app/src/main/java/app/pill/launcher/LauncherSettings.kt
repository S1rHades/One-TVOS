package app.pill.launcher

import android.content.Context
import java.util.Locale

/** How the favourites row handles more apps than fit across the screen. */
enum class FavoritesLayout { Scroll, Wrap }

/** How favourites look: wide app artwork (TV banners) or square icons. */
enum class FavoritesStyle { Banners, Icons }

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
    val favoritesStyle: FavoritesStyle = FavoritesStyle.Banners,
    /** Tint the photo with the focused app's colour. */
    val ambientLight: Boolean = true,
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
        favoritesStyle = prefs.getString("favoritesStyle", null)
            ?.let { name -> FavoritesStyle.entries.firstOrNull { it.name == name } }
            ?: FavoritesStyle.Banners,
        ambientLight = prefs.getBoolean("ambientLight", true),
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
            .putString("favoritesStyle", settings.favoritesStyle.name)
            .putBoolean("ambientLight", settings.ambientLight)
            .putString("widgets", settings.widgets.toStored())
            .putBoolean("homePromptDismissed", settings.homePromptDismissed)
            .putBoolean("onlinePhotos", settings.onlinePhotos)
            .putString("teams", teamsToJson(settings.teams))
            .putString("matchDay", settings.matchDay.name)
            .putString("newsTopics", settings.newsTopics.joinToString(",") { it.name })
            .apply()
    }

    fun readPins(): List<String> =
        prefs.getString("pins", "").orEmpty().split(",").filter { it.isNotBlank() }

    fun savePins(pins: List<String>) {
        prefs.edit().putString("pins", pins.joinToString(",")).apply()
    }
}
