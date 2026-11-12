package app.pill.launcher.ui

import app.pill.launcher.ltr
import app.pill.launcher.rowStep
import app.pill.launcher.tr
import android.text.format.DateFormat
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import app.pill.launcher.Fixtures
import app.pill.launcher.Match
import app.pill.launcher.MatchDayMode
import app.pill.launcher.MatchPhase
import app.pill.launcher.Side
import app.pill.launcher.Team
import app.pill.launcher.cardTeams
import app.pill.launcher.matchDayApplies
import app.pill.launcher.matchPhase
import app.pill.launcher.FootballMoment
import app.pill.launcher.MomentKind
import app.pill.launcher.momentText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import kotlin.random.Random
import app.pill.launcher.shortCode
import app.pill.launcher.shortCompetition
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// The football card: the followed teams in turn, match day, goals and wins.

private val AlgeriaGreen = Color(0xFF006633)
private val AlgeriaRed = Color(0xFFD21034)

/**
 * The followed teams' fixtures, one team at a time: every 20 seconds the card moves to the
 * next team. Each shows its next fixture (competition, teams with crests, local kick-off
 * time) and its latest result with W / D / L. On a match day the card stays on the teams
 * playing that day and counts down to kick-off; a match being played shows its score and
 * a pulsing LIVE. Without data it says so; a saved copy is marked when it's old.
 */
@Composable
internal fun FootballCard(
    football: List<Fixtures>,
    matchDay: MatchDayMode,
    compact: Boolean,
    modifier: Modifier,
    onClick: ((Team) -> Unit)?,
    moment: FootballMoment? = null,
    /** False on the idle screen, where the turn line is left out (nothing moves there). */
    animate: Boolean = true,
) {
    val zone = ZoneId.systemDefault()
    // Ticks every second while a countdown is on screen, otherwise twice a minute
    val now by produceState(Instant.now(), football, matchDay) {
        while (true) {
            value = Instant.now()
            val counting = football.any { f ->
                matchDayApplies(f.team, matchDay) && matchPhase(f, value, zone).let { it == MatchPhase.Today || it == MatchPhase.Soon }
            }
            delay(if (counting) 1000L - System.currentTimeMillis() % 1000L else 30_000L)
        }
    }
    // On a match day the card turns between the teams that play; Left / Right still reach
    // every followed team, after them, and the card comes back to the match on its own
    val matchTeams = cardTeams(football, matchDay, now, zone)
    val shown = matchTeams + football.filter { f -> matchTeams.none { it.team.id == f.team.id } }
    // Which team is on show. Every 20 seconds the next takes over, a line along the bottom
    // filling up until then; while the card has focus it waits, and Left / Right flip by hand.
    var turn by remember { mutableIntStateOf(0) }
    var cardFocused by remember { mutableStateOf(false) }
    val count = shown.size
    val autoCount = matchTeams.size.coerceAtMost(count)
    val index = if (count == 0) 0 else turn.mod(count)
    var turnProgress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(count, autoCount, turn, cardFocused, moment) {
        // A team flipped to by hand outside the match day's: back to the match after a turn
        val away = index >= autoCount
        if ((autoCount < 2 && !away) || moment != null) {
            turnProgress = 0f
            return@LaunchedEffect
        }
        if (cardFocused) return@LaunchedEffect
        // Frame by frame from where it got to; an endless animation as far as Compose is concerned
        val from = turnProgress
        var start = -1L
        while (true) {
            val p = withInfiniteAnimationFrameMillis { ms ->
                if (start < 0) start = ms
                from + (ms - start) / TeamTurnMs.toFloat()
            }
            if (p >= 1f) {
                turnProgress = 0f
                turn = if (index + 1 >= autoCount) 0 else index + 1
                break
            }
            turnProgress = p
        }
    }
    val flip = { by: Int ->
        val to = index + by
        if (count > 1 && to in 0 until count) {
            turn = to
            true
        } else {
            false
        }
    }
    val keys: (KeyEvent) -> Boolean = { event ->
        val by = when (event.key) {
            Key.DirectionLeft -> rowStep(-1)
            Key.DirectionRight -> rowStep(1)
            else -> 0
        }
        // Past the first or last team, Left / Right move on to the next widget as usual
        when {
            by == 0 || count < 2 || (index + by) !in 0 until count -> false
            event.type == KeyEventType.KeyDown -> {
                flip(by)
                turnProgress = 0f
                true
            }
            else -> true
        }
    }
    // A goal or a win brings its team forward for as long as it lasts
    val celebrated = moment?.let { m -> football.firstOrNull { it.team.id == m.team.id } }
    val current = celebrated ?: shown.getOrNull(index)
    val phase = current?.takeIf { matchDayApplies(it.team, matchDay) }?.let { matchPhase(it, now, zone) } ?: MatchPhase.None
    val countdown = phase == MatchPhase.Today || phase == MatchPhase.Soon
    // The green flash: up at once on a goal, then easing back over a few seconds
    val flash = remember { Animatable(0f) }
    LaunchedEffect(moment) {
        if (moment == null) { flash.snapTo(0f); return@LaunchedEffect }
        repeat(if (moment.kind == MomentKind.Goal) 2 else 1) {
            flash.animateTo(1f, tween(180, easing = Motion.EnterEasing))
            flash.animateTo(0.35f, tween(700, easing = Motion.ExitEasing))
        }
        flash.animateTo(0f, tween(2400, easing = LinearEasing))
    }
    WidgetCard(
        accent = if (moment != null) GoalGreen else if (phase == MatchPhase.Live) AlgeriaRed else AlgeriaGreen,
        modifier = modifier,
        onClick = current?.let { c -> onClick?.let { open -> { open(c.team) } } },
        wash = if (countdown || phase == MatchPhase.Live || moment != null) 0.42f else 0.2f,
        flash = 0.5f * flash.value,
        onKey = keys,
        onFocusChange = { cardFocused = it },
    ) {
        // The card's top line keeps clear of the team dots in its top right corner
        CompositionLocalProvider(LocalLabelEndRoom provides if (count > 1 && moment == null) 26.dp else 0.dp) {
        Crossfade(current, animationSpec = tween(600), label = "footballTeam", modifier = Modifier.align(Alignment.Center)) { f ->
            Box(Modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.CenterStart) {
                when {
                    f == null -> Text(tr("Loading fixtures…"), style = lineStyle(15.sp, Palette.TextPrimary), maxLines = 1)
                    moment != null && f.team.id == moment.team.id -> MomentFixtures(moment, f, now, compact)
                    else -> TeamFixtures(f, if (f.team.id == current?.team?.id) phase else MatchPhase.None, now, compact)
                }
            }
        }
        }
        if (moment?.kind == MomentKind.Win) Confetti(moment.at, Modifier.matchParentSize())
        if (count > 1 && moment == null) {
            PageDots(count, index, Modifier.align(Alignment.TopEnd))
            if (animate) TurnLine({ turnProgress }, Modifier.align(Alignment.BottomCenter).offset(y = 8.dp))
        }
    }
}

/** How long each followed team stays on the football card. */
private const val TeamTurnMs = 20_000

/**
 * A hairline along the bottom of the card filling up until the next team takes over.
 * Read in the draw phase, so it moves without recomposing the card.
 */
@Composable
private fun TurnLine(progress: () -> Float, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(3.dp)) {
        val r = size.height / 2
        drawRoundRect(Color(0x24FFFFFF), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r))
        val p = progress().coerceIn(0f, 1f)
        if (p > 0f) {
            drawRoundRect(
                Color(0x99FFFFFF),
                size = Size(size.width * p, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
        }
    }
}

private val GoalGreen = Color(0xFF2B8A3E)
private val GoalText = Color(0xFF8CE99A)

/**
 * The card during a goal or a win: the same three lines as a live match, so nothing
 * shifts. The label says what happened, the score flips to the new one, and the last
 * line names the team and minute. Every line is one line, cut short with an ellipsis.
 */
@Composable
private fun MomentFixtures(m: FootballMoment, f: Fixtures, now: Instant, compact: Boolean) {
    val match = listOfNotNull(f.last, f.next).firstOrNull { it.id == m.match.id } ?: m.match
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        CardLabel(
            when (m.kind) {
                MomentKind.Goal -> if (compact) tr("Goal") else tr("Goal · ") + shortCompetition(match.competition)
                MomentKind.Win -> if (compact) tr("Full time") else tr("Full time · ") + shortCompetition(match.competition)
            },
            color = GoalText,
        )
        Teams(match, compact, showScore = true)
        FitText(
            momentText(m, compact),
            style = lineStyle(14.sp, GoalText, 640),
            maxLines = 1,
            shorter = listOf(momentText(m, true)),
        )
    }
}

/**
 * A short burst of confetti for a win, falling from the top of the card and fading out
 * within two seconds. It is drawn inside the card, so none of it leaves the card's edge.
 */
@Composable
private fun Confetti(seed: Instant, modifier: Modifier) {
    val progress = remember(seed) { Animatable(0f) }
    LaunchedEffect(seed) { progress.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val pieces = remember(seed) {
        val r = Random(seed.toEpochMilli())
        List(26) { Triple(r.nextFloat(), r.nextFloat() * 0.35f, r.nextInt(ConfettiColors.size)) to (r.nextFloat() * 2f - 1f) }
    }
    if (progress.value >= 1f) return
    Canvas(modifier.clipToBounds()) {
        val t = progress.value
        val fade = if (t < 0.7f) 1f else 1f - (t - 0.7f) / 0.3f
        for ((piece, sway) in pieces) {
            val (x, delayAt, colour) = piece
            val local = ((t - delayAt) / (1f - delayAt)).coerceIn(0f, 1f)
            if (local <= 0f) continue
            val cx = size.width * x + sway * 18.dp.toPx() * sin(local * PI.toFloat() * 2f)
            val cy = -6.dp.toPx() + (size.height + 12.dp.toPx()) * local
            val w = 6.dp.toPx()
            val h = 3.dp.toPx()
            drawRect(
                ConfettiColors[colour].copy(alpha = 0.9f * fade),
                topLeft = Offset(cx - w / 2, cy - h / 2),
                size = Size(w, h * (0.6f + 0.4f * cos(local * 9f + x * 6f).let { it * it })),
            )
        }
    }
}

private val ConfettiColors = listOf(Color(0xFF51CF66), Color.White, Color(0xFFFFD43B), Color(0xFFFF6B6B), Color(0xFF74C0FC))

@Composable
private fun TeamFixtures(f: Fixtures, phase: MatchPhase, now: Instant, compact: Boolean) {
    val live = listOfNotNull(f.last, f.next).firstOrNull { it.isLive(now) }
    val upcoming = f.next?.takeIf { it != live }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        when {
            live != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot()
                    Spacer(Modifier.width(6.dp))
                    CardLabel(tr("Live · ") + shortCompetition(live.competition), color = Color(0xFFFF8A9B), shorter = listOf(tr("Live")))
                }
                Teams(live, compact, showScore = true)
                FitText(
                    (if (f.team.isAlgeria) tr("Les Fennecs") else f.team.name) + tr(" are playing now"),
                    style = lineStyle(14.sp, Palette.TextTertiary),
                    maxLines = 1,
                    shorter = listOf(tr("Playing now"), ""),
                )
            }
            upcoming != null && (phase == MatchPhase.Today || phase == MatchPhase.Soon) -> MatchDayCountdown(upcoming, now, compact)
            upcoming != null -> {
                CardLabel(tr("Next · %1\$s", shortCompetition(upcoming.competition)), shorter = listOf(tr("Next")))
                Teams(upcoming, compact, showScore = false)
                FitText(
                    kickoffText(upcoming.kickoff, compact),
                    style = lineStyle(15.sp, Palette.TextPrimary, 560),
                    shorter = listOf(kickoffText(upcoming.kickoff, true), kickoffText(upcoming.kickoff, true).substringAfter(" · ")),
                )
            }
            else -> {
                CardLabel(if (f.team.isAlgeria) tr("Les Fennecs") else f.team.name)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Crest(Side(f.team.id, f.team.name, f.team.badge), 26.dp)
                    Spacer(Modifier.width(10.dp))
                    FitText(
                        when {
                            f.fetchedAt != Instant.EPOCH -> tr("No match scheduled yet")
                            f.error != null -> tr("Can't reach match data · see Settings")
                            else -> tr("Loading fixtures…")
                        },
                        style = lineStyle(15.sp, Palette.TextPrimary),
                        maxLines = 2,
                        shorter = listOf(
                            when {
                                f.fetchedAt != Instant.EPOCH -> tr("No match yet")
                                f.error != null -> tr("No match data")
                                else -> tr("Loading…")
                            },
                        ),
                    )
                }
            }
        }
        if (phase != MatchPhase.Today && phase != MatchPhase.Soon) {
            f.last?.takeIf { it != live && it.outcome != null }?.let { LastResult(it, compact, Modifier.onlyIfRoom()) }
        }
        if (f.fromCache && f.fetchedAt != Instant.EPOCH && Duration.between(f.fetchedAt, now) > Duration.ofHours(6)) {
            Text(tr("Offline · saved ") + savedText(f.fetchedAt), style = lineStyle(12.sp, Palette.TextTertiary), maxLines = 1, modifier = Modifier.onlyIfRoom())
        }
    }
}

/** Match day: "Kick-off in", the time left counting down each second, and who plays when. */
@Composable
private fun MatchDayCountdown(match: Match, now: Instant, compact: Boolean) {
    val left = Duration.between(now, match.kickoff ?: now).coerceAtLeast(Duration.ZERO)
    val h = left.toHours()
    val m = left.toMinutes() % 60
    val sec = left.seconds % 60
    val time = if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(Color(0xFF8CE99A), CircleShape))
        Spacer(Modifier.width(6.dp))
        CardLabel(if (compact) tr("Kick-off in") else tr("Match day · kick-off in"), color = Color(0xFF8CE99A))
    }
    Text(
        time,
        style = TextStyle(fontFamily = FlexDisplay, color = Color.White, fontSize = if (compact) 30.sp else 34.sp, fontWeight = FontWeight(300), fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
    FitText(
        "${shortCode(match.home.name)} v ${shortCode(match.away.name)} · " + kickoffText(match.kickoff, compact).substringAfter(" · "),
        style = lineStyle(14.sp, Palette.TextPrimary, 600),
        maxLines = 1,
        shorter = listOf("${shortCode(match.home.name)} v ${shortCode(match.away.name)}"),
    )
}

/** Which of [count] pages is showing, as small dots. */
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(5.dp).background(Color.White.copy(alpha = if (i == current) 0.85f else 0.28f), CircleShape))
        }
    }
}

@Composable
private fun Teams(match: Match, compact: Boolean, showScore: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Crest(match.home, 24.dp)
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        val home = if (compact) shortCode(match.home.name) else match.home.name
        val away = if (compact) shortCode(match.away.name) else match.away.name
        val style = lineStyle(if (compact) 17.sp else 19.sp, Palette.TextPrimary, 640)
        val gap = if (compact) 4.dp else 7.dp
        // Each name gives way (to its short code) before the score does, so long names never push it out
        FitText(home, style = style, shorter = listOf(shortCode(match.home.name)), modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(gap))
        if (showScore && match.homeScore != null && match.awayScore != null) {
            FlipNumber(match.homeScore, style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(700)))
            Text("–", style = style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(700)))
            FlipNumber(match.awayScore, style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(700)))
        } else {
            Text("v", style = style.copy(color = Palette.TextTertiary, fontWeight = FontWeight(500)))
        }
        Spacer(Modifier.width(gap))
        FitText(away, style = style, shorter = listOf(shortCode(match.away.name)), modifier = Modifier.weight(1f, fill = false))
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Crest(match.away, 24.dp)
    }
}

/** A score that flips to its new value: the old number slides up and out, the new one up and in, inside its own box. */
@Composable
private fun FlipNumber(value: Int, style: TextStyle) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(tween(Motion.Enter, easing = Motion.EnterEasing)) { if (up) it else -it } + fadeIn(tween(Motion.Quick))) togetherWith
                (slideOutVertically(tween(Motion.Exit, easing = Motion.ExitEasing)) { if (up) -it else it } + fadeOut(tween(Motion.Quick))) using
                SizeTransform(clip = true)
        },
        label = "score",
        modifier = Modifier.clipToBounds(),
    ) { n ->
        Text("$n", style = style.copy(fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun LastResult(match: Match, compact: Boolean, modifier: Modifier = Modifier) {
    val outcome = match.outcome ?: return
    val color = when (outcome) { 'W' -> Color(0xFF51CF66); 'L' -> Color(0xFFFF6B6B); else -> Color(0xFFFFD43B) }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(18.dp).clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(outcomeLetter(outcome), style = lineStyle(11.sp, Palette.Ink, 800))
        }
        Spacer(Modifier.width(7.dp))
        val opponent = if (compact) shortCode(match.opponent.name) else match.opponent.name
        FitText(
            (if (compact) "" else tr("Last  ")) + ltr("${match.ourScore}–${match.opponentScore} v ${opponent}"),
            style = lineStyle(14.sp, Palette.TextTertiary),
            maxLines = 1,
            shorter = listOf(ltr("${match.ourScore}–${match.opponentScore} v ${shortCode(match.opponent.name)}"), ltr("${match.ourScore}–${match.opponentScore}")),
        )
    }
}

@Composable
private fun LiveDot() {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        0.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "livePulse",
    )
    Box(Modifier.size(8.dp).graphicsLayer { alpha = pulse }.background(Color(0xFFFF4D6D), CircleShape))
}

/** "Today · 18:00", "Tomorrow · 18:00" or "Wed 11 Nov · 18:00", in the TV's time zone. */
@Composable
fun kickoffText(kickoff: Instant?, compact: Boolean): String {
    if (kickoff == null) return tr("Date to be confirmed")
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val zone = ZoneId.systemDefault()
    val local = kickoff.atZone(zone)
    val time = local.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale))
    val days = Duration.between(LocalDate.now(zone).atStartOfDay(zone), local.toLocalDate().atStartOfDay(zone)).toDays()
    val day = when (days) {
        0L -> tr("Today")
        1L -> tr("Tomorrow")
        else -> local.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (compact) "dMMM" else "EEEdMMM"), locale))
    }
    return "$day · $time"
}

private fun savedText(at: Instant): String =
    at.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEdMMM"), Locale.getDefault()))

/** The team's crest from the feed; until it loads (or offline), a flag or initials. */
@Composable
fun Crest(side: Side, size: Dp) {
    var loaded by remember(side.badge) { mutableStateOf(false) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (!loaded) {
            if (side.isAlgeria) {
                AlgeriaFlag(size)
            } else {
                Box(
                    Modifier.size(size).background(Color(0x33FFFFFF), CircleShape).border(1.dp, Color(0x40FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(side.name.take(1).uppercase(), style = lineStyle(12.sp, Palette.TextPrimary, 700))
                }
            }
        }
        if (side.badge != null) {
            AsyncImage(model = side.badge, contentDescription = side.name, onSuccess = { loaded = true }, modifier = Modifier.size(size))
        }
    }
}

/** Algeria's flag in a disc: green and white halves, the red crescent and star. */
@Composable
fun AlgeriaFlag(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size).clip(CircleShape)) {
        val w = this.size.width
        val h = this.size.height
        drawRect(AlgeriaGreen, size = Size(w / 2, h))
        drawRect(Color.White, topLeft = Offset(w / 2, 0f), size = Size(w / 2, h))
        val c = Offset(w * 0.5f, h * 0.5f)
        val outer = h * 0.25f
        val crescent = Path().apply {
            op(
                Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, outer)) },
                Path().apply { addOval(androidx.compose.ui.geometry.Rect(c + Offset(outer * 0.25f, 0f), outer * 0.8f)) },
                PathOperation.Difference,
            )
        }
        drawPath(crescent, AlgeriaRed)
        drawPath(star(c + Offset(outer * 0.42f, 0f), outer * 0.42f), AlgeriaRed)
        drawCircle(Color(0x33000000), radius = h / 2, style = Stroke(1.dp.toPx()))
    }
}

private fun star(center: Offset, radius: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else radius * 0.4f
        // Pointing left, towards the crescent's opening, as on the flag
        val angle = PI + i * PI / 5
        val p = Offset(center.x + (r * cos(angle)).toFloat(), center.y + (r * sin(angle)).toFloat())
        if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
    }
    close()
}

// ---- Headlines ---------------------------------------------------------------------
