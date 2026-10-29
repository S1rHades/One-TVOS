package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
private val BoxShape = RoundedCornerShape(20.dp)

/**
 * Everything the free football data has for the followed teams: for the chosen team its
 * next match (with a countdown on match day) and latest result, and the top of its
 * league's table when there is one. Left / Right picks the team; Back closes.
 */
@Composable
fun FootballHub(
    football: List<Fixtures>,
    initial: Team,
    loadTable: suspend (leagueId: String, season: String) -> LeagueTable?,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(football.firstOrNull { it.team.id == initial.id }?.team?.id ?: football.firstOrNull()?.team?.id) }
    val chipFocus = remember { football.associate { it.team.id to FocusRequester() } }
    // Tables already looked up this time the page is open, so moving between teams is instant
    val tables = remember { mutableStateMapOf<String, LeagueTable?>() }

    OverlayPage(onDismiss = onDismiss, width = 880.dp) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { chipFocus[selected]?.requestFocus() }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for (f in football) {
                TeamChip(
                    team = f.team,
                    selected = f.team.id == selected,
                    onSelect = { selected = f.team.id },
                    modifier = Modifier.focusRequester(chipFocus.getValue(f.team.id)),
                )
            }
        }
        val f = football.firstOrNull { it.team.id == selected } ?: return@OverlayPage
        val now by produceState(Instant.now()) {
            while (true) {
                delay(1000)
                value = Instant.now()
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Column(Modifier.weight(1.25f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                InfoBox("Next match") {
                    val next = f.next
                    if (next == null) {
                        Text("No match scheduled yet", color = Palette.TextSecondary, fontSize = 17.sp)
                    } else {
                        Text(shortCompetition(next.competition), color = Palette.TextTertiary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        BigTeams(next, score = false)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOfNotNull(kickoffText(next.kickoff, compact = false), next.venue).joinToString(" · "),
                            color = Palette.TextPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        next.kickoff?.let { k ->
                            val left = Duration.between(now, k)
                            if (!left.isNegative && left < Duration.ofHours(24)) {
                                val h = left.toHours(); val m = left.toMinutes() % 60; val s = left.seconds % 60
                                Text(
                                    "Kick-off in " + (if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%d:%02d", m, s)),
                                    color = Color(0xFF8CE99A), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
                InfoBox("Latest result") {
                    val last = f.last
                    if (last == null) {
                        Text("No recent result", color = Palette.TextSecondary, fontSize = 17.sp)
                    } else {
                        Text(shortCompetition(last.competition), color = Palette.TextTertiary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(6.dp))
                        BigTeams(last, score = true)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            last.outcome?.let { Outcome(it) }
                            Spacer(Modifier.width(8.dp))
                            Text(kickoffText(last.kickoff, compact = false).substringBefore(" · "), color = Palette.TextSecondary, fontSize = 15.sp)
                        }
                    }
                }
            }
            // The table for the competition of the next match, else the latest one
            val match = listOfNotNull(f.next, f.last).firstOrNull { it.leagueId != null && it.season != null }
            val key = match?.let { "${it.leagueId}/${it.season}" }
            val table by produceState(key?.let { tables[it] }, key) {
                val m = match ?: return@produceState
                val k = key ?: return@produceState
                if (k !in tables) tables[k] = loadTable(m.leagueId!!, m.season!!)
                value = tables[k]
            }
            InfoBox(table?.let { "Top of the table · ${shortCompetition(it.league)}" } ?: "Table", Modifier.weight(1f)) {
                val rows = table?.rows
                when {
                    rows != null -> {
                        TableHeader()
                        for (row in rows) {
                            TableLine(row.rank, Side(row.teamId, row.team, row.badge), row.played, row.goalDifference, row.points, highlight = football.any { it.team.id == row.teamId })
                        }
                        Text("The free data has the first five places", color = Palette.TextTertiary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                    key != null && key !in tables -> Text("Looking for the table…", color = Palette.TextSecondary, fontSize = 15.sp)
                    else -> Text("No table for this competition in the free data", color = Palette.TextSecondary, fontSize = 15.sp)
                }
            }
        }
        PageFooter(
            listOfNotNull(
                "TheSportsDB" + if (f.viaMirror) " via the GitHub copy" else "",
                f.fetchedAt.takeIf { it != Instant.EPOCH }?.let { "updated " + ageText(it).lowercase() },
                if (football.size > 1) "Left / Right for other teams" else null,
                "Back to close",
            ).joinToString(" · "),
            Modifier.padding(top = 14.dp),
        )
    }
}

@Composable
private fun TeamChip(team: Team, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(
        when {
            focused -> Palette.Focus
            selected -> Color(0x40FFFFFF)
            else -> Color(0x14FFFFFF)
        },
        tween(140), label = "chipBg",
    )
    val fg = if (focused) Palette.Ink else Palette.TextPrimary
    Row(
        modifier
            .clip(ChipShape)
            .background(bg)
            // Moving onto a team shows it; there is nothing else to choose
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onSelect() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onSelect)
            .padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Crest(Side(team.id, team.name, team.badge), 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(team.name, color = fg, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun InfoBox(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(BoxShape)
            .background(Color(0x12FFFFFF))
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .heightIn(min = 120.dp),
    ) {
        PageLabel(title, Modifier.padding(bottom = 8.dp))
        content()
    }
}

/** Both teams large, with crests, and the score once there is one. */
@Composable
private fun BigTeams(match: Match, score: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crest(match.home, 34.dp)
        Spacer(Modifier.width(10.dp))
        Text(match.home.name, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Text(
            if (score && match.homeScore != null && match.awayScore != null) "  ${match.homeScore} – ${match.awayScore}  " else "  v  ",
            color = if (score) Palette.TextPrimary else Palette.TextTertiary, fontSize = 22.sp, fontWeight = if (score) FontWeight.Bold else FontWeight.Normal,
        )
        Text(match.away.name, color = Palette.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(10.dp))
        Crest(match.away, 34.dp)
    }
}

@Composable
private fun Outcome(outcome: Char) {
    val color = when (outcome) { 'W' -> Color(0xFF51CF66); 'L' -> Color(0xFFFF6B6B); else -> Color(0xFFFFD43B) }
    Box(Modifier.size(22.dp).clip(RoundedCornerShape(7.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(outcome.toString(), color = Palette.Ink, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TableHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        for ((text, width) in listOf("#" to 24, "Team" to 0, "P" to 30, "GD" to 38, "Pts" to 38)) {
            Text(
                text, color = Palette.TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = if (width == 0) Modifier.weight(1f) else Modifier.width(width.dp),
            )
        }
    }
}

@Composable
private fun TableLine(rank: Int, side: Side, played: Int, gd: Int, points: Int, highlight: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (highlight) Color(0x2E51CF66) else Color.Transparent).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.width(24.dp))
        Crest(side, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(side.name, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("$played", color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.width(30.dp))
        Text(if (gd > 0) "+$gd" else "$gd", color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.width(38.dp))
        Text("$points", color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(38.dp))
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
