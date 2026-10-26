package app.pill.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.delay

/**
 * Online photo list. Each URL is a fixed, seeded 1920x1080 photo, so the same
 * seed always returns the same picture and Coil can cache it.
 *
 * To use your own photos, replace this list with any direct image URLs
 * (for example Unsplash links ending in "?auto=format&fit=crop&w=1920&q=80").
 */
object BackgroundSources {
    private val seeds = listOf(
        "mountain-lake", "ocean-cliff", "pine-forest", "desert-dunes",
        "city-night", "aurora-sky", "canyon-river", "misty-valley",
    )
    val urls: List<String> = seeds.map { "https://picsum.photos/seed/$it/1920/1080" }
}

private const val CROSSFADE_MS = 1500

/**
 * Full-screen slideshow. The next image is downloaded and decoded BEFORE the
 * switch, so the cross-fade never flashes an empty frame. If a download fails
 * the current photo stays on screen and the next interval tries again.
 */
@Composable
fun BackgroundSlideshow(
    urls: List<String>,
    intervalMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    var index by remember { mutableIntStateOf(0) }

    fun requestFor(i: Int): ImageRequest =
        ImageRequest.Builder(context)
            .data(urls[i % urls.size])
            .size(1920, 1080)
            .crossfade(false) // the Crossfade composable below does the animation
            .build()

    LaunchedEffect(enabled, intervalMs, urls) {
        if (!enabled) return@LaunchedEffect
        val loader = context.imageLoader
        while (true) {
            delay(intervalMs)
            val next = (index + 1) % urls.size
            val result = loader.execute(requestFor(next))
            if (result is SuccessResult) index = next
        }
    }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0B0F1A), Color(0xFF1B2540))))) {
        Crossfade(
            targetState = index,
            animationSpec = tween(CROSSFADE_MS),
            modifier = Modifier.fillMaxSize(),
            label = "backgroundCrossfade",
        ) { i ->
            val request = remember(i, urls) { requestFor(i) }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Dark gradient over the photo: strong at the top (clock, pill) and bottom (app grid). */
@Composable
fun BackgroundScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xCC000000),
                    0.35f to Color(0x66000000),
                    1f to Color(0xCC000000),
                ),
            ),
    )
}
