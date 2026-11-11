package app.pill.launcher

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where a notification comes from, which decides its icon and what OK does. */
enum class NoticeKind { Update, Updated, Football, Connection, File, App, Prayer }

/**
 * One notification in the bar under the widgets: Pill Launcher's own (an update, a goal,
 * the connection, a file that arrived) or another app's.
 */
data class Notice(
    /** Stable: the same event posted twice replaces itself. */
    val id: String,
    val kind: NoticeKind,
    val title: String,
    val text: String,
    val at: Instant,
    /** The app it came from (app notifications). */
    val pkg: String? = null,
    /** What it is about: a team's id (football), a file's name (files). */
    val target: String? = null,
    /** Another app marked it private (a message's text): hidden when Hide private messages is on. */
    val isPrivate: Boolean = false,
)

/** What the bar shows of [n]: a private one from another app as just "New notification" when [hide] is on. */
fun shownNotice(n: Notice, hide: Boolean): Notice =
    if (hide && n.isPrivate) n.copy(title = "New notification", text = "") else n

/** Notifications are kept this long, and at most [MaxNotices] of them. */
val NoticeLifetime: Duration = Duration.ofDays(7)
const val MaxNotices = 40

/** [list] with [notice] added (replacing one with its id), newest first, without old ones. */
fun withNotice(list: List<Notice>, notice: Notice, now: Instant): List<Notice> =
    (list.filter { it.id != notice.id } + notice)
        .filter { it.at.isAfter(now.minus(NoticeLifetime)) }
        .sortedByDescending { it.at }
        .take(MaxNotices)

fun noticesToJson(list: List<Notice>): String = JSONArray().apply {
    for (n in list) {
        put(
            JSONObject()
                .put("id", n.id).put("kind", n.kind.name).put("title", n.title).put("text", n.text)
                .put("at", n.at.toEpochMilli()).put("pkg", n.pkg ?: "").put("target", n.target ?: ""),
        )
    }
}.toString()

fun noticesFromJson(text: String?): List<Notice> = runCatching {
    val a = JSONArray(text ?: return emptyList())
    (0 until a.length()).mapNotNull { i ->
        val o = a.getJSONObject(i)
        val kind = NoticeKind.entries.firstOrNull { it.name == o.optString("kind") } ?: return@mapNotNull null
        Notice(
            id = o.getString("id"),
            kind = kind,
            title = o.optString("title"),
            text = o.optString("text"),
            at = Instant.ofEpochMilli(o.getLong("at")),
            pkg = o.optString("pkg").ifEmpty { null },
            target = o.optString("target").ifEmpty { null },
        )
    }
}.getOrDefault(emptyList())

/** How long ago, as the bar says it: "now", "5 min", "2 h", then the weekday, then the date. */
fun noticeAge(at: Instant, now: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
    val d = Duration.between(at, now)
    return when {
        d < Duration.ofMinutes(1) -> "now"
        d < Duration.ofHours(1) -> "${d.toMinutes()} min"
        d < Duration.ofHours(24) -> "${d.toHours()} h"
        d < Duration.ofDays(6) -> DateTimeFormatter.ofPattern("EEE", Locale.getDefault()).format(at.atZone(zone))
        else -> DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()).format(at.atZone(zone))
    }
}

/**
 * A newer Pill Launcher, as a notification: from finding it until it is installed (or
 * put off with Later). Null when there is none.
 */
fun updateNotice(update: UpdateState, dismissed: String?, now: Instant): Notice? {
    val release = update.release ?: return null
    if (release.version == dismissed && update.phase != UpdatePhase.Downloading && update.phase != UpdatePhase.Installing) return null
    val title = when (update.phase) {
        UpdatePhase.Available -> "Pill Launcher ${release.version} is ready"
        UpdatePhase.Downloading -> "Downloading Pill Launcher ${release.version}"
        UpdatePhase.Installing -> "Installing Pill Launcher ${release.version}"
        UpdatePhase.Failed -> "Pill Launcher ${release.version} isn't installed yet"
        else -> return null
    }
    val text = when (update.phase) {
        UpdatePhase.Installing -> "Confirm on the screen Android shows"
        UpdatePhase.Downloading -> "${(update.progress * 100).toInt()}%"
        else -> update.message ?: release.notes.firstOrNull() ?: "Settings and favourites stay as they are"
    }
    return Notice("update:${release.version}", NoticeKind.Update, title, text, update.checkedAt ?: now)
}

/** "Updated to 3.3", after an update, with what was new in it when there is a note of it. */
fun updatedNotice(version: String, notes: List<String>, now: Instant): Notice =
    Notice("updated:$version", NoticeKind.Updated, "Updated to $version", notes.take(2).joinToString(" · ").ifEmpty { "What's new is in Settings → Updates" }, now)

/** A followed team about to kick off (the kick-off reminder). */
fun kickoffNotice(team: Team, match: Match, now: Instant): Notice = Notice(
    "kickoff:${match.id}",
    NoticeKind.Football,
    bannerTitle(team, match, now),
    "${match.home.name} v ${match.away.name} · ${shortCompetition(match.competition)}",
    match.kickoff?.minus(BannerLead) ?: now,
    target = team.id,
)

/** A goal or a win for a followed team. */
fun momentNotice(m: FootballMoment): Notice {
    val score = "${m.match.home.name} ${m.match.homeScore ?: 0}–${m.match.awayScore ?: 0} ${m.match.away.name}"
    return Notice(
        "${m.kind.name.lowercase()}:${m.match.id}:${m.match.homeScore}-${m.match.awayScore}",
        NoticeKind.Football,
        momentText(m, compact = false),
        score,
        m.at,
        target = m.team.id,
    )
}

/** No connection right now (shown while it lasts). */
/** How long a prayer's notice stays in the bar after its time. */
val PrayerNoticeFor: Duration = Duration.ofMinutes(15)

/**
 * The prayer whose time has just come, as a notice: from its time until [PrayerNoticeFor]
 * later. [clock] writes a time as the TV shows it ("6:06 PM"). Null between prayers, and
 * for times that aren't today's (a saved copy from an earlier day).
 */
fun prayerNotice(times: PrayerTimes, now: java.time.ZonedDateTime, clock: (java.time.LocalTime) -> String): Notice? {
    if (times.forDate != now.toLocalDate()) return null
    val (prayer, time) = times.list.lastOrNull { (_, t) -> !now.toLocalTime().isBefore(t) } ?: return null
    val at = java.time.ZonedDateTime.of(times.forDate, time, now.zone)
    if (Duration.between(at, now) >= PrayerNoticeFor) return null
    val iqama = times.iqama[prayer]?.let { " · Iqama " + clock(it) }.orEmpty()
    return Notice("prayer:${times.forDate}:${prayer.name}", NoticeKind.Prayer, "${prayer.title} · ${clock(time)}", "Prayer time$iqama", at.toInstant())
}

fun offlineNotice(since: Instant): Notice =
    Notice("offline:${since.toEpochMilli()}", NoticeKind.Connection, "No internet connection", "Weather, football and photos wait until it's back", since)

/** The connection came back after dropping for a while. */
fun backOnlineNotice(since: Instant, now: Instant): Notice {
    val minutes = Duration.between(since, now).toMinutes().coerceAtLeast(1)
    val length = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${minutes % 60} min"
    return Notice("online:${now.toEpochMilli()}", NoticeKind.Connection, "Back online", "The connection dropped for $length", now)
}

/** A file sent to the TV from a phone or computer. */
fun fileNotice(name: String, size: Long, from: String?, now: Instant): Notice =
    Notice("file:$name:${now.toEpochMilli()}", NoticeKind.File, "$name arrived" + (from?.let { " from $it" } ?: ""), fileSize(size) + " · OK to open", now, target = name)

/** An APK in Files whose app is now installed: OK asks to delete it, holding OK keeps it. */
fun installedApkNotice(name: String, now: Instant): Notice =
    Notice("$InstalledApk$name", NoticeKind.File, "$name: its app is installed", "OK to delete the file · hold OK to keep it", now, target = name)

/** The id prefix of [installedApkNotice]. */
const val InstalledApk = "installed-apk:"

/** "3.4 MB", "212 MB", "1.2 GB". */
fun fileSize(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var u = 0
    while (v >= 1000 && u < units.lastIndex) {
        v /= 1000
        u++
    }
    return if (u == 0 || v >= 100) "${v.toLong()} ${units[u]}" else String.format(Locale.US, "%.1f %s", v, units[u])
}

/**
 * Other apps' notifications, once the TV allows Pill Launcher to read them (Notification
 * access). Only ones a person would want on the home screen: not ongoing ones (music
 * playing, downloads), not group summaries, not the system's own.
 */
class NotificationWatcher : NotificationListenerService() {

    override fun onListenerConnected() {
        instance = this
        publish()
    }

    override fun onListenerDisconnected() {
        instance = null
        appNotices.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        val shown = active.mapNotNull { sbn -> toNotice(sbn)?.let { it to sbn.notification.contentIntent } }
        synchronized(intents) {
            intents.clear()
            for ((n, intent) in shown) if (intent != null) intents[n.id] = intent
        }
        appNotices.value = shown.map { it.first }.sortedByDescending { it.at }
    }

    private fun toNotice(sbn: StatusBarNotification): Notice? {
        val n = sbn.notification ?: return null
        if (sbn.packageName == packageName || sbn.packageName in SystemPackages) return null
        if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return null
        if (n.visibility == Notification.VISIBILITY_SECRET) return null
        val title = n.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val text = (n.extras?.getCharSequence(Notification.EXTRA_TEXT) ?: n.extras?.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString()?.trim().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return null
        return Notice(
            "app:${sbn.key}", NoticeKind.App, title.ifEmpty { text }, if (title.isEmpty()) "" else text, Instant.ofEpochMilli(sbn.postTime),
            pkg = sbn.packageName, isPrivate = n.visibility == Notification.VISIBILITY_PRIVATE,
        )
    }

    companion object {
        /** What other apps are showing now, newest first; empty without Notification access. */
        val appNotices = MutableStateFlow<List<Notice>>(emptyList())

        private val intents = HashMap<String, PendingIntent>()

        @Volatile private var instance: NotificationWatcher? = null

        private val SystemPackages = setOf("android", "com.android.systemui", "com.android.providers.downloads")

        /** Whether the TV lets Pill Launcher read other apps' notifications. */
        fun hasAccess(context: Context): Boolean = context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** The command that allows it from a computer, for TVs that hide the setting. */
        const val AdbGrant = "adb shell cmd notification allow_listener app.pill.launcher/app.pill.launcher.NotificationWatcher"

        /** Opens the TV's Notification access screen; a message saying how else to allow it when there is none. */
        fun askForAccess(context: Context): String? {
            val opened = runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.isSuccess
            return if (opened) null else "This TV hides Notification access. From a computer: $AdbGrant"
        }

        /** Does what the app's notification does when chosen, else opens the app. */
        fun open(context: Context, notice: Notice): Boolean {
            val intent = synchronized(intents) { intents[notice.id] }
            if (intent != null) {
                val sent = runCatching {
                    if (Build.VERSION.SDK_INT >= 34) {
                        val options = ActivityOptions.makeBasic()
                            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                        intent.send(context, 0, null, null, null, null, options.toBundle())
                    } else {
                        intent.send()
                    }
                }.isSuccess
                if (sent) return true
            }
            val pkg = notice.pkg ?: return false
            val launch = context.packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
            return runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        }

        /** Clears an app's notification from the TV (and so from the bar). */
        fun dismiss(notice: Notice) {
            val key = notice.id.removePrefix("app:")
            runCatching { instance?.cancelNotification(key) }
            appNotices.value = appNotices.value.filter { it.id != notice.id }
        }
    }
}

/**
 * The list's groups: each source (Pill Launcher's updates, Files, football, the connection,
 * each other app) once, with its notices under it, newest group first. A shared card per
 * source groups them at a glance (common region), however they arrived.
 */
fun noticeGroups(notices: List<Notice>, appLabel: (String) -> String?): List<Pair<String, List<Notice>>> {
    val groups = LinkedHashMap<String, MutableList<Notice>>()
    for (n in notices) {
        val source = when (n.kind) {
            NoticeKind.Update, NoticeKind.Updated -> "Pill Launcher"
            NoticeKind.File -> "Files"
            NoticeKind.Football -> "Football"
            NoticeKind.Connection -> "Connection"
            NoticeKind.Prayer -> "Prayer times"
            NoticeKind.App -> n.pkg?.let(appLabel) ?: n.title.ifBlank { "Other apps" }
        }
        groups.getOrPut(source) { mutableListOf() } += n
    }
    return groups.map { it.key to it.value }
}
