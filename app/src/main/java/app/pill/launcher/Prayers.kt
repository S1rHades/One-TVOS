package app.pill.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** The five daily prayers, in order. */
enum class Prayer(val title: String) {
    Fajr("Fajr"), Dhuhr("Dhuhr"), Asr("Asr"), Maghrib("Maghrib"), Isha("Isha");

}

/**
 * One day of a mosque's prayer times from MAWAQIT. [times] holds the five prayers (any the
 * answer lacked are left out); [iqama] the congregation times where the mosque gives them,
 * already worked out from "+10"-style offsets. [forDate] is the day the times are for: a
 * saved copy from an earlier day is still shown, marked.
 */
data class PrayerTimes(
    val mosque: String,
    val times: Map<Prayer, LocalTime>,
    val shuruq: LocalTime? = null,
    val iqama: Map<Prayer, LocalTime> = emptyMap(),
    val forDate: LocalDate,
    val fetchedAt: Instant,
    /** True for the saved copy shown before the first fetch of this run, or after a failed one. */
    val fromCache: Boolean = false,
) {
    /** The five prayers in order with their times; prayers the answer lacked are skipped. */
    val list: List<Pair<Prayer, LocalTime>> get() = Prayer.entries.mapNotNull { p -> times[p]?.let { p to it } }
}

/** The prayer coming up and when it is: the next one today, else tomorrow's Fajr. */
data class NextPrayer(val prayer: Prayer, val at: ZonedDateTime, val tomorrow: Boolean)

/** Where the community MAWAQIT API answers, unless changed in Settings. */
const val DefaultPrayerApi = "https://mawaqit-api.vercel.app/api/v1"

/** A mosque's name on MAWAQIT, as in its page's address: letters, digits and dashes. */
fun cleanMosqueSlug(text: String): String =
    text.trim().trimEnd('/').substringAfterLast('/').substringBefore('?').lowercase().filter { it.isLetterOrDigit() || it == '-' || it == '_' }

/** A name to show from the mosque's address when the answer has none: "grande-mosquee-de-paris" → "Grande Mosquee De Paris". */
fun mosqueName(slug: String): String =
    slug.split('-', '_').filter { it.isNotBlank() }.joinToString(" ") { w -> w.replaceFirstChar { it.titlecase() } }

/** The API's address with no trailing slash, or the default when blank or not a web address. */
fun cleanPrayerApi(text: String): String {
    val t = text.trim().trimEnd('/')
    return if (t.startsWith("http://") || t.startsWith("https://")) t else DefaultPrayerApi
}

/**
 * Which prayer comes next after [now] (times are in the TV's zone, as the mosque's clock
 * is). After Isha it is tomorrow's Fajr, with the same times, until the new day's arrive.
 */
fun nextPrayer(times: PrayerTimes, now: ZonedDateTime): NextPrayer? {
    val list = times.list
    if (list.isEmpty()) return null
    val today = now.toLocalDate()
    for ((p, t) in list) {
        val at = ZonedDateTime.of(today, t, now.zone)
        if (!at.isBefore(now)) return NextPrayer(p, at, tomorrow = false)
    }
    val (p, t) = list.first()
    return NextPrayer(p, ZonedDateTime.of(today.plusDays(1), t, now.zone), tomorrow = true)
}

/** "in 42 min", "in 2 h 05 min", "now" within the minute. */
fun untilText(at: ZonedDateTime, now: ZonedDateTime): String {
    val d = Duration.between(now, at)
    val minutes = d.toMinutes()
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "in $minutes min"
        else -> "in ${minutes / 60} h ${"%02d".format(minutes % 60)} min"
    }
}

/**
 * MAWAQIT's answer, in any of the shapes the community API gives or has given:
 * - `/{mosque}/prayer-times`: the six times flat, with MAWAQIT's own key names
 *   (`{"fajr", "sunrise", "dohr", "asr", "maghreb", "icha"}`) and no mosque name;
 * - `/{mosque}`: MAWAQIT's whole page data under `rawdata`, with `times` as a list of the
 *   five, `shuruq` beside it, the name, and the year's iqama offsets in `iqamaCalendar`;
 * - `{"mosque", "times": {…}, "iqama": {…}}` with English names.
 * Iqama may be clock times or "+10"-style offsets. Anything unreadable is left out; an
 * answer with no prayer at all is an error. [fallbackName] names a mosque the answer doesn't.
 */
fun parsePrayerTimes(body: String, fetchedAt: Instant, forDate: LocalDate, fallbackName: String = ""): PrayerTimes {
    val outer = JSONObject(body)
    val json = outer.optJSONObject("rawdata") ?: outer
    val mosque = listOf("mosque", "name", "label").firstNotNullOfOrNull { k ->
        json.opt(k)?.let { v -> if (v is JSONObject) v.optString("name") else v.toString() }?.takeIf { it.isNotBlank() && it != "null" }
    } ?: fallbackName
    val times = HashMap<Prayer, LocalTime>()
    var shuruq: LocalTime? = null
    when (val raw = json.opt("times")) {
        is JSONObject -> {
            readNamed(raw, times)
            shuruq = sunriseIn(raw)
        }
        is JSONArray -> {
            // Five entries are the prayers; six put sunrise second, as MAWAQIT's own pages do
            val withSunrise = raw.length() >= 6
            var i = 0
            for (p in Prayer.entries) {
                if (withSunrise && p == Prayer.Dhuhr) { shuruq = parseClock(raw.optString(i)); i++ }
                parseClock(raw.optString(i))?.let { times[p] = it }
                i++
            }
        }
        // The prayer-times endpoint: the times are the answer itself
        else -> readNamed(json, times)
    }
    if (shuruq == null) shuruq = sunriseIn(json)
    check(times.isNotEmpty()) { "No prayer times in the answer" }
    val iqama = HashMap<Prayer, LocalTime>()
    val todays = json.opt("iqama") ?: iqamaFromCalendar(json.optJSONArray("iqamaCalendar"), forDate)
    when (todays) {
        is JSONObject -> for (p in Prayer.entries) iqamaFor(namedIn(todays, p), times[p])?.let { iqama[p] = it }
        is JSONArray -> for ((i, p) in Prayer.entries.withIndex()) if (i < todays.length()) iqamaFor(todays.optString(i), times[p])?.let { iqama[p] = it }
    }
    return PrayerTimes(mosque, times, shuruq, iqama, forDate, fetchedAt)
}

/** The names a prayer goes by in MAWAQIT's answers: English, then MAWAQIT's own French ones. */
private val PrayerKeys = mapOf(
    Prayer.Fajr to listOf("fajr", "sobh"),
    Prayer.Dhuhr to listOf("dhuhr", "dohr", "zuhr", "duhr"),
    Prayer.Asr to listOf("asr"),
    Prayer.Maghrib to listOf("maghrib", "maghreb"),
    Prayer.Isha to listOf("isha", "icha"),
)

private fun namedIn(o: JSONObject, p: Prayer): String? =
    PrayerKeys.getValue(p).firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() && it != "null" } }

private fun readNamed(o: JSONObject, into: MutableMap<Prayer, LocalTime>) {
    for (p in Prayer.entries) parseClock(namedIn(o, p))?.let { into[p] = it }
}

private fun sunriseIn(o: JSONObject): LocalTime? =
    listOf("shuruq", "sunrise", "chourouk").firstNotNullOfOrNull { parseClock(o.optString(it)) }

/** Today's iqama offsets from MAWAQIT's year of them: a month per entry, keyed by the day of the month. */
private fun iqamaFromCalendar(calendar: JSONArray?, day: LocalDate): Any? {
    val month = calendar?.opt(day.monthValue - 1) ?: return null
    return when (month) {
        is JSONObject -> month.opt(day.dayOfMonth.toString())
        is JSONArray -> month.opt(day.dayOfMonth - 1)
        else -> null
    }
}

/** "05:15" or "5:15", else null. */
private fun parseClock(text: String?): LocalTime? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty() || t == "null") return null
    return runCatching { LocalTime.parse(t, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
        ?: runCatching { LocalTime.parse(t.take(5), DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()
}

/** An iqama given as a clock time, or as minutes after the prayer ("+10", "10"). */
private fun iqamaFor(text: String?, prayer: LocalTime?): LocalTime? {
    val t = text?.trim().orEmpty()
    if (t.isEmpty() || t == "null") return null
    parseClock(t)?.let { return it }
    val minutes = t.removePrefix("+").toLongOrNull() ?: return null
    return prayer?.plusMinutes(minutes)
}

/**
 * Fetches a mosque's prayer times from the community MAWAQIT API and keeps the last
 * answer, so the card shows the saved times at once after a restart or while offline.
 * Call off the main thread.
 */
class PrayerSource(context: Context, private val zone: ZoneId = ZoneId.systemDefault()) {
    private val prefs = context.getSharedPreferences("prayers", Context.MODE_PRIVATE)

    /** The saved answer for [slug], marked as from the cache; null when there is none or it is for another mosque. */
    fun cached(slug: String): PrayerTimes? = runCatching {
        if (slug.isBlank() || prefs.getString("slug", null) != slug) return null
        val body = prefs.getString("body", null) ?: return null
        val at = Instant.ofEpochMilli(prefs.getLong("at", 0))
        val forDate = prefs.getString("date", null)?.let { LocalDate.parse(it) } ?: at.atZone(zone).toLocalDate()
        parsePrayerTimes(body, at, forDate, mosqueName(slug)).copy(fromCache = true)
    }.getOrNull()

    /**
     * Today's times for [slug] from [api]: the mosque's own entry, or, when that address
     * fails, the API's plain `prayer-times` entry for it. Both failing names both reasons,
     * for Settings.
     */
    fun fetch(api: String, slug: String): Result<PrayerTimes> = runCatching {
        require(slug.isNotBlank()) { "Choose a mosque in Settings" }
        val base = cleanPrayerApi(api)
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()
        // The mosque's whole page first (its name and iqama with the times), then the times alone
        val body = runCatching { httpGet("$base/$slug/", PrayerUserAgent) }.getOrElse { first ->
            runCatching { httpGet("$base/$slug/prayer-times", PrayerUserAgent) }.getOrElse { second ->
                throw IOException("${shortReason(first)}; prayer-times: ${shortReason(second)}")
            }
        }
        val times = parsePrayerTimes(body, now, today, fallbackName = mosqueName(slug))
        prefs.edit()
            .putString("slug", slug).putString("body", body).putLong("at", now.toEpochMilli()).putString("date", today.toString())
            .apply()
        times
    }

    /** How long to wait after a good fetch: until just after the next local midnight, for the new day's times. */
    fun untilNextDay(now: ZonedDateTime = ZonedDateTime.now(zone)): Duration {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone).plusSeconds(30)
        return Duration.between(now, midnight).coerceIn(Duration.ofMinutes(1), Duration.ofHours(24))
    }
}

private const val PrayerUserAgent = "PillLauncher/3.4 github.com/S1rHades/One-TVOS"

/** "HTTP 404" or the root cause's class name, e.g. "UnknownHostException". */
private fun shortReason(error: Throwable): String {
    val root = generateSequence(error) { it.cause }.last()
    return root.message?.takeIf { it.startsWith("HTTP ") } ?: root::class.java.simpleName
}
