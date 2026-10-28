package app.pill.launcher

import android.app.Activity
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/** Whether Pill Launcher is the home screen, and whether the Home-button helper is on. */
data class HomeStatus(
    val isDefault: Boolean,
    val redirectOn: Boolean,
    /** May display over other apps, which lets Autostart open the launcher at boot. */
    val canDrawOverlays: Boolean = false,
) {
    /** Android lets the launcher open itself at boot in any of these cases. */
    val canAutostart get() = isDefault || redirectOn || canDrawOverlays
}

fun Context.homeStatus() = HomeStatus(isDefaultHome(), isRedirectEnabled(), Settings.canDrawOverlays(this))

private val homeIntent get() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

fun Context.isDefaultHome(): Boolean =
    packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName == packageName

/** Packages of the other home screens on this TV (the stock launcher among them). */
fun Context.otherHomePackages(): Set<String> =
    packageManager.queryIntentActivities(homeIntent, 0)
        .map { it.activityInfo.packageName }
        .filter { it != packageName && it != "android" && !it.contains("settings", ignoreCase = true) }
        .toSet()

fun Context.isRedirectEnabled(): Boolean {
    val me = ComponentName(this, HomeRedirectService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
    return enabled.split(':').any { it.equals(me, ignoreCase = true) }
}

/**
 * Asks Android to make Pill Launcher the home screen, trying in turn: the system's
 * "default home app" request (Android 10+), the Home app settings screen, and the
 * Home chooser. Returns a message for the user when none of these exist on this TV.
 */
fun Activity.requestDefaultHome(): String? {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roles = getSystemService(RoleManager::class.java)
        if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
            if (tryStart { startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_HOME), 1) }) return null
        }
    }
    if (tryStart { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) }) return null
    // With no default chosen yet, pressing Home shows a chooser; show it now
    val resolved = packageManager.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
    if (resolved?.activityInfo?.packageName == "android" && tryStart { startActivity(homeIntent) }) return null
    return "This TV doesn't let apps change the home screen. Turn on the Home button helper below instead."
}

/** Opens "Display over other apps" for this app (app info on TVs without that screen). */
fun Activity.requestOverlayPermission(): String? {
    val app = Uri.parse("package:$packageName")
    if (tryStart { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, app)) }) return null
    if (tryStart { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)) }) return null
    return "Couldn't open the permission screen on this TV."
}

fun Activity.openAccessibilitySettings(): String? =
    if (tryStart { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) null
    else "Couldn't open Accessibility settings on this TV."

private inline fun tryStart(block: () -> Unit): Boolean = try {
    block()
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}
