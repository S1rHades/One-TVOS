package app.pill.launcher.ui

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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

private val SunColor = Color(0xFFFFD43B)
private val MoonColor = Color(0xFFE9ECF5)
private val CloudColor = Color(0xFFF1F3F8)
private val CloudShade = Color(0xFFC9CFDC)
private val RainColor = Color(0xFF74C0FC)

/** Condition icon, temperature and today's range, for the weather card. Fades between readings. */
@Composable
fun WeatherWidget(weather: Weather, mode: WeatherMode, compact: Boolean, modifier: Modifier = Modifier, animate: Boolean = true) {
    // The icon moves in short bursts (on each new reading, then every 40 s) rather than
    // constantly, so the screen can rest between them. None with "remove animations" on.
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val progress = remember { Animatable(0f) }
    var cycles by remember { mutableIntStateOf(0) }
    LaunchedEffect(animate, weather) {
        if (!animate || reduceMotion) return@LaunchedEffect
        try {
            while (true) {
                progress.animateTo(1f, tween(4000, easing = LinearEasing))
                cycles++
                progress.snapTo(0f)
                delay(40_000)
            }
        } finally {
            // Paused mid-burst (idle screen): settle back to the resting pose
            withContext(NonCancellable) { progress.snapTo(0f) }
        }
    }

    AnimatedContent(
        targetState = weather,
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
        modifier = modifier,
        label = "weather",
    ) { w ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(skyFor(w.code), w.isDay, if (compact) 44.dp else 52.dp, motion = { progress.value }, cycles = { cycles })
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
                )
                Text(
                    listOf(describe(w.code), w.place.takeUnless { compact }.orEmpty()).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = if (compact) 14.sp else 16.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "H ${formatTemp(w.highC, mode)}   L ${formatTemp(w.lowC, mode)}",
                    style = TextStyle(fontFamily = Flex, color = Palette.TextTertiary, fontSize = if (compact) 13.sp else 15.sp),
                )
            }
        }
    }
}

/** Flat, two-tone weather glyphs drawn from simple shapes so they stay sharp at any size. */
@Composable
fun WeatherIcon(
    sky: Sky,
    isDay: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
    /** Progress through the current motion cycle (0..1); read while drawing. */
    motion: () -> Float = { 0f },
    /** Completed cycles, so the sun keeps turning the same way across cycles. */
    cycles: () -> Int = { 0 },
) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val t = motion()
        // Out and back over one cycle, so a resting icon always sits in its home position
        val sway = sin(t * 2 * PI).toFloat()
        val turn = (cycles() + t) * 45f
        when (sky) {
            Sky.Clear -> if (isDay) sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f, s, turn) else moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f)
            Sky.PartlyCloudy -> {
                if (isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.15f, s * 0.72f, turn) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.2f)
                cloud(s, dy = s * 0.08f, dx = sway * s * 0.04f)
            }
            Sky.Cloudy -> {
                translate(s * 0.16f - sway * s * 0.03f, -s * 0.1f) { cloud(s * 0.8f, color = CloudShade) }
                cloud(s, dy = s * 0.06f, dx = sway * s * 0.04f)
            }
            Sky.Fog -> {
                cloud(s, dy = -s * 0.1f)
                for (i in 0..1) {
                    val y = s * (0.76f + i * 0.12f)
                    val dx = sway * s * (if (i == 0) 0.05f else -0.05f)
                    drawLine(CloudShade, Offset(s * (0.18f + i * 0.1f) + dx, y), Offset(s * (0.82f - i * 0.06f) + dx, y), s * 0.06f, StrokeCap.Round)
                }
            }
            Sky.Rain -> {
                for (i in 0..2) {
                    // Three falls per cycle, staggered; each streak fades as it leaves
                    val fall = (t * 3 + i * 0.33f) % 1f
                    val x = s * (0.36f + i * 0.15f) - fall * s * 0.04f
                    val y = s * 0.7f + fall * s * 0.12f
                    drawLine(RainColor.copy(alpha = 1f - fall * 0.7f), Offset(x, y), Offset(x - s * 0.06f, y + s * 0.18f), s * 0.06f, StrokeCap.Round)
                }
                cloud(s, dy = -s * 0.1f)
            }
            Sky.Snow -> {
                for (i in 0..2) {
                    val fall = (t * 1.5f + i * 0.33f) % 1f
                    val x = s * (0.34f + i * 0.16f) + sin((fall + i) * 2 * PI).toFloat() * s * 0.03f
                    drawCircle(Color.White.copy(alpha = 1f - fall * 0.6f), s * 0.045f, Offset(x, s * (0.74f + fall * 0.2f)))
                }
                cloud(s, dy = -s * 0.1f)
            }
            Sky.Storm -> {
                cloud(s, dy = -s * 0.1f)
                val bolt = Path().apply {
                    moveTo(s * 0.54f, s * 0.6f)
                    lineTo(s * 0.4f, s * 0.8f)
                    lineTo(s * 0.5f, s * 0.8f)
                    lineTo(s * 0.44f, s * 0.97f)
                    lineTo(s * 0.62f, s * 0.74f)
                    lineTo(s * 0.52f, s * 0.74f)
                    close()
                }
                // Two quick flashes per cycle
                val flash = t in 0.3f..0.36f || t in 0.44f..0.48f
                if (flash) drawCircle(SunColor.copy(alpha = 0.35f), s * 0.3f, Offset(s * 0.5f, s * 0.75f))
                drawPath(bolt, if (flash) Color.White else SunColor)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, span: Float, turn: Float = 0f) {
    // A warm core fading to gold, like the light it stands for
    drawCircle(
        Brush.radialGradient(listOf(Color(0xFFFFF3BF), SunColor), center = center - Offset(radius * 0.3f, radius * 0.3f), radius = radius * 1.4f),
        radius,
        center,
    )
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0 + turn)
        val d = Offset(cos(a).toFloat(), sin(a).toFloat())
        drawLine(
            SunColor,
            center + d * (radius * 1.45f),
            center + d * (radius * 1.45f + span * 0.1f),
            span * 0.055f,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(center: Offset, radius: Float) {
    val disc = Path().apply { addOval(Rect(center, radius)) }
    val bite = Path().apply { addOval(Rect(center + Offset(radius * 0.55f, -radius * 0.4f), radius * 0.85f)) }
    drawPath(Path().apply { op(disc, bite, PathOperation.Difference) }, MoonColor)
}

/** A cloud filling the lower part of an [s]-sized square, moved down by [dy]. */
private fun DrawScope.cloud(s: Float, dy: Float = 0f, color: Color = CloudColor, dx: Float = 0f) {
    translate(dx, 0f) { cloudShape(s, dy, color) }
}

private fun DrawScope.cloudShape(s: Float, dy: Float, color: Color) {
    val left = Path().apply { addOval(Rect(Offset(s * 0.34f, s * 0.6f + dy), s * 0.15f)) }
    val top = Path().apply { addOval(Rect(Offset(s * 0.54f, s * 0.5f + dy), s * 0.21f)) }
    val right = Path().apply { addOval(Rect(Offset(s * 0.74f, s * 0.62f + dy), s * 0.13f)) }
    val base = Path().apply {
        addRoundRect(RoundRect(s * 0.19f, s * 0.6f + dy, s * 0.87f, s * 0.75f + dy, CornerRadius(s * 0.075f)))
    }
    val upper = Path().apply { op(left, top, PathOperation.Union) }
    val puffs = Path().apply { op(upper, right, PathOperation.Union) }
    // Lit from above: full colour on top, a cooler shade underneath
    val shape = Path().apply { op(puffs, base, PathOperation.Union) }
    drawPath(shape, Brush.verticalGradient(listOf(color, lerp(color, CloudShade, 0.6f)), startY = s * 0.3f + dy, endY = s * 0.78f + dy))
}
