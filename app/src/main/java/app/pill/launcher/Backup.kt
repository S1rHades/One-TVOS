package app.pill.launcher

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * The launcher's settings as one file, to keep through a reinstall or carry to another TV:
 * favourites and their order, names given to apps, the widgets, teams, the mosque, the
 * weather town and every switch. Not in it: paired phones (each TV pairs its own), fetched
 * data (fetched again) and notifications.
 */
const val BackupPrefix = "pill-launcher-backup"

/** This TV's own history rather than settings: left out of a backup, and left alone by a restore. */
private val NotBackedUp = setOf("notices", "noticesSeen", "lastVersion", "pendingNotes", "updateDismissed", "askedApks", "pendingPins")

/** Everything in [prefs] but this TV's own history, each value with its type, so it goes back as it was. */
fun backupJson(prefs: SharedPreferences, version: String, at: Instant): String {
    val values = JSONObject()
    for ((key, value) in prefs.all.toSortedMap()) {
        if (key in NotBackedUp) continue
        val entry = JSONObject()
        when (value) {
            is Boolean -> entry.put("t", "b").put("v", value)
            is Int -> entry.put("t", "i").put("v", value)
            is Long -> entry.put("t", "l").put("v", value)
            is Float -> entry.put("t", "f").put("v", value.toDouble())
            is String -> entry.put("t", "s").put("v", value)
            is Set<*> -> entry.put("t", "ss").put("v", JSONArray(value.map { it.toString() }.sorted()))
            else -> continue
        }
        values.put(key, entry)
    }
    return JSONObject().put("app", "pill-launcher").put("backup", 1).put("version", version).put("at", at.toString()).put("settings", values).toString(2)
}

/** What a backup says of itself. */
data class BackupInfo(val version: String, val at: Instant?, val count: Int)

/** Reads a backup's heading; null when [text] isn't a Pill Launcher backup. */
fun backupInfo(text: String): BackupInfo? = runCatching {
    val json = JSONObject(text)
    if (json.optString("app") != "pill-launcher" || !json.has("backup")) return null
    val settings = json.getJSONObject("settings")
    BackupInfo(json.optString("version"), runCatching { Instant.parse(json.optString("at")) }.getOrNull(), settings.length())
}.getOrNull()

/**
 * Replaces the settings in [prefs] with the backup's: every setting the backup lacks goes
 * back to its default, and this TV's own history stays as it is. False, with nothing
 * changed, when [text] isn't a backup or any value in it can't be read.
 */
fun restoreBackup(prefs: SharedPreferences, text: String): Boolean {
    backupInfo(text) ?: return false
    val settings = runCatching { JSONObject(text).getJSONObject("settings") }.getOrNull() ?: return false
    // Read it all before touching anything: a damaged backup must not leave half of itself behind
    val values = HashMap<String, Any>()
    for (key in settings.keys()) {
        if (key in NotBackedUp) continue
        val entry = settings.optJSONObject(key) ?: return false
        val value: Any = runCatching {
            when (entry.getString("t")) {
                "b" -> entry.getBoolean("v")
                "i" -> entry.getInt("v")
                "l" -> entry.getLong("v")
                "f" -> entry.getDouble("v").toFloat()
                "s" -> entry.getString("v")
                "ss" -> entry.getJSONArray("v").let { a -> (0 until a.length()).mapTo(HashSet()) { a.getString(it) } }
                else -> null
            }
        }.getOrNull() ?: return false
        values[key] = value
    }
    val edit = prefs.edit()
    for (key in prefs.all.keys) if (key !in NotBackedUp) edit.remove(key)
    for ((key, value) in values) {
        @Suppress("UNCHECKED_CAST")
        when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Float -> edit.putFloat(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> edit.putStringSet(key, value as Set<String>)
        }
    }
    return edit.commit()
}
