package app.pill.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Opens the launcher when the TV finishes starting up, if Autostart is on.
 *
 * Android 10+ only lets an app open itself from the background when it is the default
 * home screen, has the Home button helper (an accessibility service) on, or may display
 * over other apps; Settings says which is missing ([HomeStatus.canAutostart]).
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in BootActions) return
        if (!SettingsStore(context).load().autostart) return
        runCatching {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .setAction(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    private companion object {
        val BootActions = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON", // some TV boxes send this instead
        )
    }
}
