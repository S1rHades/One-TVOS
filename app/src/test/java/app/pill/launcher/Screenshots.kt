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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.Coil
import coil.ImageLoader
import coil.test.FakeImageLoaderEngine
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import org.junit.Before
import org.junit.Rule
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

    /** The pins as the screen last changed them. */
    private var currentPins: List<String> = emptyList()

    @Before
    fun setUp() {
        out.mkdirs()
    }

    @Test
    fun home() {
        show("mountain-lake", pins = favorites)
        compose.onRoot().captureRoboImage(File(out, "home.png").path)
    }

    @Test
    fun pinFromGrid() {
        show("canyon-river", pins = favorites)
        // Down into the grid, along to "Maps", then hold OK to pin it
        compose.onRoot().performKeyInput {
            pressKey(Key.DirectionDown)
            repeat(6) { pressKey(Key.DirectionRight) }
        }
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
        settle()
        compose.onRoot().captureRoboImage(File(out, "idle.png").path)
    }

    @Test
    fun search() {
        show("pine-forest", pins = favorites)
        compose.onNodeWithContentDescription("Search").performSemanticsAction(SemanticsActions.OnClick)
        settle()
        compose.onNode(hasSetTextAction()).performTextInput("m")
        settle()
        captureScreenRoboImage(File(out, "search.png").path)
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
        for (label in listOf("25° / 16°", "NEXT", "SYSTEM")) compose.onNodeWithText(label).assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "widgets.png").path)
    }

    /** Not the home screen yet: the prompt to set it up sits above the favourites. */
    @Test
    fun homePrompt() {
        show("canyon-river", pins = favorites, home = HomeStatus(isDefault = false, redirectOn = false))
        compose.onNodeWithText("Set up").assertIsDisplayed()
        compose.onRoot().captureRoboImage(File(out, "home-prompt.png").path)
    }

    /** With four on, turning on a fifth is refused with a toast. */
    @Test
    fun widgetLimit() {
        val four = listOf(WidgetKind.Clock, WidgetKind.Weather, WidgetKind.Fennecs, WidgetKind.Daylight)
        show("aurora-sky", pins = favorites, settings = LauncherSettings(weather = WeatherMode.Celsius, widgets = four))
        compose.onNodeWithContentDescription("Settings").performSemanticsAction(SemanticsActions.OnClick)
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
        captureScreenRoboImage(File(out, "settings.png").path)
    }

    /** Shows the home screen over [photo] and lets focus, images and animations settle. */
    private fun show(
        photo: String,
        pins: List<String>,
        settings: LauncherSettings = LauncherSettings(weather = WeatherMode.Celsius),
        home: HomeStatus = HomeStatus(isDefault = true, redirectOn = false),
    ) {
        check(fixtures.next != null && fixtures.last?.outcome == 'W') { "fixtures didn't parse: $fixtures" }
        currentPins = pins
        useBackground(photo)
        compose.setContent {
            var pinned by remember { mutableStateOf(pins) }
            var current by remember { mutableStateOf(settings) }
            LauncherScreen(
                apps = apps,
                weather = weather,
                fixtures = fixtures,
                pins = pinned,
                settings = current,
                onSettingsChange = { current = it },
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
        // Two stand-ins ship a TV banner, like real TV apps; the rest get generated ones
        val banner = if (label in setOf("Cinema", "Live TV")) fakeBanner(label, color) else null
        return AppEntry(label, pkg, ComponentName(pkg, "$pkg.Main"), bitmap.asImageBitmap(), banner?.asImageBitmap(), accentOf(banner ?: bitmap))
    }

    /** A 16:9 banner in the style of a TV app: a deep gradient with the name set large. */
    private fun fakeBanner(label: String, color: Int): Bitmap {
        val w = 640
        val h = 360
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawPaint(Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), lighten(color), darken(color), Shader.TileMode.CLAMP)
        })
        canvas.drawCircle(w * 0.84f, h * 0.22f, h * 0.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = 0x22FFFFFF })
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = android.graphics.Color.WHITE
            textSize = h * 0.2f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(label.uppercase(), w * 0.07f, h * 0.84f, text)
        return bitmap
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
