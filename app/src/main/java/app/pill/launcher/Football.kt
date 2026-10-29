package app.pill.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.text.Normalizer
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

/** A team to follow, as TheSportsDB knows it. [badge] is the crest's URL, when there is one. */
data class Team(
    val id: String,
    val name: String,
    val badge: String? = null,
    val league: String? = null,
    val country: String? = null,
) {
    val isAlgeria get() = id == AlgeriaId
}

/** The men's national team on TheSportsDB. Always followed, always first. */
const val AlgeriaId = "134516"
val Algeria = Team(AlgeriaId, "Algeria", league = "FIFA World Cup", country = "Algeria")

/** Teams followed at most, Algeria included: the card cycles through them and each costs two requests. */
const val MaxTeams = 6

/** One side of a match. */
data class Side(val id: String, val name: String, val badge: String?) {
    val isAlgeria get() = id == AlgeriaId
}

data class Match(
    val id: String,
    val home: Side,
    val away: Side,
    val homeScore: Int?,
    val awayScore: Int?,
    val competition: String,
    val leagueId: String?,
    val season: String?,
    val kickoff: Instant?,
    /** TheSportsDB's status code: NS not started, 1H, HT, 2H, FT and so on. */
    val status: String,
    val venue: String?,
    /** The followed team this match was fetched for: "ours" and "opponent" are from its side. */
    val teamId: String,
) {
    val ours get() = if (away.id == teamId) away else home
    val opponent get() = if (away.id == teamId) home else away
    val ourScore get() = if (away.id == teamId) awayScore else homeScore
    val opponentScore get() = if (away.id == teamId) homeScore else awayScore

    val isFinished get() = status in FinishedStatuses

    /** Being played now: a live status, or (when the feed lags) within two hours of kick-off. */
    fun isLive(now: Instant): Boolean {
        if (status in LiveStatuses) return true
        val start = kickoff ?: return false
        return status !in FinishedStatuses && now.isAfter(start) && now.isBefore(start.plus(Duration.ofMinutes(115)))
    }

    /** W, D or L for the followed team, once there is a score. */
    val outcome: Char?
        get() {
            val a = ourScore ?: return null
            val b = opponentScore ?: return null
            return when { a > b -> 'W'; a < b -> 'L'; else -> 'D' }
        }
}

/** A team's next fixture and latest result, when they were fetched, and why the last fetch failed. */
data class Fixtures(
    val team: Team,
    val next: Match?,
    val last: Match?,
    val fetchedAt: Instant,
    val fromCache: Boolean = false,
    /** Where this copy came from: TheSportsDB itself or the copy on GitHub. */
    val viaMirror: Boolean = false,
    val error: String? = null,
)

/** The top of a league table. The free data has only the first five places. */
data class TableRow(val rank: Int, val teamId: String, val team: String, val badge: String?, val played: Int, val goalDifference: Int, val points: Int, val form: String?)

data class LeagueTable(val leagueId: String, val league: String, val season: String, val rows: List<TableRow>)

private const val Api = "https://www.thesportsdb.com/api/v1/json/123"

/** Kept up to date by .github/workflows/fixtures.yml (see .github/scripts/mirror.py). */
internal const val MirrorBase = "https://raw.githubusercontent.com/S1rHades/One-TVOS/fixtures-data/"

/** TheSportsDB turns away bursts of requests, so direct ones are spaced out. */
private const val DirectSpacingMs = 1500L

/** The innermost cause's type and message, short enough for a settings row. */
internal fun describeError(error: Throwable): String {
    val root = generateSequence(error) { it.cause }.last()
    return root::class.java.simpleName + (root.message?.take(80)?.let { ": $it" } ?: "")
}
private val LiveStatuses = setOf("1H", "HT", "2H", "ET", "BT", "P", "LIVE", "INT")
private val FinishedStatuses = setOf("FT", "AET", "PEN", "AP", "CANC", "PST", "ABD", "AWD", "WO", "Match Finished")

/**
 * Fixtures from TheSportsDB's free API (no account needed), for every followed team. When
 * the TV can't reach TheSportsDB (its Cloudflare protection turns some TVs away, and it
 * limits bursts), the copy on GitHub is used instead. The last good copy of each team is
 * kept, so the card still has something to show offline. Call off the main thread.
 */
class FootballSource(context: Context) {
    private val prefs = context.getSharedPreferences("football", Context.MODE_PRIVATE)
    private val legacy = context.getSharedPreferences("fixtures", Context.MODE_PRIVATE)

    fun cached(team: Team): Fixtures? = runCatching {
        val (next, last, at) = prefs.getString("t.${team.id}", null)?.let { JSONObject(it) }
            ?.let { Triple(it.getString("next"), it.getString("last"), it.getLong("at")) }
            // Before 2.5 only Algeria was saved, under its own keys
            ?: if (team.isAlgeria) Triple(legacy.getString("next", null) ?: return null, legacy.getString("last", null) ?: return null, legacy.getLong("at", 0)) else return null
        parseFixtures(next, last, Instant.ofEpochMilli(at), team).copy(fromCache = true)
    }.getOrNull()

    /**
     * Fresh fixtures for each of [teams], in order. A team that can't be fetched keeps its
     * saved copy (marked) with the reason in [Fixtures.error]. After one direct failure
     * the rest go straight to the GitHub copy, so a blocked TV doesn't wait on each.
     */
    fun fetch(teams: List<Team>): List<Fixtures> {
        var direct = true
        return teams.mapIndexed { i, team ->
            if (direct && i > 0) Thread.sleep(DirectSpacingMs)
            val directResult = if (direct) runCatching {
                httpGet("$Api/eventsnext.php?id=${team.id}") to httpGet("$Api/eventslast.php?id=${team.id}")
            } else null
            if (directResult?.isFailure == true) direct = false
            val result: Result<Pair<Pair<String, String>, Boolean>> = directResult?.takeIf { it.isSuccess }?.map { it to false } ?: runCatching {
                val copy = JSONObject(httpGet(MirrorBase + "teams/${team.id}.json"))
                (copy.getJSONObject("next").toString() to copy.getJSONObject("last").toString()) to true
            }.recoverCatching { mirror ->
                // Name both failures, so the line in Settings says what each did
                val first = directResult?.exceptionOrNull()?.let(::describeError) ?: "skipped"
                val second = if (mirror is java.io.FileNotFoundException || mirror.message?.contains("404") == true) "no copy for this team" else describeError(mirror)
                throw IOException("TheSportsDB: $first; GitHub copy: $second")
            }
            result.fold(
                onSuccess = { (pair, viaMirror) ->
                    val now = Instant.now()
                    prefs.edit().putString("t.${team.id}", JSONObject().put("next", pair.first).put("last", pair.second).put("at", now.toEpochMilli()).toString()).apply()
                    parseFixtures(pair.first, pair.second, now, team).copy(viaMirror = viaMirror)
                },
                onFailure = { error ->
                    (cached(team) ?: Fixtures(team, null, null, Instant.EPOCH, fromCache = true)).copy(error = error.message ?: describeError(error))
                },
            )
        }
    }

    /** The teams the GitHub copy keeps fixtures for, refreshed once a day; the saved list until then. */
    fun mirroredTeams(): List<Team> {
        val saved = prefs.getString("index", null)
        val age = System.currentTimeMillis() - prefs.getLong("indexAt", 0)
        if (saved != null && age < 24 * 60 * 60 * 1000L) return parseTeamIndex(saved)
        return runCatching { httpGet(MirrorBase + "teams.json") }
            .onSuccess { prefs.edit().putString("index", it).putLong("indexAt", System.currentTimeMillis()).apply() }
            .map(::parseTeamIndex)
            .getOrElse { saved?.let(::parseTeamIndex).orEmpty() }
    }

    /** TheSportsDB's own team search; fails when the TV can't reach it. Football teams only. */
    fun searchDirect(query: String): Result<List<Team>> = runCatching {
        parseTeamSearch(httpGet("$Api/searchteams.php?t=" + URLEncoder.encode(query.trim(), "UTF-8")))
    }

    /** The top of [leagueId]'s table for [season]: from TheSportsDB, else the GitHub copy. */
    fun table(leagueId: String, season: String): LeagueTable? {
        val body = runCatching { httpGet("$Api/lookuptable.php?l=$leagueId&s=" + URLEncoder.encode(season, "UTF-8")) }
            .getOrElse { runCatching { httpGet(MirrorBase + "tables/$leagueId.json") }.getOrNull() }
            ?: return null
        return runCatching { parseTable(body) }.getOrNull()?.takeIf { it.season == season && it.rows.isNotEmpty() }
    }
}

fun parseFixtures(nextJson: String, lastJson: String, fetchedAt: Instant, team: Team = Algeria): Fixtures {
    fun events(json: String, key: String): List<Match> {
        val array: JSONArray = JSONObject(json).optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { runCatching { match(array.getJSONObject(it), team.id) }.getOrNull() }
    }
    // eventslast answers under "results", eventsnext under "events"
    val upcoming = events(nextJson, "events").sortedBy { it.kickoff ?: Instant.MAX }
    val played = (events(lastJson, "results") + events(lastJson, "events")).sortedByDescending { it.kickoff ?: Instant.MIN }
    // The feed knows the team's crest; keep it for the settings list and the hub
    val badge = team.badge ?: (upcoming + played).firstNotNullOfOrNull { m -> listOf(m.home, m.away).firstOrNull { it.id == team.id }?.badge }
    return Fixtures(team = team.copy(badge = badge), next = upcoming.firstOrNull(), last = played.firstOrNull(), fetchedAt = fetchedAt)
}

private fun match(e: JSONObject, teamId: String): Match {
    fun side(prefix: String) = Side(
        id = e.optString("id${prefix}Team"),
        name = e.getString("str${prefix}Team"),
        badge = e.optString("str${prefix}TeamBadge").takeIf { it.startsWith("http") },
    )
    fun score(key: String) = e.optString(key).toIntOrNull()
    fun text(key: String) = e.optString(key).takeIf { it.isNotBlank() && it != "null" }
    return Match(
        id = text("idEvent") ?: (text("strEvent") + text("dateEvent")),
        home = side("Home"),
        away = side("Away"),
        homeScore = score("intHomeScore"),
        awayScore = score("intAwayScore"),
        competition = text("strLeague") ?: "Match",
        leagueId = text("idLeague"),
        season = text("strSeason"),
        kickoff = kickoff(e),
        status = text("strStatus") ?: "NS",
        venue = text("strVenue"),
        teamId = teamId,
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

/** searchteams.php's answer: football teams only. */
fun parseTeamSearch(json: String): List<Team> {
    val teams = JSONObject(json).optJSONArray("teams") ?: return emptyList()
    return (0 until teams.length()).map { teams.getJSONObject(it) }
        .filter { it.optString("strSport") == "Soccer" }
        .map { t ->
            Team(
                id = t.getString("idTeam"),
                name = t.getString("strTeam"),
                badge = t.optString("strBadge").takeIf { it.startsWith("http") },
                league = t.optString("strLeague").takeIf { it.isNotBlank() && !it.startsWith("_") },
                country = t.optString("strCountry").takeIf { it.isNotBlank() },
            )
        }
}

/** The GitHub copy's list of teams it keeps fixtures for. */
fun parseTeamIndex(json: String): List<Team> {
    val teams = JSONObject(json).optJSONArray("teams") ?: return emptyList()
    return (0 until teams.length()).map { teams.getJSONObject(it) }.map { t ->
        Team(
            id = t.getString("id"),
            name = t.getString("name"),
            badge = t.optString("badge").takeIf { it.startsWith("http") },
            league = t.optString("league").takeIf { it.isNotBlank() },
            country = t.optString("country").takeIf { it.isNotBlank() },
        )
    }
}

fun parseTable(json: String): LeagueTable {
    val rows = JSONObject(json).getJSONArray("table")
    val first = rows.getJSONObject(0)
    return LeagueTable(
        leagueId = first.getString("idLeague"),
        league = first.optString("strLeague"),
        season = first.optString("strSeason"),
        rows = (0 until rows.length()).map { rows.getJSONObject(it) }.map { r ->
            TableRow(
                rank = r.optString("intRank").toIntOrNull() ?: 0,
                teamId = r.optString("idTeam"),
                team = r.optString("strTeam"),
                badge = r.optString("strBadge").takeIf { it.startsWith("http") },
                played = r.optString("intPlayed").toIntOrNull() ?: 0,
                goalDifference = r.optString("intGoalDifference").toIntOrNull() ?: 0,
                points = r.optString("intPoints").toIntOrNull() ?: 0,
                form = r.optString("strForm").takeIf { it.isNotBlank() && it != "null" },
            )
        }.sortedBy { it.rank },
    )
}

/**
 * Teams matching [query] by name, league or country, ignoring case and accents ("setif"
 * finds "ES Sétif"). Names that start with the query come first.
 */
fun searchTeams(teams: List<Team>, query: String, limit: Int = 8): List<Team> {
    val q = fold(query)
    if (q.isBlank()) return emptyList()
    return teams
        .filter { t -> listOfNotNull(t.name, t.league, t.country).any { q in fold(it) } }
        .sortedWith(compareBy({ !fold(it.name).startsWith(q) }, { q !in fold(it.name) }, { it.name.length }))
        .take(limit)
}

private fun fold(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase().trim()

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
        n.startsWith("english ") -> name.substring(8)
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

/** How a followed team is stored: a small JSON list, Algeria first. */
fun teamsToJson(teams: List<Team>): String = JSONArray(
    teams.map { t ->
        JSONObject().put("id", t.id).put("name", t.name).put("badge", t.badge ?: "").put("league", t.league ?: "").put("country", t.country ?: "")
    },
).toString()

fun teamsFromJson(json: String?): List<Team> {
    val list = runCatching { JSONArray(json ?: return listOf(Algeria)) }.getOrNull() ?: return listOf(Algeria)
    val teams = (0 until list.length()).mapNotNull { i ->
        runCatching {
            val t = list.getJSONObject(i)
            Team(
                t.getString("id"), t.getString("name"),
                t.optString("badge").takeIf { it.startsWith("http") },
                t.optString("league").takeIf { it.isNotBlank() },
                t.optString("country").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }
    return withAlgeriaFirst(teams)
}

/** Algeria first, no duplicates, at most [MaxTeams]. */
fun withAlgeriaFirst(teams: List<Team>): List<Team> =
    (listOf(teams.firstOrNull { it.isAlgeria } ?: Algeria) + teams.filter { !it.isAlgeria }).distinctBy { it.id }.take(MaxTeams)

/** A team found in Settings → Football, and whether the GitHub copy keeps its fixtures. */
data class TeamHit(val team: Team, val mirrored: Boolean)
