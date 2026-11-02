package app.pill.launcher

import android.content.Context

/** Crashes this close together mean the launcher can't start normally. */
private const val LoopMs = 60_000L

/** True when [crashes] (times, ms) hold two within the minute before [now]: start in safe mode. */
fun shouldStartSafe(crashes: List<Long>, now: Long): Boolean = crashes.count { now - it in 0..LoopMs } >= 2

/**
 * Keeps the TV usable if an update makes the launcher crash on start. Each crash is noted
 * (with what failed); two within a minute and the next start is in safe mode: the photo
 * and Favorites only, without widgets, the notification bar or Files, saying what failed.
 * A minute of normal running, or Try normally, clears the record.
 */
class CrashGuard(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("crashes", Context.MODE_PRIVATE)

    /** Notes crashes from now on, then lets Android handle them as before. */
    fun install() {
        // Once per process: the activity can be created again, and a crash must count once
        if (installed) return
        installed = true
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val times = (crashes() + System.currentTimeMillis()).takeLast(5)
                // commit, not apply: the process is about to end
                prefs.edit()
                    .putString("times", times.joinToString(","))
                    .putString("what", (error.javaClass.simpleName + (error.message?.let { ": $it" } ?: "")).take(160))
                    .commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun crashes(): List<Long> = prefs.getString("times", "").orEmpty().split(',').mapNotNull { it.toLongOrNull() }

    /** Why the launcher is starting in safe mode, or null to start normally. */
    fun safeModeReason(now: Long = System.currentTimeMillis()): String? =
        if (shouldStartSafe(crashes(), now)) prefs.getString("what", null) ?: "It stopped unexpectedly" else null

    private companion object {
        @Volatile var installed = false
    }

    /** Running fine (or Try normally): forget the crashes. */
    fun clear() {
        prefs.edit().clear().apply()
    }
}
