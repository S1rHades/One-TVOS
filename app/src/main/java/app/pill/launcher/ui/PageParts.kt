package app.pill.launcher.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The glass cards a dashboard page is made of. */
val PageCardShape = RoundedCornerShape(20.dp)

/** A page's name, large, with a quiet line after it: where the data is from, how to leave. */
@Composable
fun PageTitle(title: String, detail: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.Bottom) {
        Text(
            title,
            style = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight(500), shadow = TextShadow),
            maxLines = 1,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            detail,
            style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = 15.sp, shadow = TextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}

/** A frosted card on a dashboard page, dissolving in as wave step [step]. */
@Composable
fun PageCard(step: Int, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .reveal(step)
            .glass(PageCardShape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        content = content,
    )
}

/** A small trend line through [values], scaled to fill the space it's given. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) return
    Canvas(modifier) {
        val lo = values.min()
        val span = (values.max() - lo).takeIf { it > 0.0 } ?: 1.0
        val stroke = 2.dp.toPx()
        val path = Path()
        for ((i, v) in values.withIndex()) {
            val x = i * size.width / (values.size - 1)
            val y = size.height - stroke - ((v - lo) / span).toFloat() * (size.height - stroke * 2)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = values.last()
        drawCircle(color, stroke * 1.4f, Offset(size.width, size.height - stroke - ((last - lo) / span).toFloat() * (size.height - stroke * 2)))
    }
}
