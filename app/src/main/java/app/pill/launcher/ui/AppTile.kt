package app.pill.launcher.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.AppEntry

private val CardColor = Color(0x40FFFFFF)

/** Squircle app icon. OK opens the app, long-press OK pins or unpins it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppEntry,
    iconSize: Dp,
    pinned: Boolean,
    onOpen: () -> Unit,
    onTogglePin: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.1f else 1f, label = "tileScale")

    Column(
        Modifier
            .width(iconSize + 28.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onOpen, onLongClick = onTogglePin)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(iconSize)
                .clip(Squircle)
                .background(CardColor)
                .border(if (focused) 3.dp else 0.dp, Color.White, Squircle),
        ) {
            Image(
                bitmap = app.icon,
                contentDescription = app.label,
                modifier = Modifier.fillMaxSize().padding(iconSize / 6),
            )
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label,
            color = if (focused) Color.White else Color(0xD9FFFFFF),
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
