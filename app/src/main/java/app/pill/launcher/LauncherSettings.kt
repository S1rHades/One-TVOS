package app.pill.launcher

import android.content.Context

data class LauncherSettings(
    val slideshow: Boolean = true,
    val intervalSec: Int = 20,
) {
    companion object {
        val IntervalOptions = listOf(10, 20, 30, 60)
    }
}

/** Small SharedPreferences wrapper for settings and pinned apps. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun load() = LauncherSettings(
        slideshow = prefs.getBoolean("slideshow", true),
        intervalSec = prefs.getInt("interval", 20),
    )

    fun save(settings: LauncherSettings) {
        prefs.edit()
            .putBoolean("slideshow", settings.slideshow)
            .putInt("interval", settings.intervalSec)
            .apply()
    }

    fun readPins(): List<String> =
        prefs.getString("pins", "").orEmpty().split(",").filter { it.isNotBlank() }

    fun savePins(pins: List<String>) {
        prefs.edit().putString("pins", pins.joinToString(",")).apply()
    }
}
