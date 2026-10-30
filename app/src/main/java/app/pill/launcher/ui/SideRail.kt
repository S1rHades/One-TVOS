package app.pill.launcher.ui

import android.text.format.DateFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The places the rail leads to, top to bottom. */
enum class RailItem(val label: String) { Home("Home"), Apps("Apps"), Search("Search"), Internet("Internet"), Settings("Settings") }

private val ItemSize = 44.dp
private val ItemGap = 6.dp
private val RailPad = 6.dp

/** Room the rail takes at the left of the screen, its margin included. */
val RailRoom = 18.dp + ItemSize + RailPad * 2 + 16.dp

private val OfflineRed = Color(0xFFFF8787)

/**
 * The rail down the left of Home: Home, All apps, Search, the Internet page and Settings.
 * A disc marks where you are; while the rail has focus it turns white, glides to the
 * focused item, and the item's name appears beside it.
 */
@Composable
fun SideRail(
    current: RailItem,
    onSelect: (RailItem) -> Unit,
    settingsFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val network by rememberNetworkStatus()
    // The focused item, or -1 while focus is elsewhere
    var focused by remember { mutableIntStateOf(-1) }
    val active = focused >= 0
    val at = if (active) focused else current.ordinal
    val markerY by animateDpAsState(RailPad + (ItemSize + ItemGap) * at, Motion.glide(), label = "railMarker")
    val markerColor by animateColorAsState(if (active) Palette.Focus else Color(0x38FFFFFF), tween(Motion.Quick), label = "railMarkerColor")
    val labelShown by animateFloatAsState(if (active) 1f else 0f, tween(Motion.Quick), label = "railLabel")
    var labelAt by remember { mutableIntStateOf(0) }
    if (active) labelAt = focused

    Box(modifier) {
        Box(Modifier.glass(RoundedCornerShape(50)).padding(RailPad)) {
            Box(
                Modifier
                    .offset(y = markerY - RailPad)
                    .size(ItemSize)
                    .shadow(if (active) 12.dp else 0.dp, CircleShape, ambientColor = Color.White, spotColor = Color.White)
                    .background(markerColor, CircleShape),
            )
            Column {
                for (item in RailItem.entries) {
                    val on = focused == item.ordinal
                    val tint by animateColorAsState(if (on) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "railTint")
                    Box(
                        Modifier
                            .padding(top = if (item.ordinal == 0) 0.dp else ItemGap)
                            .size(ItemSize)
                            .clip50()
                            .semantics { contentDescription = if (item == RailItem.Internet) "Internet: " + network.label else item.label }
                            .onFocusChanged { if (it.isFocused) focused = item.ordinal else if (focused == item.ordinal) focused = -1 }
                            .then(if (item == RailItem.Settings) Modifier.focusRequester(settingsFocus) else Modifier)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(item) },
                        contentAlignment = Alignment.Center,
                    ) {
                        when (item) {
                            RailItem.Home -> HomeIcon(tint, 24.dp)
                            RailItem.Apps -> AppsIcon(tint, 22.dp)
                            RailItem.Search -> SearchIcon(tint, 24.dp)
                            RailItem.Internet -> WifiIcon(network, if (network.link == Link.Offline && !on) OfflineRed else tint, 24.dp)
                            RailItem.Settings -> GearIcon(tint, 25.dp)
                        }
                    }
                }
            }
        }
        // The focused item's name, beside the rail (outside its bounds, so it takes no room)
        Box(
            Modifier
                .offset(x = ItemSize + RailPad * 2 + 10.dp, y = RailPad + (ItemSize + ItemGap) * labelAt + (ItemSize - 30.dp) / 2)
                .graphicsLayer {
                    alpha = labelShown
                    translationX = (1f - labelShown) * -8.dp.toPx()
                }
                .shadow(10.dp, RoundedCornerShape(50))
                .background(Palette.Focus, RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 6.dp),
        ) {
            Text(RailItem.entries[labelAt].label, color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight(650), maxLines = 1)
        }
    }
}

private fun Modifier.clip50(): Modifier = this.then(Modifier.graphicsLayer { shape = CircleShape; clip = true })

/** A house outline, in the rail's stroke weight. */
@Composable
private fun HomeIcon(tint: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = size.minDimension
        val stroke = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val path = Path().apply {
            moveTo(s * 0.14f, s * 0.46f)
            lineTo(s * 0.5f, s * 0.15f)
            lineTo(s * 0.86f, s * 0.46f)
            moveTo(s * 0.24f, s * 0.38f)
            lineTo(s * 0.24f, s * 0.86f)
            lineTo(s * 0.76f, s * 0.86f)
            lineTo(s * 0.76f, s * 0.38f)
            moveTo(s * 0.42f, s * 0.86f)
            lineTo(s * 0.42f, s * 0.62f)
            lineTo(s * 0.58f, s * 0.62f)
            lineTo(s * 0.58f, s * 0.86f)
        }
        drawPath(path, tint, style = stroke)
    }
}

/** Four rounded squares: all apps. */
@Composable
private fun AppsIcon(tint: Color, iconSize: Dp) {
    Canvas(Modifier.size(iconSize)) {
        val s = size.minDimension
        val w = s * 0.36f
        val stroke = Stroke(width = s * 0.09f)
        for (x in listOf(s * 0.08f, s * 0.56f)) for (y in listOf(s * 0.08f, s * 0.56f)) {
            drawRoundRect(tint, Offset(x, y), Size(w, w), CornerRadius(s * 0.08f), style = stroke)
        }
    }
}

/** "Good evening" and so on, by the hour. */
fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    in 18..22 -> "Good evening"
    else -> "Good night"
}

/** The greeting above the widgets, with the date and where the weather is for. */
@Composable
fun Greeting(place: String?, modifier: Modifier = Modifier) {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L)
        }
    }
    val locale = Locale.getDefault()
    val date = remember(locale) { DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), locale) }
    Column(modifier) {
        Text(
            greetingFor(now.hour),
            style = TextStyle(fontFamily = Flex, color = Palette.TextPrimary, fontSize = 30.sp, fontWeight = FontWeight(400), shadow = TextShadow),
            maxLines = 1,
        )
        Text(
            listOfNotNull(now.format(date), place?.takeIf { it.isNotBlank() }).joinToString(" · "),
            style = TextStyle(fontFamily = Flex, color = Palette.TextSecondary, fontSize = 16.sp, shadow = TextShadow),
            maxLines = 1,
        )
    }
}
