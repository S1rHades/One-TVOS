package app.pill.launcher.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.PickedHeadline
import kotlinx.coroutines.delay

private val StoryRed = Color(0xFFFFC9C9)
private val RowShape = RoundedCornerShape(12.dp)

/**
 * Every picked headline, opened with OK on the headlines card: the list on the left (Up /
 * Down moves through it, the focused one in white) and the story on the right, which
 * dissolves to the next as you move. Back closes it.
 */
@Composable
fun HeadlinePage(picked: List<PickedHeadline>, start: Int, onDismiss: () -> Unit) {
    if (picked.isEmpty()) return
    var index by remember { mutableIntStateOf(start.coerceIn(0, picked.lastIndex)) }
    val first = remember { FocusRequester() }
    val list = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))

    OverlayPage(onDismiss = onDismiss, width = 880.dp, framed = false) { _ ->
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { first.requestFocus() }
        }
        PageTitle(
            "Headlines",
            listOf(if (picked.size == 1) "1 story" else "${picked.size} stories", "Up / Down for more", "Back to close").joinToString(" · "),
            Modifier.reveal(0),
        )
        Row(Modifier.fillMaxWidth().height(404.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PageCard(1, Modifier.width(320.dp).fillMaxHeight()) {
                LazyColumn(state = list, verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(picked) { i, item ->
                        HeadlineRow(
                            item = item,
                            selected = i == index,
                            onFocus = { index = i },
                            modifier = if (i == index) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
            PageCard(2, Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(
                    targetState = index,
                    transitionSpec = {
                        (fadeIn(tween(Motion.Enter, easing = Motion.EnterEasing)) + slideInVertically(tween(Motion.Enter, easing = Motion.EnterEasing)) { it / 24 }) togetherWith
                            fadeOut(tween(Motion.Exit / 2))
                    },
                    label = "story",
                    modifier = Modifier.fillMaxSize(),
                ) { i ->
                    val item = picked[i]
                    val h = item.headline
                    Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                        PageLabel(
                            listOfNotNull(item.topic?.title ?: "Top stories", h.source.title, h.published?.let { ageText(it) }).joinToString(" · "),
                            color = StoryRed,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(h.title, color = Palette.TextPrimary, fontSize = 27.sp, fontWeight = FontWeight(650), lineHeight = 33.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                        if (h.summary.isNotBlank()) {
                            Spacer(Modifier.height(14.dp))
                            Text(h.summary, color = Palette.TextSecondary, fontSize = 18.sp, lineHeight = 26.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.weight(1f))
                        PageFooter("${i + 1} of ${picked.size}")
                    }
                }
            }
        }
    }
}

/** A headline in the list: its title (two lines at most) and where and when it's from. */
@Composable
private fun HeadlineRow(item: PickedHeadline, selected: Boolean, onFocus: () -> Unit, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (selected) Palette.Focus else Color.Transparent, tween(Motion.Quick), label = "rowBg")
    val fg by animateColorAsState(if (selected) Palette.Ink else Palette.TextPrimary, tween(Motion.Quick), label = "rowText")
    val h = item.headline
    Box(
        modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(bg, RowShape)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onFocus() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column {
            Text(h.title, color = fg, fontSize = 14.sp, fontWeight = FontWeight(600), lineHeight = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(3.dp))
            Text(
                listOfNotNull(h.source.title, h.published?.let { ageText(it) }).joinToString(" · "),
                color = fg.copy(alpha = 0.7f), fontSize = 12.sp, maxLines = 1,
            )
        }
    }
}
