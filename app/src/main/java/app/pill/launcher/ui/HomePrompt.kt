package app.pill.launcher.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PromptShape = RoundedCornerShape(22.dp)

/**
 * Shown on the home screen while Pill Launcher is neither the TV's home screen nor
 * helped by the Home button helper, because until then Home and closing an app lead
 * to the TV's own launcher. [onSetUp] tries both ways; [onDismiss] hides it for good.
 */
@Composable
fun HomePrompt(onSetUp: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .glass(PromptShape)
            .padding(start = 22.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            Text("Closing an app takes you to the TV's own home screen", color = Palette.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                "Make Pill Launcher your home screen, so Home and closed apps come back here.",
                color = Palette.TextSecondary,
                fontSize = 14.sp,
                maxLines = 2,
            )
        }
        Spacer(Modifier.width(18.dp))
        PillButton("Set up", primary = true, onClick = onSetUp)
        Spacer(Modifier.width(8.dp))
        PillButton("Not now", primary = false, onClick = onDismiss)
    }
}

/**
 * A button for prompts and dialogs. It is the pages' [PageButton], so every action in the
 * launcher has one shape: alike things look alike.
 */
@Composable
fun PillButton(label: String, primary: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) =
    PageButton(label, onClick, modifier, primary = primary, enabled = enabled)
