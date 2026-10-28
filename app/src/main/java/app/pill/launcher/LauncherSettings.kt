package app.pill.launcher

import android.content.Context
import java.util.Locale

/** What the weather widget shows: nothing, or the temperature in one unit. */
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
    )

    fun save(settings: LauncherSettings) {
        prefs.edit()
            .putBoolean("slideshow", settings.slideshow)
            .putInt("interval", settings.intervalSec)
            .putString("weather", settings.weather.name)
            .putBoolean("autostart", settings.autostart)
            .putInt("idleMinutes", settings.idleMinutes)
            .putBoolean("idleAnimation", settings.idleAnimation)
            .apply()
    }

    fun readPins(): List<String> =
        prefs.getString("pins", "").orEmpty().split(",").filter { it.isNotBlank() }

    fun savePins(pins: List<String>) {
        prefs.edit().putString("pins", pins.joinToString(",")).apply()
    }
}
