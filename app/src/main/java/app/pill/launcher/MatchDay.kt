package app.pill.launcher

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Which followed teams get the match-day card and the kick-off banner. */
enum class MatchDayMode(val title: String) { All("All teams"), AlgeriaOnly("Algeria only"), Off("Off") }

/** How close a team's next match is: on today, about to start (10 minutes), or being played. */
enum class MatchPhase { None, Today, Soon, Live }

/** The banner shows from this long before kick-off... */
val BannerLead: Duration = Duration.ofMinutes(10)

/** ...until this long after, in case the TV was off at the time. */
private val BannerTail: Duration = Duration.ofMinutes(5)

fun matchDayApplies(team: Team, mode: MatchDayMode): Boolean =
    mode == MatchDayMode.All || (mode == MatchDayMode.AlgeriaOnly && team.isAlgeria)

fun matchPhase(fixtures: Fixtures, now: Instant, zone: ZoneId): MatchPhase {
    if (listOfNotNull(fixtures.last, fixtures.next).any { it.isLive(now) }) return MatchPhase.Live
    val next = fixtures.next ?: return MatchPhase.None
    val kickoff = next.kickoff ?: return MatchPhase.None
    if (next.isFinished || !kickoff.isAfter(now)) return MatchPhase.None
    if (Duration.between(now, kickoff) <= BannerLead) return MatchPhase.Soon
    if (kickoff.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()) return MatchPhase.Today
    return MatchPhase.None
}

/**
 * The teams the football card takes turns between: on a match day only the teams playing
 * that day (or playing now), otherwise all of them, in the user's order.
 */
fun cardTeams(all: List<Fixtures>, mode: MatchDayMode, now: Instant, zone: ZoneId): List<Fixtures> =
    all.filter { matchDayApplies(it.team, mode) && matchPhase(it, now, zone) != MatchPhase.None }.ifEmpty { all }

/**
 * The match whose kick-off banner is due now: from [BannerLead] before kick-off until a
 * few minutes after, not yet dismissed ([shown] holds match ids). The soonest one first.
 */
fun dueBanner(all: List<Fixtures>, mode: MatchDayMode, now: Instant, shown: Set<String>): Pair<Team, Match>? =
    all.filter { matchDayApplies(it.team, mode) }
        .mapNotNull { f -> f.next?.let { f.team to it } }
        .filter { (_, m) ->
            val kickoff = m.kickoff ?: return@filter false
            !m.isFinished && m.id !in shown && !now.isBefore(kickoff.minus(BannerLead)) && now.isBefore(kickoff.plus(BannerTail))
        }
        .minByOrNull { it.second.kickoff!! }

/** "Les Fennecs kick off in 10 minutes", "Arsenal kick off in 1 minute", "... are kicking off". */
fun bannerTitle(team: Team, match: Match, now: Instant): String {
    val who = if (team.isAlgeria) "Les Fennecs" else team.name
    val minutes = match.kickoff?.let { Math.ceil(Duration.between(now, it).seconds / 60.0).toLong() } ?: 0L
    return when {
        minutes <= 0 -> "$who are kicking off"
        minutes == 1L -> "$who kick off in 1 minute"
        else -> "$who kick off in $minutes minutes"
    }
}
