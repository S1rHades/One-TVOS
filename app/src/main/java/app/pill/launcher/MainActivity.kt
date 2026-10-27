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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val REFRESH_MS = 30 * 60 * 1000L
private const val RETRY_MS = 2 * 60 * 1000L

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)
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
            val weather by produceState<Weather?>(null, weatherOn) {
                while (weatherOn) {
                    withContext(Dispatchers.IO) { fetchWeather() }?.let { value = it }
                    delay(if (value == null) RETRY_MS else REFRESH_MS)
                }
            }

            // A launcher never exits on Back
            BackHandler(enabled = true) { }

            LauncherScreen(
                apps = apps,
                weather = weather.takeIf { weatherOn },
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
