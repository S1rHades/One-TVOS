package app.pill.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.nio.file.Files

/** The app list saved for the next cold start: what comes back is what was saved, icons and all. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class AppCacheTest {

    private fun app(pkg: String, label: String, color: Int, updatedAt: Long = 1L) = AppEntry(
        label = label,
        pkg = pkg,
        component = ComponentName(pkg, "$pkg.Main"),
        icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }.asImageBitmap(),
        accent = color,
        removable = pkg != "tv.built.in",
        category = AppCategory.Video,
        updatedAt = updatedAt,
    )

    @Test
    fun savedAppsComeBackAsTheyWere() {
        val dir = File(Files.createTempDirectory("apps").toFile(), "apps")
        val cache = AppCache(dir)
        assertNull("nothing saved on a first start", cache.read())

        val red = 0xFFE03131.toInt()
        val blue = 0xFF1C7ED6.toInt()
        // A renamed app is saved under its own name: the launcher's names are applied afterwards
        cache.write(listOf(app("tv.cinema", "Cinema", red).copy(label = "Films", originalLabel = "Cinema"), app("tv.built.in", "Live TV", blue)))
        val back = cache.read()!!
        assertEquals(listOf("Cinema", "Live TV"), back.map { it.label })
        assertEquals(ComponentName("tv.cinema", "tv.cinema.Main"), back[0].component)
        assertEquals(red, back[0].accent)
        assertEquals(AppCategory.Video, back[0].category)
        assertTrue(back[0].removable)
        assertTrue(!back[1].removable)
        assertEquals(red, back[0].icon.asAndroidBitmap().getPixel(10, 10))
        assertEquals(blue, back[1].icon.asAndroidBitmap().getPixel(10, 10))

        // An app that hasn't been updated keeps its saved icon; an updated one gets the new one
        val green = 0xFF2F9E44.toInt()
        cache.write(listOf(app("tv.cinema", "Cinema", green), app("tv.built.in", "Live TV", green, updatedAt = 2L)))
        val again = cache.read()!!
        assertEquals(red, again[0].icon.asAndroidBitmap().getPixel(10, 10))
        assertEquals(green, again[1].icon.asAndroidBitmap().getPixel(10, 10))

        // A removed app's icon goes with it
        cache.write(listOf(app("tv.built.in", "Live TV", green, updatedAt = 2L)))
        assertEquals(listOf("Live TV"), cache.read()!!.map { it.label })
        assertEquals(setOf("index.json", "tv.built.in.png"), dir.list()!!.toSet())
    }
}
