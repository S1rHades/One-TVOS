package app.pill.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.isFocused
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.pill.launcher.ui.LauncherScreen
import app.pill.launcher.ui.Palette
import app.pill.launcher.ui.WeatherIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescription
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders the README screenshots at 1920x1080 (a 1080p TV is 960x540 dp at xhdpi).
 *
 * Not a regression test: run it with the Screenshots workflow, which downloads the
 * background photos into build/screenshot-photos and commits docs/screenshots.
 * App names and icons are generic stand-ins drawn below.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-xhdpi")
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
class Screenshots {

    private val compose = createComposeRule()

    // A TV remote is a D-pad, so the window must start outside touch mode for tiles to
    // take focus. This has to happen before the compose rule launches its activity.
    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(object : ExternalResource() {
            override fun before() = InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        })
        .around(compose)

    private val out = File(System.getProperty("screenshots.out") ?: "build/screenshots")
    private val photos = File(System.getProperty("screenshots.photos") ?: "build/screenshot-photos")

    private val apps = listOf(
        "Cinema" to 0xFFE5484D, "Live TV" to 0xFF3E63DD, "Music" to 0xFFD6409F,
        "Photos" to 0xFFF76B15, "Podcasts" to 0xFF8E4EC6, "Radio" to 0xFF12A594,
        "Maps" to 0xFF0090FF, "News" to 0xFFE54666, "Browser" to 0xFF30A46C,
        "Files" to 0xFFFFB224, "Games" to 0xFF6E56CF, "Fitness" to 0xFF46A758,
        "Kids" to 0xFFFF8B3E, "Store" to 0xFF0D74CE, "Recipes" to 0xFFDC3E42,
        "Travel" to 0xFF00A2C7, "Clock" to 0xFF5B5BD6, "Gallery" to 0xFFAB4ABA,
    ).map { (label, color) -> fakeApp(label, color.toInt()) }

    private val weather = Weather(tempC = 22.4, highC = 25.0, lowC = 16.0, code = 2, isDay = true, place = "Lisbon", lat = 38.72, lon = -9.14)

    /** In TheSportsDB's shape, so the parsing is exercised too. Kick-off three days from now. */
    private val fixtures = run {
        val kickoff = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusDays(3).withHour(17).withMinute(0).withSecond(0).withNano(0)
        fun event(home: String, away: String, homeId: String, awayId: String, league: String, at: String, hs: String?, aws: String?, status: String) =
            """{"strHomeTeam":"$home","strAwayTeam":"$away","idHomeTeam":"$homeId","idAwayTeam":"$awayId","strLeague":"$league",""" +
                """"strTimestamp":"$at","intHomeScore":${hs ?: "null"},"intAwayScore":${aws ?: "null"},"strStatus":"$status"}"""
        parseFixtures(
            """{"events":[${event("Algeria", "Togo", "134516", "136484", "African Cup of Nations Qualifying", kickoff.toString() + ":00", null, null, "NS")}]}""",
            """{"results":[${event("Algeria", "Niger", "134516", "136491", "International Friendlies", "2026-10-06T17:00:00", "\"2\"", "\"1\"", "FT")}]}""",
            java.time.Instant.now(),
        )
    }

    private val favorites = listOf("Cinema", "Live TV", "Music", "Photos", "Podcasts").map(::pkg)

    private fun data(name: String): String = javaClass.classLoader!!.getResource("data/$name")!!.readText()

    private val arsenal = Team("133604", "Arsenal", league = "English Premier League", country = "England")

    /** Algeria with a match [inMinutes] from now (today), and Arsenal from the saved answers. */
    private fun matchDay(inMinutes: Long): List<Fixtures> {
        // The feed's times are UTC; "today" is the TV's own day
        val kickoff = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).plusMinutes(inMinutes).withSecond(0).withNano(0)
        val next = """{"events":[{"idEvent":"77","strHomeTeam":"Algeria","idHomeTeam":"134516","strAwayTeam":"Togo","idAwayTeam":"136484",""" +
            """"strLeague":"African Cup of Nations Qualifying","idLeague":"5520","strSeason":"2027","strTimestamp":"$kickoff:00","strStatus":"NS","strVenue":"Stade Nelson-Mandela"}]}"""
        return listOf(
            parseFixtures(next, data("last-algeria.json"), java.time.Instant.now()),
            parseFixtures(data("next-arsenal.json"), data("last-arsenal.json"), java.time.Instant.now(), arsenal),
        ).map { it.withoutCrests() }
    }

    /** The test image loader turns every URL into the background photo; crests show their stand-ins instead. */
    private fun Fixtures.withoutCrests(): Fixtures {
        fun Match.plain() = copy(home = home.copy(badge = null), away = away.copy(badge = null))
        return copy(team = team.copy(badge = null), next = next?.plain(), last = last?.plain())
    }

    /** A forecast around now, so the hours and days are always current. */
    private val forecastWeather = run {
        val now = java.time.LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
        val today = java.time.LocalDate.now()
        val codes = listOf(2, 2, 1, 0, 0, 0, 2, 3, 61, 61, 3, 2)
        weather.copy(
            forecast = Forecast(
                feelsLikeC = 21.2, humidity = 64, windKmh = 14.0, windFromDeg = 315.0, uvIndex = 3.0,
                hours = (0 until 12).map { i ->
                    HourForecast(now.plusHours(i.toLong()), 22.0 + 3 * Math.sin(i / 3.0), codes[i], true, if (codes[i] == 61) 70 else 5 * (i % 3), null)
                },
                days = (0 until 7).map { i ->
                    DayForecast(today.plusDays(i.toLong()), listOf(2, 61, 0, 0, 2, 61, 2)[i], 25.0 - (i % 3), 16.0 - (i % 2), listOf(10, 70, 0, 0, 20, 60, 10)[i], null)
                },
                source = "Open-Meteo",
                fetchedAt = java.time.Instant.now().minusSeconds(600),
            ),
        )
    }

    private val news = News(
        mapOf(
            NewsSource.AlJazeera to parseRss(data("aljazeera.xml"), NewsSource.AlJazeera),
            NewsSource.RT to parseRss(data("rt-russia.xml"), NewsSource.RT),
        ),
        emptyMap(),
        java.time.Instant.now(),
    )

    /** The pins and settings as the screen last changed them. */
    private var currentPins: List<String> = emptyList()
    private var currentSettings: LauncherSettings = LauncherSettings()

    /** The fixtures on screen; a test changes them to show what a new fetch brings. */
    private var footballNow by mutableStateOf<List<Fixtures>>(emptyList())

    @get:Rule
    val testName = org.junit.rules.TestName()

    // A test that hangs says where: after two minutes, its name and the main thread's stack
    private var watchdog: Thread? = null

    @org.junit.After
    fun stopWatchdog() {
        watchdog?.interrupt()
    }

    @Before
    fun setUp() {
        val main = Thread.currentThread()
        val name = testName.methodName
        watchdog = Thread {
            try {
                Thread.sleep(120_000)
                println("HUNG TEST: $name")
                main.stackTrace.take(40).forEach { println("HUNG   at $it") }
            } catch (_: InterruptedException) {
            }
        }.apply { isDaemon = true; start() }
        out.mkdirs()
        // English unless a test shows another language
        Lang.current = Language.English
        java.util.Locale.setDefault(java.util.Locale.US)
    }

    @org.junit.After
    fun backToEnglish() {
        Lang.current = Language.English
        java.util.Locale.setDefault(java.util.Locale.US)
    }

    @Test
    fun home() {
        show("mountain-lake", pins = favorites)
        compose.onRoot().captureRoboImage(File(out, "home.png").path)
    }

    @Test
    fun pinFromGrid() {
        show("canyon-river", pins = favorites)
        // Down past the chips into the grid (A to Z), to the second row and along to "Maps", then hold OK to pin it
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onRoot().performKeyInput { repeat(2) { pressKey(Key.DirectionRight) } }
        settle()
        // Hold OK the way a remote does: one key-down, a pause, one key-up (no repeats).
        // That opens the app's menu, and the release mustn't pick anything in it.
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodes(isRoot()).onFirst().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        compose.onNodeWithText("Add to Favorites").assertIsDisplayed()
        compose.onNodeWithText("Uninstall").assertIsDisplayed()
        captureScreenRoboImage(File(out, "menu.png").path)
        compose.onNodeWithText("Add to Favorites").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        runCatching { compose.onNodeWithText("Added Maps to Favorites").assertExists() }
            .onFailure { println(compose.onRoot().printToString(maxDepth = 12)); throw it }
        settle()
        compose.onRoot().captureRoboImage(File(out, "pin.png").path)
    }

    /** Menu → Move on the first favourite, then Right twice and OK: it ends up third. */
    @Test
    fun moveFavorite() {
        show("misty-valley", pins = favorites)
        compose.onRoot().performKeyInput { pressKey(Key.Menu) }
        settle()
        compose.onNodeWithText("Move").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionRight)
            pressKey(Key.DirectionRight)
        }
        settle()
        check(currentPins.indexOf(pkg("Cinema")) == 2) { "Cinema should be third: $currentPins" }
        compose.onRoot().captureRoboImage(File(out, "move.png").path)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        check(currentPins.indexOf(pkg("Cinema")) == 2) { "OK should only finish the move: $currentPins" }
    }

    /** The widget's condition icons side by side, for the README. */
    @Test
    fun weatherIcons() {
        compose.setContent {
            Row(
                Modifier.background(Palette.Ink).padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                for ((sky, day) in Sky.entries.map { it to true } + (Sky.Clear to false) + (Sky.PartlyCloudy to false)) {
                    WeatherIcon(sky, day, 72.dp)
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "weather-icons.png").path)
    }

    /** The same icons part way through their motion (a lightning flash included), for the README. */
    @Test
    fun weatherIconsMoving() {
        compose.setContent {
            Column(Modifier.background(Palette.Ink).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (at in listOf(0.25f, 0.42f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        for ((sky, day) in Sky.entries.map { it to true } + (Sky.Clear to false) + (Sky.PartlyCloudy to false)) {
                            WeatherIcon(sky, day, 72.dp, motion = { at })
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage(File(out, "weather-icons-moving.png").path)
    }

    @Test
    fun allApps() {
        show("misty-valley", pins = favorites)
        // Down past the first rows: the clock and favourites scroll away
        compose.onRoot().performKeyInput {
            repeat(3) { pressKey(Key.DirectionDown) }
            repeat(2) { pressKey(Key.DirectionRight) }
        }
        settle()
        compose.onRoot().captureRoboImage(File(out, "all-apps.png").path)
    }

    @Test
    fun idle() {
        show("aurora-sky", pins = favorites)
        // Default idle time is 2 minutes without a key press
        compose.mainClock.advanceTimeBy(125_000)
        // Let the widgets finish gliding to the middle
        compose.mainClock.advanceTimeBy(2_500)
        settle()
        compose.onRoot().captureRoboImage(File(out, "idle.png").path)
    }

    /** Two widgets stretch to fill the row; the weather card names the city. */
    @Test
    fun twoWidgets() {
        show("misty-valley", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Weather)))
        compose.onNodeWithText("Lisbon · Partly cloudy").assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "two-widgets.png").path)
    }

    /** Settings opens on its list of categories; TV & system holds the shortcuts and uninstall. */
    @Test
    fun settingsPages() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("TV & system").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Free up memory").assertIsDisplayed()
        captureScreenRoboImage(File(out, "settings-system.png").path)
        // Uninstall takes two presses before the TV's own confirmation
        compose.onNodeWithText("Uninstall Pill Launcher").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Press OK again to uninstall").assertExists()
    }

    @Test
    fun search() {
        show("pine-forest", pins = favorites)
        fun count(text: String) = compose.onAllNodesWithText(text).fetchSemanticsNodes().size
        val maps = count("Maps")
        val music = count("Music")
        compose.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        // Letters from the strip, no keyboard: M then A leaves the apps with "ma" in their names
        compose.onNodeWithContentDescription("M").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithContentDescription("A").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("ma").assertExists()
        // The matches add Maps to the screen, but not Music
        check(count("Maps") == maps + 1) { "Maps should match" }
        check(count("Music") == music) { "Music shouldn't match" }
        captureScreenRoboImage(File(out, "search.png").path)
    }

    private val barItems = setOf("Home", "Search", "Settings")

    /** What has focus in the launcher's own window, by name. */
    private fun focusedName(): String {
        val n = compose.onAllNodes(isFocused()).fetchSemanticsNodes().first()
        val cd = n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription) { null }?.joinToString()
        val text = n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString()
        return listOfNotNull(cd, text).joinToString(" / ")
    }

    private fun press(key: Key) {
        compose.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(key) }
        settle()
    }

    private fun focusOn(description: String) {
        compose.onAllNodesWithContentDescription(description).onFirst().requestFocus()
        settle()
    }

    /**
     * The D-pad around the home screen: Up from Favorites reaches the widgets, Up again the
     * bar; Down from the bar comes back to the widgets, then Favorites, then All apps.
     */
    @Test
    fun navigationOnHome() {
        show("misty-valley", pins = favorites)
        check(focusedName().contains("Cinema")) { "starts on the first favourite: ${focusedName()}" }
        press(Key.DirectionUp)
        check(focusedName().contains("All caught up")) { "Up from Favorites reaches the notification bar: ${focusedName()}" }
        press(Key.DirectionUp)
        val widget = focusedName()
        check(barItems.none { widget.startsWith(it) } && !widget.contains("Cinema")) { "Up from Favorites reaches a widget: $widget" }
        press(Key.DirectionUp)
        check(barItems.any { focusedName().startsWith(it) } || focusedName().startsWith("Internet")) { "Up from a widget reaches the bar: ${focusedName()}" }
        for (item in listOf("Home", "Search", "Settings")) {
            focusOn(item)
            press(Key.DirectionDown)
            val below = focusedName()
            check(barItems.none { below.startsWith(it) } && !below.contains("Cinema")) { "Down from $item reaches the widgets: $below" }
        }
        press(Key.DirectionDown)
        check(focusedName().contains("All caught up") || focusedName().startsWith("Notifications")) { "Down from the widgets reaches the notification bar: ${focusedName()}" }
        focusOn("Notifications")
        press(Key.DirectionRight)
        check(focusedName().startsWith("Share files")) { "Right from the bell reaches Files: ${focusedName()}" }
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        check(focusedName().contains("All caught up")) { "Left along the bar reaches the message: ${focusedName()}" }
        press(Key.DirectionDown)
        check(focusedName().contains("Cinema") || focusedName().contains("Live TV") || focusedName().contains("Music")) { "Down from the notification bar reaches Favorites: ${focusedName()}" }
        focusOn("Cinema")
        press(Key.DirectionDown)
        check(focusedName().contains("All")) { "Down from Favorites reaches the chips: ${focusedName()}" }
        press(Key.DirectionUp)
        check(focusedName().contains("Cinema")) { "Up from the chips comes back to Favorites: ${focusedName()}" }
        // Left from the first favourite and Right from the last stay put
        press(Key.DirectionLeft)
        check(focusedName().contains("Cinema")) { "Left from the first favourite stays: ${focusedName()}" }
    }

    /**
     * The press budget from the first favourite: Search is one press (the remote's search
     * key), All apps one (Down), and Settings no more than six by the bar. Counts are printed
     * so a change that adds presses shows up in the log before it breaks the budget.
     */
    @Test
    fun pressBudget() {
        show("misty-valley", pins = favorites)
        // Search: its key
        compose.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(Key.Search) }
        settle()
        compose.onNodeWithText("Search apps").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(800)
        settle()
        // All apps: Down
        focusOn("Cinema")
        press(Key.DirectionDown)
        check(focusedName().contains("All")) { "All apps is one press down: ${focusedName()}" }
        // Settings: Up to the bar, then along it
        focusOn("Cinema")
        var presses = 0
        while (barItems.none { focusedName().startsWith(it) } && !focusedName().startsWith("Internet") && presses < 8) {
            press(Key.DirectionUp); presses++
        }
        while (!focusedName().startsWith("Settings") && presses < 12) {
            press(Key.DirectionRight); presses++
        }
        println("PRESS BUDGET · Search 1 · All apps 1 · Settings $presses")
        check(focusedName().startsWith("Settings") && presses <= 6) { "Settings took $presses presses: ${focusedName()}" }
    }

    /**
     * Solid white means focus and nothing else (the Von Restorff effect only works for one
     * thing at a time): Settings, the app menu and a page each show exactly one solid white
     * shape, the focused item. Counted on the rendered screen in 12 px blocks, so text and
     * thin rings don't count, only filled shapes.
     */
    @Test
    fun oneWhiteThingAtATime() {
        show("pine-forest", pins = favorites)
        fun blobs(name: String): Int {
            val file = File(out, "_$name.png")
            captureScreenRoboImage(file.path)
            val bmp = BitmapFactory.decodeFile(file.path)
            file.delete()
            val n = whiteBlobs(bmp)
            println("WHITE SHAPES · $name: $n")
            return n
        }
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val settingsCount = blobs("settings")
        check(settingsCount == 1) { "Settings shows $settingsCount solid white shapes" }
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(800)
        settle()
        focusOn("Cinema")
        compose.onAllNodes(isFocused()).onFirst().performKeyInput { pressKey(Key.Menu) }
        settle()
        val menuCount = blobs("menu")
        check(menuCount == 1) { "The app menu shows $menuCount solid white shapes" }
    }

    /** Menu (or holding OK) on a widget: its settings, moving it, and removing it, without going through Settings. */
    @Test
    fun widgetMenu() {
        show("misty-valley", pins = favorites, weather = forecastWeather)
        val card = compose.onAllNodes(hasClickAction() and hasText("Lisbon", substring = true)).onFirst()
        card.requestFocus()
        settle()
        card.performKeyInput { pressKey(Key.Menu) }
        settle()
        compose.onNodeWithText("Widget settings").assertExists()
        compose.onNodeWithText("Move left").assertExists()
        compose.onNodeWithText("Remove widget").assertExists()
        captureScreenRoboImage(File(out, "widget-menu.png").path)
        compose.onNodeWithText("Remove widget").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        check(WidgetKind.Weather !in currentSettings.widgets) { "Remove widget turns it off: ${currentSettings.widgets}" }
    }

    /** Connected areas of near-white 12 px blocks at least four blocks big. */
    private fun whiteBlobs(bmp: Bitmap): Int {
        val size = 12
        val cols = bmp.width / size
        val rows = bmp.height / size
        val white = Array(rows) { r ->
            BooleanArray(cols) { c ->
                var count = 0
                for (y in 0 until size step 3) for (x in 0 until size step 3) {
                    val p = bmp.getPixel(c * size + x, r * size + y)
                    if ((p shr 16 and 0xFF) >= 240 && (p shr 8 and 0xFF) >= 240 && (p and 0xFF) >= 240) count++
                }
                count >= 15 // of 16 samples
            }
        }
        val seen = Array(rows) { BooleanArray(cols) }
        var found = 0
        for (r in 0 until rows) for (c in 0 until cols) {
            if (!white[r][c] || seen[r][c]) continue
            var area = 0
            val stack = ArrayDeque(listOf(r to c))
            seen[r][c] = true
            while (stack.isNotEmpty()) {
                val (y, x) = stack.removeLast()
                area++
                for ((dy, dx) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val ny = y + dy
                    val nx = x + dx
                    if (ny in 0 until rows && nx in 0 until cols && white[ny][nx] && !seen[ny][nx]) {
                        seen[ny][nx] = true
                        stack.addLast(ny to nx)
                    }
                }
            }
            if (area >= 4) found++
        }
        return found
    }

    /** With no widget that takes focus, Up from Favorites and Down from the bar skip the widgets. */
    @Test
    fun navigationWithQuietWidgets() {
        show("misty-valley", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Daylight, WidgetKind.System)))
        press(Key.DirectionUp)
        check(focusedName().contains("All caught up")) { "Up from Favorites reaches the notification bar: ${focusedName()}" }
        press(Key.DirectionUp)
        check(barItems.any { focusedName().startsWith(it) } || focusedName().startsWith("Internet")) { "Up again reaches the bar at the top: ${focusedName()}" }
        focusOn("Home")
        press(Key.DirectionDown)
        check(focusedName().startsWith("Notifications")) { "Down from the bar reaches the notification bar: ${focusedName()}" }
    }

    /** Down from the middle widget stops at the notification bar; Down from the bar starts Favorites at the left. */
    @Test
    fun navigationThroughTheNotificationBar() {
        show("misty-valley", pins = favorites)
        // The weather card, in the middle of the three
        // Up past the bar to the widgets, Left to the first, then Right once
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        press(Key.DirectionLeft)
        press(Key.DirectionLeft)
        val first = focusedName()
        press(Key.DirectionRight)
        val middle = focusedName()
        check(middle != first) { "Right moves to the middle widget: $first → $middle" }
        check(!middle.contains("All caught up") && barItems.none { middle.startsWith(it) } && !middle.contains("Cinema")) { "on the middle widget: $middle" }
        press(Key.DirectionDown)
        check(focusedName().contains("All caught up")) { "Down from the middle widget stops at the bar's message: ${focusedName()}" }
        focusOn("Share files")
        press(Key.DirectionDown)
        check(focusedName().contains("Cinema")) { "Down from the bar's right end starts Favorites at the left: ${focusedName()}" }
        focusOn("Notifications")
        press(Key.DirectionDown)
        check(focusedName().contains("Cinema")) { "Down from the bell starts Favorites at the left: ${focusedName()}" }
    }

    /** No favourites and no widget that takes focus: Down from the bar goes to the notification bar, then All apps. */
    @Test
    fun navigationWithNothingAbove() {
        show("misty-valley", pins = emptyList(), settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Daylight)))
        focusOn("Home")
        press(Key.DirectionDown)
        check(focusedName().startsWith("Notifications")) { "Down from the bar reaches the notification bar: ${focusedName()}" }
        press(Key.DirectionDown)
        val below = focusedName()
        check(below.contains("All") || apps.any { below.contains(it.label) }) { "Down from the notification bar reaches All apps: $below" }
    }

    /** In Settings the bar stays at the top; Down from it goes back into the panel, and Home closes it. */
    @Test
    fun navigationInSettings() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val gear = compose.onAllNodesWithContentDescription("Settings").onLast()
        gear.requestFocus()
        settle()
        gear.performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        // The home screen behind keeps its own focused tile; the panel's focus is on its first row
        compose.onNode(isFocused() and (hasText("Home screen") or hasText("Widgets") or hasText("Notifications"))).assertExists()
        compose.onAllNodesWithContentDescription("Home").onLast().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(600)
        settle()
        compose.onNodeWithText("Home screen").assertDoesNotExist()
        check(focusedName().contains("Cinema")) { "Home from Settings goes to the first favourite: ${focusedName()}" }
    }

    /** Opening Settings from the bar, focus goes straight to the panel: it never stops on the page's bar on the way. */
    @Test
    fun barKeepsOutOfTheWayAsAPageOpens() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        repeat(12) {
            compose.mainClock.advanceTimeBy(40)
            compose.waitForIdle()
            val onBar = compose.onAllNodes(isFocused() and (hasContentDescription("Home") or hasContentDescription("Search"))).fetchSemanticsNodes()
            check(onBar.isEmpty()) { "focus stopped on the bar while Settings opened (${it * 40 + 40} ms)" }
        }
        settle()
        compose.onNode(isFocused() and (hasText("Home screen") or hasText("Widgets") or hasText("Notifications"))).assertExists()
    }

    /** A page opened from the bar: Down from the bar reaches the page, Home closes it. */
    @Test
    fun navigationOnAPage() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Internet", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val bar = compose.onAllNodesWithContentDescription("Home").onLast()
        bar.requestFocus()
        settle()
        bar.performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onNode(isFocused() and (hasText("Start test") or hasText("Network settings"))).assertExists()
        compose.onAllNodesWithContentDescription("Home").onLast().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1200)
        settle()
        compose.onNodeWithText("Start test").assertDoesNotExist()
        check(focusedName().contains("Cinema")) { "Home from a page goes to the first favourite: ${focusedName()}" }
    }

    /**
     * The football card with two teams: a line fills along its bottom until the next team takes
     * over; with the card focused, Right and Left flip between the teams by hand.
     */
    @Test
    fun footballCardTurns() {
        show("canyon-river", pins = favorites, football = matchDay(inMinutes = 60 * 24 * 3))
        val card = compose.onNode(hasClickAction() and (hasText("Algeria", substring = true) or hasText("ALG", substring = true) or hasText("Arsenal", substring = true) or hasText("ARS", substring = true)))
        // Which team the card is on (its countdown can tick, so compare teams, not text)
        fun text(): String {
            val t = card.fetchSemanticsNode().config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ").orEmpty()
            return if (t.contains("Algeria") || t.contains("ALG") || t.contains("Fennecs")) "Algeria" else "Arsenal"
        }
        // Part way to the next team
        compose.mainClock.advanceTimeBy(9_000)
        settle()
        card.captureRoboImage(File(out, "football-card.png").path)
        card.requestFocus()
        settle()
        val first = text()
        card.performKeyInput { pressKey(Key.DirectionRight) }
        settle()
        val second = text()
        check(second != first) { "Right shows the other team: $first / $second" }
        card.performKeyInput { pressKey(Key.DirectionLeft) }
        settle()
        check(text() == first) { "Left comes back: ${text()}" }
        // While it has focus the card waits for you
        compose.mainClock.advanceTimeBy(25_000)
        settle()
        check(text() == first) { "no turn while focused: ${text()}" }
    }

    /** On a match day the card stays on the match, but Right still reaches the other team, and it comes back by itself. */
    @Test
    fun footballMatchDaySwitch() {
        val now = java.time.LocalDateTime.now()
        val inMinutes = if (now.plusMinutes(135).toLocalDate() == now.toLocalDate()) 135L else 5L
        show("mountain-lake", pins = favorites, football = matchDay(inMinutes))
        // The card, not the notification bar's line about the same match: the one higher up
        val matcher = hasClickAction() and (hasText("Algeria", substring = true) or hasText("ALG", substring = true) or hasText("Arsenal", substring = true) or hasText("ARS", substring = true))
        fun card() = compose.onAllNodes(matcher).let { all ->
            all[all.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index]
        }
        fun text(): String {
            val t = card().fetchSemanticsNode().config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString(" ").orEmpty()
            return if (t.contains("Algeria") || t.contains("ALG") || t.contains("Fennecs")) "Algeria" else "Arsenal"
        }
        check(text() == "Algeria") { "match day shows the match: ${text()}" }
        card().requestFocus()
        settle()
        card().performKeyInput { pressKey(Key.DirectionRight) }
        settle()
        check(text() == "Arsenal") { "Right reaches the other team on a match day: ${text()}" }
        // Coming back to the match by itself rides the card's 20-second turn, an endless
        // animation, which the test environment holds still; so that part isn't checked here
    }

    /** Left / Right on the prayer card show another prayer's time; it goes back to the next one by itself. */
    @Test
    fun prayerPicking() {
        atTwentyToFour { times ->
            show("canyon-river", pins = favorites, prayers = times, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Prayers), mosque = "moscow-cathedral-mosque"))
            val card = compose.onAllNodes(hasClickAction() and hasText("Maghrib", substring = true)).onFirst()
            card.requestFocus()
            settle()
            compose.onNodeWithText("in 4", substring = true).assertExists()
            // Left: Asr (13:49), which has passed
            card.performKeyInput { pressKey(Key.DirectionLeft) }
            settle()
            compose.onNodeWithText("min ago", substring = true).assertExists()
            compose.onNodeWithText("in 4", substring = true).assertDoesNotExist()
            // Ten seconds later it's back on Maghrib, counting down
            compose.mainClock.advanceTimeBy(11_000)
            settle()
            compose.onNodeWithText("in 4", substring = true).assertExists()
        }
    }

    /** Settings from the bar on a page opens over the page; closing Settings shows the page again. */
    @Test
    fun navigationSettingsOverAPage() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Internet", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(1200)
        settle()
        compose.onAllNodesWithContentDescription("Settings").onLast().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(600)
        settle()
        compose.onNodeWithText("Home screen").assertExists()
        compose.onNodeWithText("Start test").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(800)
        settle()
        compose.onNodeWithText("Home screen").assertDoesNotExist()
        compose.onNodeWithText("Start test").assertExists()
    }

    /** OK on a widget opens its page; closing it puts focus back on that widget. */
    @Test
    fun navigationBackFromAWidgetPage() {
        show("canyon-river", pins = favorites, weather = forecastWeather)
        // Up past the notification bar to the widgets
        press(Key.DirectionUp)
        press(Key.DirectionUp)
        val widget = focusedName()
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(1200)
        settle()
        // Close it the way Back does: the page's own dismiss (Back goes to the dialog)
        compose.onAllNodesWithContentDescription("Home").onLast().let { home ->
            check(compose.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().size == 2) { "a page with its bar opened from $widget" }
        }
        androidx.test.espresso.Espresso.pressBack()
        compose.mainClock.advanceTimeBy(1200)
        settle()
        check(compose.onAllNodesWithContentDescription("Home").fetchSemanticsNodes().size == 1) { "the page closed" }
        check(focusedName() == widget) { "focus back on $widget, not ${focusedName()}" }
    }

    /** Menu → Rename… on the first favourite: the name changes only in the launcher. */
    @Test
    fun renameApp() {
        show("misty-valley", pins = favorites)
        compose.onRoot().performKeyInput { pressKey(Key.Menu) }
        settle()
        compose.onNodeWithText("Rename…").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(hasSetTextAction()).performTextReplacement("Films")
        settle()
        compose.onNodeWithText("Only in Pill Launcher").assertExists()
        captureScreenRoboImage(File(out, "rename.png").path)
        compose.onNodeWithText("Save").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Renamed Cinema to Films").assertExists()
        compose.onAllNodesWithText("Films", substring = false).onFirst().assertExists()
    }

    /** A widget stack beside the clock and weather; Down while it has focus flips to the next widget. */
    @Test
    fun widgetStack() {
        val settings = LauncherSettings(
            weather = WeatherMode.Celsius,
            widgets = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Stack),
            stack = listOf(WidgetKind.Fennecs, WidgetKind.Headlines, WidgetKind.Daylight),
        )
        show("mountain-lake", pins = favorites, settings = settings, news = news)
        val stack = compose.onNodeWithContentDescription("Widget stack", substring = true)
        stack.assertExists()
        stack.requestFocus()
        settle()
        stack.performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onNodeWithContentDescription("Widget stack: Headlines").assertExists()
        compose.onRoot().captureRoboImage(File(out, "widget-stack.png").path)
    }

    /**
     * Settings → Widgets → Widget stack: the home screen stands aside as a live preview,
     * and holding OK on Football, then Right, moves it down the stack.
     */
    @Test
    fun widgetStackSettings() {
        val settings = LauncherSettings(
            weather = WeatherMode.Celsius,
            widgets = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Stack),
            stack = listOf(WidgetKind.Fennecs, WidgetKind.Headlines, WidgetKind.Daylight),
        )
        show("pine-forest", pins = favorites, settings = settings, news = news)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Widgets").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        // The stack's switch, then the row that opens its page
        compose.onAllNodesWithText("Widget stack").onLast().performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val football = compose.onNodeWithText("Football")
        football.requestFocus()
        settle()
        football.performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        football.performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        football.performKeyInput { pressKey(Key.DirectionRight) }
        settle()
        check(currentSettings.stack == listOf(WidgetKind.Headlines, WidgetKind.Fennecs, WidgetKind.Daylight)) { "Football should be second: ${currentSettings.stack}" }
        captureScreenRoboImage(File(out, "widget-stack-settings.png").path)
        football.performKeyInput { pressKey(Key.DirectionCenter) }
        settle()
        check(currentSettings.stack[1] == WidgetKind.Fennecs) { "OK should only put it down: ${currentSettings.stack}" }
    }

    /** A goal between two fetches: the score flips, the card flashes green and names the minute. Long names stay inside. */
    @Test
    fun goalOnTheCard() {
        val kickoff = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC).minusMinutes(70).withSecond(0).withNano(0)
        fun live(us: Int) = parseFixtures(
            """{"events":[{"idEvent":"77","strHomeTeam":"Algeria","idHomeTeam":"134516","strAwayTeam":"Democratic Republic of the Congo","idAwayTeam":"136480",""" +
                """"strLeague":"African Cup of Nations Qualifying","strTimestamp":"$kickoff:00","intHomeScore":"$us","intAwayScore":"1","strStatus":"2H"}]}""",
            data("last-algeria.json"),
            java.time.Instant.now(),
        ).withoutCrests()
        show("misty-valley", pins = favorites, football = listOf(live(1)))
        footballNow = listOf(live(2))
        compose.mainClock.advanceTimeBy(150)
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithText("Goal · Algeria", substring = true).assertExists()
        compose.onRoot().captureRoboImage(File(out, "goal.png").path)
    }

    /** Some of each kind of notification, newest first after any update. */
    private fun sampleNotices(now: java.time.Instant) = listOf(
        Notice("app:1", NoticeKind.App, "New episode", "Season 2 of The Coast is out", now.minusSeconds(5 * 60), pkg = pkg("Cinema")),
        fileNotice("holiday.jpg", 3_400_000, "an iPhone", now.minusSeconds(20 * 60)),
        updatedNotice("3.3", listOf("Notifications under the widgets", "Files from your phone"), now.minusSeconds(2 * 3600)),
        Notice("goal:77:2-1", NoticeKind.Football, "Goal · Algeria 78'", "Algeria 2–1 Togo", now.minusSeconds(26 * 3600), target = "134516"),
    )

    private val newRelease = Release("3.4", "https://example.com/pill-launcher-3.4.apk", 9_100_000, listOf("Widget stack: several widgets in one place", "Rename apps in the launcher"))

    /** A newer release: the newest notice in the bar under the widgets, and Settings → Updates. */
    @Test
    fun notificationBar() {
        val now = java.time.Instant.now()
        show("canyon-river", pins = favorites, update = UpdateState(UpdatePhase.Available, newRelease, checkedAt = now), notices = sampleNotices(now))
        compose.onNodeWithText("Pill Launcher 3.4 is ready", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "notification-bar.png").path)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        // Updates sits under TV & system
        compose.onNodeWithText("TV & system").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Updates").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Update to 3.4").assertExists()
        captureScreenRoboImage(File(out, "settings-updates.png").path)
        // Down reaches every line of what's new, so the page scrolls to the end
        var reached = false
        repeat(8) {
            compose.onAllNodes(isFocused()).onLast().performKeyInput { pressKey(Key.DirectionDown) }
            settle()
            if (compose.onAllNodes(isFocused() and hasText("Rename apps in the launcher")).fetchSemanticsNodes().isNotEmpty()) reached = true
        }
        check(reached) { "what's new can be reached with Down" }
    }

    /** OK on the bell opens the bar into the list; holding OK dismisses one; Back folds it up. */
    @Test
    fun notificationList() {
        val now = java.time.Instant.now()
        show("canyon-river", pins = favorites, update = UpdateState(UpdatePhase.Available, newRelease, checkedAt = now), notices = sampleNotices(now))
        compose.onNodeWithContentDescription("Notifications", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Clear all").assertExists()
        compose.onNode(isFocused() and hasText("Pill Launcher 3.4 is ready", substring = true)).assertExists()
        compose.onRoot().captureRoboImage(File(out, "notifications.png").path)
        // Grouped by source: the launcher's own "Updated to 3.3" comes next, then the film app's
        press(Key.DirectionDown)
        check(focusedName().contains("Updated to 3.3")) { "Down stays in the Pill Launcher group: ${focusedName()}" }
        press(Key.DirectionDown)
        check(focusedName().contains("New episode")) { "Down moves on to the next group: ${focusedName()}" }
        // Holding OK on it dismisses it
        compose.onAllNodes(isFocused()).onFirst().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onAllNodes(isRoot()).onFirst().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        compose.onNodeWithText("New episode", substring = true).assertDoesNotExist()
        check(focusedName().contains("holiday.jpg")) { "focus moves to the next notification: ${focusedName()}" }
        press(Key.Back)
        compose.onNodeWithText("Clear all").assertDoesNotExist()
        check(focusedName().startsWith("Notifications")) { "Back folds the list up onto the bell: ${focusedName()}" }
    }

    /** The notification bar stays off the idle screen, which shows only the widgets and the photo. */
    @Test
    fun notificationsNotWhileIdle() {
        show("canyon-river", pins = favorites, update = UpdateState(UpdatePhase.Failed, newRelease, message = "Couldn't download it: can't reach GitHub"))
        compose.onNodeWithText("Pill Launcher 3.4 isn't installed yet", substring = true).assertExists()
        compose.mainClock.advanceTimeBy(125_000)
        compose.mainClock.advanceTimeBy(2_500)
        settle()
        compose.onNodeWithText("Pill Launcher 3.4 isn't installed yet", substring = true).assertDoesNotExist()
    }

    /** Files, from the bar: the code to scan on the left, the TV's files as cards on the right. */
    @Test
    fun filesPage() {
        val dir = java.nio.file.Files.createTempDirectory("files").toFile()
        val day = 24 * 3600 * 1000L
        val now = System.currentTimeMillis()
        for ((i, f) in listOf("holiday.jpg" to 3_400_000, "match-highlights.mp4" to 2_120_000, "kinopub-2.4.apk" to 480_000, "tv-manual.pdf" to 51_000).withIndex()) {
            File(dir, f.first).apply { writeBytes(ByteArray(f.second)); setLastModified(now - i * day) }
        }
        val share = testShare(dir)
        try {
            show("canyon-river", pins = favorites, share = share)
            compose.onNodeWithContentDescription("Share files").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            settle()
            compose.onNodeWithText("Scan with your phone").assertExists()
            compose.onNodeWithText("holiday.jpg", substring = true).assertExists()
            compose.onNodeWithText("Keep receiving: Off").assertExists()
            captureScreenRoboImage(File(out, "files.png").path)
        } finally {
            share.stop()
            dir.deleteRecursively()
        }
    }

    /** A share as the TV shows it (a home-network address and code), listening on this machine. */
    private fun testShare(dir: File) = FileShare(
        dir,
        page = { "" },
        address = { "192.168.1.24" },
        code = "482913",
        listenOn = { java.net.InetAddress.getLoopbackAddress() },
    )

    /** A phone typed the code: Allow or Block on the TV, over the home screen. */
    @Test
    fun filesPairing() {
        val dir = java.nio.file.Files.createTempDirectory("pair").toFile()
        val share = testShare(File(dir, "Files"))
        try {
            show("pine-forest", pins = favorites, share = share)
            val request = share.askToPair("iPhone")
            settle()
            compose.onNodeWithText("iPhone wants to connect").assertExists()
            captureScreenRoboImage(File(out, "files-approve.png").path)
            compose.onNodeWithText("Allow").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            check(share.state.value.pending == null) { "Allow answers the request" }
            check(share.state.value.devices.map { it.name } == listOf("iPhone")) { "the phone is trusted: ${share.state.value.devices}" }
            check(request.id.isNotEmpty())
        } finally {
            share.stop()
            dir.deleteRecursively()
        }
    }

    /** An installed app's APK: the bar asks, and only Delete removes it. */
    @Test
    fun installedApkAsks() {
        val dir = java.nio.file.Files.createTempDirectory("apk").toFile()
        val share = testShare(File(dir, "Files"))
        val apk = File(share.dir, "kinopub-2.4.apk").apply { writeBytes(ByteArray(480_000)) }
        try {
            show("canyon-river", pins = favorites, share = share, notices = listOf(installedApkNotice(apk.name, java.time.Instant.now())))
            check(apk.exists()) { "nothing is deleted on its own" }
            compose.onNodeWithText("kinopub-2.4.apk: its app is installed", substring = true).performSemanticsAction(SemanticsActions.OnClick)
            settle()
            compose.onNodeWithText("Delete kinopub-2.4.apk?").assertExists()
            check(apk.exists()) { "OK on the notice only asks" }
            captureScreenRoboImage(File(out, "files-delete-apk.png").path)
            compose.onNodeWithText("Delete").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            check(!apk.exists()) { "Delete removes the file" }
        } finally {
            share.stop()
            dir.deleteRecursively()
        }
    }

    /** Settings → Files: keep receiving, tidying, and the trusted phones. */
    @Test
    fun settingsFiles() {
        val dir = java.nio.file.Files.createTempDirectory("files-settings").toFile()
        val share = testShare(File(dir, "Files"))
        try {
            share.approve(share.askToPair("iPhone").id)
            share.approve(share.askToPair("Computer").id)
            show("pine-forest", pins = favorites, share = share)
            compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
            settle()
            // The Files row (not the stand-in app called Files), by its summary
            compose.onNode(hasText("Files") and hasText("deleting APKs", substring = true)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            settle()
            compose.onNodeWithText("Offer to delete installed APKs").assertExists()
            // The trusted phones wait under Advanced; OK unfolds them
            compose.onNodeWithText("iPhone").assertDoesNotExist()
            compose.onNode(hasText("Advanced") and hasText("2 phones", substring = true)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
            settle()
            compose.onNodeWithText("iPhone").assertExists()
            captureScreenRoboImage(File(out, "settings-files.png").path)
        } finally {
            share.stop()
            dir.deleteRecursively()
        }
    }

    /** After two crashes in a row: the photo and Favorites only, saying why, with Try normally. */
    @Test
    fun safeMode() {
        // As the launcher runs it in safe mode: no widgets
        show("misty-valley", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = emptyList()), safeMode = "IllegalStateException: something went wrong")
        compose.onNodeWithText("Try normally").assertExists()
        compose.onNodeWithText("Lisbon", substring = true).assertDoesNotExist()
        compose.onRoot().captureRoboImage(File(out, "safe-mode.png").path)
    }

    /** Settings → Notifications: other apps' (once allowed), football and the connection. */
    @Test
    fun settingsNotifications() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Notifications").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Other apps' notifications").assertExists()
        captureScreenRoboImage(File(out, "settings-notifications.png").path)
    }

    @Test
    fun favoritesWrap() {
        // More favourites than fit across, wrapped onto a second row
        val many = apps.take(12).map { it.pkg }
        show("canyon-river", pins = many, settings = LauncherSettings(weather = WeatherMode.Celsius, favoritesLayout = FavoritesLayout.Wrap))
        compose.onRoot().captureRoboImage(File(out, "favorites-wrap.png").path)
    }

    @Test
    fun favoritesScroll() {
        // More favourites than fit: the row slides as focus moves right, edges fade
        val many = apps.take(12).map { it.pkg }
        show("canyon-river", pins = many)
        compose.onRoot().performKeyInput { repeat(8) { pressKey(Key.DirectionRight) } }
        settle()
        compose.onRoot().captureRoboImage(File(out, "favorites-scroll.png").path)
    }

    /** Fast D-pad presses across favourites, quick actions and the grid never lose or strand focus. */
    @Test
    fun fastNavigationKeepsFocusOnScreen() {
        show("pine-forest", pins = apps.take(12).map { it.pkg })
        val d = Key.DirectionDown; val u = Key.DirectionUp; val l = Key.DirectionLeft; val r = Key.DirectionRight
        val presses = listOf(r, r, r, r, r, r, u, u, l, r, d, d, d, d, r, r, r, d, d, l, l, u, u, u, u, u, l, l, l, l, l, l, d, d, d)
        for ((i, key) in presses.withIndex()) {
            compose.onRoot().performKeyInput { pressKey(key) }
            compose.mainClock.advanceTimeBy(16) // a key every frame, faster than any remote repeats
            compose.waitForIdle()
            assertTrue("focus lost after press ${i + 1} ($key)", runCatching { compose.onNode(isFocused()).fetchSemanticsNode() }.isSuccess)
        }
        settle()
        val focused = compose.onNode(isFocused()).fetchSemanticsNode().boundsInRoot
        val screen = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val slack = 8f // focus scale may push the outline a few px past the edge
        assertTrue(
            "focused item $focused is not on screen $screen",
            focused.left >= screen.left - slack && focused.top >= screen.top - slack &&
                focused.right <= screen.right + slack && focused.bottom <= screen.bottom + slack,
        )
    }

    /** Four widgets (the most allowed) switch to compact cards and all fit beside the buttons. */
    @Test
    fun widgets() {
        val four = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs, WidgetKind.System)
        show("mountain-lake", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = four))
        // Each card says as much as fits: the high and low, and the football card's next match
        for (label in listOf("16°", "NEXT")) compose.onNodeWithText(label, substring = true).assertIsDisplayed()
        compose.onNodeWithText("SYSTEM", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "widgets.png").path)
    }

    /** Not the home screen yet: the prompt to set it up sits above the favourites. */
    @Test
    fun homePrompt() {
        show("canyon-river", pins = favorites, home = HomeStatus(isDefault = false, redirectOn = false))
        compose.onNodeWithText("Set up").assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "home-prompt.png").path)
    }

    /** A new install: getting started, one step in (installed) plus the favourites, with the home screen next. */
    @Test
    fun gettingStarted() {
        show("canyon-river", pins = favorites, home = HomeStatus(isDefault = false, redirectOn = false), settings = LauncherSettings(weather = WeatherMode.Celsius, setupDone = false))
        compose.onNodeWithText("Getting started · 2 of 5 done").assertIsDisplayed()
        compose.onNodeWithText("Set up").assertIsDisplayed()
        // The old reminder waits until getting started is over
        compose.onNodeWithText("Closing an app takes you to the TV's own home screen").assertDoesNotExist()
        compose.onRoot().captureRoboImage(File(out, "getting-started.png").path)
    }

    /** With four on, turning on a fifth is refused with a toast. */
    @Test
    fun widgetLimit() {
        val four = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs, WidgetKind.Daylight)
        show("aurora-sky", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = four))
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Widgets").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("System info").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Maximum of 4 widgets can be active at once").assertIsDisplayed()
        captureScreenRoboImage(File(out, "widget-settings.png").path)
    }

    @Test
    fun firstLaunch() {
        show("pine-forest", pins = emptyList())
        compose.onRoot().captureRoboImage(File(out, "first-launch.png").path)
    }

    @Test
    fun settings() {
        show("aurora-sky", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        // All six categories show without scrolling: the last one ends above the footer
        val last = compose.onNodeWithText("TV & system").fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithText("Back to close", substring = true).fetchSemanticsNode().boundsInRoot
        check(last.bottom <= footer.top) { "TV & system is cut off: ends at ${last.bottom}, footer at ${footer.top}" }
        captureScreenRoboImage(File(out, "settings.png").path)
    }

    /** Match day: the football card counts down to kick-off and stays on the team that plays. */
    @Test
    fun matchDayCard() {
        // Later today; just before midnight, minutes away instead (the countdown shows either way)
        val now = java.time.LocalDateTime.now()
        val inMinutes = if (now.plusMinutes(135).toLocalDate() == now.toLocalDate()) 135L else 5L
        show("mountain-lake", pins = favorites, football = matchDay(inMinutes))
        compose.onNodeWithText("KICK-OFF IN", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "match-day.png").path)
    }

    /** Ten minutes before kick-off, a notice in the bar that never takes focus: OK still opens the focused app. */
    @Test
    fun kickoffNotice() {
        show("aurora-sky", pins = favorites, football = matchDay(inMinutes = 8))
        compose.onNodeWithText("Les Fennecs kick off in", substring = true).assertIsDisplayed()
        check(focusedName().contains("Cinema")) { "focus stays on the favourites: ${focusedName()}" }
        compose.onRoot().captureRoboImage(File(out, "kickoff-notice.png").path)
    }

    /** OK on the football card opens the football page; Right moves to the next team. */
    @Test
    fun footballHub() {
        show("canyon-river", pins = favorites, football = matchDay(inMinutes = 60 * 24 * 3))
        // The card shows whichever followed team is due first (the saved Arsenal match may be today)
        compose.onNode(hasClickAction() and (hasText("Algeria", substring = true) or hasText("ALG", substring = true) or hasText("ARS", substring = true)))
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("LATEST RESULT").assertIsDisplayed()
        // No table any more: the match fills the page
        compose.onNodeWithText("NEXT MATCH").assertIsDisplayed()
        compose.onNodeWithText("Manchester City").assertDoesNotExist()
        captureScreenRoboImage(File(out, "football-hub.png").path)
    }

    /** OK on the weather card opens the forecast. */
    @Test
    fun forecast() {
        show("misty-valley", pins = favorites, weather = forecastWeather)
        compose.onAllNodes(hasClickAction() and hasText("Lisbon", substring = true)).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("FEELS LIKE").assertIsDisplayed()
        compose.onNodeWithText("Tomorrow").assertIsDisplayed()
        captureScreenRoboImage(File(out, "forecast.png").path)
    }

    /** OK on the clock card opens the clock page. */
    @Test
    fun clockPage() {
        show("misty-valley", pins = favorites)
        val weekday = java.time.LocalDate.now().dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        compose.onAllNodes(hasClickAction() and hasText(weekday, substring = true)).onFirst()
            .performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("WORLD CLOCKS").assertIsDisplayed()
        compose.onNodeWithText("Algiers").assertIsDisplayed()
        captureScreenRoboImage(File(out, "clock.png").path)
    }

    /** The headlines card among four widgets, then a story's summary. */
    @Test
    fun headlines() {
        val four = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs, WidgetKind.Headlines)
        show("pine-forest", pins = favorites, news = news, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = four, newsTopics = setOf(NewsTopic.Russia)))
        compose.onNodeWithText("RUSSIA", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "headlines.png").path)
        compose.onNodeWithText("RUSSIA", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Up / Down for more", substring = true).assertIsDisplayed()
        captureScreenRoboImage(File(out, "headline-page.png").path)
    }

    /**
     * Moscow's prayer times on a November day, seen at 15:39 so Maghrib (16:22) is next, in
     * 42 minutes. The test can't set the clock, so it sets the time zone instead: one in
     * which it is now 15:39, whatever the hour where the test runs.
     */
    private fun <T> atTwentyToFour(block: (PrayerTimes) -> T): T = atClock(15, 39, block)

    /** The same day's times seen at [hour]:[minute], by the same means. */
    private fun <T> atClock(hour: Int, minute: Int, block: (PrayerTimes) -> T): T {
        // Far enough from the minute's end that the countdown can't tick over mid-test
        while (java.time.Instant.now().atZone(java.time.ZoneOffset.UTC).second > 40) Thread.sleep(500)
        val utc = java.time.Instant.now().atZone(java.time.ZoneOffset.UTC)
        var minutes = hour * 60 + minute - (utc.hour * 60 + utc.minute)
        if (minutes > 12 * 60) minutes -= 24 * 60
        if (minutes < -12 * 60) minutes += 24 * 60
        val sign = if (minutes < 0) "-" else "+"
        val zone = java.util.TimeZone.getTimeZone("GMT$sign%02d:%02d".format(Math.abs(minutes) / 60, Math.abs(minutes) % 60))
        val before = java.util.TimeZone.getDefault()
        java.util.TimeZone.setDefault(zone)
        try {
            fun t(h: Int, m: Int) = java.time.LocalTime.of(h, m)
            return block(
                PrayerTimes(
                    mosque = "Moscow Cathedral Mosque",
                    times = mapOf(Prayer.Fajr to t(6, 1), Prayer.Dhuhr to t(12, 14), Prayer.Asr to t(13, 49), Prayer.Maghrib to t(16, 22), Prayer.Isha to t(18, 7)),
                    shuruq = t(7, 55),
                    iqama = mapOf(Prayer.Fajr to t(6, 20), Prayer.Dhuhr to t(12, 30), Prayer.Asr to t(14, 0), Prayer.Maghrib to t(16, 27), Prayer.Isha to t(18, 20)),
                    forDate = java.time.LocalDate.now(zone.toZoneId()),
                    fetchedAt = java.time.Instant.now().minusSeconds(3 * 3600),
                    jumua = t(13, 0),
                ),
            )
        } finally {
            java.util.TimeZone.setDefault(before)
        }
    }

    /** The prayer card with its mosque, Maghrib next, and the five along the bottom; OK opens the page. */
    @Test
    fun prayerTimes() {
        atTwentyToFour { times -> prayerTimesAt(times) }
    }

    private fun prayerTimesAt(times: PrayerTimes) {
        show(
            "canyon-river", pins = favorites, prayers = times,
            settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Prayers), mosque = "moscow-cathedral-mosque"),
        )
        compose.onNodeWithText("MOSCOW CATHEDRAL MOSQUE").assertIsDisplayed()
        compose.onNodeWithText("Maghrib").assertIsDisplayed()
        compose.onNodeWithText("in 4", substring = true).assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "prayer-widget.png").path)
        compose.onNodeWithText("Maghrib").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(isFocused() and hasText("Refresh")).assertExists()
        // "Iqama" and its time under the next prayer (the column header reads IQAMA)
        compose.onNodeWithText("Iqama ", substring = true).assertIsDisplayed()
        // The Friday prayer under the five, and the Islamic date in the page's heading
        compose.onNodeWithText("Friday prayer").assertIsDisplayed()
        compose.onNodeWithText(" AH", substring = true).assertIsDisplayed()
        captureScreenRoboImage(File(out, "prayer-page.png").path)
    }

    /** Three minutes after Maghrib: its notice is in the bar, and OK on it opens the prayer page. */
    @Test
    fun prayerNoticeInTheBar() {
        atClock(16, 25) { times ->
            show(
                "canyon-river", pins = favorites, prayers = times,
                settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Prayers), mosque = "moscow-cathedral-mosque"),
            )
            compose.onNodeWithText("Maghrib · 4:22 PM", substring = true).assertIsDisplayed()
            compose.onRoot().captureRoboImage(File(out, "prayer-notice.png").path)
            compose.onNodeWithText("Maghrib · 4:22 PM", substring = true).performSemanticsAction(SemanticsActions.OnClick)
            settle()
            compose.onNode(isFocused() and hasText("Refresh")).assertExists()
        }
    }

    /** Turned off in Settings → Notifications, a prayer's time puts nothing in the bar. */
    @Test
    fun prayerNoticeTurnedOff() {
        atClock(16, 25) { times ->
            show(
                "canyon-river", pins = favorites, prayers = times,
                settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Prayers), mosque = "moscow-cathedral-mosque", prayerNotices = false),
            )
            compose.onNodeWithText("All caught up").assertIsDisplayed()
        }
    }

    /** The home screen in Arabic: its words, and everything laid out right to left. */
    @Test
    fun homeInArabic() {
        atTwentyToFour { times ->
            show(
                "canyon-river", pins = favorites, prayers = times, language = Language.Arabic,
                settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Fennecs, WidgetKind.Prayers), mosque = "moscow-cathedral-mosque"),
            )
            compose.onNodeWithText(tr("All caught up")).assertIsDisplayed()
            compose.onRoot().captureRoboImage(File(out, "home-arabic.png").path)
        }
    }

    /** The home screen in French. */
    @Test
    fun homeInFrench() {
        show("mountain-lake", pins = favorites, language = Language.French)
        compose.onNodeWithText("Tout est à jour").assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "home-french.png").path)
    }

    /** Settings → TV & system → Language offers the TV's language and the launcher's three; choosing one saves it. */
    @Test
    fun languageSetting() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("TV & system").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Language").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Same as the TV").assertIsDisplayed()
        compose.onNodeWithText("Français").assertIsDisplayed()
        compose.onNodeWithText("العربية").assertIsDisplayed()
        compose.onNodeWithText("Français").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        check(currentSettings.language == "fr") { "choosing Français saves it: ${currentSettings.language}" }
    }

    /**
     * Right to left, the favourites start on the right: Left goes on along the row, Right
     * comes back, and Right from the first one stays put (it is the row's start).
     */
    @Test
    fun navigationRightToLeft() {
        show("misty-valley", pins = favorites, language = Language.Arabic)
        check(focusedName().contains("Cinema")) { "starts on the first favourite: ${focusedName()}" }
        press(Key.DirectionLeft)
        check(focusedName().contains("Live TV")) { "Left goes on to the second favourite: ${focusedName()}" }
        press(Key.DirectionRight)
        check(focusedName().contains("Cinema")) { "Right comes back to the first: ${focusedName()}" }
        press(Key.DirectionRight)
        check(focusedName().contains("Cinema")) { "Right from the first favourite stays: ${focusedName()}" }
        // The bar is at the top left, in the launcher's words
        compose.onNodeWithContentDescription(tr("Settings")).assertIsDisplayed()
        compose.onNodeWithText(tr("All caught up")).assertIsDisplayed()
    }

    /** A link adder whose state the test sets, with one web shortcut made already. */
    private fun linkAdder(): LinkAdder {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val shortcuts = WebShortcuts(context)
        shortcuts.write(listOf(WebShortcut("yt", "YouTube", "https://www.youtube.com/tv")))
        return LinkAdder(context, shortcuts)
    }

    /** A site's icon as a page would give it: a red rounded square with a white triangle. */
    private fun siteIcon(): android.graphics.Bitmap {
        val b = android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.color = 0xFF2F6FDE.toInt()
        c.drawRoundRect(0f, 0f, 192f, 192f, 40f, 40f, p)
        p.color = android.graphics.Color.WHITE
        p.textSize = 120f
        p.typeface = android.graphics.Typeface.DEFAULT_BOLD
        p.textAlign = android.graphics.Paint.Align.CENTER
        c.drawText("W", 96f, 138f, p)
        return b
    }

    /**
     * Home screen → Add an app or website: a checked link (here a website, with its name and
     * icon) and what OK on it does; below, the web shortcuts made so far. Adding it makes a
     * shortcut and asks for it to be pinned.
     */
    @Test
    fun addLinkWebsite() = runBlocking<Unit> {
        val links = linkAdder()
        links.state.value = LinkState(LinkPhase.Found, "https://www.wikipedia.org/", FoundLink(LinkKind.Website, "https://www.wikipedia.org/", "Wikipedia", siteIcon()))
        show("mountain-lake", pins = favorites, links = links)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Home screen").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Add an app or website").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Website · wikipedia.org").assertIsDisplayed()
        compose.onNodeWithText("Add to the home screen").assertIsDisplayed()
        compose.onNodeWithText("YouTube").assertExists()
        captureScreenRoboImage(File(out, "settings-add-link.png").path)
        val pinned = async(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { withTimeout(5000) { links.pins.first() } }
        compose.onNodeWithText("Add to the home screen").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        val made = links.list().last()
        check(made.name == "Wikipedia" && made.url == "https://www.wikipedia.org/") { "the shortcut is made: $made" }
        check(pinned.await() == made.pkg) { "and pinned" }
        compose.onNodeWithText("Wikipedia is on the home screen, in Favorites").assertExists()
    }

    /** An APK link says what it is and its size; downloading shows how far it has got, and OK stops it. */
    @Test
    fun addLinkApp() {
        val links = linkAdder()
        val apk = FoundLink(LinkKind.Apk, "https://github.com/xbmc/xbmc/releases/download/21.1/kodi.apk", "kodi", sizeBytes = 64_000_000)
        links.state.value = LinkState(LinkPhase.Found, apk.url, apk)
        show("canyon-river", pins = favorites, links = links)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Home screen").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Add an app or website").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("App to install · 64 MB · from github.com").assertIsDisplayed()
        compose.onNodeWithText("Download and install").assertIsDisplayed()
        links.state.value = links.state.value.copy(phase = LinkPhase.Downloading, progress = 0.42f)
        settle()
        compose.onNodeWithText("Downloading · 42%").assertIsDisplayed()
    }

    /** What the web viewer asked of the page, in order. */
    private class FakePage : app.pill.launcher.ui.WebPageControls {
        val calls = mutableListOf<String>()
        var history = 1
        override fun click(x: Float, y: Float) { calls += "click ${x.toInt()},${y.toInt()}" }
        override fun scroll(x: Float, y: Float, dx: Float, dy: Float) { calls += "scroll ${dx.toInt()},${dy.toInt()}" }
        override fun pageKey(event: android.view.KeyEvent): Boolean { calls += "key ${event.keyCode}"; return true }
        override fun back(): Boolean { if (history == 0) return false; history--; calls += "back"; return true }
        override fun reload() { calls += "reload" }
        override fun exitFullscreen() { calls += "exit" }
    }

    private fun key(code: Int, action: Int = android.view.KeyEvent.ACTION_DOWN, repeat: Int = 0, held: Long = 0) =
        android.view.KeyEvent(1000L, 1000L + held, action, code, repeat)

    /** A stand-in for a website under the viewer: a header, a headline and a few lines. */
    @androidx.compose.runtime.Composable
    private fun StandInPage() {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xFFF7F7F5))) {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxWidth().height(70.dp).background(androidx.compose.ui.graphics.Color(0xFF2F6FDE)))
            androidx.compose.material3.Text("Beni Saf: a sunny week ahead", fontSize = 40.sp, color = androidx.compose.ui.graphics.Color(0xFF1B1B1B), modifier = androidx.compose.ui.Modifier.padding(start = 80.dp, top = 48.dp))
            for (w in listOf(0.82f, 0.9f, 0.76f, 0.88f, 0.6f)) {
                androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(start = 80.dp, top = 22.dp).fillMaxWidth(w * 0.7f).height(18.dp).background(androidx.compose.ui.graphics.Color(0xFFD9D9D6)))
            }
        }
    }

    /**
     * A website open full screen: arrows move the pointer and OK clicks where it is; at the
     * screen's edge the page scrolls instead; Back goes back a page, then leaves; holding OK
     * opens the viewer's menu, whose changes are kept.
     */
    @Test
    fun webViewer() {
        val page = FakePage()
        var left = false
        var saved: WebShortcut? = null
        val viewer = app.pill.launcher.ui.WebViewer(WebShortcut("yt", "Wikipedia", "https://www.wikipedia.org/"), page, onLeave = { left = true }, onSave = { saved = it })
        val icon = webTile(siteIcon(), "Wikipedia").asImageBitmap()
        compose.setContent { app.pill.launcher.ui.WebAppPage(viewer, icon) { StandInPage() } }
        settle()
        val w = viewer.size.width
        val h = viewer.size.height
        check(w > 0) { "the viewer has a size" }
        fun press(code: Int) { check(viewer.key(key(code))); check(viewer.key(key(code, android.view.KeyEvent.ACTION_UP))) }
        // The pointer glides by time, a frame (16 ms) at a time, not a jump per key repeat
        var ms = 1000L
        fun frames(n: Int, during: (Int) -> Unit = {}) = repeat(n) { i ->
            ms += 16
            during(i)
            viewer.tick(ms * 1_000_000L, ms)
        }
        fun hold(code: Int, frameCount: Int) {
            val start = ms
            check(viewer.key(android.view.KeyEvent(start, start, android.view.KeyEvent.ACTION_DOWN, code, 0)))
            frames(frameCount) { i ->
                // The remote repeats a held key every 50 ms after the first 400
                if (ms - start > 400 && i % 3 == 0) viewer.key(android.view.KeyEvent(start, ms, android.view.KeyEvent.ACTION_DOWN, code, i / 3 + 1))
            }
            check(viewer.key(android.view.KeyEvent(start, ms, android.view.KeyEvent.ACTION_UP, code, 0)))
        }
        // Held: it sets off slowly and speeds up smoothly, every frame a small step
        check(viewer.key(android.view.KeyEvent(ms, ms, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 0)))
        val xs = mutableListOf<Float>()
        val heldFrom = ms
        frames(40) { i ->
            if (ms - heldFrom > 400 && i % 3 == 0) viewer.key(android.view.KeyEvent(heldFrom, ms, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, i / 3 + 1))
            if (i > 0) xs += viewer.cursor.x
        }
        check(viewer.key(android.view.KeyEvent(heldFrom, ms, android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_DPAD_RIGHT, 0)))
        frames(3)
        val steps = xs.zipWithNext { a, b -> b - a }
        check(steps.all { it > 0f && it < w * 0.03f }) { "every frame a small step to the right: $steps" }
        check(steps.last() > steps.first() * 2) { "faster the longer it is held: $steps" }
        check(steps.zipWithNext().all { (a, b) -> b >= a - 0.01f }) { "never jerks back to slower while held: $steps" }
        val x = viewer.cursor.x
        check(x > w / 2f) { "Right moves the pointer right: $x of $w" }
        // A quick tap still nudges it, gliding there
        check(viewer.key(key(android.view.KeyEvent.KEYCODE_DPAD_LEFT)))
        check(viewer.key(key(android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.ACTION_UP)))
        val tapFrom = viewer.cursor.x
        frames(20)
        check(tapFrom - viewer.cursor.x in (w * 0.008f)..(w * 0.02f)) { "a tap moves it a little: ${tapFrom - viewer.cursor.x}" }
        check(!viewer.moving) { "and then it rests" }
        settle()
        captureScreenRoboImage(File(out, "web-viewer.png").path)
        val cx = viewer.cursor.x
        val cy = viewer.cursor.y
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(page.calls.last() == "click ${cx.toInt()},${cy.toInt()}") { "OK clicks where the pointer is: ${page.calls}" }
        // Held down to the bottom: it stops at the edge and the page scrolls on
        hold(android.view.KeyEvent.KEYCODE_DPAD_DOWN, 150)
        check(page.calls.any { it.startsWith("scroll 0,") }) { "at the bottom the page scrolls: ${page.calls}" }
        check(viewer.cursor.y < h) { "the pointer stays on screen" }
        // A release that never comes: it stops by itself
        check(viewer.key(android.view.KeyEvent(ms, ms, android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_DPAD_UP, 0)))
        frames(60)
        check(!viewer.moving) { "a lost release doesn't send it off for ever" }
        // Back: a page back, then out
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(page.calls.last() == "back" && !left) { "Back goes a page back first" }
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(left) { "then leaves" }
        // Held OK: the menu, not a click
        val clicks = page.calls.count { it.startsWith("click") }
        check(viewer.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)))
        check(viewer.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1, held = 500)))
        check(viewer.menuOpen) { "holding OK opens the menu" }
        settle()
        compose.onNodeWithText("Text size · 100%").assertExists()
        compose.onNodeWithText("Ask for the TV version").assertExists()
        captureScreenRoboImage(File(out, "web-viewer-menu.png").path)
        check(page.calls.count { it.startsWith("click") } == clicks) { "holding OK clicks nothing" }
        viewer.menuClosed()
        viewer.change(viewer.shortcut.copy(pointer = false))
        check(saved?.pointer == false) { "a change is kept" }
        // A full-screen video: OK plays or pauses (Space), Left / Right skip (the player's arrows),
        // Up brings the pointer for the player's buttons and Back puts it away, then leaves full screen
        viewer.fullscreen = true
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(page.calls.last() == "key ${android.view.KeyEvent.KEYCODE_SPACE}") { "OK is play or pause: ${page.calls}" }
        check(viewer.key(key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)))
        check(page.calls.last() == "key ${android.view.KeyEvent.KEYCODE_DPAD_RIGHT}") { "Right skips on: ${page.calls}" }
        check(!viewer.pointerOn) { "no pointer over the video" }
        press(android.view.KeyEvent.KEYCODE_DPAD_UP)
        check(viewer.pointerOn) { "Up brings the pointer" }
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(!viewer.pointerOn && viewer.fullscreen) { "Back puts the pointer away first" }
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(page.calls.last() == "exit") { "then leaves full screen: ${page.calls}" }
        viewer.fullscreen = false
        // The remote's play / pause key works anywhere
        press(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        check(page.calls.last() == "key ${android.view.KeyEvent.KEYCODE_SPACE}") { "⏯ is Space: ${page.calls}" }
        // Jumping between links: the arrows go to the page
        press(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
        check(page.calls.last() == "key ${android.view.KeyEvent.KEYCODE_DPAD_LEFT}") { "arrows go to the page: ${page.calls}" }
    }

    /** A video for the player's tests: where it is, what it has, and what was asked of it. */
    private class FakeVideo : app.pill.launcher.ui.VideoControls {
        override var position = 754_000L
        override var duration = 2_700_000L
        override var buffered = 1_020_000L
        override var playing = true
        override var buffering = false
        override var error: String? = null
        var rate = 1f
        override val speed get() = rate
        val seeks = mutableListOf<Long>()
        var audio = 0
        var text: String? = null
        override fun play() { playing = true }
        override fun pause() { playing = false }
        override fun seekTo(ms: Long) { seeks += ms; position = ms }
        override fun setSpeed(speed: Float) { rate = speed }
        override fun audioTracks() = listOf(
            app.pill.launcher.ui.TrackChoice("0:0", "English", audio == 0),
            app.pill.launcher.ui.TrackChoice("1:0", "Arabic", audio == 1),
            app.pill.launcher.ui.TrackChoice("2:0", "French", audio == 2),
        )
        override fun textTracks() = listOf(
            app.pill.launcher.ui.TrackChoice("0:0", "English", text == "0:0"),
            app.pill.launcher.ui.TrackChoice("1:0", "Arabic", text == "1:0"),
            app.pill.launcher.ui.TrackChoice("2:0", "French", text == "2:0"),
        )
        override fun selectAudio(id: String) { audio = id.substringBefore(':').toInt() }
        override fun selectText(id: String?) { text = id }
    }

    /** A frame of a film for the player's pictures: an evening sea. */
    @androidx.compose.runtime.Composable
    private fun StandInFilm() {
        androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(androidx.compose.ui.graphics.Color(0xFF1E3A5F), androidx.compose.ui.graphics.Color(0xFFE07A3F), androidx.compose.ui.graphics.Color(0xFF0E2A47)),
                ),
            ),
        )
    }

    /**
     * The launcher's own player, as Netflix's: the title, the red bar with the time left and
     * the buttons; OK pauses; Left / Right skip ten seconds, and held they scrub further, the
     * video jumping when the key is let go; Down goes to the buttons, where Audio & Subtitles
     * opens its panel; Back closes the panel, hides the controls, then leaves.
     */
    @Test
    fun videoPlayer() {
        val video = FakeVideo()
        var left = false
        val player = app.pill.launcher.ui.TvPlayer("Beni Saf by the sea", video, onExit = { left = true })
        compose.setContent { app.pill.launcher.ui.TvPlayerScreen(player) { StandInFilm() } }
        settle()
        compose.onNodeWithText("Beni Saf by the sea").assertIsDisplayed()
        compose.onNodeWithText("-32:26").assertIsDisplayed()
        captureScreenRoboImage(File(out, "player.png").path)
        fun press(code: Int, repeat: Int = 0) {
            check(player.key(key(code, repeat = repeat)))
            check(player.key(key(code, android.view.KeyEvent.ACTION_UP)))
        }
        // OK pauses and plays
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(!video.playing) { "OK pauses" }
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(video.playing) { "OK plays" }
        // A tap of Right: ten seconds on
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        check(video.seeks.last() == 764_000L) { "Right skips ten seconds: ${video.seeks}" }
        // Held: further and further, one jump when let go
        val before = video.seeks.size
        for (r in 0 until 8) check(player.key(key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, repeat = r)))
        check(video.seeks.size == before && player.scrubTarget != null) { "no jump while held" }
        settle()
        captureScreenRoboImage(File(out, "player-scrub.png").path)
        check(player.key(key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.ACTION_UP)))
        check(video.seeks.last() == 764_000L + 4 * 10_000L + 4 * 30_000L) { "one jump on release: ${video.seeks}" }
        // Down to the buttons, OK on Audio & Subtitles: Arabic subtitles
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(player.panel == app.pill.launcher.ui.TvPlayer.Panel.Tracks) { "the panel opens" }
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(video.text == "1:0") { "Arabic subtitles chosen: ${video.text}" }
        settle()
        captureScreenRoboImage(File(out, "player-tracks.png").path)
        // Back: the panel, then the controls, then out
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(player.panel == app.pill.launcher.ui.TvPlayer.Panel.None && player.controlsVisible)
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(!player.controlsVisible && !left) { "Back hides the controls while it plays" }
        // Hidden controls: OK still pauses, and brings them back
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(!video.playing && player.controlsVisible)
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(left) { "Back on a paused video leaves" }
        // The speed button
        val other = app.pill.launcher.ui.TvPlayer("x", video, onExit = {})
        other.refresh()
        check(other.key(key(android.view.KeyEvent.KEYCODE_DPAD_DOWN)))
        check(other.key(key(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)))
        check(other.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)))
        check(other.panel == app.pill.launcher.ui.TvPlayer.Panel.Speed)
        check(other.key(key(android.view.KeyEvent.KEYCODE_DPAD_DOWN)))
        check(other.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)))
        check(video.speed == 1.25f && other.panel == app.pill.launcher.ui.TvPlayer.Panel.None) { "speed 1.25x: ${video.speed}" }
    }

    /** What the TV app view asked of its hidden page. */
    private class FakeSiteActions : app.pill.launcher.ui.SiteActions {
        val opened = mutableListOf<String>()
        val tabs = mutableListOf<String>()
        val backs = mutableListOf<String?>()
        var played = 0
        val pages = mutableListOf<Boolean>()
        var reloaded = 0
        var left = false
        var asked = 0
        val searched = mutableListOf<String>()
        val signIns = mutableListOf<String?>()
        val tabsPicked = mutableListOf<String>()
        override fun open(card: SiteCard) { opened += card.title }
        override fun tab(card: SiteCard) { tabs += card.title }
        override fun backTo(screen: app.pill.launcher.ui.SiteScreen) { backs += screen.url }
        override fun play(screen: app.pill.launcher.ui.SiteScreen) { played++ }
        override fun showPage(pop: Boolean) { pages += pop }
        override fun reload() { reloaded++ }
        override fun leave() { left = true }
        override fun askSearch() { asked++ }
        override fun search(words: String) { searched += words }
        override fun signIn(card: SiteCard) { signIns += card.url }
        override fun pickTab(screen: app.pill.launcher.ui.SiteScreen, tab: SiteTab) { tabsPicked += tab.title }
    }

    /**
     * A website as a TV app: the launcher's own screens from the scanner's reading of the
     * sample pages (pictures stood in by the bundled photos). The menu, the banner and rows
     * move with the arrows, OK opens, Back goes up a level, a held OK opens the menu.
     */
    @Test
    fun tvApp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        fun scan(name: String) = parseSiteScan(javaClass.classLoader!!.getResource("data/$name")!!.readText())!!
        val home = scan("sitescan-home.json")
        val film = scan("sitescan-detail.json")
        val found = scan("sitescan-search.json")
        // Each picture a different photo, made small
        val files = File("src/main/assets/photos").listFiles()!!.sortedBy { it.name }
        val urls = (home.heroes.map { it.image } + home.rows.flatMap { r -> r.cards.map { it.image } } + film.detail.image + film.rows.flatMap { r -> r.cards.map { it.image } } + found.rows.flatMap { r -> r.cards.map { it.image } }).filterNotNull().distinct()
        val engine = FakeImageLoaderEngine.Builder().apply {
            urls.forEachIndexed { i, u ->
                val bm = BitmapFactory.decodeFile(files[i % files.size].path, BitmapFactory.Options().apply { inSampleSize = 4 })
                intercept(u, BitmapDrawable(context.resources, bm))
            }
        }.default(BitmapDrawable(context.resources, gradient())).build()
        Coil.setImageLoader(ImageLoader.Builder(context).components { add(engine) }.build())

        val acts = FakeSiteActions()
        val site = app.pill.launcher.ui.TvSite("Harbour TV", acts)
        site.start("https://harbour.example/home", home)
        val icon = webTile(null, "Harbour TV").asImageBitmap()
        compose.setContent { app.pill.launcher.ui.TvSiteScreen(site, icon) }
        settle()
        fun press(code: Int) { check(site.key(key(code))); check(site.key(key(code, android.view.KeyEvent.ACTION_UP))) }
        val top = { site.top!! }
        // Opens on the banner, from the saved reading, before the page has loaded
        compose.onNodeWithText("Night Harbour").assertIsDisplayed()
        compose.onAllNodesWithText("Films")[0].assertIsDisplayed()
        compose.onNodeWithText("Sign in").assertIsDisplayed()
        captureScreenRoboImage(File(out, "tv-app-home.png").path)
        // Right past More info: the banner's next picture
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        check(top().hero == 1) { "the next banner" }
        // Down to the rows; the spotlight follows the focused card
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        settle()
        check(site.focused(top())?.title == "Dust and Gold")
        compose.onAllNodesWithText("Dust and Gold")[0].assertIsDisplayed() // the spotlight's
        // Down: the next row's tabs first, then its cards
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        check(top().line == app.pill.launcher.ui.tabsLine(1)) { "the tabs: ${top().line}" }
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        settle()
        captureScreenRoboImage(File(out, "tv-app-tabs.png").path)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.tabsPicked == listOf("Series")) { "Series picked: ${acts.tabsPicked}" }
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        check(top().line == 1 && top().col(1) == 0)
        // Wide banners in their own shape
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        settle()
        captureScreenRoboImage(File(out, "tv-app-rows.png").path)
        check(top().line == 2)
        // Back: to the top of the screen; Up to the menu, Films, OK
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(top().line == -2) { "Back goes to the menu: ${top().line}" }
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.tabs == listOf("Films") && site.tab == 1) { "Films opens: ${acts.tabs}" }
        site.scanned(home)
        check(top().kind == SiteScreenKind.Browse && top().line == -2) { "focus stays on the menu" }
        // Search and Sign in at the end of the line
        check(site.topItems(top()).map { it.title } == listOf("Home", "Films", "Series", "Search", "Sign in")) { site.topItems(top()).toString() }
        // Back on another page of the menu: Home again
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(acts.tabs.last() == "Home" && site.tab == 0)
        site.scanned(home)
        // The banner's Play: its page opens, and plays once it is read as a film's
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        check(top().line == -1)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.opened == listOf("Night Harbour") && top().kind == null) { "the banner opens: ${acts.opened}" }
        settle()
        compose.onNodeWithText("Loading").assertIsDisplayed()
        site.scanned(film)
        check(top().kind == SiteScreenKind.Details && acts.played == 1) { "Play plays" }
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(site.stack.size == 1 && acts.backs.last() == "https://harbour.example/") { "Back to the first screen: ${acts.backs}" }
        // A card: its details, without playing
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.opened.last() == "Velvet Hours")
        site.scanned(film)
        check(top().kind == SiteScreenKind.Details && acts.played == 1)
        settle()
        compose.onNodeWithText("Ember Road").assertIsDisplayed()
        compose.onNodeWithText("2h 4m").assertIsDisplayed()
        captureScreenRoboImage(File(out, "tv-app-details.png").path)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.played == 2) { "OK on Play" }
        press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.pages == listOf(false)) { "Open as web page" }
        // Down to More like this, Back up to the buttons, Back to the rows
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        check(top().line == 0)
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(top().line == -1 && site.stack.size == 2)
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(site.stack.size == 1)
        // Search: the menu line's Search asks for words; their results are rows
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(top().line == -2)
        repeat(3) { press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.asked == 1) { "Search asks for words" }
        site.searchFor("red")
        check(acts.searched == listOf("red") && top().kind == null)
        site.scanned(found)
        check(top().kind == SiteScreenKind.Browse) { "results: ${top().kind}" }
        press(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
        settle()
        compose.onNodeWithText("Results").assertIsDisplayed()
        captureScreenRoboImage(File(out, "tv-app-search.png").path)
        // Back to the first screen; Sign in, at the end of the line
        press(android.view.KeyEvent.KEYCODE_BACK)
        press(android.view.KeyEvent.KEYCODE_BACK)
        check(site.stack.size == 1) { "back from the results: ${site.stack.size}" }
        // (The results' page said someone is signed in, so Sign in went; the first page says not)
        check(site.login == null)
        site.scanned(home)
        if (top().line != -2) press(android.view.KeyEvent.KEYCODE_BACK)
        repeat(4) { press(android.view.KeyEvent.KEYCODE_DPAD_RIGHT) }
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.signIns == listOf("https://harbour.example/login")) { "Sign in: ${acts.signIns}" }
        // A held OK: the menu; Down to the end, OK leaves
        check(site.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER)))
        check(site.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat = 1)))
        check(site.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER, repeat = 2)))
        check(site.menuOpen)
        settle()
        captureScreenRoboImage(File(out, "tv-app-menu.png").path)
        check(site.key(key(android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.ACTION_UP)))
        check(site.menuOpen) { "letting go of the hold keeps the menu" }
        check(site.menuItems == listOf("Search", "Sign in", "Open as web page", "Reload", "Back to the launcher")) { site.menuItems.toString() }
        repeat(4) { press(android.view.KeyEvent.KEYCODE_DPAD_DOWN) }
        press(android.view.KeyEvent.KEYCODE_DPAD_CENTER)
        check(acts.left && !site.menuOpen)
        // A page with nothing to draw shows the page itself
        site.start("https://harbour.example/home", null)
        site.scanned(SiteScan("https://harbour.example/login", "Harbour TV"))
        check(acts.pages.last()) { "the page, closing its screen on Back" }
    }

    /**
     * Updating the launcher from itself: its own page while Android replaces it (Back hides
     * it), the update noted so the new launcher opens its home screen, and the note on the
     * home screen once it has.
     */
    @Test
    fun updatingPage() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        SelfUpdate.started(context, "9.9")
        check(SelfUpdate.pending(context) == "9.9") { "the update is noted" }
        var hidden = false
        var notice by androidx.compose.runtime.mutableStateOf<String?>(null)
        compose.setContent {
            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                if (!hidden) app.pill.launcher.ui.UpdatingPage("9.9", onHide = { hidden = true })
                notice?.let { app.pill.launcher.ui.UpdatedNotice(it) { notice = null } }
            }
        }
        settle()
        compose.onNodeWithText("Updating Pill Launcher to 9.9").assertIsDisplayed()
        captureScreenRoboImage(File(out, "updating.png").path)
        // A cancelled install forgets it
        SelfUpdate.clear(context)
        check(SelfUpdate.pending(context) == null)
        hidden = true
        notice = "9.9"
        settle()
        compose.onNodeWithText("Pill Launcher updated to 9.9").assertIsDisplayed()
    }

    private val beniSaf = Place("Beni Saf", "Aïn Témouchent", "Algeria", 35.30099, -1.38226)

    /** Settings → Widgets → Weather location: Automatic, or a town found by name. */
    @Test
    fun settingsWeatherPlace() {
        show("mountain-lake", pins = favorites)
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Widgets").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(hasText("Weather location") and hasText("Automatic", substring = true)).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Automatic").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("beni saf")
        compose.mainClock.advanceTimeBy(1000)
        settle()
        compose.onNodeWithText("Beni Saf").assertIsDisplayed()
        captureScreenRoboImage(File(out, "settings-weather-place.png").path)
        // OK on it: the weather is for Beni Saf from now on
        compose.onNodeWithText("Beni Saf").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        check(currentSettings.weatherPlace == beniSaf) { "Beni Saf chosen: ${currentSettings.weatherPlace}" }
    }

    /** No mosque chosen: the card says where to choose one; the setting page takes the mosque's name. */
    @Test
    fun settingsPrayers() {
        show("pine-forest", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Prayers)))
        compose.onNodeWithText("Choose a mosque in Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Widgets").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(hasText("Prayer times") and hasText("No mosque chosen")).performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Refresh now").assertIsDisplayed()
        compose.onNodeWithText("Mosque ID", substring = true).assertIsDisplayed()
        captureScreenRoboImage(File(out, "settings-prayers.png").path)
    }

    /** The rail's Internet page: the speed test, ready to start, and a way to network settings. */
    @Test
    fun internetPage() {
        show("mountain-lake", pins = favorites)
        compose.onNodeWithContentDescription("Internet", substring = true).performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Start test").assertIsDisplayed()
        compose.onNodeWithText("Network settings").assertIsDisplayed()
        // The page opens on Network settings; Down goes to Start test
        compose.onNode(isFocused() and hasText("Network settings")).assertExists()
        captureScreenRoboImage(File(out, "internet.png").path)
        compose.onNode(isFocused() and hasText("Network settings")).performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        compose.onNode(isFocused() and hasText("Start test")).assertExists()
    }

    /** Down to the chips above All apps, then OK on Video: only the video apps, waved in. */
    @Test
    fun appChips() {
        show("canyon-river", pins = favorites)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        settle()
        // OK on the Video chip (the first clickable "Video": the chips come before the cards)
        compose.onAllNodes(hasClickAction() and hasText("Video")).onFirst().performSemanticsAction(SemanticsActions.OnClick)
        compose.mainClock.advanceTimeBy(2_000)
        settle()
        compose.onAllNodes(hasText("Video")).onFirst().assertExists()
        compose.onNodeWithText("Kids").assertExists()
        compose.onNodeWithText("Maps").assertDoesNotExist()
        compose.onRoot().captureRoboImage(File(out, "all-apps-video.png").path)
    }

    /** Settings → Widgets → Football teams: the followed teams and a search to add one. */
    @Test
    fun footballSettings() {
        show("aurora-sky", pins = favorites, football = matchDay(inMinutes = 60 * 24 * 3), settings = LauncherSettings(weather = WeatherMode.Celsius, teams = listOf(Algeria, arsenal)))
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Widgets").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNodeWithText("Football teams").performScrollTo().performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("alg")
        compose.mainClock.advanceTimeBy(600)
        settle()
        compose.onNodeWithText("MC Alger").assertExists()
        captureScreenRoboImage(File(out, "settings-football.png").path)
    }

    /** Shows the home screen over [photo] and lets focus, images and animations settle. */
    private fun show(
        photo: String,
        pins: List<String>,
        settings: LauncherSettings = LauncherSettings(weather = WeatherMode.Celsius),
        home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
        football: List<Fixtures> = listOf(fixtures),
        weather: Weather = this.weather,
        news: News? = null,
        update: UpdateState = UpdateState(),
        notices: List<Notice> = emptyList(),
        share: FileShare? = null,
        safeMode: String? = null,
        prayers: PrayerTimes? = null,
        prayersError: String? = null,
        /** The launcher's language: its words, its dates, and for Arabic the direction it is laid out in. */
        language: Language = Language.English,
        links: LinkAdder? = null,
    ) {
        check(fixtures.next != null && fixtures.last?.outcome == 'W') { "fixtures didn't parse: $fixtures" }
        Lang.current = language
        java.util.Locale.setDefault(Lang.localeFor(language, java.util.Locale.US))
        currentPins = pins
        currentSettings = settings
        footballNow = football
        useBackground(photo)
        compose.setContent {
            var pinned by remember { mutableStateOf(pins) }
            var current by remember { mutableStateOf(settings) }
            var names by remember { mutableStateOf(emptyMap<String, String>()) }
            var kept by remember { mutableStateOf(notices) }
            // The window's own direction too, as on a TV set to the language: focus moves by it
            androidx.compose.ui.platform.LocalView.current.rootView.layoutDirection =
                if (language.rightToLeft) android.view.View.LAYOUT_DIRECTION_RTL else android.view.View.LAYOUT_DIRECTION_LTR
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides
                    if (language.rightToLeft) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
            ) {
            LauncherScreen(
                notices = kept,
                onDismissNotice = { n -> kept = kept.filter { it.id != n.id } },
                share = share,
                links = links,
                safeMode = safeMode,
                prayers = prayers,
                prayersError = prayersError,
                apps = withNames(apps, names),
                weather = weather,
                football = footballNow,
                news = news,
                searchPlaces = { q -> Result.success(if (q.trim().lowercase().startsWith("beni")) listOf(beniSaf, Place("Beni Saf Port", "Aïn Témouchent", "Algeria", 35.306, -1.387)) else emptyList()) },
                searchTeams = { q -> searchTeams(listOf(Algeria, arsenal, Team("139283", "MC Alger", league = "Algerian Ligue 1", country = "Algeria"), Team("1", "Algeria U23", country = "Algeria")), q).map { TeamHit(it, it.id != "1") } },
                pins = pinned,
                settings = current,
                onSettingsChange = { current = it; currentSettings = it },
                onRename = { app, name ->
                    val own = app.originalLabel ?: app.label
                    names = cleanName(name, own)?.let { names + (app.pkg to it) } ?: (names - app.pkg)
                },
                update = update,
                version = "3.0",
                onOpen = {},
                onTogglePin = { app -> pinned = if (app.pkg in pinned) pinned - app.pkg else pinned + app.pkg; currentPins = pinned },
                onMovePin = { app, by ->
                    val from = pinned.indexOf(app.pkg)
                    val to = (from + by).coerceIn(0, pinned.lastIndex)
                    pinned = pinned.toMutableList().apply { add(to, removeAt(from)) }
                    currentPins = pinned
                },
                onOpenSystemSettings = {},
                home = home,
            )
            }
        }
        settle()
    }

    private fun settle() {
        repeat(8) {
            compose.mainClock.advanceTimeBy(150)
            Thread.sleep(50) // Coil decodes off the main thread
            compose.waitForIdle()
        }
    }

    /** Every background URL resolves to one local photo; a dark gradient if it was not downloaded. */
    private fun useBackground(seed: String) {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(photos, "$seed.jpg")
        val drawable: Drawable = BitmapDrawable(
            context.resources,
            if (file.exists()) BitmapFactory.decodeFile(file.path) else gradient(),
        )
        val engine = FakeImageLoaderEngine.Builder().default(drawable).build()
        Coil.setImageLoader(ImageLoader.Builder(context).components { add(engine) }.build())
    }

    private fun gradient(): Bitmap {
        val bitmap = Bitmap.createBitmap(1920, 1080, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, 1920f, 1080f, 0xFF27406B.toInt(), 0xFF0B0F1A.toInt(), Shader.TileMode.CLAMP)
        })
        return bitmap
    }

    private fun pkg(label: String) = "app.example." + label.lowercase().replace(" ", "")

    /** A rounded-square icon with a soft gradient and the app's initial. */
    private fun fakeApp(label: String, color: Int): AppEntry {
        val size = 216
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, 0f, size.toFloat(), lighten(color), color, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(RectF(0f, 0f, size.toFloat(), size.toFloat()), size * 0.24f, size * 0.24f, bg)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = size * 0.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val y = size / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label.take(1), size / 2f, y, text)
        val pkg = pkg(label)
        return AppEntry(label, pkg, ComponentName(pkg, "$pkg.Main"), bitmap.asImageBitmap(), accentOf(bitmap), category = categoryOf(-1, false, label, pkg))
    }

    private fun darken(color: Int): Int {
        fun ch(shift: Int) = (color shr shift and 0xFF) / 2
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun lighten(color: Int): Int {
        fun ch(shift: Int) = ((color shr shift and 0xFF) + 255) / 2
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
