package app.pill.launcher

import android.content.Context
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** One side of a match. [badge] is the crest's URL, when the data has one. */
data class Side(val name: String, val badge: String?, val isAlgeria: Boolean)

data class Match(
    val home: Side,
    val away: Side,
    val homeScore: Int?,
    val awayScore: Int?,
    val competition: String,
    val kickoff: Instant?,
    /** TheSportsDB's status code: NS not started, 1H, HT, 2H, FT and so on. */
    val status: String,
) {
    val algeria get() = if (away.isAlgeria) away else home
    val opponent get() = if (away.isAlgeria) home else away
    val algeriaScore get() = if (away.isAlgeria) awayScore else homeScore
    val opponentScore get() = if (away.isAlgeria) homeScore else awayScore

    /** Being played now: a live status, or (when the feed lags) within two hours of kick-off. */
    fun isLive(now: Instant): Boolean {
        if (status in LiveStatuses) return true
        val start = kickoff ?: return false
        return status !in FinishedStatuses && now.isAfter(start) && now.isBefore(start.plus(Duration.ofMinutes(115)))
    }

    /** W, D or L for Algeria, once there is a score. */
    val outcome: Char?
        get() {
            val a = algeriaScore ?: return null
            val b = opponentScore ?: return null
            return when { a > b -> 'W'; a < b -> 'L'; else -> 'D' }
        }
}

/** Algeria's next fixture and latest result, and when they were fetched. */
data class Fixtures(val next: Match?, val last: Match?, val fetchedAt: Instant, val fromCache: Boolean = false)

/** The men's national team on TheSportsDB. */
private const val AlgeriaId = "134516"
private const val Api = "https://www.thesportsdb.com/api/v1/json/123"

/** Refreshed every 30 minutes by .github/workflows/fixtures.yml. */
private const val Mirror = "https://raw.githubusercontent.com/S1rHades/One-TVOS/fixtures-data/fixtures.json"

/** The innermost cause's type and message, short enough for a settings row. */
private fun describeError(error: Throwable): String {
    val root = generateSequence(error) { it.cause }.last()
    return root::class.java.simpleName + (root.message?.take(80)?.let { ": $it" } ?: "")
}
private val LiveStatuses = setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")
private val FinishedStatuses = setOf("FT", "AET", "PEN", "AP", "CANC", "PST", "ABD", "AWD", "WO", "Match Finished")

/**
 * Fetches the fixtures from TheSportsDB's free API (no account needed) and keeps the
 * last good copy, so the widget still has something to show when the TV is offline.
 * Call off the main thread.
 */
class FixturesSource(context: Context) {
    private val prefs = context.getSharedPreferences("fixtures", Context.MODE_PRIVATE)

    fun cached(): Fixtures? = runCatching {
        val next = prefs.getString("next", null) ?: return null
        val last = prefs.getString("last", null) ?: return null
        parseFixtures(next, last, Instant.ofEpochMilli(prefs.getLong("at", 0))).copy(fromCache = true)
    }.getOrNull()

    /** TheSportsDB first (it has live scores); if the TV can't reach it, the copy on GitHub. */
    fun fetch(): Result<Fixtures> = runCatching {
        val (next, last) = runCatching {
            httpGet("$Api/eventsnext.php?id=$AlgeriaId") to httpGet("$Api/eventslast.php?id=$AlgeriaId")
        }.getOrElse { direct ->
            runCatching {
                val copy = JSONObject(httpGet(Mirror))
                copy.getJSONObject("next").toString() to copy.getJSONObject("last").toString()
            }.getOrElse { mirror ->
                // Name both failures, so the line in Settings says what each did
                throw IOException("TheSportsDB: ${describeError(direct)}; GitHub copy: ${describeError(mirror)}")
            }
        }
        val now = Instant.now()
        parseFixtures(next, last, now).also {
            prefs.edit().putString("next", next).putString("last", last).putLong("at", now.toEpochMilli()).apply()
        }
    }
}

fun parseFixtures(nextJson: String, lastJson: String, fetchedAt: Instant): Fixtures {
    fun events(json: String, key: String): List<Match> {
        val array: JSONArray = JSONObject(json).optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { match(array.getJSONObject(it)) }.getOrNull() }
    }
    // eventslast answers under "results", eventsnext under "events"
    val upcoming = events(nextJson, "events").sortedBy { it.kickoff ?: Instant.MAX }
    val played = (events(lastJson, "results") + events(lastJson, "events")).sortedByDescending { it.kickoff ?: Instant.MIN }
    return Fixtures(next = upcoming.firstOrNull(), last = played.firstOrNull(), fetchedAt = fetchedAt)
}

private fun match(e: JSONObject): Match {
    fun side(prefix: String) = Side(
        name = e.getString("str${prefix}Team"),
        badge = e.optString("str${prefix}TeamBadge").takeIf { it.startsWith("http") },
        isAlgeria = e.optString("id${prefix}Team") == AlgeriaId,
    )
    fun score(key: String) = e.optString(key).toIntOrNull()
    return Match(
        home = side("Home"),
        away = side("Away"),
        homeScore = score("intHomeScore"),
        awayScore = score("intAwayScore"),
        competition = e.optString("strLeague").ifBlank { "Match" },
        kickoff = kickoff(e),
        status = e.optString("strStatus").ifBlank { "NS" },
    )
}

/** strTimestamp is UTC without a zone; older entries only have dateEvent and strTime. */
private fun kickoff(e: JSONObject): Instant? {
    val stamp = e.optString("strTimestamp").takeIf { it.length >= 16 && it != "null" }
        ?: e.optString("dateEvent").takeIf { it.length == 10 }?.let { date ->
            date + "T" + e.optString("strTime").takeIf { it.length >= 5 && it != "null" }?.take(8).orEmpty().ifBlank { "00:00:00" }
        }
        ?: return null
    return runCatching { LocalDateTime.parse(stamp.take(19).removeSuffix("Z")).toInstant(ZoneOffset.UTC) }.getOrNull()
}

/** Competition names as fans say them, short enough for the card. */
fun shortCompetition(name: String): String {
    val n = name.lowercase()
    return when {
        "friendl" in n -> "Friendly"
        ("african cup of nations" in n || "africa cup of nations" in n) && "qualif" in n -> "AFCON qualifier"
        "african cup of nations" in n || "africa cup of nations" in n -> "AFCON"
        "world cup" in n && "qualif" in n -> "World Cup qualifier"
        "world cup" in n -> "World Cup"
        "arab cup" in n -> "Arab Cup"
        "african nations championship" in n -> "CHAN"
        else -> name
    }
}

/** Short names for the crest fallback and narrow cards. */
fun shortCode(team: String): String = when (team.lowercase()) {
    "algeria" -> "ALG"
    else -> team.filter { it.isLetter() || it == ' ' }.trim().let { name ->
        val words = name.split(" ").filter { it.isNotBlank() }
        if (words.size >= 2) words.take(3).joinToString("") { it.take(1) }.uppercase().padEnd(3, words.last().getOrElse(1) { 'X' }.uppercaseChar())
        else name.take(3).uppercase()
    }
}
