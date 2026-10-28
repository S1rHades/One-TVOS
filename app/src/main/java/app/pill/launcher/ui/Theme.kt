package app.pill.launcher.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.dp

/** Shared colours, so every surface and text tint comes from one place. */
object Palette {
    val Ink = Color(0xFF0B0F1A)
    val InkLight = Color(0xFF1B2540)
    val Panel = Color(0xF5121826)
    val Scrim = Color(0x99000000)

    val TextPrimary = Color.White
    val TextSecondary = Color(0xCCFFFFFF)
    val TextTertiary = Color(0xA6FFFFFF)

    val Glass = Color(0x33FFFFFF)
    val GlassStrong = Color(0x4DFFFFFF)
    val Hairline = Color(0x33FFFFFF)
    val Focus = Color.White
    val Accent = Color(0xFF4C6EF5)
}

/** Soft drop shadow so text stays readable over bright photos. */
val TextShadow = Shadow(Color(0x99000000), Offset(0f, 3f), 12f)

/** Shared spacing. Screen margins keep content inside the TV overscan-safe area. */
object Spacing {
    val ScreenH = 48.dp
    val ScreenV = 27.dp
    /** Space from a tile's edge to its icon (room for the focus ring); headers are indented the same so everything lines up. */
    val Tile = 12.dp
}
