package app.pill.launcher.ui

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pill.launcher.tr
import kotlinx.coroutines.delay

/**
 * The launcher's own page while Android installs an update of it: Android stops the
 * launcher to replace it, so this says what is happening, and the new launcher opens its
 * home screen by itself when it is in place. Android's confirmation may show over it.
 * Back hides it (the update carries on).
 */
@Composable
fun UpdatingPage(version: String?, onHide: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Palette.InkLight, Palette.Ink)))
            .focusRequester(focus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK && e.nativeKeyEvent.action == KeyEvent.ACTION_UP) onHide()
                // Nothing else behind it reacts while it shows
                true
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.width(560.dp)) {
            CircularProgressIndicator(color = Palette.Accent, strokeWidth = 4.dp, modifier = Modifier.size(52.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                if (version != null) tr("Updating Pill Launcher to %1\$s", version) else tr("Updating Pill Launcher"),
                color = Palette.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                tr("The launcher closes for a moment while Android puts the new version in place, then comes back to the home screen by itself."),
                color = Palette.TextSecondary,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center,
            )
            Text(tr("If Android asks, choose Install."), color = Palette.TextTertiary, fontSize = 15.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Said on the home screen for a few seconds after the launcher was updated from itself. */
@Composable
fun UpdatedNotice(version: String, onGone: () -> Unit) {
    var shown by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(5000)
        shown = false
        delay(400)
        onGone()
    }
    Box(Modifier.fillMaxSize().padding(bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
        AnimatedVisibility(shown, enter = fadeIn(), exit = fadeOut()) {
            Text(
                tr("Pill Launcher updated to %1\$s", version),
                color = Palette.TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Palette.Panel).padding(horizontal = 22.dp, vertical = 11.dp),
            )
        }
    }
}
