package app.pill.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
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
import app.pill.launcher.ui.LauncherScreen
import app.pill.launcher.ui.describe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val REFRESH_MS = 30 * 60 * 1000L
/** Waits between attempts while the weather cannot be fetched (no network yet at boot, say). */
private val RetryDelaysMs = listOf(10_000L, 30_000L, 60_000L, 2 * 60_000L, 5 * 60_000L)

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)
    private var homePresses by mutableIntStateOf(0)
    private lateinit var store: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SettingsStore(this)

        setContent {
            // null until the first scan finishes; later refreshes keep the old list on screen
            val apps by produceState<List<AppEntry>?>(null, refresh) {
                value = withContext(Dispatchers.Default) { loadApps(packageManager, packageName) }
            }
            var pins by remember { mutableStateOf(store.readPins()) }
            var settings by remember { mutableStateOf(store.load()) }
            // Refreshed every 30 minutes while the widget is on; the last reading stays up if a refresh fails
            val weatherOn = settings.weather != WeatherMode.Off
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

            // Re-checked whenever the launcher comes back (e.g. from the Home app settings)
            val home = remember(refresh) { homeStatus() }

            // A launcher never exits on Back
            BackHandler(enabled = true) { }

            LauncherScreen(
                apps = apps,
                weather = weather.takeIf { weatherOn },
                weatherError = weatherError.takeIf { weatherOn },
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
                home = home,
                onMakeDefault = { requestDefaultHome() },
                onHomeHelper = { openAccessibilitySettings() },
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
