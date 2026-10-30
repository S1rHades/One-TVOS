package app.pill.launcher.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.Sky
import app.pill.launcher.Weather
import app.pill.launcher.WeatherMode
import app.pill.launcher.describe
import app.pill.launcher.formatTemp
import app.pill.launcher.skyFor
import kotlin.math.cos
import kotlin.math.PI
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Brush
import kotlin.math.sin
import kotlin.math.exp
import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis

private val SunColor = Color(0xFFFFD43B)
private val MoonColor = Color(0xFFE9ECF5)
private val CloudColor = Color(0xFFF1F3F8)
private val CloudShade = Color(0xFFC9CFDC)
private val RainColor = Color(0xFF74C0FC)

/** Condition icon, temperature and today's range, for the weather card. Fades between readings. */
@Composable
fun WeatherWidget(weather: Weather, mode: WeatherMode, compact: Boolean, modifier: Modifier = Modifier, animate: Boolean = true) {
    // One slow, continuous loop drives every part of the icon (see WeatherIcon). It pauses
    // where it is on the idle screen and resumes from there; none with "remove animations" on.
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animate) {
        if (!animate || reduceMotion) return@LaunchedEffect
        var last = -1L
        while (true) {
            withInfiniteAnimationFrameMillis { ms ->
                if (last >= 0) phase.floatValue = (phase.floatValue + (ms - last) / IconCycleMs) % 1f
                last = ms
            }
        }
    }

    AnimatedContent(
        targetState = weather,
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
        modifier = modifier,
        label = "weather",
    ) { w ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(skyFor(w.code), w.isDay, if (compact) 44.dp else 52.dp, motion = { phase.floatValue })
            Spacer(Modifier.width(if (compact) 10.dp else 14.dp))
            Column {
                Text(
                    formatTemp(w.tempC, mode),
                    style = TextStyle(
                        fontFamily = Flex,
                        color = Palette.TextPrimary,
                        fontSize = if (compact) 34.sp else 40.sp,
                        fontWeight = FontWeight.Light,
                    ),
                    maxLines = 1,
                    softWrap = false,
                )
                // The city always; the condition too when there's room (the icon shows it anyway)
                Text(
                    if (compact) w.place.ifBlank { describe(w.code) } else listOf(w.place, describe(w.code)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (compact) "${formatTemp(w.highC, mode)} / ${formatTemp(w.lowC, mode)}" else "H ${formatTemp(w.highC, mode)}   L ${formatTemp(w.lowC, mode)}",
                    style = TextStyle(fontFamily = Flex, color = Palette.TextTertiary, fontSize = if (compact) 13.sp else 15.sp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** One loop of the icons' motion. Every movement repeats a whole number of times in it. */
private const val IconCycleMs = 12_000f

/**
 * Weather glyphs drawn from soft shapes so they stay sharp at any size. With [motion] they
 * move all the time, gently: the sun's glow breathes and its rays turn and shimmer, cloud
 * puffs swell and drift, rain and snow fall, fog rolls and lightning flickers. Without it
 * (the forecast page) they hold their resting pose.
 */
@Composable
fun WeatherIcon(
    sky: Sky,
    isDay: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Position in the motion loop (0..1); read while drawing, so only the icon redraws. */
    motion: () -> Float = { 0f },
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val p = motion()
        val w = (p * 2 * PI).toFloat()
        when (sky) {
            Sky.Clear -> if (isDay) sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f, s, w) else moon(Offset(s * 0.48f, s * 0.52f), s * 0.28f, s, w)
            Sky.PartlyCloudy -> {
                if (isDay) sun(Offset(s * 0.38f, s * 0.36f), s * 0.15f, s * 0.72f, w) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.2f, s * 0.7f, w)
                cloud(s, w, dy = s * 0.08f, dx = sin(w) * s * 0.05f)
            }
            Sky.Cloudy -> {
                // Two layers drifting out of step, the far one slower and greyer
                cloud(s * 0.8f, w + 2f, dx = s * 0.16f + sin(w + PI.toFloat() / 2) * s * 0.03f, dy = -s * 0.1f, color = CloudShade)
                cloud(s, w, dy = s * 0.06f, dx = sin(w) * s * 0.045f)
            }
            Sky.Fog -> {
                cloud(s, w, dy = -s * 0.1f)
                for (i in 0..2) {
                    val y = s * (0.72f + i * 0.1f)
                    val dx = sin(w + i * 2.1f) * s * 0.06f
                    val alpha = 0.55f + 0.3f * sin(w * 2 + i * 1.7f)
                    drawLine(CloudShade.copy(alpha = alpha), Offset(s * (0.16f + i * 0.06f) + dx, y), Offset(s * (0.84f - i * 0.08f) + dx, y), s * 0.055f, StrokeCap.Round)
                }
            }
            Sky.Rain -> {
                for (i in 0..3) {
                    // Eight falls per loop, staggered; each drop fades in under the cloud and out below
                    val fall = (p * 8 + i * 0.27f) % 1f
                    val x = s * (0.32f + i * 0.13f) - fall * s * 0.05f
                    val y = s * 0.66f + fall * s * 0.2f
                    val alpha = sin(fall * PI).toFloat()
                    drawLine(RainColor.copy(alpha = alpha), Offset(x, y), Offset(x - s * 0.04f, y + s * 0.13f), s * 0.05f, StrokeCap.Round)
                }
                cloud(s, w, dy = -s * 0.1f, dx = sin(w) * s * 0.02f)
            }
            Sky.Snow -> {
                for (i in 0..3) {
                    val fall = (p * 3 + i * 0.25f) % 1f
                    val x = s * (0.3f + i * 0.14f) + sin((fall * 2 + i * 0.5f) * PI).toFloat() * s * 0.035f
                    drawCircle(Color.White.copy(alpha = sin(fall * PI).toFloat()), s * 0.042f, Offset(x, s * (0.68f + fall * 0.26f)))
                }
                cloud(s, w, dy = -s * 0.1f, dx = sin(w) * s * 0.02f)
            }
            Sky.Storm -> {
                // Two flickers a loop, each a quick bright flash fading out
                val flash = maxOf(bump(p, 0.42f, 0.012f), 0.7f * bump(p, 0.47f, 0.01f), 0.5f * bump(p, 0.86f, 0.012f))
                if (flash > 0.01f) {
                    drawCircle(
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f * flash), Color.Transparent), Offset(s * 0.5f, s * 0.55f), s * 0.5f),
                        s * 0.5f,
                        Offset(s * 0.5f, s * 0.55f),
                    )
                }
                cloud(s, w, dy = -s * 0.1f, color = lerp(CloudShade, Color.White, flash * 0.6f), shade = Color(0xFF8A93A6))
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.6f)
                    lineTo(s * 0.4f, s * 0.8f)
                    lineTo(s * 0.5f, s * 0.8f)
                    lineTo(s * 0.44f, s * 0.97f)
                    lineTo(s * 0.62f, s * 0.74f)
                    lineTo(s * 0.52f, s * 0.74f)
                    close()
                }
                drawPath(bolt, lerp(SunColor, Color.White, flash))
            }
        }
    }
}

/** A smooth pulse around [at] in the loop, [width] wide. */
private fun bump(p: Float, at: Float, width: Float): Float {
    val d = (p - at) / width
    return exp(-d * d)
}

/** The sun: a breathing glow, a warm core, and eight rays that turn slowly and shimmer. */
private fun DrawScope.sun(center: Offset, radius: Float, span: Float, w: Float) {
    val glow = radius * (2.1f + 0.15f * sin(w * 2))
    drawCircle(
        Brush.radialGradient(listOf(SunColor.copy(alpha = 0.32f + 0.08f * sin(w * 2)), Color.Transparent), center, glow),
        glow,
        center,
    )
    // 45° a loop: the rays are 45° apart, so the loop joins up seamlessly
    val turn = w / (2 * PI.toFloat()) * 45f
    for (k in 0 until 8) {
        val deg = k * 45f + turn
        val a = Math.toRadians(deg.toDouble())
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        // Each ray's length follows where it points, so the shimmer travels round with the turn
        val length = span * (0.085f + 0.03f * sin(Math.toRadians(deg * 2.0).toFloat() + w * 3))
        drawLine(SunColor, center + d * (radius * 1.45f), center + d * (radius * 1.45f + length), span * 0.05f, StrokeCap.Round)
    }
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFF6CC), SunColor, Color(0xFFFFB627)), center = center - Offset(radius * 0.35f, radius * 0.35f), radius = radius * 1.7f),
        radius,
        center,
    )
}

/** The moon: a crescent in a soft glow, with two stars twinkling beside it. */
private fun DrawScope.moon(center: Offset, radius: Float, span: Float, w: Float) {
    val glow = radius * (1.9f + 0.12f * sin(w * 2))
    drawCircle(Brush.radialGradient(listOf(MoonColor.copy(alpha = 0.22f), Color.Transparent), center, glow), glow, center)
    val disc = Path().apply { addOval(Rect(center, radius)) }
    val bite = Path().apply { addOval(Rect(center + Offset(radius * 0.55f, -radius * 0.4f), radius * 0.85f)) }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, MoonColor)
    for ((i, star) in listOf(Offset(0.86f, 0.2f), Offset(0.78f, 0.46f)).withIndex()) {
        val twinkle = 0.5f + 0.5f * sin(w * 3 + i * 2.4f)
        drawCircle(Color.White.copy(alpha = 0.25f + 0.75f * twinkle), span * (0.018f + 0.012f * twinkle), Offset(span * star.x, span * star.y))
    }
}

/**
 * A cloud filling the lower part of an [s]-sized square, moved by [dx] and [dy]. Its three
 * puffs swell and settle out of step with [w]. Every part shares one top-lit gradient, so
 * the overlaps don't show.
 */
private fun DrawScope.cloud(s: Float, w: Float, dx: Float = 0f, dy: Float = 0f, color: Color = CloudColor, shade: Color = CloudShade) {
    val brush = Brush.verticalGradient(listOf(color, lerp(color, shade, 0.65f)), startY = s * 0.3f + dy, endY = s * 0.78f + dy)
    translate(dx, 0f) {
        // A faint shadow underneath lifts it off the card
        drawOval(Color.Black.copy(alpha = 0.1f), topLeft = Offset(s * 0.22f, s * 0.71f + dy), size = Size(s * 0.62f, s * 0.1f))
        for ((i, puff) in Puffs.withIndex()) {
            val r = s * puff.r * (1f + 0.04f * sin(w * 2 + i * 2.1f))
            drawCircle(brush, r, Offset(s * puff.x, s * puff.y + dy))
        }
        drawRoundRect(brush, Offset(s * 0.19f, s * 0.6f + dy), Size(s * 0.68f, s * 0.15f), CornerRadius(s * 0.075f))
    }
}

/** One of a cloud's puffs: its centre and radius, as fractions of the cloud's size. */
private class Puff(val x: Float, val y: Float, val r: Float)

private val Puffs = listOf(Puff(0.34f, 0.6f, 0.15f), Puff(0.54f, 0.5f, 0.21f), Puff(0.74f, 0.62f, 0.13f))
