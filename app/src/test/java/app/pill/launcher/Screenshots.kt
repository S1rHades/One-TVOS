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

    private val weather = Weather(tempC = 22.4, highC = 25.0, lowC = 16.0, code = 2, isDay = true, place = "Lisbon")

    private val favorites = listOf("Cinema", "Live TV", "Music", "Photos", "Podcasts").map(::pkg)

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
        // Hold OK the way a remote does: one key-down, a pause, one key-up (no repeats)
        compose.onRoot().performKeyInput { keyDown(Key.DirectionCenter) }
        compose.mainClock.advanceTimeBy(800)
        compose.onRoot().performKeyInput { keyUp(Key.DirectionCenter) }
        settle()
        runCatching { compose.onNodeWithText("Added Maps to Favorites").assertExists() }
            .onFailure { println(compose.onRoot().printToString(maxDepth = 12)); throw it }
        settle()
        compose.onRoot().captureRoboImage(File(out, "pin.png").path)
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
    ) {
        useBackground(photo)
        compose.setContent {
            var pinned by remember { mutableStateOf(pins) }
            var current by remember { mutableStateOf(settings) }
            LauncherScreen(
                apps = apps,
                weather = weather,
                pins = pinned,
                settings = current,
                onSettingsChange = { current = it },
                onOpen = {},
                onTogglePin = { app -> pinned = if (app.pkg in pinned) pinned - app.pkg else pinned + app.pkg },
                onOpenSystemSettings = {},
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
        return AppEntry(label, pkg, ComponentName(pkg, "$pkg.Main"), bitmap.asImageBitmap())
    }

    private fun lighten(color: Int): Int {
        fun ch(shift: Int) = ((color shr shift and 0xFF) + 255) / 2
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}
