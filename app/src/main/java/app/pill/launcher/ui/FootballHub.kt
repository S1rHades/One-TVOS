package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.Fixtures
import app.pill.launcher.LeagueTable
import app.pill.launcher.Match
import app.pill.launcher.Side
import app.pill.launcher.Team
import app.pill.launcher.bannerTitle
import app.pill.launcher.shortCompetition
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.util.Locale

private val ChipShape = RoundedCornerShape(50)
private val RowHeight = 60.dp
private val RowGap = 6.dp

/**
 * Everything the free football data has for the followed teams, in three columns: the
 * teams (Up / Down picks one, a white marker gliding to it), the match centre (the next
 * match with a countdown, and the latest result), and the team with the top of its
 * league's table when there is one. Back closes.
 */
@Composable
fun FootballHub(
    football: List<Fixtures>,
    initial: Team,
    loadTable: suspend (leagueId: String, season: String) -> LeagueTable?,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(football.firstOrNull { it.team.id == initial.id }?.team?.id ?: football.firstOrNull()?.team?.id) }
    val rowFocus = remember { football.associate { it.team.id to FocusRequester() } }
    // Tables already looked up this time the page is open, so moving between teams is instant
    val tables = remember { mutableStateMapOf<String, LeagueTable?>() }
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1000)
            value = Instant.now()
        }
    }

    OverlayPage(onDismiss = onDismiss, width = 880.dp, framed = false) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { rowFocus[selected]?.requestFocus() }
        }
        val f = football.firstOrNull { it.team.id == selected } ?: return@OverlayPage
        PageTitle(
            "Football",
            listOfNotNull(
                "TheSportsDB" + if (f.viaMirror) " via the GitHub copy" else "",
                f.fetchedAt.takeIf { it != Instant.EPOCH }?.let { "updated " + ageText(it).lowercase() },
                if (football.size > 1) "Up / Down for other teams" else null,
                "Back to close",
            ).joinToString(" · "),
            Modifier.reveal(0),
        )
        // The table for the competition of the next match, else the latest one
        val match = listOfNotNull(f.next, f.last).firstOrNull { it.leagueId != null && it.season != null }
        val key = match?.let { "${it.leagueId}/${it.season}" }
        val table by produceState(key?.let { tables[it] }, key) {
            val m = match ?: return@produceState
            val k = key ?: return@produceState
            if (k !in tables) tables[k] = loadTable(m.leagueId!!, m.season!!)
            value = tables[k]
        }

        Row(Modifier.fillMaxWidth().height(404.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageCard(1, Modifier.width(236.dp).fillMaxHeight()) {
                PageLabel("Your teams", Modifier.padding(bottom = 8.dp))
                TeamList(football, selected, onSelect = { selected = it }, rowFocus)
            }
            PageCard(2, Modifier.weight(1f).fillMaxHeight()) {
                MatchCentre(f, now)
            }
            PageCard(3, Modifier.width(252.dp).fillMaxHeight()) {
                TeamColumn(f, table, loading = key != null && key !in tables, followed = football.map { it.team.id }.toSet())
            }
        }
    }
}

/** The followed teams, one row each; focus picks a team, and a white marker glides to it. */
@Composable
private fun TeamList(football: List<Fixtures>, selected: String?, onSelect: (String) -> Unit, focus: Map<String, FocusRequester>) {
    val index = football.indexOfFirst { it.team.id == selected }.coerceAtLeast(0)
    val y by animateDpAsState((RowHeight + RowGap) * index, Motion.glide(), label = "teamMarker")
    Box {
        Box(
            Modifier
                .offset(y = y)
                .fillMaxWidth()
                .height(RowHeight)
                .shadow(10.dp, RoundedCornerShape(14.dp))
                .background(Palette.Focus, RoundedCornerShape(14.dp)),
        )
        Column(verticalArrangement = Arrangement.spacedBy(RowGap)) {
            for (f in football) {
                val on = f.team.id == selected
                val fg by animateColorAsState(if (on) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "teamText")
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .focusRequester(focus.getValue(f.team.id))
                        // Moving onto a team shows it; there is nothing else to choose
                        .onFocusChanged { if (it.isFocused) onSelect(f.team.id) }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(f.team.id) }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // A dark disc behind the crest, so a white crest (or its stand-in) shows on the white marker
                    Box(Modifier.size(36.dp).background(if (on) Palette.Ink.copy(alpha = 0.85f) else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                        Crest(Side(f.team.id, f.team.name, f.team.badge), 28.dp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(f.team.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight(650), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            f.next?.let { "Next: " + it.opponent.name + ", " + kickoffText(it.kickoff, compact = true).substringBefore(" · ") } ?: "No match scheduled",
                            color = fg.copy(alpha = 0.72f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** The next match, large, with a countdown; then the latest result. */
@Composable
private fun MatchCentre(f: Fixtures, now: Instant) {
    PageLabel("Next match")
    val next = f.next
    if (next == null) {
        Text("No match scheduled yet", color = Palette.TextSecondary, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
    } else {
        Text(shortCompetition(next.competition), color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        BigTeams(next, score = false)
        Spacer(Modifier.height(6.dp))
        Text(
            listOfNotNull(kickoffText(next.kickoff, compact = false), next.venue).joinToString(" · "),
            color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        next.kickoff?.let { k ->
            val left = Duration.between(now, k)
            if (!left.isNegative) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val parts = if (left.toDays() >= 1) {
                        listOf(left.toDays() to (if (left.toDays() == 1L) "day" else "days"), left.toHours() % 24 to "hours", left.toMinutes() % 60 to "min")
                    } else {
                        listOf(left.toHours() to "hours", left.toMinutes() % 60 to "min", left.seconds % 60 to "sec")
                    }
                    for ((n, unit) in parts) CountBox(n, unit)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x24FFFFFF)))
    Spacer(Modifier.height(12.dp))
    PageLabel("Latest result")
    val last = f.last
    if (last == null) {
        Text("No recent result", color = Palette.TextSecondary, fontSize = 17.sp, modifier = Modifier.padding(top = 8.dp))
    } else {
        Spacer(Modifier.height(8.dp))
        BigTeams(last, score = true)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            last.outcome?.let { Outcome(it) }
            Spacer(Modifier.width(8.dp))
            Text(
                listOf(shortCompetition(last.competition), kickoffText(last.kickoff, compact = false).substringBefore(" · ")).joinToString(" · "),
                color = Palette.TextSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CountBox(n: Long, unit: String) {
    Column(
        Modifier
            .width(66.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1FFFFFFF))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("$n", style = TextStyle(fontFamily = FlexDisplay, color = Palette.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight(300)), maxLines = 1)
        Text(unit, color = Palette.TextSecondary, fontSize = 12.sp, maxLines = 1)
    }
}

/** The team: its crest and where it stands, and the top of its league's table. */
@Composable
private fun TeamColumn(f: Fixtures, table: LeagueTable?, loading: Boolean, followed: Set<String>) {
    PageLabel("Team")
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(Color(0x1FFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
            Crest(Side(f.team.id, f.team.name, f.team.badge), 44.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(f.team.name, color = Palette.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight(650), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val ours = table?.rows?.firstOrNull { it.teamId == f.team.id }
        Text(
            listOfNotNull(
                (table?.league ?: f.team.league)?.let { shortCompetition(it) },
                ours?.let { "${ordinal(it.rank)}, ${it.points} pts" },
            ).joinToString(" · ").ifBlank { f.team.country ?: "" },
            color = Palette.TextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(14.dp))
    val rows = table?.rows
    when {
        rows != null -> {
            for (row in rows) {
                val highlight = row.teamId in followed
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (highlight) Color(0x26FFFFFF) else Color.Transparent)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${row.rank}", color = Palette.TextSecondary, fontSize = 13.sp, modifier = Modifier.width(18.dp))
                    Crest(Side(row.teamId, row.team, row.badge), 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(row.team, color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = if (highlight) FontWeight(650) else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${row.points}", color = Palette.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight(650))
                }
            }
            Text("Top five from the free data", color = Palette.TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        }
        loading -> Text("Looking for the table…", color = Palette.TextSecondary, fontSize = 14.sp)
        else -> Text("No table for this competition in the free data", color = Palette.TextSecondary, fontSize = 14.sp)
    }
}

/** 1st, 2nd, 3rd, 4th… */
fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}

/** Both teams large, with crests, and the score once there is one. */
@Composable
private fun BigTeams(match: Match, score: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crest(match.home, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(match.home.name, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight(650), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(
            if (score && match.homeScore != null && match.awayScore != null) "  ${match.homeScore} – ${match.awayScore}  " else "  v  ",
            color = if (score) Palette.TextPrimary else Palette.TextTertiary, fontSize = 22.sp, fontWeight = if (score) FontWeight(650) else FontWeight.Normal,
        )
        Text(match.away.name, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight(650), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(10.dp))
        Crest(match.away, 32.dp)
    }
}

@Composable
private fun Outcome(outcome: Char) {
    val color = when (outcome) { 'W' -> Color(0xFF51CF66); 'L' -> Color(0xFFFF6B6B); else -> Color(0xFFFFD43B) }
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(outcome.toString(), color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * Shown just before a followed team kicks off: who, how soon, and the match. Information
 * only, and it never takes focus: Back hides it, and it goes by itself after 30 seconds.
 */
@Composable
fun KickoffBanner(team: Team, match: Match, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val now by produceState(Instant.now()) {
        while (true) {
            delay(1000)
            value = Instant.now()
        }
    }
    Row(
        modifier
            .glass(ChipShape)
            .background(Color(0x3351CF66), ChipShape)
            .padding(start = 14.dp, end = 26.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(match.ours, 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f, fill = false)) {
            Text(bannerTitle(team, match, now), color = Palette.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "${match.home.name} v ${match.away.name} · ${shortCompetition(match.competition)}",
                color = Palette.TextSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(20.dp))
        Text("Back hides this", style = TextStyle(fontFamily = Flex, color = Palette.TextTertiary, fontSize = 14.sp), maxLines = 1)
    }
    LaunchedEffect(match.id) {
        delay(30_000)
        onDismiss()
    }
}
