package app.pill.launcher.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.Fixtures
import app.pill.launcher.MatchDayMode
import app.pill.launcher.MaxTeams
import app.pill.launcher.News
import app.pill.launcher.PrayerTimes
import app.pill.launcher.Place
import app.pill.launcher.Weather
import app.pill.launcher.DefaultPrayerApi
import app.pill.launcher.cleanMosqueSlug
import app.pill.launcher.cleanPrayerApi
import app.pill.launcher.NewsSource
import app.pill.launcher.NewsTopic
import app.pill.launcher.TeamHit
import app.pill.launcher.withAlgeriaFirst
import java.time.Instant
import app.pill.launcher.LauncherSettings
import app.pill.launcher.WidgetKind
import app.pill.launcher.moved
import app.pill.launcher.withStackToggled
import app.pill.launcher.withStackMoved
import app.pill.launcher.StackTurnSec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

// Settings → Widgets: the pages for football teams, headlines, the weather location, prayer times and the widget stack.

/**
 * The teams the football card follows: Algeria (always first) and up to five more, which
 * Left / Right reorders and OK removes. Below, a search to add one. A team the GitHub copy
 * keeps fixtures for works on any network; others need the TV to reach TheSportsDB.
 */
@Composable
internal fun FootballPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    football: List<Fixtures>,
    searchTeams: suspend (String) -> List<TeamHit>,
    firstModifier: Modifier,
) {
    val teams = settings.teams
    Group("Following · ${teams.size} of $MaxTeams · Left / Right moves") {
        for ((i, team) in teams.withIndex()) {
            key(team.id) {
                val f = football.firstOrNull { it.team.id == team.id }
                val status = when {
                    f == null -> "Loads with the next refresh"
                    f.error != null && f.fetchedAt == Instant.EPOCH -> "Can't get fixtures: ${f.error}"
                    f.error != null -> "Offline · showing the saved copy"
                    f.viaMirror -> "Updated from the GitHub copy"
                    else -> "Updated from TheSportsDB"
                }
                SettingRow(
                    title = team.name,
                    subtitle = if (team.isAlgeria) "Always first · $status" else "$status · OK removes",
                    modifier = if (i == 0) firstModifier else Modifier,
                    onClick = { if (!team.isAlgeria) onChange(settings.copy(teams = teams - team)) },
                    onStep = if (team.isAlgeria) null else { by ->
                        val to = (i + by).coerceIn(1, teams.lastIndex)
                        if (to != i) onChange(settings.copy(teams = teams.toMutableList().apply { add(to, removeAt(i)) }))
                    },
                ) { focused -> if (team.isAlgeria) Check(focused) else Mark("✕", focused) }
            }
        }
    }

    var query by remember { mutableStateOf("") }
    // null while a search is running
    var hits by remember { mutableStateOf<List<TeamHit>?>(emptyList()) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            hits = emptyList()
            return@LaunchedEffect
        }
        hits = null
        delay(450) // wait for typing to pause
        hits = searchTeams(query)
    }
    Group("Add a team") {
        TeamSearchField(query, { query = it }, hint = "Search teams")
        val found = hits
        when {
            query.isBlank() -> Note("A club or a country, e.g. MC Alger, Arsenal, Morocco")
            found == null -> Note("Searching…")
            found.isEmpty() -> Note("No teams found for “${query.trim()}”")
            else -> for (hit in found) {
                key(hit.team.id) {
                    val following = teams.any { it.id == hit.team.id }
                    val full = teams.size >= MaxTeams
                    SettingRow(
                        title = hit.team.name,
                        subtitle = listOfNotNull(
                            hit.team.league,
                            hit.team.country,
                            when {
                                following -> "following"
                                full -> "remove a team first"
                                hit.mirrored -> "works on any network"
                                else -> "needs a direct connection to TheSportsDB"
                            },
                        ).joinToString(" · "),
                        dimmed = following || full,
                        onClick = { if (!following && !full) onChange(settings.copy(teams = withAlgeriaFirst(teams + hit.team))) },
                    ) { focused -> if (following) Check(focused) else Mark("+", focused) }
                }
            }
        }
    }

    Group(null) {
        val modes = MatchDayMode.entries
        val current = modes.indexOf(settings.matchDay)
        SettingRow(
            title = "Match day",
            subtitle = if (settings.matchDay == MatchDayMode.Off) "No countdown or kick-off banner" else "A countdown on the card that day, and a banner 10 minutes before kick-off",
            onClick = { onChange(settings.copy(matchDay = modes[(current + 1) % modes.size])) },
            onStep = { by ->
                val next = (current + by).coerceIn(0, modes.lastIndex)
                if (next != current) onChange(settings.copy(matchDay = modes[next]))
            },
        ) { focused -> Stepper(settings.matchDay.title, current > 0, current < modes.lastIndex, focused) }
    }
}

/** What the headlines widget picks stories about, and how each source is doing. */
@Composable
internal fun NewsPage(settings: LauncherSettings, onChange: (LauncherSettings) -> Unit, news: News?, firstModifier: Modifier) {
    Group("Topics") {
        for ((i, topic) in NewsTopic.entries.withIndex()) {
            val on = topic in settings.newsTopics
            SettingRow(
                title = topic.title,
                subtitle = when (topic) {
                    NewsTopic.Algeria -> "Stories that mention Algeria"
                    NewsTopic.Russia -> "Russia, the Kremlin, RT's Russia section"
                    NewsTopic.World -> "Diplomacy, elections, conflicts, the UN"
                    NewsTopic.AfricaMiddleEast -> "North Africa, the Sahel, the Gulf, the Levant"
                    NewsTopic.Economy -> "Markets, trade, energy, currencies"
                    NewsTopic.Sport -> "Football and other sport"
                } + if (on && settings.newsTopics.size == 1) " · keep at least one" else "",
                modifier = if (i == 0) firstModifier else Modifier,
                // At least one topic stays on; with none the card would only show top stories
                onClick = { if (!on) onChange(settings.copy(newsTopics = settings.newsTopics + topic)) else if (settings.newsTopics.size > 1) onChange(settings.copy(newsTopics = settings.newsTopics - topic)) },
            ) { focused -> Switch(checked = on, focused = focused) }
        }
    }
    Group("Sources") {
        for (source in NewsSource.entries) {
            val count = news?.headlines?.get(source)?.size
            val error = news?.errors?.get(source)
            SettingRow(
                title = source.title + " English",
                subtitle = when {
                    news == null -> "Loading…"
                    error != null && count != null -> "Offline · showing $count saved stories"
                    error != null -> if (source == NewsSource.RT) "Not available on this network" else "Can't load: $error"
                    else -> "$count stories"
                } + if (source == NewsSource.RT) " · read directly only, never through the GitHub copy" else "",
                onClick = {},
            ) { focused -> if (error == null && count != null) Check(focused) else Mark("–", focused) }
        }
    }
}

/**
 * Settings → Widgets → Weather location: Automatic (around the mosque when one is chosen
 * and MAWAQIT says where it is; else the TV's approximate location, from its internet
 * address, which can be a city some way off), or a town found by name, or coordinates
 * typed in. The forecast is for that exact point.
 */
@Composable
internal fun WeatherPlacePage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    weather: Weather?,
    /** Automatic goes by the mosque's place rather than the internet address. */
    nearMosque: Boolean,
    searchPlaces: suspend (String) -> Result<List<Place>>,
    firstModifier: Modifier,
) {
    val chosen = settings.weatherPlace
    Group("Weather for") {
        SettingRow(
            title = "Automatic",
            subtitle = (if (nearMosque) "Around your mosque" else "From the TV's internet address, which may be the nearest city") +
                (if (chosen == null) weather?.place?.takeIf { it.isNotBlank() }?.let { " · now $it" } ?: "" else ""),
            onClick = { onChange(settings.copy(weatherPlace = null)) },
            modifier = firstModifier,
        ) { focused -> if (chosen == null) Check(focused) else Box(Modifier) }
        if (chosen != null) {
            SettingRow(
                title = chosen.name,
                subtitle = listOf(chosen.region, chosen.country).filter { it.isNotBlank() }.distinct().joinToString(", ")
                    .ifEmpty { "Coordinates" } + " · the forecast for this exact place",
                onClick = {},
            ) { focused -> Check(focused) }
        }
    }

    var query by remember { mutableStateOf("") }
    // null while a search is running; a failure when it couldn't be reached
    var found by remember { mutableStateOf<Result<List<Place>>?>(Result.success(emptyList())) }
    LaunchedEffect(query) {
        if (query.isBlank()) {
            found = Result.success(emptyList())
            return@LaunchedEffect
        }
        found = null
        delay(450) // wait for typing to pause
        found = searchPlaces(query)
    }
    Group("Choose a town") {
        TeamSearchField(query, { query = it }, hint = "Town or village, e.g. Beni Saf")
        val result = found
        val places = result?.getOrNull()
        when {
            query.isBlank() -> Note("Any town or village, or a latitude and longitude such as 35.30, -1.38")
            result == null -> Note("Searching…")
            result.isFailure -> Note("Can't reach the place search: ${result.exceptionOrNull()?.let { describe(it) }}")
            places.isNullOrEmpty() -> Note("No places found for “${query.trim()}”. Try its name without accents, or its coordinates")
            else -> for (p in places) {
                key(p.key) {
                    val current = chosen?.key == p.key
                    SettingRow(
                        title = p.name,
                        subtitle = listOf(p.region, p.country).filter { it.isNotBlank() }.distinct().joinToString(", ").ifEmpty { "Coordinates" } +
                            String.format(java.util.Locale.ROOT, " · %.2f, %.2f", p.lat, p.lon),
                        onClick = {
                            onChange(settings.copy(weatherPlace = p))
                            query = ""
                        },
                    ) { focused -> if (current) Check(focused) else Mark("+", focused) }
                }
            }
        }
    }
}

/**
 * Settings → Widgets → Prayer times: the mosque (its name in its MAWAQIT address, typed
 * with the TV keyboard, or pasted as the whole address), what came back and when, Refresh
 * now, and the API's address for anyone running their own copy of it.
 */
@Composable
internal fun PrayersPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    prayers: PrayerTimes?,
    error: String?,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    firstModifier: Modifier,
) {
    Group("Mosque") {
        var mosque by remember(settings.mosque) { mutableStateOf(settings.mosque) }
        Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
            TextRow(
                value = mosque,
                hint = "Mosque ID or address, e.g. 1234",
                onChange = { mosque = it },
                onDone = { onChange(settings.copy(mosque = cleanMosqueSlug(mosque))) },
                // With no mosque yet the page opens on the field; once there is one, on Refresh now (focusing
                // the field would bring up the keyboard each time)
                modifier = if (settings.mosque.isBlank()) firstModifier else Modifier,
            )
        }
        // Which mosque the ID found, by name, so the right one is recognised rather than trusted
        when {
            settings.mosque.isBlank() -> Unit
            refreshing -> Text("Looking up ${settings.mosque}…", color = Palette.TextSecondary, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            prayers != null && prayers.mosque.isNotBlank() -> Text(
                "✓ Found: " + prayers.mosque,
                color = PrayerGreen,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )
        }
        Note("Type the mosque's ID: the number at the bottom left of its MAWAQIT screen. Or the end of its mawaqit.net address, such as obada-benisaf. MAWAQIT doesn't let other apps search its mosques.")
        val status = when {
            settings.mosque.isBlank() -> "Type the mosque, then Done"
            refreshing -> "Asking MAWAQIT…"
            prayers == null && error != null -> error
            prayers == null -> "Loads with the next refresh"
            error != null -> "Showing times saved " + ageText(prayers.fetchedAt).lowercase() + " · " + error
            prayers.fromCache -> "Saved " + ageText(prayers.fetchedAt).lowercase() + " · refreshes after midnight"
            else -> "Updated " + ageText(prayers.fetchedAt).lowercase() + " · refreshes after midnight"
        }
        SettingRow(
            title = "Refresh now",
            subtitle = (prayers?.mosque?.ifBlank { null }?.let { "$it · " } ?: "") + status,
            // Even while it's asking: a slow answer shouldn't make the row feel dead, and a press starts afresh
            onClick = onRefresh,
            enabled = settings.mosque.isNotBlank(),
            modifier = if (settings.mosque.isBlank()) Modifier else firstModifier,
        ) { focused -> RefreshIcon(if (focused) Palette.Ink else Palette.TextTertiary, 22.dp, spinning = refreshing) }
    }
    // Mosques that worked before, by name: one press to go back to one
    val recent = settings.recentMosques.filter { it.first != settings.mosque }
    if (recent.isNotEmpty()) {
        Group("Recent") {
            for ((slug, name) in recent) {
                key(slug) {
                    SettingRow(
                        title = name,
                        subtitle = if (slug.all { it.isDigit() }) "ID $slug" else slug,
                        onClick = { onChange(settings.copy(mosque = slug)) },
                    ) { focused -> Chevron(focused) }
                }
            }
        }
    }
    // Hardly anyone needs the API address: it stays folded unless it was changed
    Group(null) {
        Advanced(
            summary = if (settings.prayerApi == DefaultPrayerApi) "API address · the community MAWAQIT API" else "API address · your own copy",
            startOpen = settings.prayerApi != DefaultPrayerApi,
        ) {
            var api by remember(settings.prayerApi) { mutableStateOf(settings.prayerApi) }
            Box(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                TextRow(
                    value = api,
                    hint = DefaultPrayerApi,
                    onChange = { api = it },
                    onDone = { onChange(settings.copy(prayerApi = cleanPrayerApi(api))) },
                )
            }
            Note(
                if (settings.prayerApi == DefaultPrayerApi) "The community MAWAQIT API, used when the mosque's own page can't be read. Change it only if you run your own copy."
                else "Your own copy of the API. Clear the field for the community one.",
            )
        }
    }
}

private val StackOkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.ButtonA)

/**
 * Settings → Widgets → Widget stack: which widgets take turns in the stack, and in what
 * order. OK adds or removes one. Moving works like moving a favourite: hold OK (or press
 * Menu) on one in the stack, then Left / Right (or Up / Down) moves it, and OK or Back
 * puts it down. The home screen beside the panel shows the focused one.
 */
@Composable
internal fun StackPage(
    settings: LauncherSettings,
    onChange: (LauncherSettings) -> Unit,
    moving: WidgetKind?,
    onMoving: (WidgetKind?) -> Unit,
    onPreview: (WidgetKind?) -> Unit,
    firstModifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var holdJob by remember { mutableStateOf<Job?>(null) }
    // A move started by holding OK: the release that follows doesn't put it down
    var heldMove by remember { mutableStateOf(false) }
    val inStack = settings.stack
    val others = WidgetKind.entries.filter { it != WidgetKind.Stack && it !in inStack }
    val label = when {
        moving != null -> "Moving ${moving.title} · Left / Right, then OK"
        inStack.isEmpty() -> "Empty · OK adds a widget"
        else -> "${inStack.size} take turns · hold OK to move one"
    }
    Group(label) {
        for (kind in inStack + others) {
            key(kind) {
                val on = kind in inStack
                val position = inStack.indexOf(kind)
                val onHome = !on && kind in settings.widgets
                SettingRow(
                    title = kind.title,
                    subtitle = when {
                        moving == kind -> "Position ${position + 1} of ${inStack.size}"
                        on -> "Position ${position + 1} of ${inStack.size}" + if (inStack.size > 1) " · hold OK to move" else ""
                        onHome -> "On the home screen by itself · OK moves it into the stack"
                        else -> kind.description
                    },
                    moving = moving == kind,
                    modifier = (if (kind == (inStack + others).first()) firstModifier else Modifier)
                        .onFocusChanged { if (it.isFocused) onPreview(kind.takeIf { k -> k in settings.stack }) },
                    onClick = { if (moving == null) onChange(settings.withStackToggled(kind)) },
                    onPreviewKey = { event ->
                        val down = event.type == KeyEventType.KeyDown
                        val up = event.type == KeyEventType.KeyUp
                        val ok = event.key in StackOkKeys
                        when {
                            moving == kind -> {
                                when (event.key) {
                                    Key.DirectionLeft, Key.DirectionUp -> if (down) onChange(settings.withStackMoved(kind, -1))
                                    Key.DirectionRight, Key.DirectionDown -> if (down) onChange(settings.withStackMoved(kind, 1))
                                    else -> if (up && (ok || event.key == Key.Back || event.key == Key.Escape || event.key == Key.ButtonB)) {
                                        if (heldMove) heldMove = false else onMoving(null)
                                    }
                                }
                                true
                            }
                            moving != null || !on -> false
                            event.key == Key.Menu -> { if (down) onMoving(kind); true }
                            ok && down && event.nativeKeyEvent.repeatCount > 0 -> true
                            ok && down -> {
                                // Many remotes never repeat a held key, so the hold is timed here
                                holdJob?.cancel()
                                holdJob = scope.launch {
                                    delay(HoldToMoveMs)
                                    heldMove = true
                                    onMoving(kind)
                                }
                                false
                            }
                            ok && up -> { holdJob?.cancel(); false }
                            else -> false
                        }
                    },
                ) { focused ->
                    if (moving == kind) MoveMarks() else Switch(checked = on, focused = focused)
                }
            }
        }
    }
    Note("The stack shows one widget at a time and slides up to the next every $StackTurnSec seconds. On the home screen, Up / Down flips through it by hand.")
}

private const val HoldToMoveMs = 550L

/** The two round arrows a widget being moved carries, gently nudging apart, as on a favourite being moved. */
@Composable
private fun MoveMarks() {
    val nudge by rememberInfiniteTransition(label = "stackMove").animateFloat(
        0f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "stackMoveNudge",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        for (left in listOf(true, false)) {
            Box(
                Modifier
                    .graphicsLayer { translationX = (if (left) -1f else 1f) * 3.dp.toPx() * nudge }
                    .size(26.dp)
                    .background(Palette.Ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (left) "‹" else "›", color = Palette.Focus, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            if (left) Spacer(Modifier.width(6.dp))
        }
    }
}
