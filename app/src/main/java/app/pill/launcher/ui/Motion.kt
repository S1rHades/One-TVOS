package app.pill.launcher.ui

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Glass motion: the one way things move in the launcher.
 *
 * - **Dissolve.** Things arrive frosted (blurred, see-through, a few dp low) and come into
 *   focus; they leave the same way, faster.
 * - **Wave.** A group arrives in reading order, one step after another.
 * - **Rack focus.** While one screen gives way to another the photo goes soft.
 * - **Glide.** A selection marker slides between items rather than jumping.
 *
 * Every duration and curve lives here, so screens can't drift apart.
 */
object Motion {
    /** Feedback: a marker gliding, a colour change. */
    const val Quick = 220
    /** Things leaving. */
    const val Exit = 280
    /** Things arriving. */
    const val Enter = 450
    /** A whole page: its frame, then its sections in a wave. */
    const val Page = 600
    /** Between two steps of a wave. */
    const val Step = 50
    /** A wave stops getting longer after this many steps, so a long list never waits. */
    const val MaxSteps = 8

    /** How frosted something is at the start of a dissolve. */
    val Blur = 12.dp
    /** How far below its place something starts. */
    val Rise = 8.dp

    /** Things arriving: fast, then settling. */
    val EnterEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    /** Things leaving: gathering speed as they go. */
    val ExitEasing: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    /** For a selection marker gliding to its next item. */
    fun <T> glide(): AnimationSpec<T> = tween(Quick + 80, easing = EnterEasing)

    /** The whole of a page's entrance: the last step of a full wave finishing. */
    val RevealMs: Int get() = Enter + Step * MaxSteps
}

/** Delay of wave step [step], capped at [Motion.MaxSteps]. */
fun waveDelay(step: Int): Int = Motion.Step * step.coerceIn(0, Motion.MaxSteps)

/**
 * How far step [step] of a wave has come in, 0..1 (eased), [elapsedMs] after the wave
 * started. Pure, so the wave's order and timing can be unit tested.
 */
fun waveProgress(elapsedMs: Float, step: Int): Float {
    val t = ((elapsedMs - waveDelay(step)) / Motion.Enter).coerceIn(0f, 1f)
    return Motion.EnterEasing.transform(t)
}

/**
 * The time since the current page (or other group) started its wave, in ms. Outside a
 * page it reads as long finished, so [reveal] leaves things as they are.
 */
val LocalReveal = compositionLocalOf<State<Float>> { Finished }

private val Finished = mutableFloatStateOf(Float.MAX_VALUE)

/**
 * Dissolves this in as step [step] of the surrounding wave ([LocalReveal]). Read in the
 * draw phase only, so a wave redraws but never recomposes or relayouts.
 */
fun Modifier.reveal(step: Int): Modifier = composed {
    val clock = LocalReveal.current
    dissolve { waveProgress(clock.value, step) }
}

/**
 * Shows this as far as [shown] says: 1 in place and sharp, 0 gone (see-through, a little
 * low and frosted). Blur needs Android 12; older TVs get the fade and the rise alone.
 */
fun Modifier.dissolve(shown: () -> Float): Modifier = graphicsLayer {
    val p = shown().coerceIn(0f, 1f)
    alpha = p
    if (p < 1f) {
        translationY = (1f - p) * Motion.Rise.toPx()
        val s = 0.985f + 0.015f * p
        scaleX = s
        scaleY = s
        val b = (1f - p) * Motion.Blur.toPx()
        renderEffect = if (b > 0.5f) BlurEffect(b, b, TileMode.Decal) else null
    } else {
        translationY = 0f
        scaleX = 1f
        scaleY = 1f
        renderEffect = null
    }
}

/**
 * Part of a group that dissolves together in a wave, driven by one value: [shown] 0..1 for
 * the whole group, this being [step] of [steps]. Earlier steps lead, later ones follow, and
 * all are fully in at 1 and fully out at 0.
 */
fun Modifier.dissolveStep(shown: () -> Float, step: Int, steps: Int): Modifier = dissolve {
    staggered(shown(), step, steps)
}

/** [dissolveStep]'s mapping, pulled out for tests. */
fun staggered(shown: Float, step: Int, steps: Int, spread: Float = 0.45f): Float {
    if (steps <= 1) return shown
    val lead = spread * step.coerceIn(0, steps - 1) / (steps - 1)
    return ((shown * (1f + spread) - lead) / 1f).coerceIn(0f, 1f)
}
