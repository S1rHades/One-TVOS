package app.pill.launcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.delay

/**
 * Nature photos bundled in the app (assets/photos), so the slideshow works without a
 * photo server: some networks refuse image hosts outright. They are Lorem Picsum photos
 * (Unsplash licence, free to use), fetched by .github/workflows/photos.yml.
 *
 * To change the set, edit the id list here and in photos.yml, then run that workflow.
 * Any direct image URLs (https://...) work here too.
 */
object BackgroundSources {
    private val ids = listOf(10, 11, 13, 15, 16, 17, 28, 29, 46, 62, 110, 128)
    val urls: List<String> = ids.map { "file:///android_asset/photos/$it.jpg" }
}

private const val CROSSFADE_MS = 1500

/** Waits between attempts while photos cannot be loaded (no network yet at boot, say). */
private val RetryDelaysMs = listOf(3_000L, 10_000L, 30_000L, 60_000L)

/**
 * Full-screen slideshow. Each photo is downloaded and decoded before it is shown, so the
 * cross-fade never flashes an empty frame. Until the first photo loads it keeps retrying,
 * moving through the list; a failed later photo keeps the current one on screen.
 * [onStatus] reports null while photos load fine, or why the last attempt failed.
 */
@Composable
fun BackgroundSlideshow(
    urls: List<String>,
    intervalMs: Long,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onStatus: (String?) -> Unit = {},
) {
    if (urls.isEmpty()) return
    val context = LocalContext.current
    val report by rememberUpdatedState(onStatus)
    // Index of the photo on screen; null until one has loaded
    var shown by remember { mutableStateOf<Int?>(null) }

    fun requestFor(i: Int): ImageRequest =
        ImageRequest.Builder(context)
            .data(urls[i % urls.size])
            .size(1920, 1080)
            .crossfade(false) // the Crossfade composable below does the animation
            .build()

    LaunchedEffect(enabled, intervalMs, urls) {
        val loader = context.imageLoader
        var failures = 0
        var candidate = shown?.let { it + 1 } ?: 0
        while (true) {
            if (shown != null) {
                if (!enabled) return@LaunchedEffect
                delay(intervalMs)
            }
            when (val result = loader.execute(requestFor(candidate))) {
                is SuccessResult -> {
                    shown = candidate % urls.size
                    failures = 0
                    report(null)
                }
                is ErrorResult -> {
                    report(describe(result.throwable))
                    delay(RetryDelaysMs[failures.coerceAtMost(RetryDelaysMs.lastIndex)])
                    failures++
                }
            }
            candidate = (candidate + 1) % urls.size
        }
    }

    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Palette.Ink, Palette.InkLight)))) {
        Crossfade(
            targetState = shown,
            animationSpec = tween(CROSSFADE_MS),
            modifier = Modifier.fillMaxSize(),
            label = "backgroundCrossfade",
        ) { i ->
            if (i != null) {
                // Already decoded into Coil's memory cache by the loop above
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
}

/** A short, readable reason for a failed download, e.g. "SSLHandshakeException: …". */
fun describe(error: Throwable): String {
    val root = generateSequence(error) { it.cause }.last()
    return listOfNotNull(root::class.java.simpleName, root.message?.take(80)).joinToString(": ")
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
                    0.3f to Color(0x4D000000),
                    0.55f to Color(0x66000000),
                    1f to Color(0xE6000000),
                ),
            ),
    )
}
