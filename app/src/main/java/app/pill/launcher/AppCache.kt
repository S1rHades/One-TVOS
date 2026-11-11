package app.pill.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The app list as it was last seen, kept on disk (names, and each icon as a picture), so a
 * cold start shows the apps at once instead of waiting for every app's icon to be read
 * from the app again. It only stands in: the real scan replaces it a moment later, so an
 * app removed or updated meanwhile is put right then. Call off the main thread.
 */
class AppCache(private val dir: File) {
    private val index = File(dir, "index.json")

    /** The saved list, or null when there is none (a first start) or it can't be read. */
    fun read(): List<AppEntry>? = runCatching {
        val saved = JSONArray(index.readText())
        (0 until saved.length()).mapNotNull { i ->
            val o = saved.getJSONObject(i)
            val pkg = o.getString("pkg")
            val icon = BitmapFactory.decodeFile(iconFile(pkg).path) ?: return@mapNotNull null
            AppEntry(
                label = o.getString("label"),
                pkg = pkg,
                component = ComponentName(pkg, o.getString("activity")),
                icon = icon.asImageBitmap(),
                accent = o.optInt("accent", DefaultAccent),
                removable = o.optBoolean("removable", true),
                category = AppCategory.entries.firstOrNull { it.name == o.optString("category") } ?: AppCategory.Other,
                updatedAt = o.optLong("updatedAt"),
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    /** Saves [apps] (as scanned: their own names). An icon is written only for an app that is new or has been updated since. */
    fun write(apps: List<AppEntry>) {
        runCatching {
            dir.mkdirs()
            val before = runCatching { JSONArray(index.readText()) }.getOrNull()
            val savedAt = HashMap<String, Long>()
            if (before != null) for (i in 0 until before.length()) before.optJSONObject(i)?.let { savedAt[it.optString("pkg")] = it.optLong("updatedAt") }
            val out = JSONArray()
            for (app in apps) {
                val file = iconFile(app.pkg)
                if (!file.exists() || savedAt[app.pkg] != app.updatedAt) {
                    val part = File(dir, file.name + ".part")
                    part.outputStream().use { app.icon.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) }
                    part.renameTo(file)
                }
                out.put(
                    JSONObject().put("label", app.originalLabel ?: app.label).put("pkg", app.pkg).put("activity", app.component.className)
                        .put("accent", app.accent).put("removable", app.removable).put("category", app.category.name).put("updatedAt", app.updatedAt),
                )
            }
            // The list last and whole, so a save cut short leaves the old list, never half of one
            val part = File(dir, "index.json.part")
            part.writeText(out.toString())
            part.renameTo(index)
            val kept = apps.mapTo(HashSet()) { iconFile(it.pkg).name } + index.name
            dir.listFiles()?.forEach { if (it.name !in kept) it.delete() }
        }
    }

    private fun iconFile(pkg: String) = File(dir, pkg.filter { it.isLetterOrDigit() || it == '.' || it == '_' } + ".png")
}
