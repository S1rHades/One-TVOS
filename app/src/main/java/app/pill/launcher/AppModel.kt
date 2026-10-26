package app.pill.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(
    val label: String,
    val pkg: String,
    val component: ComponentName,
    val icon: ImageBitmap,
)

/** Lists launchable apps (TV apps first, then regular launcher apps), one entry per package. */
fun loadApps(pm: PackageManager, self: String): List<AppEntry> {
    val seen = LinkedHashMap<String, AppEntry>()
    for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (info in pm.queryIntentActivities(query, 0)) {
            val pkg = info.activityInfo.packageName
            if (pkg == self || seen.containsKey(pkg)) continue
            val bitmap = try {
                info.activityInfo.loadIcon(pm).toBitmap(160, 160).asImageBitmap()
            } catch (e: Exception) {
                continue
            }
            seen[pkg] = AppEntry(
                label = info.loadLabel(pm).toString(),
                pkg = pkg,
                component = ComponentName(pkg, info.activityInfo.name),
                icon = bitmap,
            )
        }
    }
    return seen.values.sortedBy { it.label.lowercase() }
}
