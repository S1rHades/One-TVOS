package app.pill.launcher.ui

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Superellipse ("squircle") shape. Higher n = squarer corners. */
class SquircleShape(private val n: Double = 5.0) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val a = size.width / 2.0
        val b = size.height / 2.0
        val steps = 160
        for (i in 0..steps) {
            val t = 2.0 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val x = a + a * sign(c) * abs(c).pow(2.0 / n)
            val y = b + b * sign(s) * abs(s).pow(2.0 / n)
            if (i == 0) path.moveTo(x.toFloat(), y.toFloat()) else path.lineTo(x.toFloat(), y.toFloat())
        }
        path.close()
        return Outline.Generic(path)
    }
}

val Squircle = SquircleShape()
