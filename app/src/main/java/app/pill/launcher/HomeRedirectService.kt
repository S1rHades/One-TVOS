package app.pill.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Optional helper for TVs that don't let apps become the home screen (most Google TV
 * devices). When the TV's own home screen comes to the front (Home pressed, or an app
 * closed or killed), it brings Pill Launcher back. It only looks at which app's window
 * appeared, never at what is on screen. The user turns it on in Accessibility settings.
 */
class HomeRedirectService : AccessibilityService() {

    private var stockHomes: Set<String> = emptySet()
    private var lastRedirect = 0L

    override fun onServiceConnected() {
        stockHomes = otherHomePackages() + KnownTvLaunchers
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in stockHomes || isDefaultHome()) return
        // The stock launcher can post several window changes in a row; act once
        val now = SystemClock.elapsedRealtime()
        if (now - lastRedirect < 1500) return
        lastRedirect = now
        startActivity(
            Intent(this, MainActivity::class.java)
                .setAction(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
        )
    }

    override fun onInterrupt() = Unit

    private companion object {
        /** Stock launchers of Google TV and Android TV, in case they don't answer the Home query. */
        val KnownTvLaunchers = setOf(
            "com.google.android.apps.tv.launcherx",
            "com.google.android.tvlauncher",
            "com.google.android.leanbacklauncher",
            "com.amazon.tv.launcher",
        )
    }
}
