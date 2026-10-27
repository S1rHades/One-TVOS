package app.pill.launcher.ui

import androidx.compose.animation.AnimatedContent
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
import kotlin.math.sin

private val SunColor = Color(0xFFFFD43B)
private val MoonColor = Color(0xFFE9ECF5)
private val CloudColor = Color(0xFFF1F3F8)
private val CloudShade = Color(0xFFC9CFDC)
private val RainColor = Color(0xFF74C0FC)

/** Condition icon, temperature and today's range. Fades between readings. */
@Composable
fun WeatherWidget(weather: Weather, mode: WeatherMode, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = weather,
        transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(600)) },
        modifier = modifier,
        label = "weather",
    ) { w ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            WeatherIcon(skyFor(w.code), w.isDay, 60.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    formatTemp(w.tempC, mode),
                    style = TextStyle(
                        color = Palette.TextPrimary,
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Light,
                        shadow = TextShadow,
                    ),
                )
                Text(
                    listOf(describe(w.code), w.place).filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = TextStyle(color = Palette.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Medium, shadow = TextShadow),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "H ${formatTemp(w.highC, mode)}   L ${formatTemp(w.lowC, mode)}",
                    style = TextStyle(color = Palette.TextTertiary, fontSize = 16.sp, shadow = TextShadow),
                )
            }
        }
    }
}

/** Flat, two-tone weather glyphs drawn from simple shapes so they stay sharp at any size. */
@Composable
fun WeatherIcon(sky: Sky, isDay: Boolean, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        when (sky) {
            Sky.Clear -> if (isDay) sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f, s) else moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f)
            Sky.PartlyCloudy -> {
                if (isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.15f, s * 0.72f) else moon(Offset(s * 0.38f, s * 0.36f), s * 0.2f)
                cloud(s, dy = s * 0.08f)
            }
            Sky.Cloudy -> {
                translate(s * 0.16f, -s * 0.1f) { cloud(s * 0.8f, color = CloudShade) }
                cloud(s, dy = s * 0.06f)
            }
            Sky.Fog -> {
                cloud(s, dy = -s * 0.1f)
                for (i in 0..1) {
                    val y = s * (0.76f + i * 0.12f)
                    drawLine(CloudShade, Offset(s * (0.18f + i * 0.1f), y), Offset(s * (0.82f - i * 0.06f), y), s * 0.06f, StrokeCap.Round)
                }
            }
            Sky.Rain -> {
                cloud(s, dy = -s * 0.1f)
                for (i in 0..2) {
                    val x = s * (0.36f + i * 0.15f)
                    drawLine(RainColor, Offset(x, s * 0.72f), Offset(x - s * 0.06f, s * 0.9f), s * 0.06f, StrokeCap.Round)
                }
            }
            Sky.Snow -> {
                cloud(s, dy = -s * 0.1f)
                for (i in 0..2) {
                    drawCircle(Color.White, s * 0.045f, Offset(s * (0.34f + i * 0.16f), s * (0.8f + (i % 2) * 0.08f)))
                }
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
                drawPath(bolt, SunColor)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float, span: Float) {
    drawCircle(SunColor, radius, center)
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
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
private fun DrawScope.cloud(s: Float, dy: Float = 0f, color: Color = CloudColor) {
    val left = Path().apply { addOval(Rect(Offset(s * 0.34f, s * 0.6f + dy), s * 0.15f)) }
    val top = Path().apply { addOval(Rect(Offset(s * 0.54f, s * 0.5f + dy), s * 0.21f)) }
    val right = Path().apply { addOval(Rect(Offset(s * 0.74f, s * 0.62f + dy), s * 0.13f)) }
    val base = Path().apply {
        addRoundRect(RoundRect(s * 0.19f, s * 0.6f + dy, s * 0.87f, s * 0.75f + dy, CornerRadius(s * 0.075f)))
    }
    val upper = Path().apply { op(left, top, PathOperation.Union) }
    val puffs = Path().apply { op(upper, right, PathOperation.Union) }
    drawPath(Path().apply { op(puffs, base, PathOperation.Union) }, color)
}
