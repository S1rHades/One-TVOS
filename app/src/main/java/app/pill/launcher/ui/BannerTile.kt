package app.pill.launcher.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.pill.launcher.AppEntry

/** A TV banner's 16:9 card, sized like Google TV's app row. */
val BannerWidth = 196.dp
val BannerHeight = 110.dp
private val CardShape = RoundedCornerShape(14.dp)

/** Card plus side air, so the focused card's glow and outline are never clipped. */
val BannerTileWidth = BannerWidth + 12.dp

/**
 * A favourite shown as wide artwork: the app's own TV banner, or one generated from its
 * icon and colour. On focus it springs to 1.10x from its base, glows in the app's colour,
 * gains a white outline and a light sheen, and its name appears below.
 */
@Composable
fun BannerTile(
    app: AppEntry,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: (Boolean) -> Unit = {},
) {
    val input = rememberTileInput(onOpen, onTogglePin, onFocus)
    val active = input.active
    val accent = Color(app.accent)
    val scale by animateFloatAsState(if (active) 1.1f else 1f, FocusSpring, label = "bannerScale")
    val shine by animateFloatAsState(if (active) 1f else 0f, tween(200), label = "bannerShine")
    val lift by animateDpAsState(if (active) 24.dp else 4.dp, label = "bannerLift")

    Column(
        modifier
            .zIndex(if (active) 1f else 0f)
            .width(BannerTileWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0.5f, 0.62f)
            }
            .then(input.modifier)
            .padding(horizontal = 6.dp, vertical = 6.dp),
    ) {
        Box(
            Modifier
                .size(BannerWidth, BannerHeight)
                .shadow(lift, CardShape, ambientColor = if (active) accent else Color.Black, spotColor = if (active) accent else Color.Black)
                .clip(CardShape),
        ) {
            val banner = app.banner
            if (banner != null) {
                Image(banner, contentDescription = app.label, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                GeneratedBanner(app)
            }
            // A light sheen across the top left while focused
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = shine }
                    .background(Brush.linearGradient(listOf(Color(0x47FFFFFF), Color.Transparent), end = Offset(260f, 220f))),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .border(2.5.dp, Color.White.copy(alpha = shine), CardShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f * (1 - shine)), CardShape),
            )
        }
        // The name shows only for the focused card, as on Google TV; the space stays reserved
        Text(
            app.label,
            style = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 15.sp, fontWeight = FontWeight(700), shadow = TextShadow),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp, top = 10.dp).height(20.dp).graphicsLayer { alpha = shine },
        )
    }
}

/** For apps without a TV banner: their icon and name on a gradient in their own colour. */
@Composable
private fun GeneratedBanner(app: AppEntry) {
    val accent = Color(app.accent)
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(lerp(accent, Color.White, 0.18f), lerp(accent, Color.Black, 0.45f)),
                    center = Offset(Float.POSITIVE_INFINITY, 0f),
                    radius = 700f,
                ),
            ),
    ) {
        Image(
            app.icon,
            contentDescription = app.label,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(56.dp)
                .shadow(8.dp, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp)),
        )
        Text(
            app.label,
            style = TextStyle(fontFamily = Flex, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight(750), letterSpacing = (-0.2).sp, shadow = TextShadow),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 86.dp, end = 12.dp),
        )
    }
}
