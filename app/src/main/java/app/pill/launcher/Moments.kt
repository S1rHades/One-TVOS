package app.pill.launcher

import java.time.Duration
import java.time.Instant

/** Something worth a moment on the football card. */
enum class MomentKind { Goal, Win }

/** A goal for a followed team, or its win at full time, noticed between two fetches. */
data class FootballMoment(val kind: MomentKind, val team: Team, val match: Match, val minute: Int?, val at: Instant)

/** How long the card celebrates. */
fun momentMillis(kind: MomentKind): Long = if (kind == MomentKind.Win) 7_000L else 9_000L

/**
 * The minute of a match being played, worked out from kick-off (the free data has no
 * clock): the first half runs to 45, half-time holds at 45, and the second half starts
 * at 46 about fifteen minutes later. Null before kick-off or without one.
 */
fun matchMinute(match: Match, now: Instant): Int? {
    val start = match.kickoff ?: return null
    val elapsed = Duration.between(start, now).toMinutes().toInt() + 1
    if (elapsed < 1) return null
    return when (match.status) {
        "1H" -> elapsed.coerceAtMost(45)
        "HT" -> 45
        "2H" -> (elapsed - 15).coerceIn(46, 90)
        "ET" -> (elapsed - 20).coerceIn(91, 120)
        else -> when {
            elapsed <= 45 -> elapsed
            elapsed <= 60 -> 45
            else -> (elapsed - 15).coerceAtMost(90)
        }
    }
}

/**
 * What changed for the followed teams between [before] and [after]: a goal when a team's
 * score in the same match went up, a win when a match it was playing has finished with
 * it ahead. Nothing on the first load (no real data before) or for a match first seen
 * already over, so opening the launcher after a game doesn't celebrate it.
 */
fun footballMoments(before: List<Fixtures>, after: List<Fixtures>, now: Instant): List<FootballMoment> {
    val moments = mutableListOf<FootballMoment>()
    for (f in after) {
        val old = before.firstOrNull { it.team.id == f.team.id } ?: continue
        if (old.fetchedAt == Instant.EPOCH) continue
        val oldMatches = listOfNotNull(old.last, old.next)
        for (match in listOfNotNull(f.last, f.next).distinctBy { it.id }) {
            val was = oldMatches.firstOrNull { it.id == match.id } ?: continue
            if (was.isFinished) continue
            val scoredBefore = was.ourScore ?: 0
            val scored = match.ourScore ?: 0
            if (scored > scoredBefore && match.ourScore != null) {
                moments += FootballMoment(MomentKind.Goal, f.team, match, matchMinute(match, now), now)
            }
            if (match.isFinished && match.outcome == 'W') {
                moments += FootballMoment(MomentKind.Win, f.team, match, null, now)
            }
        }
    }
    return moments
}

/** What the card says: "Goal · Algeria 78'" or "Algeria win 2–1"; shorter on a compact card. */
fun momentText(m: FootballMoment, compact: Boolean): String {
    val name = if (compact) shortCode(m.team.name) else m.team.name
    return when (m.kind) {
        MomentKind.Goal -> listOfNotNull("Goal", name, m.minute?.let { "$it'" }).let { parts ->
            if (compact) "Goal" + (m.minute?.let { " · $it'" } ?: "") else parts.first() + " · " + parts.drop(1).joinToString(" ")
        }
        MomentKind.Win -> "$name win ${m.match.ourScore}–${m.match.opponentScore}"
    }
}
